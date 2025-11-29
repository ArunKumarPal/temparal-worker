package com.arun.temporal.worker.model.activity;

public record FileMetadata(
        int lineTerminatorSize,
        int avgLineSize,
        long fileSize,
        String[] headers
) {
}
