package com.netwatch.processor.ml;

import java.util.Arrays;
import java.util.random.RandomGenerator;
import java.util.random.RandomGeneratorFactory;

/**
 * Isolation Forest anomaly detector (Liu, Ting &amp; Zhou, 2008).
 *
 * <p>An ensemble of {@link IsolationTree}s is built from random subsamples of a
 * training set.  At scoring time the average path length across all trees is
 * converted to an anomaly score in [0, 1] where values &gt; 0.5 indicate
 * increasing anomalousness.
 *
 * <p>This is a pure-Java implementation with no external ML library dependency.
 */
public final class IsolationForest {

    private final IsolationTree[] trees;
    private final int sampleSize;

    private IsolationForest(IsolationTree[] trees, int sampleSize) {
        this.trees = trees;
        this.sampleSize = sampleSize;
    }

    /**
     * Train a new forest.
     *
     * @param data       training data (row-major, each row = one observation)
     * @param numTrees   number of isolation trees
     * @param sampleSize subsample size per tree (typically 256)
     * @param seed       random seed for reproducibility
     */
    public static IsolationForest fit(double[][] data, int numTrees,
                                      int sampleSize, long seed) {
        int effectiveSample = Math.min(sampleSize, data.length);
        int maxDepth = (int) Math.ceil(Math.log(effectiveSample) / Math.log(2));
        RandomGenerator rng = RandomGeneratorFactory.of("L64X128MixRandom")
                .create(seed);

        IsolationTree[] trees = new IsolationTree[numTrees];
        for (int t = 0; t < numTrees; t++) {
            double[][] sample = subsample(data, effectiveSample, rng);
            trees[t] = IsolationTree.build(sample, maxDepth, rng);
        }
        return new IsolationForest(trees, effectiveSample);
    }

    /**
     * Compute the anomaly score for a single observation.
     *
     * @return score in [0, 1]; values closer to 1 are more anomalous
     */
    public double score(double[] point) {
        double avgPath = Arrays.stream(trees)
                .mapToDouble(t -> t.pathLength(point))
                .average()
                .orElse(0);
        double c = IsolationTree.averagePathLength(sampleSize);
        if (c == 0) return 0.5;
        return Math.pow(2.0, -avgPath / c);
    }

    /** Number of trees in the ensemble. */
    public int numTrees() {
        return trees.length;
    }

    // ---- internal ----

    private static double[][] subsample(double[][] data, int size,
                                        RandomGenerator rng) {
        double[][] sample = new double[size][];
        for (int i = 0; i < size; i++) {
            sample[i] = data[rng.nextInt(data.length)];
        }
        return sample;
    }
}
