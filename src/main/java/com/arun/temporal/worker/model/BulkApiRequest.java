package com.arun.temporal.worker.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@AllArgsConstructor
@NoArgsConstructor
public class BulkApiRequest {
    private String apiType;
    private String fileId;
    private String fileName;
    private String outputFileId;
    private String workspaceId;
    private String emailId;
    private String requestId;
    private String reportRequired;
    private String delimiter;
    private String apiKey;
    private ReportDetail reportDetail;
}
