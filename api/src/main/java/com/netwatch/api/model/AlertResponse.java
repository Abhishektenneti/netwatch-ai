package com.netwatch.api.model;

/**
 * REST response model for an anomaly alert with explanation.
 */
public record AlertResponse(
    String eventId,
    String timestamp,
    String deviceId,
    String srcIp,
    String dstIp,
    String protocol,
    double anomalyScore,
    String explanation
) {}
