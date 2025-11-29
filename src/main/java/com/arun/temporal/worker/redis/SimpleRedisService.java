package com.arun.temporal.worker.redis;

import io.lettuce.core.KeyValue;
import io.lettuce.core.api.reactive.RedisReactiveCommands;
import jakarta.inject.Singleton;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Singleton
public class SimpleRedisService implements RedisService {

    private final RedisReactiveCommands<String, String> redisReactiveCommands;
    private static final long TTL_SECONDS = 43200;

    public SimpleRedisService(RedisReactiveCommands<String, String> redisReactiveCommands) {
        this.redisReactiveCommands = redisReactiveCommands;
    }

    @Override
    public Mono<Boolean> saveBatch(String queryId, String batchId, String status) {
        return redisReactiveCommands.hset(queryId, batchId, status).then(redisReactiveCommands.expire(queryId, TTL_SECONDS));
    }

    @Override
    public Flux<KeyValue<String, String>> getCompletedBatch(String queryId) {
        return redisReactiveCommands.hgetall(queryId);
    }


}
