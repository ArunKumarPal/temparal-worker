package com.arun.temporal.worker.worker;

import jakarta.inject.Singleton;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@Singleton
public class WorkerStatus {

    private final AtomicInteger running = new AtomicInteger(0);
    private final AtomicReference<Instant> lastActivity = new AtomicReference<>(Instant.now());

    public void startActivity() {
        running.incrementAndGet();
        lastActivity.set(Instant.now());
    }

    public void endActivity() {
        running.decrementAndGet();
    }

    public int runningActivity() {
        return running.get();
    }

    public Instant lastTimestamp() {
        return lastActivity.get();
    }
}