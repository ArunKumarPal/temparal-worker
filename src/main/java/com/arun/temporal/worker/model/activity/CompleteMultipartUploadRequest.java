package com.arun.temporal.worker.model.activity;

import java.util.List;


public record CompleteMultipartUploadRequest(String fileKey, String uploadId, List<PartDetail> parts) {
}

