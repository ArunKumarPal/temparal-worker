package com.arun.temporal.worker.health;


import io.micronaut.health.HealthStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class BulkProcessorHealthTest {

    @Test
    void testGetResultReturnsHealthyStatus() {
        BulkProcessorHealth healthIndicator = new BulkProcessorHealth();

        StepVerifier.create(healthIndicator.getResult())
                .expectNextMatches(result -> {
                    return result.getName().equals("bulk_processor_check") &&
                            result.getStatus().equals(HealthStatus.UP) &&
                            "Bulk Processor Service is running healthy"
                                    .equals(result.getDetails().toString());
                })
                .verifyComplete();
    }

}
