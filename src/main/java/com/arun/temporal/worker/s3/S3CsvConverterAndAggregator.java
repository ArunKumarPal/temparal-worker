package com.arun.temporal.worker.s3;

import com.arun.temporal.worker.redis.RedisService;
import com.arun.temporal.worker.util.MDCLogging;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.arun.temporal.worker.configuration.AwsConfiguration;
import com.arun.temporal.worker.constant.Constants;
import com.arun.temporal.worker.exception.BulkProcessorException;
import com.arun.temporal.worker.model.BulkApiRequest;
import com.arun.temporal.worker.model.BulkResponse;
import com.arun.temporal.worker.model.ReportData;
import com.arun.temporal.worker.model.activity.ChunkDetail;
import com.arun.temporal.worker.model.activity.ChunkProcessingResult;
import com.arun.temporal.worker.service.S3Service;
import com.arun.temporal.worker.util.AddressReader;
import com.arun.temporal.worker.util.S3Util;
import io.lettuce.core.KeyValue;
import io.temporal.activity.Activity;
import io.temporal.activity.ActivityExecutionContext;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import static com.arun.temporal.worker.constant.Constants.*;

@Singleton
public class S3CsvConverterAndAggregator {
    private static final Logger logger = LoggerFactory.getLogger(S3CsvConverterAndAggregator.class);
    private final String sourceBucket;
    private final String destinationBucket;
    private final S3Service s3Service;
    Map<String, String> callerMdc;
    private static final ExecutorService executorService = Executors.newFixedThreadPool(10);
    public static final ReentrantLock lock = new ReentrantLock();
    private final RedisService redisService;

    public S3CsvConverterAndAggregator(AwsConfiguration awsConfiguration, S3Service s3Service, RedisService redisService) {
        this.s3Service = s3Service;
        this.destinationBucket = S3Util.getBucketKey(awsConfiguration.getEnv(), awsConfiguration.getRegion(), BULK_API);
        this.sourceBucket = S3Util.getBucketKey(awsConfiguration.getEnv(), awsConfiguration.getRegion(), BULK_RESULT);
        this.redisService = redisService;
    }

    public ChunkProcessingResult startUploadingProcess(int chunkId, String queryId, int totalBatches, BulkApiRequest request, String uploadId) {
        logger.info("start uploading chunk process  for chunk number {} total Batch in chunk {}", chunkId, totalBatches);
        List<String> completedBatches = new ArrayList<>();
        List<CompletableFuture<Boolean>> featureList = new ArrayList<>();
        ActivityExecutionContext ctx = Activity.getExecutionContext();
        String finalFileKey = S3Util.getCsvFileOutputObjectKey(S3Util.generateFileNameWithId(request.getFileName(), request.getFileId()), request.getOutputFileId(), request.getWorkspaceId(), request.getEmailId());
        String jsonOutputFolderPath = String.format("%s%s/%s/%s", "KF_RSLT_", request.getWorkspaceId(), request.getApiType(), queryId);
        long lastRunTime = 0L;
        String tempFileKey = finalFileKey + "temp" + chunkId;
        String chunkUploadId = s3Service.getUploadId(tempFileKey, destinationBucket).join();
        ChunkDetail chunkDetail = new ChunkDetail(tempFileKey, chunkUploadId, uploadId, finalFileKey);
        if (chunkId == 1) {
            chunkDetail.getDataBuffer().append(Constants.getOutputCsvHeaders(request.getDelimiter()));
        }
        while (completedBatches.size() < totalBatches) {
            List<String> newCompletedBatches = redisService.getCompletedBatch(queryId).filter(v -> v.getValue().equals("COMPLETED")).map(KeyValue::getKey).filter(batchId -> Integer.parseInt(batchId.substring(queryId.length()+1).split("_")[0]) == chunkId &&  !completedBatches.contains(batchId)).collectList().block();
            lastRunTime = sendHeartbeatIfNeeded(ctx, lastRunTime);
            if (newCompletedBatches == null || newCompletedBatches.isEmpty()) {
                waitForNextIteration();
            } else {
                completedBatches.addAll(newCompletedBatches);
                newCompletedBatches.forEach(batchId -> featureList.add(getS3ObjectConvertToLines(batchId, chunkDetail, jsonOutputFolderPath, request)));
            }
        }

        while (true) {
            try {
                CompletableFuture.allOf(featureList.toArray(CompletableFuture[]::new)).get(10L, TimeUnit.SECONDS);
                break;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.warn("get interrupted exception", e);
            } catch (TimeoutException var14) {
                lastRunTime = sendHeartbeatIfNeeded(ctx, lastRunTime);
            } catch (ExecutionException e) {
                throw new BulkProcessorException("Exception when aggregate the csv lines", e);
            }
        }

        String tagId = processChunkUpload(featureList, chunkDetail, chunkId);
        return new ChunkProcessingResult(chunkId, chunkDetail.getTotalRecordCount().get(), completedBatches.size(), chunkDetail.getReportData(), tagId);
    }

    private String processChunkUpload(List<CompletableFuture<Boolean>> featureList, ChunkDetail chunkDetail, int chunkId) {
        return CompletableFuture.allOf(featureList.toArray(CompletableFuture[]::new))
                .thenCompose(ignore -> (!chunkDetail.getDataBuffer().isEmpty()) ? s3Service.uploadFilePart(destinationBucket, chunkDetail.getChunkUploadKey(), chunkDetail.getChunkUploadId(), chunkDetail.getPartNo().getAndIncrement(), chunkDetail.getDataBuffer(), chunkDetail.getParts()) : CompletableFuture.completedFuture(false))
                .thenCompose(ignore -> s3Service.completeMultiPartUpload(destinationBucket, chunkDetail.getChunkUploadKey(), chunkDetail.getChunkUploadId(), chunkDetail.getParts()))
                .thenCompose(ignore -> s3Service.uploadChunkFilePart(destinationBucket, chunkDetail.getFinalUploadKey(), chunkDetail.getFinalUploadId(), chunkId, chunkDetail.getChunkUploadKey())
                        .thenCompose(chunkTagId -> s3Service.deleteChunkData(destinationBucket, chunkDetail.getChunkUploadKey()).thenApply(ignore2 -> chunkTagId)))
                .join();
    }

    private CompletableFuture<Boolean> getS3ObjectConvertToLines(String batchId, ChunkDetail chunkDetail, String jsonOutputFolderPath, BulkApiRequest request) {
        String finalKey = S3Util.generateSFFileKey(jsonOutputFolderPath, batchId);
        return s3Service.getOutputStream(sourceBucket, finalKey)
                .thenApplyAsync(responseBytes -> {
                    if (responseBytes.response().contentLength() > 0L) {
                        String csvData = generateCsvRowsString(generateBulkResponseObject(responseBytes), chunkDetail.getReportData(), chunkDetail.getTotalRecordCount(), batchId, request);
                        return fillDataBufferAndUpload(csvData, destinationBucket, chunkDetail);
                    } else {
                        MDCLogging.setMDCContext(callerMdc);
                        logger.error("Issue for batchId {} with response bytes {}", batchId, responseBytes.asUtf8String());
                        return CompletableFuture.completedFuture(false);
                    }
                }, executorService).join();
    }

    private String generateCsvRowsString(BulkResponse bulkResponse, ReportData reportData, AtomicInteger totalRecordCount, String currentBatchId, BulkApiRequest request) {
        if (bulkResponse.getResponses() != null && !bulkResponse.getResponses().isEmpty()) {
            totalRecordCount.addAndGet(bulkResponse.getResponses().size());
            return bulkResponse.getResponses().stream().map(response -> AddressReader.getRowValueForOutputCsv(response, request.getDelimiter(), reportData)).collect(Collectors.joining("\n"));
        } else {
            MDCLogging.setMDCContext(callerMdc);
            logger.error("No responses object found. Instead found : {} for batchId : {}", bulkResponse, currentBatchId);
            return EMPTY_STRING;
        }
    }

    private BulkResponse generateBulkResponseObject(ResponseBytes<GetObjectResponse> responseBytes) {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode jsonNode = objectMapper.readTree(responseBytes.asUtf8String());
            return objectMapper.treeToValue(jsonNode, BulkResponse.class);
        } catch (JsonProcessingException e) {
            MDCLogging.setMDCContext(callerMdc);
            throw new BulkProcessorException(e.getMessage(), e);
        }
    }

    private CompletableFuture<Boolean> fillDataBufferAndUpload(String csvData, String destinationBucket, ChunkDetail chunkDetail) {
        lock.lock();
        chunkDetail.getDataBuffer().append(csvData);
        chunkDetail.getDataBuffer().append("\n");
        long dataSize = chunkDetail.getDataBuffer().length();
        if (dataSize > 10 * 1024 * 1024) {
            StringBuilder finalData = new StringBuilder(chunkDetail.getDataBuffer());
            chunkDetail.getDataBuffer().setLength(0);
            int partNo = chunkDetail.getPartNo().getAndIncrement();
            lock.unlock();
            return s3Service.uploadFilePart(destinationBucket, chunkDetail.getChunkUploadKey(), chunkDetail.getChunkUploadId(), partNo, finalData, chunkDetail.getParts())
                    .exceptionally(throwable -> {
                        logger.error("Error on Upload the part chunk upload key {} part no {}", chunkDetail.getChunkUploadKey(), partNo, throwable);
                        throw new BulkProcessorException("Error on Upload the part" + throwable.getMessage());
                    });
        } else {
            lock.unlock();
            return CompletableFuture.completedFuture(false);
        }
    }

    private long sendHeartbeatIfNeeded(ActivityExecutionContext ctx, long lastRunTime) {
        if (Instant.now().toEpochMilli() - lastRunTime >= 60000) {
            lastRunTime = Instant.now().toEpochMilli();
            ctx.heartbeat("uploading");
        }
        return lastRunTime;
    }

    private void waitForNextIteration() {
        try {
            Thread.sleep(Duration.ofSeconds(20));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("get interrupted exception", e);
        }
    }
}