package com.netwatch.processor.ml;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class IsolationForestTest {

    /**
     * Build a forest from a cluster of "normal" points near the origin and
     * verify that a far-away outlier scores higher than a normal point.
     */
    @Test
    void outlierScoresHigherThanInlier() {
        Random rng = new Random(42);
        int n = 500;
        double[][] data = new double[n][2];
        for (int i = 0; i < n; i++) {
            data[i][0] = rng.nextGaussian() * 10;   // cluster around 0
            data[i][1] = rng.nextGaussian() * 10;
        }

        IsolationForest forest = IsolationForest.fit(data, 100, 256, 0L);

        double normalScore = forest.score(new double[]{0.0, 0.0});
        double outlierScore = forest.score(new double[]{1000.0, 1000.0});

        assertTrue(outlierScore > normalScore,
                "outlier score (%f) should exceed normal score (%f)"
                    .formatted(outlierScore, normalScore));
    }

    @Test
    void scoresInZeroToOneRange() {
        double[][] data = {{1, 2}, {3, 4}, {5, 6}, {7, 8}, {9, 10}};
        IsolationForest forest = IsolationForest.fit(data, 50, 5, 1L);

        for (double[] point : data) {
            double s = forest.score(point);
            assertTrue(s >= 0.0 && s <= 1.0,
                    "score %f out of [0,1] range".formatted(s));
        }
    }

    @Test
    void deterministicWithSameSeed() {
        double[][] data = {{1}, {2}, {3}, {100}};
        IsolationForest a = IsolationForest.fit(data, 20, 4, 77L);
        IsolationForest b = IsolationForest.fit(data, 20, 4, 77L);

        for (double[] point : data) {
            assertEquals(a.score(point), b.score(point), 1e-12);
        }
    }

    @Test
    void numTreesMatchesConfig() {
        double[][] data = {{1}, {2}, {3}};
        IsolationForest forest = IsolationForest.fit(data, 37, 3, 0L);
        assertEquals(37, forest.numTrees());
    }

    @Test
    void averagePathLengthEdgeCases() {
        assertEquals(0, IsolationTree.averagePathLength(0));
        assertEquals(0, IsolationTree.averagePathLength(1));
        assertEquals(1, IsolationTree.averagePathLength(2));
        assertTrue(IsolationTree.averagePathLength(100) > 0);
    }
}
