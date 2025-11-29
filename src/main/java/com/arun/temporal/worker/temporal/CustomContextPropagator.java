package com.arun.temporal.worker.temporal;

import io.micronaut.context.annotation.Value;
import io.temporal.api.common.v1.Payload;
import io.temporal.common.context.ContextPropagator;
import io.temporal.common.converter.DataConverter;
import io.temporal.common.converter.GlobalDataConverter;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


@Slf4j
@Singleton
public class CustomContextPropagator implements ContextPropagator {

    private final List<String> contextKeys;

    public static final String CONTEXT_PROPAGATOR_NAME = "CustomContextPropagator";
    public static final String JOB_ID = "job_id";

    public CustomContextPropagator(@Value("${temporal.context-keys:`job_id`}") List<String> contextKeys) {
        this.contextKeys = (contextKeys == null || contextKeys.isEmpty())
                ? List.of(JOB_ID)
                : List.copyOf(contextKeys);
    }

    @Override
    public String getName() {
        return CONTEXT_PROPAGATOR_NAME;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Payload> serializeContext(Object context) {
        if (!(context instanceof Map<?, ?> ctx)) {
            String type = (context == null) ? "null" : context.getClass().getName();
            log.error("Expected context to be Map but was: {}", type);
            throw new IllegalArgumentException("Expected context to be Map but was: " + type);
        }
        final DataConverter dc = GlobalDataConverter.get();
        return contextKeys.stream()
                .filter(ctx::containsKey)
                .map(key -> Map.entry(key, dc.toPayload(ctx.get(key)).orElse(null)))
                .filter(e -> e.getValue() != null)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    @Override
    public Object deserializeContext(Map<String, Payload> context) {
        final DataConverter dc = GlobalDataConverter.get();
        Map<String, String> out = new HashMap<>();
        context.forEach((key, payload) -> {
            String value = dc.fromPayload(payload, String.class, String.class);
            out.put(key, value);
        });
        return out;
    }

    @Override
    public Object getCurrentContext() {
        String workspaceId = MDC.get(JOB_ID);
        if (workspaceId != null && !workspaceId.isBlank()) {
            return Map.of(JOB_ID, workspaceId);
        }
        return Collections.emptyMap();
    }

    @Override
    @SuppressWarnings("unchecked")
    public void setCurrentContext(Object context) {
        if (context instanceof Map<?, ?> map) {
            Object v = map.get(JOB_ID);
            if (v instanceof String ws && !ws.isBlank()) {
                MDC.put(JOB_ID, ws);
            }
        }
    }
}
