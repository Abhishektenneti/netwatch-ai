package com.netwatch.processor.service;

import com.netwatch.processor.config.NetwatchProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * Lightweight MLflow integration that logs experiment runs via the MLflow
 * REST API.
 *
 * <p>Each Isolation Forest training event is recorded as a run so operators
 * can track model drift and compare parameter sets over time.
 */
@Service
public class ModelTrackingService {

    private static final Logger log = LoggerFactory.getLogger(ModelTrackingService.class);

    private final RestClient rest;
    private final String experimentName;
    private volatile String experimentId;

    public ModelTrackingService(NetwatchProperties props) {
        this.rest = RestClient.builder()
                .baseUrl(props.mlflow().trackingUri())
                .build();
        this.experimentName = props.mlflow().experimentName();
    }

    /**
     * Log an Isolation Forest training run for a device.
     *
     * <p>Best-effort: failures are logged but never propagate.
     */
    public void logTrainingRun(String deviceId, int numTrees, int sampleSize,
                               int windowSize, double meanAnomalyScore) {
        try {
            ensureExperiment();
            String runId = createRun();
            if (runId == null) return;

            logParam(runId, "device_id", deviceId);
            logParam(runId, "num_trees", String.valueOf(numTrees));
            logParam(runId, "sample_size", String.valueOf(sampleSize));
            logParam(runId, "window_size", String.valueOf(windowSize));
            logMetric(runId, "mean_anomaly_score", meanAnomalyScore);

            updateRun(runId, "FINISHED");
            log.debug("Logged MLflow run={} for device={}", runId, deviceId);
        } catch (Exception e) {
            log.warn("MLflow logging failed for device={}: {}", deviceId, e.getMessage());
        }
    }

    // ---- MLflow REST helpers ----

    private void ensureExperiment() {
        if (experimentId != null) return;
        try {
            var body = rest.post()
                    .uri("/api/2.0/mlflow/experiments/create")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("name", experimentName))
                    .retrieve()
                    .body(Map.class);
            if (body != null) {
                experimentId = String.valueOf(body.get("experiment_id"));
            }
        } catch (Exception e) {
            // Experiment may already exist — try to fetch it
            try {
                var body = rest.get()
                        .uri("/api/2.0/mlflow/experiments/get-by-name?experiment_name={name}",
                             experimentName)
                        .retrieve()
                        .body(Map.class);
                if (body != null) {
                    @SuppressWarnings("unchecked")
                    var exp = (Map<String, Object>) body.get("experiment");
                    if (exp != null) {
                        experimentId = String.valueOf(exp.get("experiment_id"));
                    }
                }
            } catch (Exception e2) {
                log.debug("Could not resolve MLflow experiment: {}", e2.getMessage());
            }
        }
    }

    @SuppressWarnings("unchecked")
    private String createRun() {
        var body = rest.post()
                .uri("/api/2.0/mlflow/runs/create")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("experiment_id", experimentId != null ? experimentId : "0"))
                .retrieve()
                .body(Map.class);
        if (body == null) return null;
        var run = (Map<String, Object>) body.get("run");
        if (run == null) return null;
        var info = (Map<String, Object>) run.get("info");
        return info != null ? String.valueOf(info.get("run_id")) : null;
    }

    private void logParam(String runId, String key, String value) {
        rest.post()
            .uri("/api/2.0/mlflow/runs/log-parameter")
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("run_id", runId, "key", key, "value", value))
            .retrieve()
            .toBodilessEntity();
    }

    private void logMetric(String runId, String key, double value) {
        rest.post()
            .uri("/api/2.0/mlflow/runs/log-metric")
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("run_id", runId, "key", key, "value", value,
                         "timestamp", System.currentTimeMillis()))
            .retrieve()
            .toBodilessEntity();
    }

    private void updateRun(String runId, String status) {
        rest.post()
            .uri("/api/2.0/mlflow/runs/update")
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("run_id", runId, "status", status))
            .retrieve()
            .toBodilessEntity();
    }
}
