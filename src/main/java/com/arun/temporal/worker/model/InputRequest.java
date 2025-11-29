package com.arun.temporal.worker.model;

import io.micronaut.core.annotation.Introspected;
import lombok.*;

@Data
@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Introspected
public class InputRequest {
    private String input1;
    private String input2;
    private String input3;
}
