package com.arun.temporal.worker.model.activity;

import com.arun.temporal.worker.model.ReportData;
import lombok.Getter;
import lombok.Setter;
import software.amazon.awssdk.services.s3.model.CompletedPart;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

@Getter
@Setter
public class ChunkDetail {
    ReportData reportData;
    StringBuilder dataBuffer;
    AtomicInteger totalRecordCount;
    String chunkUploadKey;
    String chunkUploadId;
    AtomicInteger partNo;
    List<CompletedPart> parts;
    String finalUploadId;
    String finalUploadKey;

    public ChunkDetail(String chunkUploadKey, String chunkUploadId, String finalUploadId, String finalUploadKey) {
        this.reportData = new ReportData();
        this.dataBuffer = new StringBuilder();
        this.totalRecordCount = new AtomicInteger(0);
        this.chunkUploadKey = chunkUploadKey;
        this.chunkUploadId = chunkUploadId;
        this.partNo = new AtomicInteger(1);
        this.parts = new CopyOnWriteArrayList<>();
        this.finalUploadId = finalUploadId;
        this.finalUploadKey = finalUploadKey;
    }
}
