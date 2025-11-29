package com.arun.temporal.worker.kafka;

import com.arun.temporal.worker.model.KafkaEvent;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.header.internals.RecordHeader;

import java.util.List;
import java.util.UUID;

import static java.nio.charset.StandardCharsets.UTF_8;

@Singleton
@Slf4j
public class KafkaEventProducer {

    private final KafkaMsgClient kafkaMsgClient;

    public static final ObjectMapper KAFKA_OBJECT_MAPPER = new ObjectMapper().setSerializationInclusion(JsonInclude.Include.ALWAYS);

    public KafkaEventProducer(KafkaMsgClient kafkaMsgClient) {
        this.kafkaMsgClient = kafkaMsgClient;
    }

    public void sendKafkaEvent(KafkaEvent kafkaEvent) {
        List<RecordHeader> messageHeaders = List.of(
                new RecordHeader("type", "kafkaEvent".getBytes(UTF_8)),
                new RecordHeader("schemaVersion", "1".getBytes(UTF_8)),
                new RecordHeader("messageId", UUID.randomUUID().toString().getBytes(UTF_8))
        );
        try {
            String event = KAFKA_OBJECT_MAPPER.writeValueAsString(kafkaEvent);
            kafkaMsgClient.sendEvent(UUID.randomUUID().toString(), event, messageHeaders);
        } catch (JsonProcessingException ex) {
            log.error("Error pushing message to Kafka", ex);
        }
    }
}
