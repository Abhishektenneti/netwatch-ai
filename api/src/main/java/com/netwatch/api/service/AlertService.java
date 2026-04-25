package com.netwatch.api.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netwatch.api.model.AlertResponse;
import com.netwatch.api.model.DeviceStats;
import com.netwatch.api.repository.AlertRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * Consumes three Kafka topics to maintain the in-memory alert store:
 *
 * <ul>
 *   <li>{@code anomaly-events} — stores the full alert (no explanation yet)</li>
 *   <li>{@code explained-events} — attaches an LLM explanation to an existing alert</li>
 *   <li>{@code processed-events} — tracks per-device total event counts for
 *       {@link #deviceStats(String)} without storing the full event</li>
 * </ul>
 */
@Service
public class AlertService {

    private static final Logger log = LoggerFactory.getLogger(AlertService.class);

    private final AlertRepository repository;
    private final ObjectMapper mapper;

    /** Per-device total event counter (fed by processed-events topic). */
    private final ConcurrentMap<String, LongAdder> eventCounts = new ConcurrentHashMap<>();

    public AlertService(AlertRepository repository, ObjectMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    // ---- Kafka listeners ----

    @KafkaListener(
        topics = "${netwatch.kafka.topics.anomaly-events}",
        groupId = "${spring.kafka.consumer.group-id}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void onAnomalyEvent(String message) {
        try {
            JsonNode node = mapper.readTree(message);
            AlertResponse alert = new AlertResponse(
                text(node, "event_id"),
                text(node, "timestamp"),
                text(node, "device_id"),
                text(node, "src_ip"),
                text(node, "dst_ip"),
                text(node, "protocol"),
                node.path("anomaly_score").asDouble(0.0),
                null  // explanation arrives later via explained-events
            );
            repository.save(alert);
            log.debug("Stored alert eventId={} device={}", alert.eventId(), alert.deviceId());
        } catch (Exception e) {
            log.warn("Failed to parse anomaly event: {}", e.getMessage());
        }
    }

    @KafkaListener(
        topics = "${netwatch.kafka.topics.explained-events}",
        groupId = "${spring.kafka.consumer.group-id}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void onExplainedEvent(String message) {
        try {
            JsonNode node = mapper.readTree(message);
            String eventId = text(node, "event_id");
            String explanation = text(node, "explanation");
            if (eventId != null && explanation != null) {
                repository.updateExplanation(eventId, explanation);
                log.debug("Updated explanation for eventId={}", eventId);
            }
        } catch (Exception e) {
            log.warn("Failed to parse explained event: {}", e.getMessage());
        }
    }

    /**
     * Counts every processed event per device so {@link DeviceStats#totalEvents()}
     * reflects all traffic, not just anomalies.  Only the {@code device_id}
     * field is read — no full deserialization.
     */
    @KafkaListener(
        topics = "${netwatch.kafka.topics.processed-events}",
        groupId = "${spring.kafka.consumer.group-id}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void onProcessedEvent(String message) {
        try {
            // Read only the device_id field to avoid full deserialization overhead.
            String deviceId = mapper.readTree(message).path("device_id").asText(null);
            if (deviceId != null) {
                eventCounts.computeIfAbsent(deviceId, k -> new LongAdder()).increment();
            }
        } catch (Exception e) {
            log.warn("Failed to read device_id from processed event: {}", e.getMessage());
        }
    }

    // ---- Query API ----

    public DeviceStats deviceStats(String deviceId) {
        long totalEvents = eventCounts.getOrDefault(deviceId, new LongAdder()).sum();
        long anomalyCount = repository.countByDeviceId(deviceId);
        double anomalyRate = totalEvents == 0 ? 0.0 : (double) anomalyCount / totalEvents;
        return new DeviceStats(deviceId, totalEvents, anomalyCount, anomalyRate);
    }

    public boolean deviceKnown(String deviceId) {
        return eventCounts.containsKey(deviceId);
    }

    // ---- internal ----

    private static String text(JsonNode node, String field) {
        JsonNode f = node.get(field);
        return (f == null || f.isNull()) ? null : f.asText();
    }
}
