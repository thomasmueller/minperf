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
    double generateMicros;
    double evaluateMicros;
    
    @Override
    public String toString() {
        return "size: " + size +
                " leafSize: " + leafSize +
                " loadFactor: " + loadFactor +
                " bitsPerKey: " + bitsPerKey +
                " generateSeconds: " + (generateMicros * size / 1000000) + 
                " generateMicrosPerKey: " + generateMicros + 
                " evaluateMicrosPerKey: " + evaluateMicros;
    }

}
