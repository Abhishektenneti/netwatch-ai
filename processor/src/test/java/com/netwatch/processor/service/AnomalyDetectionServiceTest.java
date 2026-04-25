package com.netwatch.processor.service;

import com.netwatch.processor.config.NetwatchProperties;
import com.netwatch.processor.config.NetwatchProperties.*;
import com.netwatch.processor.model.NetworkEvent;
import com.netwatch.processor.model.ProcessedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AnomalyDetectionServiceTest {

    private AnomalyDetectionService service;

    @BeforeEach
    void setUp() {
        // Wire up with small window + sample so the forest trains quickly
        var props = new NetwatchProperties(
            new KafkaTopics(new KafkaTopics.Topics("raw", "proc", "anom")),
            new QdrantProps("localhost", 6334, "test", 8),
            new MlflowProps("http://localhost:5000", "test"),
            new AnomalyProps(50, 10, 0.6, 50) // 50 trees, sample 10, threshold 0.6, window 50
        );
        // Run the trainer on the caller thread so assertions don't race with it,
        // and pass a null ModelTrackingService — MLflow isn't part of these tests.
        service = new AnomalyDetectionService(props, null, Runnable::run);
    }

    private NetworkEvent normalEvent(String deviceId, int seq) {
        return new NetworkEvent(
            UUID.randomUUID().toString(), "2025-01-01T00:00:00Z", deviceId,
            "10.0.0.1", "10.0.0.2", 1024, 80, "TCP",
            2000 + seq, 5000 + seq, 15, 200,
            List.of()
        );
    }

    private NetworkEvent outlierEvent(String deviceId) {
        return new NetworkEvent(
            UUID.randomUUID().toString(), "2025-01-01T00:00:00Z", deviceId,
            "10.0.0.1", "10.0.0.2", 1024, 80, "TCP",
            5_000_000, 0, 500, 5,  // massive bytes, zero received, many packets, tiny duration
            List.of("data_exfiltration")
        );
    }

    @Test
    void processReturnsEnrichedEvent() {
        ProcessedEvent result = service.process(normalEvent("dev-0001", 0));

        assertNotNull(result);
        assertEquals("dev-0001", result.deviceId());
        assertNotNull(result.rollingStats());
        assertEquals(1, result.rollingStats().eventCount());
    }

    @Test
    void beforeTrainingScoreIsNeutral() {
        // With only 1 event, there isn't enough data to train a forest
        ProcessedEvent result = service.process(normalEvent("dev-0001", 0));
        assertEquals(0.5, result.anomalyScore(), 1e-6, "default score should be 0.5");
    }

    @Test
    void afterTrainingNormalEventsScoreLow() {
        // Feed enough events to trigger training (sampleSize = 10)
        for (int i = 0; i < 15; i++) {
            service.process(normalEvent("dev-0001", i));
        }
        // Now process one more normal event — should score below threshold
        ProcessedEvent result = service.process(normalEvent("dev-0001", 99));
        assertTrue(result.anomalyScore() < 0.6,
                "normal event should score below threshold, got " + result.anomalyScore());
        assertFalse(result.isAnomaly());
    }

    @Test
    void outlierScoresHigherAfterTraining() {
        // Train on normal data
        for (int i = 0; i < 20; i++) {
            service.process(normalEvent("dev-0002", i));
        }
        // Inject outlier
        ProcessedEvent outlier = service.process(outlierEvent("dev-0002"));
        ProcessedEvent normal = service.process(normalEvent("dev-0002", 99));

        assertTrue(outlier.anomalyScore() > normal.anomalyScore(),
                "outlier score (%f) should exceed normal score (%f)"
                    .formatted(outlier.anomalyScore(), normal.anomalyScore()));
    }

    @Test
    void rollingStatsUpdateCorrectly() {
        service.process(normalEvent("dev-0003", 0));
        service.process(normalEvent("dev-0003", 0));
        ProcessedEvent third = service.process(normalEvent("dev-0003", 0));

        assertEquals(3, third.rollingStats().eventCount());
        assertTrue(third.rollingStats().meanBytesSent() > 0);
    }

    @Test
    void differentDevicesHaveIndependentModels() {
        // Train device A on normal data
        for (int i = 0; i < 15; i++) {
            service.process(normalEvent("dev-A", i));
        }
        // Device B has no model yet — should get default 0.5
        ProcessedEvent result = service.process(normalEvent("dev-B", 0));
        assertEquals(0.5, result.anomalyScore(), 1e-6);
    }
}
