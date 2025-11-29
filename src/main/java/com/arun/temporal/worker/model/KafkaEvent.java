package com.arun.temporal.worker.model;

import io.micronaut.core.annotation.Introspected;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Data
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Introspected
public class KafkaEvent {
    String queryId;
    String batchId;
    int payloadLength;
    List<InputRequest> requests;
}
