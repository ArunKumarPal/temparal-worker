package com.arun.temporal.worker.util;

import static com.arun.temporal.worker.constant.Constants.NAME_ID_SEPARATOR;

public class S3Util {

    private S3Util() {
    }

    public static String getFileInputObjectKey(String fileName, String workSpaceId, String userName) {
        return """
                %s/%s/input/%s
                """.formatted(workSpaceId, userName, fileName).trim();
    }

    public static String getCsvFileOutputObjectKey(String fileName, String outputFileId, String workSpaceId, String userName) {
        return """
                %s/%s/output/%s/%s
                """.formatted(workSpaceId, userName, outputFileId, fileName.concat(".csv")).trim();
    }

    public static String generateFileNameWithId(String fileName, String fileId) {
        return """
                %s%s%s
                """.formatted(fileName, NAME_ID_SEPARATOR, fileId).trim();
    }


    public static String getBucketKey(String env, String region, String bucket) {
        return """
                %s-%s-%s
                """.formatted(env, region, bucket).trim();
    }

    public static String generateSFFileKey(String path, String key) {
        return """
                %s/%s
                """.formatted(path, key).trim();
    }

    public static String getReportOutputObjectKey(String outputFileId, String workSpaceId, String userName) {
        return """
                %s/%s/output/%s/
                """.formatted(workSpaceId, userName, outputFileId).trim();
    }

}
