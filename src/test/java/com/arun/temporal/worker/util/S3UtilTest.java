package com.arun.temporal.worker.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class S3UtilTest {


    @Test
    void should_get_File_Input_Object_Key(){
        String fileInputObjectKey = S3Util.getFileInputObjectKey("test", "w1", "testUser");
        assertEquals("w1/testUser/input/test", fileInputObjectKey);
    }

    @Test
    void should_get_csv_file_output_object_key(){
        String getCsvFileOutPutObjectKey =  S3Util.getCsvFileOutputObjectKey("test", "outputFile", "w1", "testUser");
        assertEquals("w1/testUser/output/outputFile/test.csv", getCsvFileOutPutObjectKey);
    }

    @Test
    void should_generate_file_name_with_id(){
        String fineNameWithId = S3Util.generateFileNameWithId("test", "1");
        assertEquals("test::1", fineNameWithId);
    }

    @Test
    void should_get_Bucket_Key(){
        String bucketKey = S3Util.getBucketKey("dev", "us-east-2", "tempBucket");
        assertEquals("dev-us-east-2-tempBucket", bucketKey);
    }

    @Test
    void should_generate_sf_file_key(){
        String fileKey = S3Util.generateSFFileKey("testPath", "key1");
        assertEquals("testPath/key1", fileKey);
    }

    @Test
    void should_get_CASS_form_output_object_key() {
        String result = S3Util.getReportOutputObjectKey("output123", "ws1", "userA");
        assertEquals("ws1/userA/output/output123/", result);
    }
}
