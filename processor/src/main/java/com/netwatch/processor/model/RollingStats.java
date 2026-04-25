package com.netwatch.processor.model;

/**
 * Rolling statistics computed per device over a sliding window.
 */
public record RollingStats(
    double meanBytesSent,
    double stdBytesSent,
    double meanBytesReceived,
    double stdBytesReceived,
    double meanDurationMs,
    long eventCount
) {}
