package org.minperf.generator;

import java.util.ArrayList;

import org.minperf.BitBuffer;

/**
 * A single-threaded processor.
 *
 * @param <T> the type
 */
public class SingleThreadedProcessor<T> implements Processor<T> {

    BitBuffer out;
    private final Generator<T> generator;

    SingleThreadedProcessor(Generator<T> generator) {
        this.generator = generator;
    }

    @Override
    public void process(T[][] lists, long[][] hashLists, ArrayList<BitBuffer> outList) {
        for (int i = 0; i < lists.length; i++) {
            int size = lists[i].length;
            out = new BitBuffer(size * 4 + 100);
            generator.generate(lists[i], hashLists[i], 0, this);
            outList.add(out);
        }
    }

    @Override
    public void writeLeaf(int shift, long index) {
        out.writeGolombRice(shift, index);
    }

    @Override
    public void split(int shift, long index, long startIndex, T[][] data, long[][] hashes) {
        out.writeGolombRice(shift, index);
        for (int i = 0; i < data.length; i++) {
            generator.generate(data[i], hashes[i], startIndex, this);
        }
    }

    @Override
    public void dispose() {
        // nothing to do
    }

}