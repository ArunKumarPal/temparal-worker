package com.arun.temporal.worker.s3;

import com.arun.temporal.worker.configuration.S3ClientConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.s3.S3AsyncClient;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class S3FactoryTest {

    @Mock
    S3ClientConfiguration s3ClientConfiguration;

    @InjectMocks
    S3Factory s3Factory;

    @Test
    void testS3AsyncClientCreation() {
        when(s3ClientConfiguration.getMaxConcurrency()).thenReturn(50);
        when(s3ClientConfiguration.getMaxPendingConnectionAcquires()).thenReturn(10000);
        when(s3ClientConfiguration.getConnectionAcquisitionTimeout()).thenReturn(60);

        S3AsyncClient client = s3Factory.s3AsyncClient();

        assertNotNull(client);
    }
}
