package org.minperf;

import java.util.Collection;

import org.minperf.generator.Generator;
import org.minperf.universal.UniversalHash;

/**
 * A builder to generate a MPHF description, or to get an evaluator of a description.
 *
 * @param <T> the type
 */
public class RecSplitBuilder<T> {

    private final UniversalHash<T> hash;
    private int loadFactor = 256;
    private int leafSize = 10;
    private boolean multiThreaded = true;

    private RecSplitBuilder(UniversalHash<T> hash) {
        this.hash = hash;
    }

    /**
     * Create a new instance of the builder, with the given universal hash implementation.
     *
     * @param <T> the type
     * @param hash the universal hash function
     * @return the builder
     */
    public static <T> RecSplitBuilder<T> newInstance(UniversalHash<T> hash) {
        return new RecSplitBuilder<T>(hash);
    }

    public RecSplitBuilder<T> loadFactor(int loadFactor) {
        if (loadFactor < 8 || loadFactor > 65536) {
            throw new IllegalArgumentException("loadFactor out of range: " + loadFactor);
        }
        this.loadFactor = loadFactor;
        return this;
    }

    public RecSplitBuilder<T> leafSize(int leafSize) {
        if (leafSize < 1 || leafSize > 25) {
            throw new IllegalArgumentException("leafSize out of range: " + leafSize);
        }
        this.leafSize = leafSize;
        return this;
    }

    public RecSplitBuilder<T> multiThreaded(boolean multiThreaded) {
        this.multiThreaded = multiThreaded;
        return this;
    }

    public BitBuffer generate(Collection<T> collection) {
        Settings s = new Settings(leafSize, loadFactor);
        Generator<T> g = new Generator<T>(hash, s, multiThreaded);
        BitBuffer result = g.generate(collection);
        // we could re-use the generator,
        // so that starting and stopping threads is not needed
        // when generate is called multiple times
        g.dispose();
        return result;
    }

    public RecSplitEvaluator<T> buildEvaluator(BitBuffer description) {
        Settings s = new Settings(leafSize, loadFactor);
        return new RecSplitEvaluator<T>(new BitBuffer(description), hash, s);
    }

}
