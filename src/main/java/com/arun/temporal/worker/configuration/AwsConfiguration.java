package com.arun.temporal.worker.configuration;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.naming.Named;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

@ConfigurationProperties("aws")
public interface AwsConfiguration extends Named {

    @NotNull
    @NotBlank
    @NotEmpty
    String getEnv();

    @NotNull
    @NotBlank
    @NotEmpty
    String getRegion();
}
