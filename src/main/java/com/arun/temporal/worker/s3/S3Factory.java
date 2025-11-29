package com.arun.temporal.worker.s3;

import com.arun.temporal.worker.configuration.S3ClientConfiguration;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.netty.util.internal.SystemPropertyUtil;
import jakarta.inject.Singleton;
import software.amazon.awssdk.core.client.config.ClientAsyncConfiguration;
import software.amazon.awssdk.http.async.SdkAsyncHttpClient;
import software.amazon.awssdk.http.nio.netty.NettyNioAsyncHttpClient;
import software.amazon.awssdk.http.nio.netty.SdkEventLoopGroup;
import software.amazon.awssdk.services.s3.S3AsyncClient;

import java.time.Duration;

@Factory
public class S3Factory {

    private final S3ClientConfiguration s3ClientConfiguration;

    public S3Factory(S3ClientConfiguration s3ClientConfiguration) {
        this.s3ClientConfiguration = s3ClientConfiguration;
    }

    @Bean
    @Singleton
    public S3AsyncClient s3AsyncClient() {

        int eventLoopThreads = Math.max(1, SystemPropertyUtil.getInt("io.netty.eventLoopThreads",
                Runtime.getRuntime().availableProcessors() * 2));

        SdkEventLoopGroup eventLoopGroup = SdkEventLoopGroup.builder()
                .numberOfThreads(eventLoopThreads)
                .build();

        SdkAsyncHttpClient httpClient = NettyNioAsyncHttpClient.builder()
                .maxConcurrency(s3ClientConfiguration.getMaxConcurrency()) // Default value: 50
                .maxPendingConnectionAcquires(s3ClientConfiguration.getMaxPendingConnectionAcquires()) // Default value: 10000
                .connectionAcquisitionTimeout(Duration.ofSeconds(s3ClientConfiguration.getConnectionAcquisitionTimeout())) // Default value: 60 seconds
                .eventLoopGroup(eventLoopGroup)
                .build();

        ClientAsyncConfiguration asyncConfig = ClientAsyncConfiguration.builder()
                .build();

        return S3AsyncClient.builder()
                .httpClient(httpClient)
                .asyncConfiguration(asyncConfig)
                .build();
    }
}

