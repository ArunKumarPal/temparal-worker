package com.arun.temporal.worker.configuration;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.naming.Named;
import jakarta.validation.constraints.Min;

@ConfigurationProperties("s3-client")
public interface S3ClientConfiguration extends Named {

    @Min(1)
    int getMaxConcurrency();

    @Min(1)
    int getMaxPendingConnectionAcquires();

    @Min(1)
    int getConnectionAcquisitionTimeout();
}
