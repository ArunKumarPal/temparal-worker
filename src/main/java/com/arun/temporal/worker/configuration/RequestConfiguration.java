package com.arun.temporal.worker.configuration;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.naming.Named;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

@ConfigurationProperties("request-config")
public interface RequestConfiguration extends Named {
    @Min(1)
    int getBatchSize();
}
