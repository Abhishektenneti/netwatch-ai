package com.netwatch.processor.service;

import com.netwatch.processor.config.NetwatchProperties;
import com.netwatch.processor.ml.IsolationForest;
import com.netwatch.processor.model.NetworkEvent;
import com.netwatch.processor.model.ProcessedEvent;
import com.netwatch.processor.model.RollingStats;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

import jakarta.annotation.PreDestroy;

/**
 * Core anomaly detection pipeline.
 *
 * <ol>
 *   <li>Maintain a per-device rolling window of recent feature vectors.</li>
 *   <li>Compute rolling statistics (mean, std) for the enriched event.</li>
 *   <li>Train/retrain a per-device Isolation Forest once enough data has
 *       accumulated (≥ sampleSize events). Training happens on a dedicated
 *       executor so the Kafka consumer thread is never blocked.</li>
 *   <li>Score the incoming event and flag it as anomalous when the score
 *       exceeds the configured threshold.</li>
 * </ol>
 */
@Service
public class AnomalyDetectionService {

    private static final Logger log = LoggerFactory.getLogger(AnomalyDetectionService.class);

    private final RollingWindowStore windowStore;
    private final ModelTrackingService tracking;
    private final ConcurrentMap<String, IsolationForest> forests = new ConcurrentHashMap<>();
    /** Devices whose training job is currently in flight — prevents piling up work. */
    private final ConcurrentMap<String, Boolean> trainingInFlight = new ConcurrentHashMap<>();
    /** Rolling sum of observed anomaly scores per device (for MLflow metric). */
    private final ConcurrentMap<String, ScoreAccumulator> scoreAcc = new ConcurrentHashMap<>();

    private final Executor trainer;
    /** Non-null only when we own the executor (so we can shut it down). */
    private final ExecutorService ownedTrainer;

    private final int numTrees;
    private final int sampleSize;
    private final double scoreThreshold;
    private final int windowSize;

    private final LongAdder anomaliesDetected = new LongAdder();
    private final LongAdder modelsTrained = new LongAdder();

    public AnomalyDetectionService(NetwatchProperties props, ModelTrackingService tracking) {
        this(props, tracking, defaultTrainer());
        // defaultTrainer returns an ExecutorService we own
    }

    /**
     * Package-private constructor for tests — lets the caller inject a
     * synchronous "direct" executor so {@link #process(NetworkEvent)} returns
     * with training already complete.
     */
    AnomalyDetectionService(NetwatchProperties props, ModelTrackingService tracking,
                            Executor trainer) {
        var cfg = props.anomaly();
        this.numTrees = cfg.numTrees();
        this.sampleSize = cfg.sampleSize();
        this.scoreThreshold = cfg.scoreThreshold();
        this.windowSize = cfg.windowSize();
        this.windowStore = new RollingWindowStore(windowSize);
        this.tracking = tracking;
        this.trainer = trainer;
        this.ownedTrainer = (trainer instanceof ExecutorService es) ? es : null;
    }

    private static ExecutorService defaultTrainer() {
        return Executors.newFixedThreadPool(
                Math.max(2, Runtime.getRuntime().availableProcessors() / 2),
                r -> {
                    Thread t = new Thread(r, "if-trainer");
                    t.setDaemon(true);
                    return t;
                });
    }

    /**
     * Process a single raw event: enrich with rolling stats, score for
     * anomalies, and return the fully enriched {@link ProcessedEvent}.
     */
    public ProcessedEvent process(NetworkEvent raw) {
        // 1. Update rolling window and get stats
        RollingStats stats = windowStore.addAndCompute(raw);

        String deviceId = raw.deviceId();

        // 2. Kick off async (re)training when appropriate.
        maybeTriggerTraining(deviceId, stats.eventCount());

        // 3. Score with whatever model we currently have (may be null early on).
        double score = 0.5; // default — neutral until a model is available
        IsolationForest forest = forests.get(deviceId);
        if (forest != null) {
            score = forest.score(raw.toFeatureVector());
        }

        boolean isAnomaly = score >= scoreThreshold;
        if (isAnomaly) {
            anomaliesDetected.increment();
            log.info("ANOMALY device={} score={} event={}", deviceId, score, raw.eventId());
        }

        // 4. Accumulate per-device score so the next training run can report
        //    a meaningful mean_anomaly_score metric to MLflow.
        scoreAcc.computeIfAbsent(deviceId, k -> new ScoreAccumulator()).add(score);

        return ProcessedEvent.from(raw, score, isAnomaly, stats);
    }

    public long anomaliesDetected() {
        return anomaliesDetected.sum();
    }

    public long modelsTrained() {
        return modelsTrained.sum();
    }

    public int devicesWithModels() {
        return forests.size();
    }

    /** Visible for testing. */
    RollingWindowStore windowStore() {
        return windowStore;
    }

    @PreDestroy
    void shutdown() {
        if (ownedTrainer != null) {
            ownedTrainer.shutdown();
        }
    }

    // ---- internal ----

    /**
     * Decide whether to fire an async training job for this device.
     * Triggers on:
     *   - first time the window reaches sampleSize (no existing model), OR
     *   - every {@code sampleSize} subsequent events (periodic refresh).
     * A single device never has two concurrent training jobs.
     */
    private void maybeTriggerTraining(String deviceId, long eventCount) {
        boolean noModelYet = !forests.containsKey(deviceId);
        boolean enoughData = eventCount >= sampleSize;
        boolean periodicRefresh = enoughData && eventCount % sampleSize == 0;

        if (!enoughData) return;
        if (!noModelYet && !periodicRefresh) return;

        // Take the "slot" atomically; skip if a job is already running.
        if (trainingInFlight.putIfAbsent(deviceId, Boolean.TRUE) != null) {
            return;
        }

        trainer.execute(() -> {
            try {
                double[][] snapshot = windowStore.getWindow(deviceId);
                if (snapshot.length < sampleSize) return;
                trainForest(deviceId, snapshot);
            } catch (Exception e) {
                log.warn("Training failed for device={}: {}", deviceId, e.getMessage());
            } finally {
                trainingInFlight.remove(deviceId);
            }
        });
    }

    private void trainForest(String deviceId, double[][] data) {
        long seed = deviceId.hashCode();
        IsolationForest forest = IsolationForest.fit(data, numTrees, sampleSize, seed);
        forests.put(deviceId, forest);
        modelsTrained.increment();
        log.debug("Trained IsolationForest for device={} trees={} samples={}",
                  deviceId, numTrees, data.length);

        // Best-effort MLflow logging — never throws.
        ScoreAccumulator acc = scoreAcc.get(deviceId);
        double meanScore = acc != null ? acc.snapshotAndReset() : 0.0;
        if (tracking != null) {
            tracking.logTrainingRun(deviceId, numTrees, sampleSize, windowSize, meanScore);
        }
    }

    /** Thread-safe running-mean accumulator (reset each time stats are reported). */
    private static final class ScoreAccumulator {
        private final AtomicLong count = new AtomicLong();
        private volatile double sum;

        synchronized void add(double v) {
            sum += v;
            count.incrementAndGet();
        }

        synchronized double snapshotAndReset() {
            long n = count.getAndSet(0);
            double s = sum;
            sum = 0.0;
            return n == 0 ? 0.0 : s / n;
        }
    }
}
