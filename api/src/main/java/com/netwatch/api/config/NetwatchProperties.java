package com.netwatch.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Type-safe binding for all {@code netwatch.*} configuration properties.
 */
@ConfigurationProperties(prefix = "netwatch")
public record NetwatchProperties(
    QdrantProps qdrant,
    KafkaTopics kafka
) {
    public record QdrantProps(String host, int port, String collection) {}

    public record KafkaTopics(Topics topics) {
        public record Topics(
            String anomalyEvents,
            String explainedEvents,
            String processedEvents
        ) {}
    }
}
