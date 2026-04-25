package com.netwatch.processor.model;

import java.util.List;

/**
 * Enriched event with rolling statistics and anomaly detection results.
 */
public record ProcessedEvent(
    String eventId,
    String timestamp,
    String deviceId,
    String srcIp,
    String dstIp,
    String protocol,
    long bytesSent,
    long bytesReceived,
    int packets,
    long durationMs,
    List<String> flags,
    double anomalyScore,
    boolean isAnomaly,
    RollingStats rollingStats
) {
    public static ProcessedEvent from(NetworkEvent raw, double anomalyScore,
                                      boolean isAnomaly, RollingStats stats) {
        return new ProcessedEvent(
            raw.eventId(), raw.timestamp(), raw.deviceId(),
            raw.srcIp(), raw.dstIp(), raw.protocol(),
            raw.bytesSent(), raw.bytesReceived(), raw.packets(), raw.durationMs(),
            raw.flags(),
            anomalyScore, isAnomaly, stats
        );
    }
}
