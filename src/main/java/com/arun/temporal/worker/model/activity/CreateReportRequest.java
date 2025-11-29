package com.arun.temporal.worker.model.activity;

import com.arun.temporal.worker.model.ReportData;
import com.arun.temporal.worker.model.ReportDetail;

public record CreateReportRequest(
        String outputKey,
        ReportData reportData,
        ReportDetail reportDetail,
        String reportType) {
}
