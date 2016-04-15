package org.minperf.generator;

import java.util.ArrayList;

import org.minperf.BitBuffer;

/**
 * A processor to generate the hash table.
 *
 * @param <T> the type
 */
interface Processor<T> {
    
    void process(T[][] lists, int[][] hashLists, ArrayList<BitBuffer> outList);

    void writeLeaf(int shift, long index);

    void split(int shift, long index, long startIndex, T[][] data2, int[][] hashes2);

    void dispose();
}