package com.arun.temporal.worker.model.activity;

import com.arun.temporal.worker.model.BulkApiRequest;

public record ProcessChunkRequest(
        int chunkNumber,
        String uploadId,
        int totalBatch,
        BulkApiRequest bulkApiRequest
) {
}
