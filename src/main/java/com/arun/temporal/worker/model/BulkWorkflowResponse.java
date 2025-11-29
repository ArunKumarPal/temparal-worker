package com.arun.temporal.worker.model;

import lombok.Builder;

@Builder
public record BulkWorkflowResponse(
        String apiType,
        String fileId,
        String fileName,
        String outputFileId,
        String workspaceId,
        String emailId,
        String header,
        String errorMessage
) {
}
