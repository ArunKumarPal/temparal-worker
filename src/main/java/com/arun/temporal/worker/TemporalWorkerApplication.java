package com.arun.temporal.worker;

import com.arun.temporal.worker.activities.BulkActivities;
import com.arun.temporal.worker.worker.TemporalWorkerFactory;
import com.arun.temporal.worker.workflow.BulkWorkflowImpl;
import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.runtime.Micronaut;
import io.micronaut.runtime.event.ApplicationStartupEvent;
import io.micronaut.runtime.event.annotation.EventListener;
import io.temporal.client.WorkflowClient;
import jakarta.inject.Singleton;

@Singleton
public class TemporalWorkerApplication implements ApplicationEventListener<ApplicationStartupEvent> {

    private final TemporalWorkerFactory temporalWorkerFactory;
    private final BulkActivities bulkActivities;
    private final WorkflowClient workflowClient;

    public TemporalWorkerApplication(TemporalWorkerFactory temporalWorkerFactory, BulkActivities bulkActivities, WorkflowClient workflowClient) {
        this.bulkActivities = bulkActivities;
        this.workflowClient = workflowClient;
        this.temporalWorkerFactory = temporalWorkerFactory;
    }

    @EventListener
    @Override
    public void onApplicationEvent(ApplicationStartupEvent event) {
        temporalWorkerFactory.startWorkerFactory(workflowClient, BulkWorkflowImpl.class, bulkActivities);
    }

    public static void main(String[] args) {
        Micronaut.run(TemporalWorkerApplication.class, args);
    }
}
