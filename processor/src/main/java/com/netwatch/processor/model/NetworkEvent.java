package com.netwatch.processor.model;

import java.util.List;

/**
 * Raw network event consumed from the raw-events Kafka topic.
 *
 * <p>Field names match the snake_case JSON produced by the Python simulator
 * (global Jackson SNAKE_CASE naming strategy handles the mapping).
 */
public record NetworkEvent(
    String eventId,
    String timestamp,
    String deviceId,
    String srcIp,
    String dstIp,
    Integer srcPort,
    Integer dstPort,
    String protocol,
    long bytesSent,
    long bytesReceived,
    int packets,
    long durationMs,
    List<String> flags
) {
    /**
     * Extract the numeric feature vector used by the anomaly detector.
     * Order: bytesSent, bytesReceived, packets, durationMs, srcPort, dstPort.
     */
    public double[] toFeatureVector() {
        return new double[]{
            bytesSent,
            bytesReceived,
            packets,
            durationMs,
            srcPort != null ? srcPort : 0,
            dstPort != null ? dstPort : 0
        };
    }
}
