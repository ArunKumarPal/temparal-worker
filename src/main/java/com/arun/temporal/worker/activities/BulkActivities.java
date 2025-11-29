package com.arun.temporal.worker.activities;

import com.arun.temporal.worker.model.activity.*;
import com.arun.temporal.worker.model.BulkApiRequest;
import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

@ActivityInterface
public interface BulkActivities {

    @ActivityMethod
    MaxParallelChunkResponse getMaxParallelChunks();

    @ActivityMethod
    GenerateUploadIdResponse generateUploadId(GenerateUploadIdRequest uploadIdRequest);

    @ActivityMethod
    FileChunkListResponse splitFileIntoChunks(SplitChunkRequest splitChunkRequest);

    @ActivityMethod
    ChunkSubmitResult uploadChunk(FileChunk fileChunk, BulkApiRequest bulkApiRequest);

    @ActivityMethod
    ChunkProcessingResult processChunk(ProcessChunkRequest processChunkRequest);

    @ActivityMethod
    void finalizeFileUpload(CompleteMultipartUploadRequest completeMultipartUploadRequest);

    @ActivityMethod
    void createAndUploadReport(CreateReportRequest createReportRequest);
}
