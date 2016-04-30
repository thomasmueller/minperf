package org.minperf.generator;

import java.util.ArrayList;

import org.minperf.BitBuffer;

/**
 * A processor to generate the hash table.
 *
 * @param <T> the type
 */
interface Processor<T> {

    void process(T[][] lists, long[][] hashLists, ArrayList<BitBuffer> outList);

    void writeLeaf(int shift, long index);

    void split(int shift, long index, long startIndex, T[][] data2, long[][] hashes2);

    void dispose();
}