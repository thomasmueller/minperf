package org.minperf.generator;

import java.util.ArrayList;

import org.minperf.BitBuffer;

/**
 * A processor to generate the hash table.
 *
 * @param <T> the type
 */
public interface Processor<T> {

    /**
     * Process multiple buckets.
     *
     * @param lists the data
     * @param hashLists the hashes of the previous step
     * @param outList the target list
     */
    void process(T[][] lists, long[][] hashLists, ArrayList<BitBuffer> outList);

    /**
     * Write a leaf.
     *
     * @param shift the Rice parameter k
     * @param index the index
     */
    void writeLeaf(int shift, long index);

    /**
     * Split a set.
     *
     * @param shift the Rice parameter k
     * @param index the index (relative to the last start index)
     * @param startIndex the start index
     * @param data the data
     * @param hashes the hashes of the previous step
     */
    void split(int shift, long index, long startIndex, T[][] data, long[][] hashes);

    void dispose();
}