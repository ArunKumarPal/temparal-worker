package com.arun.temporal.worker.service;

import com.arun.temporal.worker.model.ApiResponseStatus;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.reactor.http.client.ReactorHttpClient;
import io.opentelemetry.api.trace.Span;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@Singleton
public class RestClientService {

    private static final Logger logger = LoggerFactory.getLogger(RestClientService.class);
    final ReactorHttpClient httpClient;
    private static final int MAX_RETRY_ATTEMPTS = 10;
    private static final Duration RETRY_INTERVAL = Duration.ofSeconds(10);

    public RestClientService(ReactorHttpClient httpClient) {
        this.httpClient = httpClient;
    }

    AtomicInteger apiCount = new AtomicInteger(1);
    AtomicInteger apiCountGet = new AtomicInteger(1);


    public <U> Mono<ApiResponseStatus> externalApiCall(MutableHttpRequest<U> request, String requestId, Span parentSpan) {
        AtomicInteger retryCounter = new AtomicInteger(1);
        int api = apiCount.get();
        return Mono.defer(() -> {
            request.getHeaders().add("X-Request-Id", requestId).add("Connection", "close");
            return Mono.from(this.httpClient.exchange(request))
                    .flatMap(response -> {
                        int statusCode = response.status().getCode();

                        if (statusCode == 202) {
                            // If response status code is 202, return the ApiResponseStatus
                            return Mono.just(new ApiResponseStatus(statusCode));
                        } else {
                            // If response status code is not 202, trigger retry logic
                            return Mono.error(new RuntimeException("Received non-202 response: " + statusCode));
                        }

                    })
                    .doOnError(e -> logger.error("Error in externalApiCall : " + "for api : " + apiCount.get() + " " + e.getMessage()))
                    .retryWhen(Retry.backoff(MAX_RETRY_ATTEMPTS, RETRY_INTERVAL).maxBackoff(Duration.ofMinutes(5))
                            .onRetryExhaustedThrow((retrySignal, throwable) -> {
                                // Log retry attempts
                                int retries = retryCounter.incrementAndGet();
                                logger.error("Retry attempt #" + retries + " for api " + api + " on thread " + Thread.currentThread().getName());
                                return null;
                            }))
                    .onErrorResume(throwable -> {
                        // Log errors after retrying
                        logger.error("Error in externalApiCall after retries: " + throwable.getMessage());
                        return Mono.error(throwable);
                    })
                    .publishOn(Schedulers.boundedElastic());
        });
    }

    public <U> Mono<ApiResponseStatus> externalApiCallGet(MutableHttpRequest<U> request, String requestId, Span parentSpan) {
        AtomicInteger retryCounter = new AtomicInteger(1);
        return Mono.defer(() -> {
            request.getHeaders().add("X-Request-Id", requestId).add("Connection", "close");

            return Mono.from(this.httpClient.exchange(request))
                    .flatMap(response -> {
                        int statusCode = response.status().getCode();
                        if (statusCode == 200) {
                            Map batchIds = response.getBody(Map.class).orElseGet(() -> Map.of());
//                            logger.info("batchIds size : " + batchIds.size());
                            return Mono.just(new ApiResponseStatus(statusCode, batchIds));
                        } else if (statusCode == 202) {
                            // If response status code is not 202, trigger retry logic
                            return Mono.just(new ApiResponseStatus(statusCode));
                        } else {

                            return Mono.error(new RuntimeException("Non 200 || 202 - Status code : " + statusCode));
                        }

                    })
                    .doOnError(e -> logger.error("Error in externalApiCall : " + " for api get : " + apiCountGet.get() + " " + e.getMessage()))
                    .retryWhen(Retry.backoff(2, RETRY_INTERVAL).maxBackoff(Duration.ofMinutes(1))
                            .onRetryExhaustedThrow((retrySignal, throwable) -> {
                                // Log retry attempts
                                int retries = retryCounter.incrementAndGet();
                                System.out.println("Retry attempt #" + retries + " for get api  on thread " + Thread.currentThread().getName());
                                return null;
                            }))
                    .onErrorResume(throwable -> {
                        // Log errors after retrying
                        logger.error("Error in externalApiCall after retries: " + throwable.getMessage());
                        return Mono.error(throwable);
                    })
                    .publishOn(Schedulers.boundedElastic());
        });
    }


    public <U> Mono<ApiResponseStatus> externalRoyaltyApiCallGet(MutableHttpRequest<U> request, String requestId, Span parentSpan) {
        AtomicInteger retryCounter = new AtomicInteger(1);
        return Mono.defer(() -> {
            request.getHeaders().add("X-Request-Id", requestId).add("Connection", "close");

            return Mono.from(this.httpClient.exchange(request))
                    .flatMap(response -> {
                        int statusCode = response.status().getCode();
                        if (statusCode == 200) {
                            return Mono.just(new ApiResponseStatus(statusCode));
                        } else {

                            return Mono.error(new RuntimeException("Non 200  Status code : " + statusCode));
                        }

                    })
                    .doOnError(e -> logger.error("Error in externalRoyaltyApiCallGet : " + " for royalty api get : " + apiCountGet.get() + " " + e.getMessage()))
                    .retryWhen(Retry.backoff(2, RETRY_INTERVAL).maxBackoff(Duration.ofMinutes(1))
                            .onRetryExhaustedThrow((retrySignal, throwable) -> {
                                // Log retry attempts
                                int retries = retryCounter.incrementAndGet();
                                logger.info("Retry attempt #" + retries + " for get api  on thread " + Thread.currentThread().getName());
                                return null;
                            }))
                    .onErrorResume(throwable -> {
                        // Log errors after retrying
                        logger.error("Error in externalRoyaltyApiCallGet after retries: " + throwable.getMessage());
                        return Mono.error(throwable);
                    })
                    .publishOn(Schedulers.boundedElastic());
        });
    }
}
