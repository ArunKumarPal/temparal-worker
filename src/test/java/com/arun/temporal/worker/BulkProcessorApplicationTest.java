package com.arun.temporal.worker;

import com.arun.temporal.worker.activities.BulkActivities;
import com.arun.temporal.worker.worker.TemporalWorkerFactory;
import io.micronaut.runtime.event.ApplicationStartupEvent;
import io.temporal.client.WorkflowClient;
import io.temporal.worker.Worker;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;

@ExtendWith(MockitoExtension.class)
class BulkProcessorApplicationTest {

    @Mock
    WorkflowClient workflowClient;
    @Mock
    BulkActivities bulkActivities;
    @Mock
    TemporalWorkerFactory temporalWorkerFactory;
    @Mock
    Worker worker;

    @Test
    void testOnApplicationEvent() {
        doNothing().when(temporalWorkerFactory).startWorkerFactory(any(), any(), any());
        TemporalWorkerApplication app = new TemporalWorkerApplication(temporalWorkerFactory, bulkActivities, workflowClient);
        app.onApplicationEvent(Mockito.mock(ApplicationStartupEvent.class));
    }
}