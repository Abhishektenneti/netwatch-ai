package com.netwatch.processor.service;

import com.netwatch.processor.model.NetworkEvent;
import com.netwatch.processor.model.RollingStats;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RollingWindowStoreTest {

    private static NetworkEvent event(String deviceId, long bytesSent,
                                      long bytesReceived, int packets,
                                      long durationMs) {
        return new NetworkEvent(
            "evt-1", "2025-01-01T00:00:00Z", deviceId,
            "10.0.0.1", "10.0.0.2", 1024, 80, "TCP",
            bytesSent, bytesReceived, packets, durationMs,
            List.of()
        );
    }

    @Test
    void singleEventStats() {
        var store = new RollingWindowStore(100);
        RollingStats stats = store.addAndCompute(event("d1", 100, 200, 5, 50));

        assertEquals(1, stats.eventCount());
        assertEquals(100.0, stats.meanBytesSent(), 1e-6);
        assertEquals(0.0, stats.stdBytesSent(), 1e-6);
        assertEquals(200.0, stats.meanBytesReceived(), 1e-6);
        assertEquals(50.0, stats.meanDurationMs(), 1e-6);
    }

    @Test
    void twoEventsComputeMeanAndStd() {
        var store = new RollingWindowStore(100);
        store.addAndCompute(event("d1", 100, 200, 5, 50));
        RollingStats stats = store.addAndCompute(event("d1", 300, 400, 15, 150));

        assertEquals(2, stats.eventCount());
        assertEquals(200.0, stats.meanBytesSent(), 1e-6);
        // std = sqrt(mean(x^2) - mean(x)^2) = sqrt((100^2+300^2)/2 - 200^2)
        //     = sqrt(50000 - 40000) = 100
        assertEquals(100.0, stats.stdBytesSent(), 1e-6);
    }

    @Test
    void windowEvictsOldEntries() {
        var store = new RollingWindowStore(3);

        // Fill window with 3 events: 10, 20, 30
        store.addAndCompute(event("d1", 10, 0, 1, 0));
        store.addAndCompute(event("d1", 20, 0, 1, 0));
        store.addAndCompute(event("d1", 30, 0, 1, 0));

        // Add a 4th — should evict the first (10)
        RollingStats stats = store.addAndCompute(event("d1", 40, 0, 1, 0));

        assertEquals(3, stats.eventCount()); // window capped at 3
        assertEquals(30.0, stats.meanBytesSent(), 1e-6); // (20+30+40)/3
    }

    @Test
    void devicesAreIsolated() {
        var store = new RollingWindowStore(100);
        store.addAndCompute(event("d1", 100, 0, 1, 0));
        RollingStats stats = store.addAndCompute(event("d2", 999, 0, 1, 0));

        // d2 should only have 1 event
        assertEquals(1, stats.eventCount());
        assertEquals(999.0, stats.meanBytesSent(), 1e-6);
    }

    @Test
    void getWindowReturnsFeatureVectors() {
        var store = new RollingWindowStore(100);
        store.addAndCompute(event("d1", 100, 200, 5, 50));
        store.addAndCompute(event("d1", 300, 400, 15, 150));

        double[][] window = store.getWindow("d1");
        assertEquals(2, window.length);
        assertEquals(6, window[0].length); // feature vector size

        // First event: bytesSent=100
        assertEquals(100.0, window[0][0], 1e-6);
        // Second event: bytesSent=300
        assertEquals(300.0, window[1][0], 1e-6);
    }

    @Test
    void unknownDeviceReturnsEmptyWindow() {
        var store = new RollingWindowStore(100);
        double[][] window = store.getWindow("nonexistent");
        assertEquals(0, window.length);
    }

    @Test
    void deviceCountTracksDistinctDevices() {
        var store = new RollingWindowStore(100);
        assertEquals(0, store.deviceCount());

        store.addAndCompute(event("d1", 1, 1, 1, 1));
        assertEquals(1, store.deviceCount());

        store.addAndCompute(event("d2", 1, 1, 1, 1));
        assertEquals(2, store.deviceCount());

        // Same device again — no new entry
        store.addAndCompute(event("d1", 2, 2, 2, 2));
        assertEquals(2, store.deviceCount());
    }
}
