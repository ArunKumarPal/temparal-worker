package com.arun.temporal.worker.service;

import com.arun.temporal.worker.exception.BulkProcessorException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.core.async.AsyncResponseTransformer;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.*;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class S3ServiceTest {

    @Mock
    private S3AsyncClient s3AsyncClient;

    @InjectMocks
    private S3Service s3Service;

    @Mock
    private ResponseBytes<GetObjectResponse> responseBytes;

    @Mock
    private ResponseInputStream<GetObjectResponse> responseInputStream;

    @Test
    void testGetInputFileSize() {
        HeadObjectResponse headObjectResponse = HeadObjectResponse.builder().contentLength(123L).build();
        CompletableFuture<HeadObjectResponse> future = CompletableFuture.completedFuture(headObjectResponse);
        when(s3AsyncClient.headObject(any(HeadObjectRequest.class))).thenReturn(future);

        CompletableFuture<Long> result = s3Service.getInputFileSize("bucket", "key");
        assertEquals(123L, result.join());
    }

    @Test
    void testGetUploadId() {
        CreateMultipartUploadResponse response = CreateMultipartUploadResponse.builder().uploadId("uploadId123").build();
        CompletableFuture<CreateMultipartUploadResponse> future = CompletableFuture.completedFuture(response);
        when(s3AsyncClient.createMultipartUpload(any(CreateMultipartUploadRequest.class))).thenReturn(future);

        CompletableFuture<String> result = s3Service.getUploadId("outputKey", "bucket");
        assertEquals("uploadId123", result.join());
    }

    @Test
    void testCompleteMultiPartUpload2() {
        CompleteMultipartUploadResponse response = CompleteMultipartUploadResponse.builder().build();
        CompletableFuture<CompleteMultipartUploadResponse> future = CompletableFuture.completedFuture(response);
        when(s3AsyncClient.completeMultipartUpload(any(CompleteMultipartUploadRequest.class))).thenReturn(future);

        List<CompletedPart> parts = new ArrayList<>(List.of(CompletedPart.builder().partNumber(2).build(), CompletedPart.builder().partNumber(1).build()));
        CompletableFuture<Boolean> result = s3Service.completeMultiPartUpload("bucket", "key", "uploadId", parts);
        assertTrue(result.join());
        assertEquals(1, parts.getFirst().partNumber());
    }

    @Test
    void testGetInputStreamNormal() {
        CompletableFuture<ResponseInputStream<GetObjectResponse>> future = CompletableFuture.completedFuture(responseInputStream);
        when(s3AsyncClient.getObject(any(GetObjectRequest.class), any(AsyncResponseTransformer.class))).thenReturn(future);

        CompletableFuture<InputStream> result = s3Service.getInputStream("bucket", "key", 0, 10);
        assertEquals(responseInputStream, result.join());
    }

    @Test
    void testGetInputStreamNullThrows() {
        CompletableFuture<ResponseInputStream<GetObjectResponse>> future = CompletableFuture.completedFuture(null);
        when(s3AsyncClient.getObject(any(GetObjectRequest.class), any(AsyncResponseTransformer.class))).thenReturn(future);
        CompletionException ex = assertThrows(
                CompletionException.class,
                () -> s3Service.getInputStream("bucket", "key", 0, 10).join()
        );
        assertInstanceOf(BulkProcessorException.class, ex.getCause());
    }

    @Test
    void testGetOutputStream() {
        CompletableFuture<ResponseBytes<GetObjectResponse>> future = CompletableFuture.completedFuture(responseBytes);
        when(s3AsyncClient.getObject(any(GetObjectRequest.class), any(AsyncResponseTransformer.class))).thenReturn(future);

        CompletableFuture<ResponseBytes<GetObjectResponse>> result = s3Service.getOutputStream("bucket", "inputKey");
        assertEquals(responseBytes, result.join());
    }

    @Test
    void testUploadFilePart() {
        UploadPartResponse uploadPartResponse = UploadPartResponse.builder().eTag("etag123").build();
        CompletableFuture<UploadPartResponse> future = CompletableFuture.completedFuture(uploadPartResponse);
        when(s3AsyncClient.uploadPart(any(UploadPartRequest.class), any(AsyncRequestBody.class))).thenReturn(future);

        List<CompletedPart> completedParts = new java.util.ArrayList<>();
        CompletableFuture<Boolean> result = s3Service.uploadFilePart("bucket", "key", "uploadId", 1, new StringBuilder("data"), completedParts);
        assertTrue(result.join());
        assertEquals("etag123", completedParts.getFirst().eTag());
    }

    @Test
    void testUploadChunkFilePart() {
        CopyPartResult copyPartResult = CopyPartResult.builder().eTag("etag456").build();
        UploadPartCopyResponse response = UploadPartCopyResponse.builder().copyPartResult(copyPartResult).build();
        CompletableFuture<UploadPartCopyResponse> future = CompletableFuture.completedFuture(response);
        when(s3AsyncClient.uploadPartCopy(any(UploadPartCopyRequest.class))).thenReturn(future);

        CompletableFuture<String> result = s3Service.uploadChunkFilePart("bucket", "key", "uploadId", 1, "chunkKey");
        assertEquals("etag456", result.join());
    }

    @Test
    void testDeleteChunkData() {
        DeleteObjectResponse response = DeleteObjectResponse.builder().build();
        CompletableFuture<DeleteObjectResponse> future = CompletableFuture.completedFuture(response);
        when(s3AsyncClient.deleteObject(any(DeleteObjectRequest.class))).thenReturn(future);

        CompletableFuture<Boolean> result = s3Service.deleteChunkData("bucket", "key");
        assertTrue(result.join());
    }

    @Test
    void testUploadReport() {
        PutObjectResponse putObjectResponse = PutObjectResponse.builder().build();
        CompletableFuture<PutObjectResponse> future = CompletableFuture.completedFuture(putObjectResponse);
        when(s3AsyncClient.putObject(any(PutObjectRequest.class), any(AsyncRequestBody.class))).thenReturn(future);

        CompletableFuture<Boolean> result = s3Service.uploadReport("bucket", "key", "testdata");
        assertTrue(result.join());
    }
	
}
