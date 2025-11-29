package com.arun.temporal.worker.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UtilTest {

    @Test
    void get_current_query_id(){
        String apiType = "test";
        String fileName = "SampleFileName";
        String outputFileId = "RandomOutputFileId";

        String currentQueryId = Util.getCurrentQueryId(apiType, fileName, outputFileId);
        assertEquals(apiType + "-" + fileName + "-" + outputFileId, currentQueryId);
    }
}
