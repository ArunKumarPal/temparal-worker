package com.arun.temporal.worker.model;

import lombok.Getter;
import java.util.concurrent.atomic.AtomicInteger;


@Getter
public class ReportData {
    private final AtomicInteger records = new AtomicInteger(0);
    private final AtomicInteger totalName = new AtomicInteger(0);
    public void addRecs() {
        records.incrementAndGet();
    }

    public void addTotalName() {
        totalName.incrementAndGet();
    }

    public void addReportData(ReportData other, boolean isRequiredReport) {
        if (other != null) {
            if (isRequiredReport) {
                records.addAndGet(other.getRecords().get());
                totalName.addAndGet(other.getTotalName().get());
            }
        }
    }
}
