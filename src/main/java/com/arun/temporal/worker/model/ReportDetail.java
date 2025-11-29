package com.arun.temporal.worker.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@AllArgsConstructor
@NoArgsConstructor
public class ReportDetail {
    private String name;
    private String email;
    private String phoneNumber;
    private String address;
}
