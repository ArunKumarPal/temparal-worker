package com.arun.temporal.worker.model.activity;

public record SplitChunkRequest(String key, String delimiter) {
}
