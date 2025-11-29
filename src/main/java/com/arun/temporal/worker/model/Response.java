package com.arun.temporal.worker.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@AllArgsConstructor
@NoArgsConstructor
public class Response {
    String name;
    String email;
    String phone;
    String address;
    String error;
}
