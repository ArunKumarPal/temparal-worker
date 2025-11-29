package com.arun.temporal.worker.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.micronaut.core.annotation.Introspected;
import lombok.*;

import java.util.List;

@Data
@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Introspected
@JsonIgnoreProperties(ignoreUnknown = true)
public class BulkResponse {
    private List<ResponseWithInputRequest> responses;
    private Integer payloadLength;
}
