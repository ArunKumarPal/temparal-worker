package com.arun.temporal.worker.workflow;

import com.arun.temporal.worker.model.BulkApiRequest;
import com.arun.temporal.worker.model.BulkWorkflowResponse;
import com.arun.temporal.worker.model.ReportData;
import com.arun.temporal.worker.model.ReportDetail;
import com.arun.temporal.worker.model.activity.*;
import com.arun.temporal.worker.activities.BulkActivities;
import io.temporal.activity.ActivityOptions;
import io.temporal.workflow.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BulkGeoAddressingWorkflowImplTest {
    @Mock
    BulkActivities bulkActivities;
    @Mock
    Promise<ChunkProcessingResult> promise;
    BulkGeoAddressingWorkflowImpl workflow;
    private MockedStatic<Workflow> workflowStatic;
    private WorkflowInfo mockWorkflowInfo;
    private MockedStatic<Async> asyncMock;
    MockedStatic<Promise> promiseMock;

    @BeforeEach
    void setup() throws Exception {
        mockWorkflowInfo = mock(WorkflowInfo.class);
        workflowStatic = mockStatic(Workflow.class);
        workflowStatic.when(() -> Workflow.newActivityStub(
                        eq(BulkActivities.class),
                        any(ActivityOptions.class)))
                .thenReturn(bulkActivities);
        workflowStatic.when(Workflow::getInfo)
                .thenReturn(mockWorkflowInfo);

        workflow = new BulkGeoAddressingWorkflowImpl();
        Field field = BulkGeoAddressingWorkflowImpl.class.getDeclaredField("bulkActivities");
        field.setAccessible(true);
        field.set(workflow, bulkActivities);
        asyncMock = mockStatic(Async.class);
        asyncMock.when(() -> Async.function(any(Functions.Func.class))).thenAnswer(invocation -> promise);
        promiseMock = mockStatic(Promise.class);
        promiseMock.when(() -> Promise.anyOf(any(List.class))).thenAnswer(invocationOnMock -> promise);
        when(promise.isCompleted()).thenReturn(true);
    }

    @AfterEach
    void tearDown() {
        workflowStatic.close();
        asyncMock.close();
        promiseMock.close();
    }

    @Test
    void testExecuteWorkflow_NormalFlow() {
        ReportDetail reportDetail = new ReportDetail("TESTING INC.", "TEST", "1", "delhi india");
        BulkApiRequest input = new BulkApiRequest("TEST", "fileId", "file.csv", "outputId", "wsId", "user@domain.com", "sample", "header", ",", "apiKey", reportDetail);

        when(bulkActivities.generateUploadId(any(GenerateUploadIdRequest.class))).thenReturn(new GenerateUploadIdResponse("uploadId"));
        when(bulkActivities.getMaxParallelChunks()).thenReturn(new MaxParallelChunkResponse(4));
        List<FileChunk> chunks = List.of(new FileChunk(1, 0, 10, mock(FileMetadata.class)));
        when(bulkActivities.splitFileIntoChunks(any(SplitChunkRequest.class))).thenReturn(new FileChunkListResponse(chunks));
        when(bulkActivities.uploadChunk(any(), any())).thenReturn(new ChunkSubmitResult(1, 1));
        doNothing().when(bulkActivities).finalizeFileUpload(any());
        ChunkProcessingResult result = new ChunkProcessingResult(1, 2, 1, new ReportData(), "tag1");
        when(promise.get()).thenReturn(result);
        BulkWorkflowResponse response = workflow.executeWorkflow(input);
        assertNotNull(response);
        assertEquals("TEST", response.apiType());
        assertEquals("header", response.header());
        assertEquals("user@domain.com", response.emailId());
        assertEquals("wsId", response.workspaceId());
        assertEquals("fileId", response.fileId());
        assertEquals("file.csv", response.fileName());
        assertEquals("outputId", response.outputFileId());
    }

    @Test
    void testExecuteWorkflow_ReportFlow() {
        ReportDetail reportDetail = new ReportDetail("TESTING INC.", "TEST", "1", "delhi india");
        BulkApiRequest input = new BulkApiRequest("TEST", "fileId", "file.csv", "outputId", "wsId", "user@domain.com", "sample", "with_report", ",", "apiKey", reportDetail);
        when(bulkActivities.getMaxParallelChunks()).thenReturn(new MaxParallelChunkResponse(4));
        when(bulkActivities.generateUploadId(any(GenerateUploadIdRequest.class))).thenReturn(new GenerateUploadIdResponse("uploadId"));
        List<FileChunk> chunks = List.of(new FileChunk(1, 0, 10, mock(FileMetadata.class)));
        when(bulkActivities.splitFileIntoChunks(any(SplitChunkRequest.class))).thenReturn(new FileChunkListResponse(chunks));
        when(bulkActivities.uploadChunk(any(), any())).thenReturn(new ChunkSubmitResult(1, 1));
        doNothing().when(bulkActivities).finalizeFileUpload(any());
        doNothing().when(bulkActivities).createAndUploadReport(any());
        ReportData reportData = new ReportData();
        ChunkProcessingResult result = new ChunkProcessingResult(1, 2, 1, reportData, "tag1");
        when(promise.get()).thenReturn(result);
        BulkWorkflowResponse response = workflow.executeWorkflow(input);
        assertNotNull(response);
        assertEquals("TEST", response.apiType());
        assertEquals("with_report", response.header());
        assertEquals("user@domain.com", response.emailId());
        assertEquals("wsId", response.workspaceId());
        assertEquals("fileId", response.fileId());
        assertEquals("file.csv", response.fileName());
        assertEquals("outputId", response.outputFileId());

        verify(bulkActivities).createAndUploadReport(argThat(req -> req.reportType().equals("CASS")));
    }
}

