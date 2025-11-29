package com.arun.temporal.worker.workflow;

import com.arun.temporal.worker.model.activity.*;
import com.arun.temporal.worker.activities.BulkActivities;
import com.arun.temporal.worker.model.BulkApiRequest;
import com.arun.temporal.worker.model.BulkWorkflowResponse;
import com.arun.temporal.worker.model.ReportData;
import com.arun.temporal.worker.util.S3Util;
import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.workflow.Async;
import io.temporal.workflow.Promise;
import io.temporal.workflow.Workflow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

import static com.arun.temporal.worker.constant.Constants.*;
import static com.arun.temporal.worker.util.Util.*;


public class BulkWorkflowImpl implements BulkWorkflow {
    private static final Logger logger = LoggerFactory.getLogger(BulkWorkflowImpl.class);
    private final BulkActivities bulkActivities;

    public BulkWorkflowImpl() {
        ActivityOptions options = ActivityOptions.newBuilder()
                .setStartToCloseTimeout(Duration.ofMinutes(MAX_ACTIVITY_RUN_TIME_OUT))
                .setHeartbeatTimeout(Duration.ofMinutes(5))
                .setRetryOptions(RetryOptions.newBuilder()
                        .setInitialInterval(Duration.ofSeconds(1))
                        .setMaximumInterval(Duration.ofMinutes(1))
                        .setBackoffCoefficient(2.0)
                        .setMaximumAttempts(3)
                        .build())
                .build();
        this.bulkActivities = Workflow.newActivityStub(BulkActivities.class, options);
    }

    @Override
    public BulkWorkflowResponse executeWorkflow(BulkApiRequest input) {
        AtomicLong successfulRecordCount = new AtomicLong(0);
        AtomicLong totalRecordCount = new AtomicLong(0);
        String workspaceId = input.getWorkspaceId();
        String email = input.getEmailId();
        String currentQueryId = getCurrentQueryId(input.getApiType(), input.getFileName(), input.getOutputFileId());
        setValueInMDCContext(workspaceId, email, input.getRequestId(), currentQueryId, input.getOutputFileId());
        logger.info(currentQueryId);
        String fileInputObjectKey = S3Util.getFileInputObjectKey(S3Util.generateFileNameWithId(input.getFileName(), input.getFileId()), workspaceId, email);
        String finalOutputFileKey = S3Util.getCsvFileOutputObjectKey(S3Util.generateFileNameWithId(input.getFileName(), input.getFileId()), input.getOutputFileId(), workspaceId, email);
        String finalReportFileKey = S3Util.getReportOutputObjectKey(input.getOutputFileId(), workspaceId, email);
        GenerateUploadIdResponse uploadIdResponse = bulkActivities.generateUploadId(new GenerateUploadIdRequest(finalOutputFileKey));
        List<PartDetail> partDetails = new CopyOnWriteArrayList<>();
        List<FileChunk> listOfChunks = bulkActivities.splitFileIntoChunks(new SplitChunkRequest(fileInputObjectKey, input.getDelimiter())).chunks();
        logger.info("Total Chunks: {} for query id {}", listOfChunks.size(), currentQueryId);
        ReportData finalReportData = new ReportData();
        boolean isRequiredReport = REPORT_HEADER.equalsIgnoreCase(input.getReportRequired());
        int maxParallelChunks = bulkActivities.getMaxParallelChunks().maxParallelChunks();
        List<Promise<ChunkProcessingResult>> running = new ArrayList<>();
        Queue<FileChunk> chunkQueue = new LinkedList<>(listOfChunks);
        startChunkProcessing(running, chunkQueue, input, maxParallelChunks, uploadIdResponse.uploadId(), totalRecordCount);
        while (!running.isEmpty()) {
            Promise.anyOf(running).get();
            List<Promise<ChunkProcessingResult>> completed = running.stream()
                    .filter(Promise::isCompleted)
                    .toList();
            processCompleted(completed, successfulRecordCount, finalReportData, isRequiredReport, partDetails);
            running.removeAll(completed);
            startChunkProcessing(running, chunkQueue, input, maxParallelChunks, uploadIdResponse.uploadId(), totalRecordCount);
        }
        logger.info("Total records Submit: {}", totalRecordCount.get());
        logger.info("Total records Processed: {}", successfulRecordCount.get());
        bulkActivities.finalizeFileUpload(new CompleteMultipartUploadRequest(finalOutputFileKey, uploadIdResponse.uploadId(), partDetails));
        if (REPORT_HEADER.equalsIgnoreCase(input.getReportRequired())) {
            logger.info("generating cass report");
            bulkActivities.createAndUploadReport(new CreateReportRequest(finalReportFileKey + REPORT_NAME, finalReportData, input.getReportDetail(), "CASS"));
        }
        logger.info("job completed for query id {} and job id {}", currentQueryId, input.getOutputFileId());
        return BulkWorkflowResponse.builder().apiType(input.getApiType()).header(input.getReportRequired()).emailId(input.getEmailId()).workspaceId(input.getWorkspaceId()).fileId(input.getFileId()).fileName(input.getFileName()).outputFileId(input.getOutputFileId()).build();
    }

    private void startChunkProcessing(List<Promise<ChunkProcessingResult>> running, Queue<FileChunk> chunkQueue, BulkApiRequest input,
                                      int maxParallelChunk, String uploadId, AtomicLong totalRecordCount) {
        while (running.size() < maxParallelChunk && !chunkQueue.isEmpty()) {
            FileChunk next = chunkQueue.poll();
            ChunkSubmitResult totalBatch = bulkActivities.uploadChunk(next, input);
			logger.info("Submitted chunk {} Completed with {} records and {} batches.", next.chunkNumber(), totalBatch.totalRecords(), totalBatch.totalBatches());
            totalRecordCount.addAndGet(totalBatch.totalRecords());
            running.add(startChunkAsync(next, input, uploadId, totalBatch.totalBatches()));
        }
    }


    private void processCompleted(List<Promise<ChunkProcessingResult>> completed, AtomicLong successfulRecordCount, ReportData finalReportData, boolean isRequiredReport, List<PartDetail> partDetails) {
        completed.forEach(promise -> {
            ChunkProcessingResult result = promise.get();
            logger.info("chunk completed {} total batch in chunk {} total record in chunk {}", result.id(), result.totalBatchCount(), result.totalRecordCount());
            successfulRecordCount.addAndGet(result.totalRecordCount());
            finalReportData.addReportData(result.reportData(), isRequiredReport);
            partDetails.add(new PartDetail(result.id(), result.uploadTagId()));
        });
    }

    private Promise<ChunkProcessingResult> startChunkAsync(
            FileChunk state,
            BulkApiRequest input,
            String uploadId,
            int totalBatch) {
        return Async.function(() -> bulkActivities.processChunk(new ProcessChunkRequest(state.chunkNumber(), uploadId, totalBatch, input)));
    }
}
