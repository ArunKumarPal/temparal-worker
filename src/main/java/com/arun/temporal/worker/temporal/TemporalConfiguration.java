package com.arun.temporal.worker.temporal;

import com.uber.m3.tally.Scope;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Primary;
import io.micronaut.context.annotation.Replaces;
import io.micronaut.context.annotation.Value;
import io.micronaut.core.annotation.Nullable;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowClientOptions;
import io.temporal.serviceclient.SimpleSslContextBuilder;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;

@Factory
@Slf4j
public class TemporalConfiguration {

    private final String hostPort;
    private final String namespace;
    private final String clientCertKey;
    private final String clientCertPem;
    private final boolean encryptionEnabled;

    public TemporalConfiguration(
            @Value("${temporal.hostport:127.0.0.1:7233}") String hostPort,
            @Value("${temporal.namespace:default}") String namespace,
            @Value("${temporal.client-cert.key:}") String clientCertKey,
            @Value("${temporal.client-cert.pem:}") String clientCertPem,
            @Value("${temporal.encryption.enabled:false}") boolean encryptionEnabled
    ) {
        this.hostPort = hostPort;
        this.namespace = namespace;
        this.clientCertKey = clientCertKey;
        this.clientCertPem = clientCertPem;
        this.encryptionEnabled = encryptionEnabled;
    }

    @Singleton
    @Primary
    @Replaces(WorkflowServiceStubs.class)
    public WorkflowServiceStubs workflowServiceStubs(
            @Nullable Scope temporalMetricsScope
    ) throws Exception {
        WorkflowServiceStubsOptions.Builder opts = WorkflowServiceStubsOptions.newBuilder()
                .setTarget(hostPort);

        if (temporalMetricsScope != null) {
            opts.setMetricsScope(temporalMetricsScope);
        }

        final boolean hasCertKey = clientCertKey != null && !clientCertKey.isBlank();
        final boolean hasCertPem  = clientCertPem != null && !clientCertPem.isBlank();

        if (hasCertKey && hasCertPem) {
            try (InputStream clientKeyPemBytes = new ByteArrayInputStream(clientCertPem.getBytes());
                 InputStream clientCertKeyBytes  = new ByteArrayInputStream(clientCertKey.getBytes())) {
                opts.setSslContext(SimpleSslContextBuilder.forPKCS8(clientKeyPemBytes, clientCertKeyBytes).build());
                log.info("Temporal TLS enabled via provided client cert/key.");
            }
        } else {
            log.info("Temporal TLS disabled (no client cert/key configured).");
        }
        WorkflowServiceStubs stubs = WorkflowServiceStubs.newServiceStubs(opts.build());
        log.info("Temporal WorkflowServiceStubs initialized (target='{}')", hostPort);
        return stubs;
    }

    @Singleton
    @Primary
    @Replaces(WorkflowClient.class)
    public WorkflowClient workflowClient(
            WorkflowServiceStubs stubs,
            @Nullable CustomContextPropagator customContextPropagator,
            @Nullable CustomDataConverter customDataConverter
    ) {
        WorkflowClientOptions.Builder builder = WorkflowClientOptions.newBuilder()
                .setNamespace(namespace);

        if (customContextPropagator != null) {
            builder.setContextPropagators(List.of(customContextPropagator));
            log.info("Temporal ContextPropagator registered: {}", customContextPropagator.getName());
        } else {
            log.info("No CustomContextPropagator bean found; continuing without context propagation.");
        }
        if (encryptionEnabled) {
            if (customDataConverter != null) {
                builder.setDataConverter(customDataConverter);
                log.info("Temporal data encryption enabled (CustomDataConverter active).");
            } else {
                log.warn("temporal.encryption.enabled=true but no CustomDataConverter bean found; using default DataConverter.");
            }
        } else {
            log.info("Temporal data encryption disabled (default DataConverter).");
        }
        WorkflowClient client = WorkflowClient.newInstance(stubs, builder.build());
        log.info("Temporal WorkflowClient initialized (namespace='{}').", namespace);
        return client;
    }
}
