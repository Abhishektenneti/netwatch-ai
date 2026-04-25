package com.netwatch.processor.service;

import com.netwatch.processor.model.NetworkEvent;
import com.netwatch.processor.model.RollingStats;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Maintains a bounded sliding window of recent feature vectors per device and
 * computes rolling statistics (mean, std) over each window.
 *
 * <p>Thread-safe: each device window is accessed through a ConcurrentHashMap
 * and individual windows are synchronised.
 */
public class RollingWindowStore {

    private final int windowSize;
    private final ConcurrentMap<String, Deque<double[]>> windows = new ConcurrentHashMap<>();

    public RollingWindowStore(int windowSize) {
        this.windowSize = windowSize;
    }

    /**
     * Add an event's feature vector to the device window and return the
     * current rolling statistics (computed <em>after</em> the new event is
     * added).
     */
    public RollingStats addAndCompute(NetworkEvent event) {
        double[] features = event.toFeatureVector();
        Deque<double[]> window = windows.computeIfAbsent(
                event.deviceId(), k -> new ArrayDeque<>(windowSize));

        synchronized (window) {
            window.addLast(features);
            if (window.size() > windowSize) {
                window.removeFirst();
            }
            return computeStats(window);
        }
    }

    /**
     * Return all feature vectors currently stored for a device (for training
     * the Isolation Forest).  Returns an empty array if the device has no
     * history.
     */
    public double[][] getWindow(String deviceId) {
        Deque<double[]> window = windows.get(deviceId);
        if (window == null) return new double[0][];
        synchronized (window) {
            return window.toArray(double[][]::new);
        }
    }

    /** Number of devices currently tracked. */
    public int deviceCount() {
        return windows.size();
    }

    // ---- internal ----

    private static RollingStats computeStats(Deque<double[]> window) {
        int n = window.size();
        if (n == 0) {
            return new RollingStats(0, 0, 0, 0, 0, 0);
        }

        // Feature order: bytesSent(0), bytesReceived(1), packets(2),
        //                durationMs(3), srcPort(4), dstPort(5)
        double sumBytesSent = 0, sumBytesRecv = 0, sumDuration = 0;
        double sq2BytesSent = 0, sq2BytesRecv = 0;

        for (double[] f : window) {
            sumBytesSent += f[0];
            sumBytesRecv += f[1];
            sumDuration  += f[3];
            sq2BytesSent += f[0] * f[0];
            sq2BytesRecv += f[1] * f[1];
        }

        double meanSent = sumBytesSent / n;
        double meanRecv = sumBytesRecv / n;
        double meanDur  = sumDuration  / n;

        double stdSent = Math.sqrt(Math.max(0, sq2BytesSent / n - meanSent * meanSent));
        double stdRecv = Math.sqrt(Math.max(0, sq2BytesRecv / n - meanRecv * meanRecv));

        return new RollingStats(meanSent, stdSent, meanRecv, stdRecv, meanDur, n);
    }
}
