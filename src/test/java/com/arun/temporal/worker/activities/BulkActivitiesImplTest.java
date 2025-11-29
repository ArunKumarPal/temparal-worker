package com.arun.temporal.worker.activities;

import com.arun.temporal.worker.configuration.RequestConfiguration;
import com.arun.temporal.worker.model.*;
import com.arun.temporal.worker.model.activity.*;
import com.arun.temporal.worker.configuration.AwsConfiguration;
import com.arun.temporal.worker.s3.S3CsvConverterAndAggregator;
import com.arun.temporal.worker.service.S3Service;
import com.arun.temporal.worker.util.ReportUtil;
import com.arun.temporal.worker.worker.WorkerStatus;
import io.temporal.failure.ApplicationFailure;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BulkActivitiesImplTest {

    @Mock
    S3Service s3Service;
    @Mock
    RequestConfiguration requestConfiguration;
    @Mock
    ReportUtil reportUtil;
    @Mock
    AwsConfiguration awsConfiguration;
    @Mock
    S3CsvConverterAndAggregator s3CsvConverterAndAggregator;

    @Mock
    WorkerStatus workerStatus;

    @InjectMocks
    BulkActivitiesImpl bulkActivities;

    BulkApiRequest request;

    @BeforeEach
    void setup() {
        request = new BulkApiRequest("TEST", "file.csv", "fileId", "outputId", "clId", "user@domain.com", "sample", "header", ",", "apiKey", null);
        setPrivateField(bulkActivities, "minChunkSize", 500L);
        setPrivateField(bulkActivities, "minLinesPerChunk", 1);
        setPrivateField(bulkActivities, "maxParallelChunk", 4);
    }

    @Test
    void test_getMaxParallelChunks() {
        assertEquals(4, bulkActivities.getMaxParallelChunks().maxParallelChunks());
    }

    @Test
    void testGenerateUploadId() {
        when(s3Service.getUploadId(anyString(), anyString())).thenReturn(CompletableFuture.completedFuture("uploadId"));
        GenerateUploadIdResponse result = bulkActivities.generateUploadId(new GenerateUploadIdRequest("outputKey"));
        assertEquals("uploadId", result.uploadId());
    }

    @Test
    void testGenerateUploadId_failure() {
        when(s3Service.getUploadId(anyString(), anyString()))
                .thenThrow(new RuntimeException("S3 error", new IOException("IO")));
        ApplicationFailure ex = assertThrows(ApplicationFailure.class, () -> bulkActivities.generateUploadId(new GenerateUploadIdRequest("outputKey")));
        assertTrue(ex.getMessage().contains("S3 error"));
    }

    @Test
    void testGenerateUploadId_valid_failure() {
        when(s3Service.getUploadId(anyString(), anyString()))
                .thenThrow(new RuntimeException("S3 error", NoSuchKeyException.builder().build()));
        ApplicationFailure ex = assertThrows(ApplicationFailure.class, () -> bulkActivities.generateUploadId(new GenerateUploadIdRequest("outputKey")));
        assertTrue(ex.getMessage().contains("Output Bucket not found"));
    }

    @Test
    void testSplitFileIntoChunks() {
        CompletableFuture<Long> fileSizeFuture = CompletableFuture.completedFuture(1000L);
        when(s3Service.getInputFileSize(anyString(), anyString())).thenReturn(fileSizeFuture);
        String content = "input1,input2,input3\n1,test, test3\n2,test4,test5";
        InputStream inputStream = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
        ResponseInputStream<GetObjectResponse> responseInputStream =
                new ResponseInputStream<>(GetObjectResponse.builder().build(), inputStream);
        when(s3Service.getInputStream(anyString(), anyString(), anyLong(), anyLong())).thenReturn(CompletableFuture.completedFuture(responseInputStream));

        List<FileChunk> chunks = bulkActivities.splitFileIntoChunks(new SplitChunkRequest("tempInputKey", ",")).chunks();
        assertFalse(chunks.isEmpty());
        assertEquals(2, chunks.size());
    }

    @Test
    void testSplitFileIntoChunks_failure() {
        when(s3Service.getInputFileSize(anyString(), anyString()))
                .thenThrow(new RuntimeException("S3 error", new IOException("IO")));
        ApplicationFailure ex = assertThrows(ApplicationFailure.class, () -> bulkActivities.splitFileIntoChunks(new SplitChunkRequest("key", ",")));
        assertTrue(ex.getMessage().contains("S3 error"));
    }


    @Test
    void testSplitFileIntoChunks_valid_failure() {
        when(s3Service.getInputFileSize(anyString(), anyString()))
                .thenThrow(new RuntimeException("S3 error", NoSuchKeyException.builder().build()));
        when(awsConfiguration.getEnv()).thenReturn("test");
        when(awsConfiguration.getRegion()).thenReturn("test");
        ApplicationFailure ex = assertThrows(ApplicationFailure.class, () -> bulkActivities.splitFileIntoChunks(new SplitChunkRequest("key", ",")));
        assertTrue(ex.getMessage().contains("S3 file missing"));
    }

    @Test
    void testSplitFileIntoChunks_with_tab() {
        CompletableFuture<Long> fileSizeFuture = CompletableFuture.completedFuture(1000L);
        when(s3Service.getInputFileSize(anyString(), anyString())).thenReturn(fileSizeFuture);
        String content = "input1,input2,input3\n1,test, test3\n2,test4,test5";
        InputStream inputStream = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
        ResponseInputStream<GetObjectResponse> responseInputStream =
                new ResponseInputStream<>(GetObjectResponse.builder().build(), inputStream);
        when(s3Service.getInputStream(anyString(), anyString(), anyLong(), anyLong())).thenReturn(CompletableFuture.completedFuture(responseInputStream));

        List<FileChunk> chunks = bulkActivities.splitFileIntoChunks(new SplitChunkRequest("tempInputKey", "\t")).chunks();
        assertFalse(chunks.isEmpty());
        assertEquals(2, chunks.size());
    }

    @Test
    void testUploadChunk() {
        FileMetadata metadata = new FileMetadata(1, 500, 1000, new String[]{"address", "country"});
        FileChunk chunk = new FileChunk(1, 2, 1000, metadata);
        ChunkSubmitResult expected = new ChunkSubmitResult(1, 2);
        String content = "input1,input2,input3\n1,test, test3\n2,test4,test5";
        InputStream inputStream = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
        when(requestConfiguration.getBatchSize()).thenReturn(4);
        when(s3Service.getInputStream(anyString(), anyString(), anyLong(), anyLong())).thenReturn(CompletableFuture.completedFuture(inputStream));
        ChunkSubmitResult result = bulkActivities.uploadChunk(chunk, request);
        assertEquals(expected, result);
    }

    @Test
    void testUploadChunk_valid_failure() {
        FileMetadata metadata = new FileMetadata(1, 500, 1000, new String[]{"address", "country"});
        FileChunk chunk = new FileChunk(1, 2, 1000, metadata);
        when(awsConfiguration.getEnv()).thenReturn("test");
        when(awsConfiguration.getRegion()).thenReturn("test");
        when(s3Service.getInputStream(anyString(), anyString(), anyLong(), anyLong()))
                .thenThrow(new RuntimeException("S3 error", NoSuchKeyException.builder().build()));
        ApplicationFailure ex = assertThrows(ApplicationFailure.class, () -> bulkActivities.uploadChunk(chunk, request));
        assertTrue(ex.getMessage().contains("S3 file missing "));
    }

    @Test
    void testUploadChunk_failure() {
        FileMetadata metadata = new FileMetadata(1, 500, 1000, new String[]{"address", "country"});
        FileChunk chunk = new FileChunk(1, 2, 1000, metadata);
        when(awsConfiguration.getEnv()).thenReturn("test");
        when(awsConfiguration.getRegion()).thenReturn("test");
        when(s3Service.getInputStream(anyString(), anyString(), anyLong(), anyLong()))
                .thenThrow(new RuntimeException("S3 error", new IOException("IO")));
        ApplicationFailure ex = assertThrows(ApplicationFailure.class, () -> bulkActivities.uploadChunk(chunk, request));
        assertTrue(ex.getMessage().contains("S3 error"));
    }

    @Test
    void testUploadChunk_with_tab_and_cass() throws Exception {
        BulkApiRequest request2 = new BulkApiRequest("GEOCODE", "file.csv", "fileId", "outputId", "wsId", "user@domain.com", "sample", "with_report", "\t", "apiKey", null);
        FileMetadata metadata = new FileMetadata(1, 500, 1000, new String[]{"address", "country"});
        FileChunk chunk = new FileChunk(1, 2, 1000, metadata);
        ChunkSubmitResult expected = new ChunkSubmitResult(1, 2);
        String content = "input1,input2,input3\n1,test, test3\n2,test4,test5";
        InputStream inputStream = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
        when(requestConfiguration.getBatchSize()).thenReturn(4);
        when(s3Service.getInputStream(anyString(), anyString(), anyLong(), anyLong())).thenReturn(CompletableFuture.completedFuture(inputStream));
        ChunkSubmitResult result = bulkActivities.uploadChunk(chunk, request2);
        assertEquals(expected, result);
    }

    @Test
    void testUploadChunk_with_tab_and_with_empty_header() throws Exception {
        BulkApiRequest request2 = new BulkApiRequest("GEOCODE", "file.csv", "fileId", "outputId", "wsId", "user@domain.com", "sample", null, "\t", "apiKey", null);
        FileMetadata metadata = new FileMetadata(1, 500, 1000, new String[]{"input1","input2","input3"});
        FileChunk chunk = new FileChunk(1, 2, 1000, metadata);
        ChunkSubmitResult expected = new ChunkSubmitResult(1, 2);
        String content = "input1,input2,input3\n1,test, test3\n2,test4,test5";
        InputStream inputStream = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
        when(requestConfiguration.getBatchSize()).thenReturn(4);
        when(s3Service.getInputStream(anyString(), anyString(), anyLong(), anyLong())).thenReturn(CompletableFuture.completedFuture(inputStream));
        ChunkSubmitResult result = bulkActivities.uploadChunk(chunk, request2);
        assertEquals(expected, result);
    }

    @Test
    void testProcessChunk() {
        ProcessChunkRequest req = new ProcessChunkRequest(1, "test1",2,  request);
        when(s3CsvConverterAndAggregator.startUploadingProcess(anyInt(), anyString(), anyInt(), any(), anyString())).thenReturn(ChunkProcessingResult.builder().id(1).uploadTagId("tag1").totalBatchCount(2).build());
        ChunkProcessingResult result = bulkActivities.processChunk(req);
        assertNotNull(result);
    }

    @Test
    void testProcessChunk_failure() {
        ProcessChunkRequest req = mock(ProcessChunkRequest.class);
        when(req.bulkApiRequest()).thenReturn(request);
        when(req.chunkNumber()).thenReturn(1);
        when(req.uploadId()).thenReturn("uploadId");
        when(req.totalBatch()).thenReturn(1);
        when(s3CsvConverterAndAggregator.startUploadingProcess(anyInt(), anyString(), anyInt(), any(), anyString()))
                .thenThrow(new RuntimeException("Process error"));
        ApplicationFailure ex = assertThrows(ApplicationFailure.class, () -> bulkActivities.processChunk(req));
        assertTrue(ex.getMessage().contains("Error in processing chunk"));
    }

    @Test
    void testFinalizeFileUpload() {
        CompleteMultipartUploadRequest req = new CompleteMultipartUploadRequest("fileKey", "uploadId", List.of(new PartDetail(1, "etag")));
        when(s3Service.completeMultiPartUpload(anyString(), anyString(), anyString(), anyList())).thenReturn(CompletableFuture.completedFuture(true));
        bulkActivities.finalizeFileUpload(req);
        verify(s3Service).completeMultiPartUpload(anyString(), eq("fileKey"), eq("uploadId"), anyList());
    }

    @Test
    void testFinalizeFileUpload_failure() {
        CompleteMultipartUploadRequest req = new CompleteMultipartUploadRequest("fileKey", "uploadId", List.of(new PartDetail(1, "etag")));
        when(s3Service.completeMultiPartUpload(anyString(), anyString(), anyString(), anyList()))
                .thenThrow(new RuntimeException("Finalize error"));
        ApplicationFailure ex = assertThrows(ApplicationFailure.class, () -> bulkActivities.finalizeFileUpload(req));
        assertTrue(ex.getMessage().contains("Error in complete file upload"));
    }

    @Test
    void testCreateAndUploadReportCass() {
        ReportDetail reportDetail = new ReportDetail("TESTING INC.", "TEST", "1", "delhi, India");
        CreateReportRequest req = new CreateReportRequest("REPORT", new ReportData(), reportDetail, "TEST");
        when(reportUtil.generateAndUploadReport(anyString(), anyString(), any(ReportData.class), any(ReportDetail.class))).thenReturn(CompletableFuture.completedFuture(true));
        assertDoesNotThrow(() -> bulkActivities.createAndUploadReport(req));
    }

    private void setPrivateField(Object target, String fieldName, Object value) {
        try {
            java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
