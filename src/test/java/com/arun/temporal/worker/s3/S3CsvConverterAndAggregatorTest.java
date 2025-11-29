package com.arun.temporal.worker.s3;

import com.arun.temporal.worker.configuration.AwsConfiguration;
import com.arun.temporal.worker.model.BulkApiRequest;
import com.arun.temporal.worker.model.activity.ChunkProcessingResult;
import com.arun.temporal.worker.service.S3Service;
import com.arun.temporal.worker.util.TestData;
import io.temporal.activity.Activity;
import io.temporal.activity.ActivityExecutionContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class S3CsvConverterAndAggregatorTest {

    @Mock
    AwsConfiguration awsConfiguration;
    @Mock
    S3Service s3Service;

    @InjectMocks
    S3CsvConverterAndAggregator aggregator;

    BulkApiRequest request;

    @BeforeEach
    void setUp() {
        request = new BulkApiRequest("TEST", "file.csv", "fileId", "outputId", "wsId", "user@domain.com", "sample", "header", ",", "apiKey", null);
    }

    @Test
    void startUploadingProcess_shouldReturnChunkProcessingResult_whenBatchesAreCompleted() {
        int chunkId = 1;
        int totalBatches = 2;
        ActivityExecutionContext mockContext = mock(ActivityExecutionContext.class);
        var mockedStatic = Mockito.mockStatic(Activity.class);
        mockedStatic.when(Activity::getExecutionContext).thenReturn(mockContext);
        var mockResponseBytes = mock(ResponseBytes.class);
        var response = mock(GetObjectResponse.class);
        when(mockResponseBytes.response()).thenReturn(response);
        when(response.contentLength()).thenReturn(10L);
        when(mockResponseBytes.asUtf8String()).thenReturn(TestData.multipleGeocodedOutput);
        List<String> completedBatchIds = List.of("queryId_1_0", "queryId_1_1");
        when(s3Service.getUploadId(Mockito.anyString(), Mockito.anyString())).thenReturn(CompletableFuture.completedFuture("chunkUploadId"));
        when(s3Service.getOutputStream(Mockito.anyString(), Mockito.anyString())).thenReturn(CompletableFuture.completedFuture(mockResponseBytes));
        when(s3Service.uploadFilePart(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.anyInt(), Mockito.any(), Mockito.any())).thenReturn(CompletableFuture.completedFuture(true));
        when(s3Service.completeMultiPartUpload(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any())).thenReturn(CompletableFuture.completedFuture(true));
        when(s3Service.uploadChunkFilePart(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.anyInt(), Mockito.any())).thenReturn(CompletableFuture.completedFuture("tagId"));
        when(s3Service.deleteChunkData(Mockito.any(), Mockito.any())).thenReturn(CompletableFuture.completedFuture(true));
        ChunkProcessingResult result = aggregator.startUploadingProcess(chunkId, "queryId", totalBatches, request, "uploadId1");
        assertNotNull(result);
        assertEquals(chunkId, result.id());
        assertEquals(totalBatches, result.totalBatchCount());
        assertEquals("tagId", result.uploadTagId());
        mockedStatic.close();
    }

    @Test
    void startUploadingProcess_shouldReturnChunkProcessingResult_whenBatchesAreCompleted_with_report_header() {
        BulkApiRequest request2 = new BulkApiRequest("TEST", "file.csv", "fileId", "outputId", "wsId", "user@domain.com", "sample", "with_report", ",", "apiKey", null);
        int chunkId = 1;
        int totalBatches = 2;
        ActivityExecutionContext mockContext = mock(ActivityExecutionContext.class);
        var mockedStatic = Mockito.mockStatic(Activity.class);
        mockedStatic.when(Activity::getExecutionContext).thenReturn(mockContext);
        var mockResponseBytes = mock(ResponseBytes.class);
        var response = mock(GetObjectResponse.class);
        when(mockResponseBytes.response()).thenReturn(response);
        when(response.contentLength()).thenReturn(10L);
        when(mockResponseBytes.asUtf8String()).thenReturn(TestData.multipleGeocodedOutput);
        List<String> completedBatchIds = List.of("queryId_1_0", "queryId_1_1");
        when(s3Service.getUploadId(Mockito.anyString(), Mockito.anyString())).thenReturn(CompletableFuture.completedFuture("chunkUploadId"));
        when(s3Service.getOutputStream(Mockito.anyString(), Mockito.anyString())).thenReturn(CompletableFuture.completedFuture(mockResponseBytes));
        when(s3Service.uploadFilePart(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.anyInt(), Mockito.any(), Mockito.any())).thenReturn(CompletableFuture.completedFuture(true));
        when(s3Service.completeMultiPartUpload(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any())).thenReturn(CompletableFuture.completedFuture(true));
        when(s3Service.uploadChunkFilePart(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.anyInt(), Mockito.any())).thenReturn(CompletableFuture.completedFuture("tagId"));
        when(s3Service.deleteChunkData(Mockito.any(), Mockito.any())).thenReturn(CompletableFuture.completedFuture(true));
        ChunkProcessingResult result = aggregator.startUploadingProcess(chunkId, "queryId", totalBatches, request2, "uploadId1");
        assertNotNull(result);
        assertEquals(chunkId, result.id());
        assertEquals(totalBatches, result.totalBatchCount());
        assertEquals("tagId", result.uploadTagId());
        mockedStatic.close();
    }


    @Test
    void startUploadingProcess_shouldReturnChunkProcessingResult_whenBatchesAreCompleted_with_seed_exception() {
        int chunkId = 1;
        int totalBatches = 2;
        ActivityExecutionContext mockContext = mock(ActivityExecutionContext.class);
        var mockedStatic = Mockito.mockStatic(Activity.class);
        mockedStatic.when(Activity::getExecutionContext).thenReturn(mockContext);
        var mockResponseBytes = mock(ResponseBytes.class);
        var response = mock(GetObjectResponse.class);
        when(mockResponseBytes.response()).thenReturn(response);
        when(response.contentLength()).thenReturn(10L);
        when(mockResponseBytes.asUtf8String()).thenReturn(TestData.multipleGeocodedOutput);
        List<String> completedBatchIds = List.of("queryId_1_0", "queryId_1_1");
        when(s3Service.getUploadId(Mockito.anyString(), Mockito.anyString())).thenReturn(CompletableFuture.completedFuture("chunkUploadId"));
        when(s3Service.getOutputStream(Mockito.anyString(), Mockito.anyString())).thenReturn(CompletableFuture.completedFuture(mockResponseBytes));
        when(s3Service.uploadFilePart(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.anyInt(), Mockito.any(), Mockito.any())).thenReturn(CompletableFuture.completedFuture(true));
        when(s3Service.completeMultiPartUpload(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any())).thenReturn(CompletableFuture.completedFuture(true));
        when(s3Service.uploadChunkFilePart(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.anyInt(), Mockito.any())).thenReturn(CompletableFuture.completedFuture("tagId"));
        when(s3Service.deleteChunkData(Mockito.any(), Mockito.any())).thenReturn(CompletableFuture.completedFuture(true));
        ChunkProcessingResult result = aggregator.startUploadingProcess(chunkId, "queryId", totalBatches, request, "uploadId1");

        aggregator.startUploadingProcess(chunkId, "queryId", totalBatches, request, "uploadId1");
        aggregator.startUploadingProcess(chunkId, "queryId", totalBatches, request, "uploadId1");
        aggregator.startUploadingProcess(chunkId, "queryId", totalBatches, request, "uploadId1");

        assertNotNull(result);
        assertEquals(chunkId, result.id());
        assertEquals(totalBatches, result.totalBatchCount());
        assertEquals("tagId", result.uploadTagId());
        mockedStatic.close();
    }

    @Test
    void startUploadingProcess_shouldWaitForNextIteration_whenNoCompletedBatchesInitially() {
        int chunkId = 1;
        int totalBatches = 1;
        ActivityExecutionContext mockContext = mock(ActivityExecutionContext.class);
        var mockedStatic = Mockito.mockStatic(Activity.class);
        mockedStatic.when(Activity::getExecutionContext).thenReturn(mockContext);

        var mockResponseBytes = mock(ResponseBytes.class);
        var response = mock(GetObjectResponse.class);
        when(mockResponseBytes.response()).thenReturn(response);
        when(response.contentLength()).thenReturn(10L);
        when(mockResponseBytes.asUtf8String()).thenReturn(TestData.multipleGeocodedOutput);
        when(s3Service.getUploadId(anyString(), anyString())).thenReturn(CompletableFuture.completedFuture("chunkUploadId"));
        when(s3Service.getOutputStream(anyString(), anyString())).thenReturn(CompletableFuture.completedFuture(mockResponseBytes));
        when(s3Service.uploadFilePart(any(), any(), any(), anyInt(), any(), any())).thenReturn(CompletableFuture.completedFuture(true));
        when(s3Service.completeMultiPartUpload(any(), any(), any(), any())).thenReturn(CompletableFuture.completedFuture(true));
        when(s3Service.uploadChunkFilePart(any(), any(), any(), anyInt(), any())).thenReturn(CompletableFuture.completedFuture("tagId"));
        when(s3Service.deleteChunkData(any(), any())).thenReturn(CompletableFuture.completedFuture(true));

        ChunkProcessingResult result = aggregator.startUploadingProcess(chunkId, "queryId", totalBatches, request, "uploadId1");
        assertNotNull(result);
        assertEquals(chunkId, result.id());
        mockedStatic.close();
    }

    @Test
    void startUploadingProcess_shouldFilterBatchIdsCorrectly() throws Exception{
        int chunkId = 2;
        int totalBatches = 5;

        ActivityExecutionContext mockContext = mock(ActivityExecutionContext.class);
        var mockedStatic = Mockito.mockStatic(Activity.class);
        mockedStatic.when(Activity::getExecutionContext).thenReturn(mockContext);
        String resourceName = "TEST-1000-batch";
        String fileContent;
        try (var inputStream = getClass().getClassLoader().getResourceAsStream(resourceName)) {
            assertNotNull(inputStream, "Resource not found: " + resourceName);
            fileContent = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
        var mockResponseBytes = mock(ResponseBytes.class);
        var response = mock(GetObjectResponse.class);
        when(mockResponseBytes.response()).thenReturn(response);
        when(response.contentLength()).thenReturn(10L);
        when(mockResponseBytes.asUtf8String()).thenReturn(fileContent);
        when(s3Service.getUploadId(anyString(), anyString())).thenReturn(CompletableFuture.completedFuture("chunkUploadId"));
        when(s3Service.getOutputStream(anyString(), anyString())).thenReturn(CompletableFuture.completedFuture(mockResponseBytes));
        when(s3Service.uploadFilePart(any(), any(), any(), anyInt(), any(), any())).thenReturn(CompletableFuture.completedFuture(true));
        when(s3Service.completeMultiPartUpload(any(), any(), any(), any())).thenReturn(CompletableFuture.completedFuture(true));
        when(s3Service.uploadChunkFilePart(any(), any(), any(), anyInt(), any())).thenReturn(CompletableFuture.completedFuture("tagId"));
        when(s3Service.deleteChunkData(any(), any())).thenReturn(CompletableFuture.completedFuture(true));

        ChunkProcessingResult result = aggregator.startUploadingProcess(chunkId, "queryId", totalBatches, request, "uploadId1");
        assertNotNull(result);
        assertEquals(chunkId, result.id());
        mockedStatic.close();
    }

    private Object getPrivateField(Object obj, String fieldName) {
        try {
            var field = obj.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.get(obj);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}