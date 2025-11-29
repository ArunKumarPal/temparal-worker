package com.arun.temporal.worker.activities;

import com.arun.temporal.worker.kafka.KafkaEventProducer;
import com.arun.temporal.worker.model.*;
import com.arun.temporal.worker.model.activity.*;
import com.arun.temporal.worker.redis.RedisService;
import com.arun.temporal.worker.util.*;
import com.arun.temporal.worker.configuration.AwsConfiguration;
import com.arun.temporal.worker.configuration.RequestConfiguration;
import com.arun.temporal.worker.exception.BulkProcessorException;
import com.arun.temporal.worker.s3.S3CsvConverterAndAggregator;
import com.arun.temporal.worker.service.S3Service;
import com.arun.temporal.worker.worker.WorkerStatus;
import io.micronaut.context.annotation.Value;
import io.temporal.failure.ApplicationFailure;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.services.s3.model.CompletedPart;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static com.arun.temporal.worker.constant.Constants.*;
import static com.arun.temporal.worker.util.FileChunkReader.*;
import static com.arun.temporal.worker.util.Util.*;

@Singleton
public class BulkActivitiesImpl implements BulkActivities {

    private static final Logger logger = LoggerFactory.getLogger(BulkActivitiesImpl.class);
    private final S3Service s3Service;
    private final RequestConfiguration requestConfiguration;
    private final ReportUtil reportUtil;
    private final AwsConfiguration awsConfiguration;
    private final S3CsvConverterAndAggregator s3CsvConverterAndAggregator;
    private final WorkerStatus workerStatus;
    private final RedisService redisService;
    private final KafkaEventProducer kafkaEventProducer;

    @Value("${bulk.processor.min.chunk.size:5242880}")
    private long minChunkSize;

    @Value("${bulk.processor.min.lines.per.chunk:50000}")
    private int minLinesPerChunk;

    @Value("${bulk.processor.max.chunk:4}")
    private int maxParallelChunk;

    @Value("${bulk.processor.sample.lines:100}")
    private int sampleLines;

    public BulkActivitiesImpl(S3Service s3Service, RequestConfiguration requestConfiguration, ReportUtil reportUtil, AwsConfiguration awsConfiguration, S3CsvConverterAndAggregator s3CsvConverterAndAggregator, WorkerStatus workerStatus, RedisService redisService, KafkaEventProducer kafkaEventProducer) {
        this.s3Service = s3Service;
        this.requestConfiguration = requestConfiguration;
        this.reportUtil = reportUtil;
        this.awsConfiguration = awsConfiguration;
        this.s3CsvConverterAndAggregator = s3CsvConverterAndAggregator;
        this.workerStatus = workerStatus;
        this.redisService = redisService;
        this.kafkaEventProducer = kafkaEventProducer;
    }

    @Override
    public MaxParallelChunkResponse getMaxParallelChunks() {
        return new MaxParallelChunkResponse(maxParallelChunk);
    }

    @Override
    public GenerateUploadIdResponse generateUploadId(GenerateUploadIdRequest uploadIdRequest) {
        workerStatus.startActivity();
        String bucketName = S3Util.getBucketKey(awsConfiguration.getEnv(), awsConfiguration.getRegion(), BULK_API);
        try {
            return s3Service.getUploadId(uploadIdRequest.fileUploadKey(), bucketName)
                    .thenApply(GenerateUploadIdResponse::new).join();
        } catch (Exception ex) {
            if (ex.getCause() instanceof NoSuchKeyException) {
                logger.error("Output Bucket not found in S3 for bucket {} key : {}", bucketName, uploadIdRequest.fileUploadKey(), ex);
                throw ApplicationFailure.newNonRetryableFailure(
                        "Output Bucket not found ",
                        "UploadIdException"
                );
            } else {
                logger.error("Error in generating upload Id for bucket: {} key: {}", bucketName, uploadIdRequest.fileUploadKey(), ex);
                throw ApplicationFailure.newFailure(
                        ex.getMessage(),
                        ex.getCause().getClass().getName()
                );
            }
        } finally {
            workerStatus.endActivity();
        }
    }

    @Override
    public FileChunkListResponse splitFileIntoChunks(SplitChunkRequest splitChunkRequest) {
        workerStatus.startActivity();
        String bucketName = S3Util.getBucketKey(awsConfiguration.getEnv(), awsConfiguration.getRegion(), BULK_API);
        try {
            return s3Service.getInputFileSize(bucketName, splitChunkRequest.key())
                    .thenCompose(fileSize -> estimateLineSize(sampleLines, splitChunkRequest.key(), fileSize, splitChunkRequest.delimiter())
                            .thenApply(fileDetail -> getFileChunkList(minChunkSize, minLinesPerChunk, fileDetail)))
                    .join();
        } catch (Exception ex) {
            if (ex.getCause() instanceof NoSuchKeyException) {
                logger.error("Input File not found in S3 for key: {}", splitChunkRequest.key(), ex);
                throw ApplicationFailure.newNonRetryableFailure(
                        "S3 file missing ",
                        "NoSuchKeyException"
                );
            } else {
                logger.error("Error in splitting file into chunks for key: {} delimiter: {}", splitChunkRequest.key(), splitChunkRequest.delimiter(), ex);
                throw ApplicationFailure.newFailure(
                        ex.getMessage(),
                        ex.getCause().getClass().getName()
                );
            }
        } finally {
            workerStatus.endActivity();
        }
    }

    @Override
    public ChunkSubmitResult uploadChunk(FileChunk fileChunk, BulkApiRequest bulkApiRequest) {
        workerStatus.startActivity();
        setValueInMDCContext(bulkApiRequest.getWorkspaceId(), bulkApiRequest.getEmailId(), bulkApiRequest.getEmailId(), getCurrentQueryId(bulkApiRequest.getApiType(), bulkApiRequest.getFileName(), bulkApiRequest.getOutputFileId()), bulkApiRequest.getOutputFileId());
        logger.info("submitFileChunk {}", fileChunk.chunkNumber());
        try {
            return submitChunk(fileChunk, bulkApiRequest).join();
        } catch (Exception ex) {
            if (ex.getCause() instanceof NoSuchKeyException) {
                logger.error("Input File not found in S3 for file Name: {} file id: {}", bulkApiRequest.getFileName(), bulkApiRequest.getFileId(), ex);
                throw ApplicationFailure.newNonRetryableFailure(
                        "S3 file missing ",
                        "NoSuchKeyException"
                );
            } else {
                logger.error("Error in submit chunks for chunk No : {} file Name: {} file id: {}", fileChunk.chunkNumber(), bulkApiRequest.getFileName(), bulkApiRequest.getFileId(), ex);
                throw ApplicationFailure.newFailure(
                        ex.getMessage(),
                        ex.getCause().getClass().getName()
                );
            }
        } finally {
            workerStatus.endActivity();
        }
    }

    @Override
    public ChunkProcessingResult processChunk(ProcessChunkRequest processChunkRequest) {
        workerStatus.startActivity();
        String queryId = getCurrentQueryId(processChunkRequest.bulkApiRequest().getApiType(), processChunkRequest.bulkApiRequest().getFileName(), processChunkRequest.bulkApiRequest().getOutputFileId());
        try {
            return s3CsvConverterAndAggregator.startUploadingProcess(processChunkRequest.chunkNumber(), queryId, processChunkRequest.totalBatch(), processChunkRequest.bulkApiRequest(), processChunkRequest.uploadId());
        } catch (Exception e) {
            logger.error("Error in processing chunk for chunk No : {} file Name: {} file id: {}", processChunkRequest.chunkNumber(), processChunkRequest.bulkApiRequest().getFileName(), processChunkRequest.bulkApiRequest().getFileId(), e);
            throw ApplicationFailure.newFailure(
                    "Error in processing chunk",
                    "ProcessChunkException"
            );
        } finally {
            workerStatus.endActivity();
        }

    }

    @Override
    public void finalizeFileUpload(CompleteMultipartUploadRequest completeMultipartUploadRequest) {
        workerStatus.startActivity();
        String bucketName = S3Util.getBucketKey(awsConfiguration.getEnv(), awsConfiguration.getRegion(), BULK_API);
        List<CompletedPart> parts = new ArrayList<>(completeMultipartUploadRequest.parts().stream().map(part -> CompletedPart.builder().partNumber(part.partNumber()).eTag(part.eTag()).build()).toList());
        try {
            s3Service.completeMultiPartUpload(bucketName, completeMultipartUploadRequest.fileKey(), completeMultipartUploadRequest.uploadId(), parts).join();
        } catch (Exception e) {
            logger.error("Error in finalize File Upload on key {} by upload id {}", completeMultipartUploadRequest.fileKey(), completeMultipartUploadRequest.uploadId(), e);
            throw ApplicationFailure.newFailure(
                    "Error in complete file upload",
                    "finalizeFileUploadException"
            );
        } finally {
            workerStatus.endActivity();
        }
    }

    @Override
    public void createAndUploadReport(CreateReportRequest createReportRequest) {
        workerStatus.startActivity();
        String bucketName = S3Util.getBucketKey(awsConfiguration.getEnv(), awsConfiguration.getRegion(), BULK_API);
        try {
            reportUtil.generateAndUploadReport(bucketName, createReportRequest.outputKey(), createReportRequest.reportData(), createReportRequest.reportDetail()).join();
        } catch (Exception e) {
            logger.error("Error in create and upload report report type {} on output key {}", createReportRequest.reportType(), createReportRequest.outputKey(), e);
            throw ApplicationFailure.newFailure(
                    "Error in create and upload report",
                    "createAndUploadReportException"
            );
        } finally {
            workerStatus.endActivity();
        }
    }

    private CompletableFuture<FileMetadata> estimateLineSize(int sampleLines, String key, long fileSize, String delimiter) {
        String bucketName = S3Util.getBucketKey(awsConfiguration.getEnv(), awsConfiguration.getRegion(), BULK_API);
        return s3Service.getInputStream(bucketName, key, 0L, Math.min(1024 * 1024L, fileSize))
                .thenApply(responseStream -> {
                    try {
                        FileMetadata metadata = getFileMetadata(sampleLines, fileSize, delimiter, responseStream);
                        AddressReader.validateHeaders(metadata.headers());
                        return metadata;
                    } catch (IOException e) {
                        throw new BulkProcessorException("Error While Fetching Line Size", e);
                    }
                });
    }

    private CompletableFuture<ChunkSubmitResult> submitChunk(FileChunk fileChunk, BulkApiRequest input) {
        AtomicInteger batchCount = new AtomicInteger();
        AtomicInteger totalRecordCount = new AtomicInteger(0);
        FileMetadata fileMetadata = fileChunk.metadata();
        long adjustedStart = fileChunk.startOffset() - 1;
        long adjustEnd = Math.min(fileChunk.endOffset() + (2L * fileMetadata.avgLineSize()), fileMetadata.fileSize());
        String bucketName = S3Util.getBucketKey(awsConfiguration.getEnv(), awsConfiguration.getRegion(), BULK_API);
        String fileInputObjectKey = S3Util.getFileInputObjectKey(S3Util.generateFileNameWithId(input.getFileName(), input.getFileId()), input.getWorkspaceId(), input.getEmailId());
        return s3Service.getInputStream(bucketName, fileInputObjectKey, adjustedStart, adjustEnd)
                .thenApply(inputStream -> Mono.using(
                        () -> inputStream,
                        inStream -> processAddress(fileChunk, input, batchCount, inputStream, totalRecordCount),
                        inStream -> {
                            try {
                                inStream.close();
                            } catch (IOException e) {
                                logger.error("Error While Closing Buffered Reader", e);
                                throw new BulkProcessorException("Error While Closing Buffered Reader");
                            }
                        }
                ).block())
                .thenApply(s -> new ChunkSubmitResult(batchCount.get(), totalRecordCount.get()));
    }

    private Mono<List<Boolean>> processAddress(FileChunk fileChunk, BulkApiRequest input, AtomicInteger batchCount, InputStream s3ChunkStream, AtomicInteger totalRecordCount) {
        AtomicLong currentPosition = new AtomicLong(fileChunk.startOffset() - 1);
        String queryId = getCurrentQueryId(input.getApiType(), input.getFileName(), input.getOutputFileId());
        String splitter = TAB_SEPARATOR.equals(input.getDelimiter()) ? input.getDelimiter() : "\\".concat(input.getDelimiter());
        int batchSize = requestConfiguration.getBatchSize();
        return readDataFromInputStream(s3ChunkStream, currentPosition, fileChunk.endOffset())
                .skip(1)
                .map(line -> AddressReader.convertToRequest(fileChunk.metadata().headers(), line, splitter, totalRecordCount))
                .buffer(batchSize)
                .delayUntil(lines -> {
                    int currentBatch = batchCount.incrementAndGet();
                    if (currentBatch % 10 == 0) {
                        return Mono.delay(Duration.ofMillis(500));
                    }
                    return Mono.empty();
                })
                .flatMap(lines -> submitRequest(queryId, lines, getBatchId(fileChunk.chunkNumber(), batchCount.get())))
                .collectList();
    }

    private Mono<Boolean> submitRequest(String queryId, List<InputRequest> addresses, String batchId) {
        String queryBatchId = queryId+ "-" + batchId;
        KafkaEvent event = new KafkaEvent(queryId, queryBatchId, addresses.size(), addresses);
        kafkaEventProducer.sendKafkaEvent(event);
        return redisService.saveBatch(queryId, queryBatchId, "SUBMITTED");
    }
}
