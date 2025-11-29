package com.arun.temporal.worker.health;

import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.health.HealthStatus;
import io.micronaut.management.health.indicator.HealthIndicator;
import io.micronaut.management.health.indicator.HealthResult;
import jakarta.inject.Singleton;
import org.reactivestreams.Publisher;

@Singleton
public class BulkProcessorHealth implements HealthIndicator {

    @Override
    public Publisher<HealthResult> getResult() {
        return Publishers.just(HealthResult.builder("bulk_processor_check", HealthStatus.UP)
                .details("Bulk Processor Service is running healthy")
                .build());
    }
}
