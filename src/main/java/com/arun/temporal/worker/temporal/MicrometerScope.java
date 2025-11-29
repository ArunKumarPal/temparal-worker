package com.arun.temporal.worker.temporal;

import com.uber.m3.tally.*;
import com.uber.m3.util.Duration;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

import java.util.HashMap;
import java.util.Map;

public class MicrometerScope implements Scope {

    private final MeterRegistry registry;
    private final String prefix;
    private final Map<String, String> tags;

    public MicrometerScope(MeterRegistry registry) {
        this(registry, "", Map.of());
    }

    private MicrometerScope(MeterRegistry registry, String prefix, Map<String, String> tags) {
        this.registry = registry;
        this.prefix = prefix;
        this.tags = tags;
    }

    @Override
    public Counter counter(String name) {
        io.micrometer.core.instrument.Counter micrometerCounter =
                io.micrometer.core.instrument.Counter.builder(metricName(name))
                        .tags(flattenTags(tags))
                        .register(registry);

        return micrometerCounter::increment;
    }

    @Override
    public com.uber.m3.tally.Gauge gauge(String name) {
        class MicrometerGauge implements com.uber.m3.tally.Gauge {
            private double value = 0.0;

            @Override
            public void update(double v) {
                this.value = v;
                Gauge.builder(metricName(name), () -> this.value)
                        .tags(flattenTags(tags))
                        .register(registry);
            }
        }
        return new MicrometerGauge();
    }

    @Override
    public Timer timer(String s) {
        return null;
    }

    @Override
    public Histogram histogram(String name, Buckets buckets) {
        DistributionSummary summary =
                DistributionSummary.builder(metricName(name))
                        .tags(flattenTags(tags))
                        .register(registry);

        return new Histogram() {
            @Override
            public void recordValue(double v) {
                summary.record(v);
            }

            @Override
            public void recordDuration(Duration duration) {
                summary.record(duration.getSeconds());
            }

            @Override
            public Stopwatch start() {
                return null;
            }
        };
    }

    @Override
    public Scope tagged(Map<String, String> extraTags) {
        Map<String, String> merged = new HashMap<>(this.tags);
        merged.putAll(extraTags);
        return new MicrometerScope(registry, prefix, merged);
    }

    @Override
    public Scope subScope(String name) {
        String newPrefix = this.prefix.isEmpty() ? name : this.prefix + "." + name;
        return new MicrometerScope(registry, newPrefix, tags);
    }

    @Override
    public Capabilities capabilities() {
        return new Capabilities() {
            @Override
            public boolean reporting() {
                return true;
            }

            @Override
            public boolean tagging() {
                return true;
            }
        };
    }

    @Override
    public void close() {
        // Micrometer doesn’t require cleanup
    }

    // ---------- helpers ----------
    private String metricName(String name) {
        return prefix.isEmpty() ? name : prefix + "." + name;
    }

    private String[] flattenTags(Map<String, String> tags) {
        return tags.entrySet().stream()
                .flatMap(e -> java.util.stream.Stream.of(e.getKey(), e.getValue()))
                .toArray(String[]::new);
    }
}

