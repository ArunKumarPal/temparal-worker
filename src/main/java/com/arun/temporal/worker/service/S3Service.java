package com.arun.temporal.worker.service;


import com.arun.temporal.worker.exception.BulkProcessorException;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.core.async.AsyncResponseTransformer;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.*;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Singleton
public class S3Service {

    private static final Logger logger = LoggerFactory.getLogger(S3Service.class);

    private final S3AsyncClient s3AsyncClient;

    public S3Service(S3AsyncClient s3AsyncClient) {
        this.s3AsyncClient = s3AsyncClient;
    }

    public CompletableFuture<Long> getInputFileSize(String bucketName, String key) {
        HeadObjectRequest headObjectRequest = HeadObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build();
        return s3AsyncClient.headObject(headObjectRequest)
                .thenApply(HeadObjectResponse::contentLength);
    }

    public CompletableFuture<String> getUploadId(String outputKey, String bucketName) {
        CreateMultipartUploadRequest createRequest = CreateMultipartUploadRequest.builder()
                .bucket(bucketName)
                .key(outputKey)
                .build();
        return s3AsyncClient.createMultipartUpload(createRequest).thenApply(CreateMultipartUploadResponse::uploadId);
    }

    public CompletableFuture<Boolean> completeMultiPartUpload(String bucketName, String outputKey, String uploadId, List<CompletedPart> completedParts) {
        completedParts.sort(Comparator.comparingInt(CompletedPart::partNumber));

        CompleteMultipartUploadRequest request = CompleteMultipartUploadRequest
                .builder()
                .bucket(bucketName)
                .uploadId(uploadId)
                .multipartUpload(builder -> builder.parts(completedParts))
                .key(outputKey)
                .build();
        return s3AsyncClient.completeMultipartUpload(request).thenApply(response -> true);

    }

    public CompletableFuture<InputStream> getInputStream(String bucketName, String inputKey, long startPosition, long endPosition) {
        GetObjectRequest request = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(inputKey)
                .range("bytes=" + startPosition + "-" + endPosition)
                .build();
        return s3AsyncClient.getObject(request, AsyncResponseTransformer.toBlockingInputStream()).thenApply(responseInputStream -> {
            if (responseInputStream != null) {
                return responseInputStream;
            } else {
                logger.error("Error in fetching S3 object for bucket: {}, key: {}, range: {}-{}", bucketName, inputKey, startPosition, endPosition);
                throw new BulkProcessorException("Error in fetching S3 object");
            }
        });
    }

    public CompletableFuture<ResponseBytes<GetObjectResponse>> getOutputStream(String bucketName, String inputKey) {
        GetObjectRequest request = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(inputKey)
                .build();
        return s3AsyncClient.getObject(request, AsyncResponseTransformer.toBytes());
    }

    public CompletableFuture<Boolean> uploadFilePart(String bucketName, String key, String uploadId, int partNumber, StringBuilder dataBuffer, List<CompletedPart> completedParts) {
        UploadPartRequest request = UploadPartRequest.builder()
                .bucket(bucketName)
                .key(key)
                .uploadId(uploadId)
                .partNumber(partNumber)
                .build();
        return s3AsyncClient.uploadPart(request, generateAsyncRequestBody(dataBuffer))
                .thenApply(response -> {
                    completedParts.add(CompletedPart.builder().eTag(response.eTag()).partNumber(partNumber).build());
                    return true;
                });
    }


    public CompletableFuture<String> uploadChunkFilePart(String bucketName, String key, String uploadId, int partNumber, String chunkFileKey) {
        UploadPartCopyRequest request = UploadPartCopyRequest.builder()
                .destinationBucket(bucketName)
                .destinationKey(key)
                .uploadId(uploadId)
                .partNumber(partNumber)
                .sourceBucket(bucketName)
                .sourceKey(chunkFileKey)
                .build();
        return s3AsyncClient.uploadPartCopy(request).thenApply(res -> res.copyPartResult().eTag());
    }

    public CompletableFuture<Boolean> deleteChunkData(String bucketName, String key) {
        DeleteObjectRequest deleteObjectsRequest = DeleteObjectRequest.builder()
                .bucket(bucketName).key(key).build();
        return s3AsyncClient.deleteObject(deleteObjectsRequest).thenApply(res -> true);
    }

    public CompletableFuture<Boolean> uploadReport(String bucketName, String key, String data) {
        PutObjectRequest putOb = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .metadata(new HashMap<>())
                .build();
        return s3AsyncClient.putObject(putOb, AsyncRequestBody.fromBytes(data.getBytes())).thenApply(response -> true);
    }

    private AsyncRequestBody generateAsyncRequestBody(StringBuilder dataBuffer) {
        return AsyncRequestBody.fromByteBuffer(ByteBuffer.wrap(dataBuffer.toString().getBytes(StandardCharsets.UTF_8)));
    }
}
