package com.arun.temporal.worker.temporal;

import com.uber.m3.tally.Scope;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.inject.Singleton;
import lombok.Getter;

@Singleton
@Getter
public class MicrometerTallyScopeProvider {

    private final Scope scope;

    public MicrometerTallyScopeProvider(MeterRegistry meterRegistry) {
        this.scope = new MicrometerScope(meterRegistry);
    }
}