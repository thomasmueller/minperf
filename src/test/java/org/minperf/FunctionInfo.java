package org.minperf;

/**
 * The information (time and space usage) of a minimum perfect hash function.
 */
public class FunctionInfo {

    int size;
    int split;
    int firstPartSize;
    int leafSize;
    int averageBucketSize;
    double bitsPerKey;
    double generateNanos;
    double evaluateNanos;

    @Override
    public String toString() {
        String s = "size " + size +
                " leafSize " + leafSize +
                " bits/key " + bitsPerKey +
                " generate " + generateNanos +
                " evaluate " + evaluateNanos;
        if (averageBucketSize > 0) {
            s += " averageBucketSize " + averageBucketSize;
        }
        if (split != 0) {
            s += " split " +
                    (split > 0 ? split : (-split + ":" + (size + split)));
        }
        return s;
    }

    @Override
    public int hashCode() {
        return leafSize ^ averageBucketSize ^ (int) Double.doubleToLongBits(bitsPerKey);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof FunctionInfo)) {
            return false;
        }
        FunctionInfo other = (FunctionInfo) o;
        if (bitsPerKey != other.bitsPerKey) {
            return false;
        }
        if (leafSize != other.leafSize) {
            return false;
        }
        if (averageBucketSize != other.averageBucketSize) {
            return false;
        }
        return true;
    }

}
