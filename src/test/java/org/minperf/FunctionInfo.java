package org.minperf;

/**
 * The information (time and space usage) of a minimum perfect hash function.
 */
public class FunctionInfo {

    int size;
    int split;
    int firstPartSize;
    int leafSize;
    int loadFactor;
    double bitsPerKey;
    double headerBitsPerKey;
    double generateNanos;
    double evaluateNanos;

    @Override
    public String toString() {
        return "size: " + size +
                " leafSize: " + leafSize +
                " loadFactor: " + loadFactor +
                " bitsPerKey: " + bitsPerKey +
                " generateSeconds: " + (generateNanos * size / 1_000_000_000) +
                " generateNanosPerKey: " + generateNanos +
                " evaluateNanosPerKey: " + evaluateNanos;
    }

}
