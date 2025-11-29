package com.arun.temporal.worker.worker;

import org.junit.jupiter.api.Test;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class WorkerStatusTest {

    @Test
    void testStartAndEndActivity() {
        WorkerStatus status = new WorkerStatus();

        int initial = status.runningActivity();
        Instant before = status.lastTimestamp();

        status.startActivity();
        assertEquals(initial + 1, status.runningActivity());
        assertTrue(status.lastTimestamp().isAfter(before) || status.lastTimestamp().equals(before));

        status.endActivity();
        assertEquals(initial, status.runningActivity());
    }

    @Test
    void testMultipleActivities() {
        WorkerStatus status = new WorkerStatus();

        status.startActivity();
        status.startActivity();
        assertEquals(2, status.runningActivity());

        status.endActivity();
        assertEquals(1, status.runningActivity());

        status.endActivity();
        assertEquals(0, status.runningActivity());
    }
}

