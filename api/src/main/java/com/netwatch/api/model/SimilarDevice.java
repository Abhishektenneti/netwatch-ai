package com.netwatch.api.model;

/**
 * A device with similar traffic behaviour, returned by the similarity search.
 */
public record SimilarDevice(
    String deviceId,
    double similarity,
    double anomalyScore,
    long eventCount
) {}
