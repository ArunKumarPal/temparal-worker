package com.arun.temporal.worker.worker;

import io.micronaut.context.annotation.Value;
import io.temporal.client.WorkflowClient;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkerFactory;
import io.temporal.worker.WorkerOptions;
import jakarta.inject.Singleton;
import lombok.Getter;

import java.time.Duration;

@Singleton
public class TemporalWorkerFactory {

    @Getter
    private WorkerFactory workerFactory;

    @Value("${temporal.queue}")
    String queueName;

    public void startWorkerFactory(WorkflowClient workflowClient,
                                   Class<?> workflowClass,
                                   Object activities) {
        WorkerOptions options = WorkerOptions.newBuilder()
                .setMaxConcurrentWorkflowTaskPollers(2)
                .setMaxConcurrentActivityTaskPollers(4)
                .setMaxConcurrentWorkflowTaskExecutionSize(2)
                .setDefaultHeartbeatThrottleInterval(Duration.ofMinutes(1))
                .setMaxConcurrentActivityExecutionSize(4)
                .setDefaultDeadlockDetectionTimeout(60000)
                .build();
        workerFactory = WorkerFactory.newInstance(workflowClient);
        Worker worker = workerFactory.newWorker(queueName, options);
        worker.registerWorkflowImplementationTypes(workflowClass);
        worker.registerActivitiesImplementations(activities);
        workerFactory.start();
    }
}
