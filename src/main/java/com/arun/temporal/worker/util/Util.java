package com.arun.temporal.worker.util;

import org.slf4j.MDC;

public class Util {

    private Util() {
    }

    public static void setValueInMDCContext(String workspaceId, String email, String requestId, String queryId, String jobId) {
        MDC.put("workspace_id", workspaceId);
        MDC.put("user", email);
        MDC.put("req_id", requestId);
        MDC.put("query_id", queryId);
        MDC.put("job_id", jobId);
    }

    public static String getCurrentQueryId(String apiType, String fileName, String outputFileId) {
        return apiType + "-" + fileName + "-" + outputFileId;
    }

    public static String getBatchId(int chunkId, int batchCount) {
        return chunkId + "_" + batchCount;
    }

}
