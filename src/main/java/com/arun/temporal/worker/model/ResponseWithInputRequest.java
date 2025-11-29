package com.arun.temporal.worker.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.micronaut.core.annotation.Introspected;
import lombok.*;

@Data
@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Introspected
@JsonIgnoreProperties(ignoreUnknown = true)
public class ResponseWithInputRequest extends Response {
    InputRequest inputRequest;
}
