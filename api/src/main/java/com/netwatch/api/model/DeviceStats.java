package com.netwatch.api.model;

/**
 * Summary statistics for a given device.
 */
public record DeviceStats(
    String deviceId,
    long totalEvents,
    long anomalyCount,
    double anomalyRate
) {}
