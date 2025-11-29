package com.arun.temporal.worker.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ApiResponseStatus {
    private int statusCode;
    private Map batchIdWithStatus;

    public ApiResponseStatus(int statusCode) {
        this.statusCode = statusCode;
    }
}
