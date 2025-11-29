package com.arun.temporal.worker.model.activity;

import com.arun.temporal.worker.model.ReportData;
import lombok.Builder;

@Builder
public record ChunkProcessingResult(
        int id,
        int totalRecordCount,
        int totalBatchCount,
        ReportData reportData,
        String uploadTagId
) {
}
