package com.netwatch.processor.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netwatch.processor.model.NetworkEvent;
import com.netwatch.processor.model.ProcessedEvent;
import com.netwatch.processor.producer.ProcessedEventProducer;
import com.netwatch.processor.service.AnomalyDetectionService;
import com.netwatch.processor.service.EmbeddingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.LongAdder;

/**
 * Kafka consumer that reads raw network events from the {@code raw-events}
 * topic, runs them through the anomaly detection pipeline, and publishes the
 * enriched results downstream.
 */
@Component
public class RawEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(RawEventConsumer.class);

    private final ObjectMapper mapper;
    private final AnomalyDetectionService detector;
    private final ProcessedEventProducer producer;
    private final EmbeddingService embeddings;

    private final LongAdder consumed = new LongAdder();
    private final LongAdder dropped = new LongAdder();

    public RawEventConsumer(ObjectMapper mapper,
                            AnomalyDetectionService detector,
                            ProcessedEventProducer producer,
                            EmbeddingService embeddings) {
        this.mapper = mapper;
        this.detector = detector;
        this.producer = producer;
        this.embeddings = embeddings;
    }

    @KafkaListener(
        topics = "${netwatch.kafka.topics.raw-events}",
        groupId = "${spring.kafka.consumer.group-id}"
    )
    public void onMessage(String message) {
        NetworkEvent raw;
        try {
            raw = mapper.readValue(message, NetworkEvent.class);
        } catch (JsonProcessingException e) {
            dropped.increment();
            log.warn("Dropping unparseable message: {}", e.getMessage());
            return;
        }

        // 1. Enrich + score
        ProcessedEvent processed = detector.process(raw);

        // 2. Publish to downstream Kafka topics
        producer.send(processed);

        // 3. Update device embedding in Qdrant (best-effort)
        embeddings.upsert(processed);

        consumed.increment();
        long c = consumed.sum();
        if (c % 500 == 0) {
            log.info("Consumed {} events", c);
        }
    }

    public long consumedCount() {
        return consumed.sum();
    }

    public long droppedCount() {
        return dropped.sum();
    }
}
