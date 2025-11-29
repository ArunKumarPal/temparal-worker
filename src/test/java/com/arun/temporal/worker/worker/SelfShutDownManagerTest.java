package com.arun.temporal.worker.worker;

import io.temporal.worker.WorkerFactory;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.Duration;
import java.time.Instant;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SelfShutDownManagerTest {

    @Mock
    WorkerStatus workerStatus;
    @Mock
    TemporalWorkerFactory temporalWorkerFactory;
    @Mock
    WorkerFactory workerFactory;

    @InjectMocks
    SelfShutDownManager manager;

    @Test
    void shouldShutdownWhenIdle() {
        when(workerStatus.lastTimestamp()).thenReturn(Instant.now().minus(Duration.ofMinutes(20)));
        when(workerStatus.runningActivity()).thenReturn(0);
        when(temporalWorkerFactory.getWorkerFactory()).thenReturn(workerFactory);

        boolean result = invokeShouldContinueShutdownCheck(manager);

        verify(workerFactory).suspendPolling();
        verify(workerFactory).shutdown();
        verify(workerFactory, never()).resumePolling();
        assert(!result);
    }

    @Test
    void shouldNotShutdownWhenActive() {
        when(workerStatus.lastTimestamp()).thenReturn(Instant.now());
        when(workerStatus.runningActivity()).thenReturn(1);

        boolean result = invokeShouldContinueShutdownCheck(manager);

        verify(workerFactory, never()).suspendPolling();
        verify(workerFactory, never()).shutdown();
        verify(workerFactory, never()).resumePolling();
        assert(result);
    }

    @Test
    void shouldAbortShutdownIfActivityDetectedDuringGracePeriod() {
        when(workerStatus.lastTimestamp())
                .thenReturn(Instant.now().minus(Duration.ofMinutes(20)))
                .thenReturn(Instant.now());
        when(workerStatus.runningActivity()).thenReturn(0).thenReturn(1);
        when(temporalWorkerFactory.getWorkerFactory()).thenReturn(workerFactory);

        boolean result = invokeShouldContinueShutdownCheck(manager);

        verify(workerFactory).suspendPolling();
        verify(workerFactory, never()).shutdown();
        verify(workerFactory).resumePolling();
        assert(result);
    }

    private boolean invokeShouldContinueShutdownCheck(SelfShutDownManager manager) {
        try {
            var m = SelfShutDownManager.class.getDeclaredMethod("shouldContinueShutdownCheck");
            m.setAccessible(true);
            return (boolean) m.invoke(manager);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
