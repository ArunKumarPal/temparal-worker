package com.arun.temporal.worker.temporal;

import com.arun.temporal.worker.service.EncryptionService;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.micronaut.context.annotation.Context;
import io.micronaut.context.annotation.Requires;
import io.temporal.api.common.v1.Payload;
import io.temporal.api.common.v1.Payloads;
import io.temporal.common.converter.DataConverter;
import io.temporal.common.converter.DataConverterException;
import io.temporal.common.converter.DefaultDataConverter;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.google.protobuf.ByteString.copyFromUtf8;

@Context
@Singleton
@Requires(property = "temporal.encryption.enabled", value = "true")
@Slf4j
public class CustomDataConverter implements DataConverter {

    private final EncryptionService encryptionService;
    private final DataConverter defaultConverter;
    private final ObjectMapper objectMapper;

    public CustomDataConverter(EncryptionService encryptionService) {
        this.encryptionService = encryptionService;
        this.objectMapper = getObjectMapper();
        this.defaultConverter = DefaultDataConverter.STANDARD_INSTANCE;
    }

    @Override
    public <T> Optional<Payload> toPayload(T value) throws DataConverterException {
        try {
            Optional<Payload> plainPayloadOpt = defaultConverter.toPayload(value);
            if (plainPayloadOpt.isEmpty()) return Optional.empty();
            String plainJson = plainPayloadOpt.get().getData().toStringUtf8();

            String encrypted = encryptionService.encryptPayload(plainJson);
            Payload p = defaultConverter.toPayload(encrypted).orElse(null);
            if (p == null) return Optional.empty();

            Payload withMeta = p.toBuilder()
                    .putMetadata("enc", copyFromUtf8("v1"))
                    .build();

            return Optional.of(withMeta);
        } catch (Exception e) {
            log.error("Error in toPayload: ", e);
            throw new DataConverterException("Failed to serialize and encrypt payload", e);
        }
    }

    @Override
    public <T> T fromPayload(Payload payload, Class<T> valueClass, Type valueType)
            throws DataConverterException {
        try {
            String encrypted = defaultConverter.fromPayload(payload, String.class, String.class);
            String decryptedJson = encryptionService.decryptPayload(encrypted);

            JavaType jt = (valueType != null)
                            ? objectMapper.getTypeFactory().constructType(valueType)
                            : objectMapper.constructType(valueClass);
            return objectMapper.readValue(decryptedJson, jt);
        } catch (Exception e) {
            log.error("Error in fromPayload: ", e);
            throw new DataConverterException("Failed to decrypt and deserialize payload", e);
        }
    }

    @Override
    public Optional<Payloads> toPayloads(Object... values) throws DataConverterException {
        if (values == null || values.length == 0) {
            return Optional.empty();
        }
        List<Payload> encryptedPayloads = new ArrayList<>(values.length);
        for (Object value : values) {
            Optional<Payload> p = toPayload(value);
            p.ifPresent(encryptedPayloads::add);
        }
        return Optional.of(Payloads.newBuilder().addAllPayloads(encryptedPayloads).build());
    }

    @Override
    public <T> T fromPayloads(int index, Optional<Payloads> content,
                              Class<T> valueType, Type valueGenericType) throws DataConverterException {
        if (content.isEmpty() || content.get().getPayloadsCount() <= index) {
            log.error("Payload at index {} not found.", index);
            throw new IndexOutOfBoundsException("Payload at index " + index + " not found.");
        }
        return fromPayload(content.get().getPayloads(index), valueType, valueGenericType);
    }

    private static ObjectMapper getObjectMapper() {
        ObjectMapper m = new ObjectMapper();
        m.registerModule(new JavaTimeModule());
        m.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        m.disable(SerializationFeature.WRITE_DURATIONS_AS_TIMESTAMPS);
        m.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        m.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        m.configure(DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE, false);
        return m;
    }
}
