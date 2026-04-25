package com.netwatch.processor.api;

import com.netwatch.processor.consumer.RawEventConsumer;
import com.netwatch.processor.service.AnomalyDetectionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Internal diagnostics endpoint that surfaces pipeline counters so operators
 * can sanity-check throughput, anomaly rate, and per-device model coverage
 * without scraping logs.
 */
@RestController
@RequestMapping("/stats")
public class StatsController {

    private final RawEventConsumer consumer;
    private final AnomalyDetectionService detector;

    public StatsController(RawEventConsumer consumer,
                           AnomalyDetectionService detector) {
        this.consumer = consumer;
        this.detector = detector;
    }

    @GetMapping
    public Map<String, Object> stats() {
        long consumed = consumer.consumedCount();
        long anomalies = detector.anomaliesDetected();
        double anomalyRate = consumed == 0 ? 0.0 : (double) anomalies / consumed;

        return Map.of(
            "events_consumed", consumed,
            "events_dropped", consumer.droppedCount(),
            "anomalies_detected", anomalies,
            "anomaly_rate", anomalyRate,
            "devices_with_models", detector.devicesWithModels(),
            "models_trained", detector.modelsTrained()
        );
    }
}
