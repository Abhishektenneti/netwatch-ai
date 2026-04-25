package com.netwatch.processor.producer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netwatch.processor.config.NetwatchProperties;
import com.netwatch.processor.model.ProcessedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Publishes enriched events to downstream Kafka topics.
 *
 * <ul>
 *   <li><b>processed-events</b> — every event (normal + anomalous)</li>
 *   <li><b>anomaly-events</b> — only events flagged as anomalies</li>
 * </ul>
 */
@Component
public class ProcessedEventProducer {

    private static final Logger log = LoggerFactory.getLogger(ProcessedEventProducer.class);

    private final KafkaTemplate<String, String> kafka;
    private final ObjectMapper mapper;
    private final String processedTopic;
    private final String anomalyTopic;

    public ProcessedEventProducer(KafkaTemplate<String, String> kafka,
                                  ObjectMapper mapper,
                                  NetwatchProperties props) {
        this.kafka = kafka;
        this.mapper = mapper;
        this.processedTopic = props.kafka().topics().processedEvents();
        this.anomalyTopic = props.kafka().topics().anomalyEvents();
    }

    public void send(ProcessedEvent event) {
        String json = serialise(event);
        if (json == null) return;

        String key = event.deviceId();

        // Every event goes to processed-events
        kafka.send(processedTopic, key, json);

        // Only anomalies go to anomaly-events
        if (event.isAnomaly()) {
            kafka.send(anomalyTopic, key, json);
            log.debug("Published anomaly event={} device={} score={}",
                      event.eventId(), key, event.anomalyScore());
        }
    }

    private String serialise(ProcessedEvent event) {
        try {
            return mapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialise ProcessedEvent {}: {}",
                      event.eventId(), e.getMessage());
            return null;
        }
    }
}
