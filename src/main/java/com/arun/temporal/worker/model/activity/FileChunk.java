package com.arun.temporal.worker.model.activity;


public record FileChunk(
        int chunkNumber,
        long startOffset,
        long endOffset,
        FileMetadata metadata
) {
}
