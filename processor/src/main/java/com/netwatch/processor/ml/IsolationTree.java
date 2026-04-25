package com.netwatch.processor.ml;

import java.util.random.RandomGenerator;

/**
 * A single tree in an Isolation Forest.
 *
 * <p>Built by recursively picking a random feature and a random split value
 * between the min/max of that feature in the sample.  A point that is easy to
 * isolate (i.e. anomalous) reaches an external node after few splits, giving
 * it a short path length.
 */
final class IsolationTree {

    private final Node root;

    private IsolationTree(Node root) {
        this.root = root;
    }

    /**
     * Build a tree from a subsample of the data.
     *
     * @param data      row-major matrix (each row is one observation)
     * @param maxDepth  height limit ≈ ceil(log2(sampleSize))
     * @param rng       random generator (caller controls seed)
     */
    static IsolationTree build(double[][] data, int maxDepth, RandomGenerator rng) {
        return new IsolationTree(buildNode(data, 0, maxDepth, rng));
    }

    /** Return the path length for {@code point} in this tree. */
    double pathLength(double[] point) {
        return pathLength(point, root, 0);
    }

    // ---- internal ----

    private sealed interface Node permits InternalNode, ExternalNode {}

    private record InternalNode(int splitFeature, double splitValue,
                                Node left, Node right) implements Node {}

    private record ExternalNode(int size) implements Node {}

    private static Node buildNode(double[][] data, int depth, int maxDepth,
                                  RandomGenerator rng) {
        int n = data.length;
        if (n <= 1 || depth >= maxDepth) {
            return new ExternalNode(n);
        }

        int numFeatures = data[0].length;
        int feature = rng.nextInt(numFeatures);

        double min = Double.MAX_VALUE;
        double max = -Double.MAX_VALUE;
        for (double[] row : data) {
            if (row[feature] < min) min = row[feature];
            if (row[feature] > max) max = row[feature];
        }

        if (min == max) {
            return new ExternalNode(n);
        }

        double splitValue = min + rng.nextDouble() * (max - min);

        // Partition into left (< splitValue) and right (>= splitValue)
        int leftCount = 0;
        for (double[] row : data) {
            if (row[feature] < splitValue) leftCount++;
        }

        double[][] leftData = new double[leftCount][];
        double[][] rightData = new double[n - leftCount][];
        int li = 0, ri = 0;
        for (double[] row : data) {
            if (row[feature] < splitValue) {
                leftData[li++] = row;
            } else {
                rightData[ri++] = row;
            }
        }

        return new InternalNode(
            feature, splitValue,
            buildNode(leftData, depth + 1, maxDepth, rng),
            buildNode(rightData, depth + 1, maxDepth, rng)
        );
    }

    private static double pathLength(double[] point, Node node, int currentDepth) {
        return switch (node) {
            case ExternalNode ext -> currentDepth + averagePathLength(ext.size());
            case InternalNode internal -> {
                if (point[internal.splitFeature()] < internal.splitValue()) {
                    yield pathLength(point, internal.left(), currentDepth + 1);
                } else {
                    yield pathLength(point, internal.right(), currentDepth + 1);
                }
            }
        };
    }

    /**
     * Average path length of an unsuccessful search in a BST with {@code n}
     * elements — the normalization constant c(n) from the original Isolation
     * Forest paper (Liu et al. 2008).
     */
    static double averagePathLength(int n) {
        if (n <= 1) return 0;
        if (n == 2) return 1;
        double harmonic = Math.log(n - 1.0) + 0.5772156649; // Euler-Mascheroni
        return 2.0 * harmonic - 2.0 * (n - 1.0) / n;
    }
}
