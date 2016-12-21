package org.minperf.generator;

import java.util.ArrayList;
import java.util.concurrent.RecursiveAction;

import org.minperf.BitBuffer;

/**
 * A multi-threaded processor.
 *
 * @param <T> the type
 */
public class Processor<T> extends RecursiveAction {

    private static final long serialVersionUID = 1L;
    public BitBuffer out;
    final Generator<T> generator;
    private T[] data;
    private long[] hashes;
    private long startIndex;

    public Processor(Generator<T> generator, T[] data, long[] hashes, long startIndex) {
        this.generator = generator;
        this.data = data;
        this.hashes = hashes;
        this.startIndex = startIndex;
    }

    public Processor(Generator<T> recSplitGenerator) {
        generator = recSplitGenerator;
    }

    /**
     * Process multiple buckets.
     *
     * @param lists the data
     * @param hashLists the hashes of the previous step
     * @param outList the target list
     */
    public void process(final T[][] lists, final long[][] hashLists, final ArrayList<BitBuffer> outList) {
        generator.pool.invoke(new RecursiveAction() {

            private static final long serialVersionUID = 1L;

            @Override
            protected void compute() {
                final int bucketCount = lists.length;
                @SuppressWarnings("unchecked")
                Processor<T>[] list = new Processor[bucketCount];
                for (int i = 0; i < bucketCount; i++) {
                    list[i] = new Processor<T>(generator, lists[i], hashLists[i], 0);
                }
                generator.pool.invokeAll(list);
                for (int i = 0; i < bucketCount; i++) {
                    Processor<T> p = list[i];
                    outList.add(p.out);
                }
            }
        });
    }

    /**
     * Write a leaf.
     *
     * @param shift the Rice parameter k
     * @param index the index
     */
    public void writeLeaf(int shift, long index) {
        int bits = BitBuffer.getGolombRiceSize(shift, index);
        out = new BitBuffer(bits);
        out.writeGolombRice(shift, index);
    }

    /**
     * Split a set.
     *
     * @param shift the Rice parameter k
     * @param index the index (relative to the last start index)
     * @param startIndex the start index
     * @param data the data
     * @param hashes the hashes of the previous step
     */
    public void split(int shift, long index, long startIndex, T[][] data, long[][] hashes) {
        int split = data.length;
        @SuppressWarnings("unchecked")
        Processor<T>[] list = new Processor[split];
        for (int i = 0; i < split; i++) {
            list[i] = new Processor<T>(generator, data[i], hashes[i],
                    startIndex);
        }
        generator.pool.invokeAll(list);
        int bits = BitBuffer.getGolombRiceSize(shift, index);
        for (Processor<T> p : list) {
            if (p.out != null) {
                bits += p.out.position();
            }
        }
        out = new BitBuffer(bits);
        out.writeGolombRice(shift, index);
        for (Processor<T> p : list) {
            if (p.out != null) {
                out.write(p.out);
            }
            p.clean();
        }
    }

    private void clean() {
        this.out = null;
        this.data = null;
        this.hashes = null;
    }

    @Override
    public void compute() {
        generator.generate(data, hashes, startIndex, this);
    }

    public void dispose() {
        generator.pool.shutdown();
    }

}