package com.netwatch.processor.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Type-safe binding for all {@code netwatch.*} configuration properties.
 */
@ConfigurationProperties(prefix = "netwatch")
public record NetwatchProperties(
    KafkaTopics kafka,
    QdrantProps qdrant,
    MlflowProps mlflow,
    AnomalyProps anomaly
) {
    public record KafkaTopics(Topics topics) {
        public record Topics(String rawEvents, String processedEvents, String anomalyEvents) {}
    }

    public record QdrantProps(String host, int port, String collection, int vectorSize) {}

    public record MlflowProps(String trackingUri, String experimentName) {}

    public record AnomalyProps(int numTrees, int sampleSize, double scoreThreshold, int windowSize) {}
}
