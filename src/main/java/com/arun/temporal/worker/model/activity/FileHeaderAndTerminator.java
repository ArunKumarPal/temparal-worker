package com.arun.temporal.worker.model.activity;

public record FileHeaderAndTerminator (
    String header,
    int terminatorSize
){}
