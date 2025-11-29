package com.arun.temporal.worker.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class BulkProcessorException extends RuntimeException {
    private final String message;
    private final Throwable cause;

    public BulkProcessorException(String message) {
        this.message = message;
        this.cause = null;
    }
}
