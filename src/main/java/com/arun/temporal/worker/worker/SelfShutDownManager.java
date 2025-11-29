package com.arun.temporal.worker.worker;

import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.runtime.event.ApplicationShutdownEvent;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;

import static com.arun.temporal.worker.constant.Constants.MAX_ACTIVITY_RUN_TIME_OUT;

@Singleton
public class SelfShutDownManager implements ApplicationEventListener<ApplicationShutdownEvent> {


    private static final Logger logger = LoggerFactory.getLogger(SelfShutDownManager.class);
    private static final long POD_IDLE_TIME_MINUTES = 10;
    private static final Duration SHUTDOWN_GRACE_PERIOD = Duration.ofSeconds(15);

    private final WorkerStatus workerStatus;
    private final TemporalWorkerFactory temporalWorkerFactory;

    public SelfShutDownManager(WorkerStatus workerStatus, TemporalWorkerFactory temporalWorkerFactory) {
        this.workerStatus = workerStatus;
        this.temporalWorkerFactory = temporalWorkerFactory;
    }

    private boolean shouldContinueShutdownCheck() {
        if (isEligibleForShutdown()) {
            var worker = temporalWorkerFactory.getWorkerFactory();
            worker.suspendPolling();
            try {
                Thread.sleep(SHUTDOWN_GRACE_PERIOD.toMillis());
            } catch (InterruptedException ex) {
                logger.warn("Sleep interrupted during shutdown grace period", ex);
                Thread.currentThread().interrupt();
            }
            if (isEligibleForShutdown()) {
                logger.info("Shutting down worker due to inactivity");
                worker.shutdown();
                return false;
            } else {
                logger.info("Shutdown aborted, new activity detected during grace period");
                worker.resumePolling();
            }
        }
        return true;
    }

    private boolean isEligibleForShutdown() {
        Duration idleDuration = Duration.between(workerStatus.lastTimestamp(), Instant.now());
        long allowedIdleMinutes = workerStatus.runningActivity() > 0
                ? MAX_ACTIVITY_RUN_TIME_OUT + POD_IDLE_TIME_MINUTES
                : POD_IDLE_TIME_MINUTES;
        return idleDuration.toMinutes() > allowedIdleMinutes;
    }

    @Override
    public void onApplicationEvent(ApplicationShutdownEvent event) {
        while (shouldContinueShutdownCheck()) {
            try {
                logger.info("Application waiting for shutdown. Current running activities: {}", workerStatus.runningActivity());
                Thread.sleep(Duration.ofMinutes(1).toMillis());
            } catch (InterruptedException e) {
                logger.warn("Sleep interrupted during shutdown wait", e);
                Thread.currentThread().interrupt();
            }
        }
        logger.info("Application shutdown complete");
    }
}
