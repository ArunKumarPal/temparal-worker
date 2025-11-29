package com.arun.temporal.worker.kafka;

import io.micronaut.configuration.kafka.annotation.KafkaClient;
import io.micronaut.configuration.kafka.annotation.KafkaKey;
import io.micronaut.configuration.kafka.annotation.Topic;
import io.micronaut.http.annotation.Header;
import lombok.Generated;
import org.apache.kafka.common.header.internals.RecordHeader;

import java.util.List;

@KafkaClient
@Generated
public interface KafkaMsgClient {
    @Topic("spark-events")
    void sendEvent(@KafkaKey String key, String value, @Header List<RecordHeader> headers);
}