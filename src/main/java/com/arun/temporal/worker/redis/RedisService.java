package com.arun.temporal.worker.redis;

import io.lettuce.core.KeyValue;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface RedisService {
    Mono<Boolean> saveBatch(String queryId, String batchId, String status);
    Flux<KeyValue<String, String>> getCompletedBatch(String queryId);
}
