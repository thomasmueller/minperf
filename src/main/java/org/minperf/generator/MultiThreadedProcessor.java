package org.minperf.generator;

import java.util.ArrayList;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;

import org.minperf.BitBuffer;

/**
 * A multi-threaded processor.
 *
 * @param <T> the type
 */
public class MultiThreadedProcessor<T> extends RecursiveAction implements Processor<T> {

    private static ForkJoinPool pool;

    private static final long serialVersionUID = 1L;
    final Generator<T> generator;
    BitBuffer out;
    private T[] data;
    private long[] hashes;
    private long startIndex;

    MultiThreadedProcessor(Generator<T> generator, T[] data, long[] hashes, long startIndex) {
        this.generator = generator;
        this.data = data;
        this.hashes = hashes;
        this.startIndex = startIndex;
    }

    public MultiThreadedProcessor(Generator<T> recSplitGenerator) {
        pool = new ForkJoinPool(Generator.PARALLELISM);
        generator = recSplitGenerator;
    }

    @Override
    public void process(final T[][] lists, final long[][] hashLists, final ArrayList<BitBuffer> outList) {
        pool.invoke(new RecursiveAction() {

            private static final long serialVersionUID = 1L;

            @Override
            protected void compute() {
                final int bucketCount = lists.length;
                @SuppressWarnings("unchecked")
                MultiThreadedProcessor<T>[] list = new MultiThreadedProcessor[bucketCount];
                for (int i = 0; i < bucketCount; i++) {
                    list[i] = new MultiThreadedProcessor<T>(generator, lists[i], hashLists[i], 0);
                }
                invokeAll(list);
                for (int i = 0; i < bucketCount; i++) {
                    MultiThreadedProcessor<T> p = list[i];
                    outList.add(p.out);
                }
            }
        });
    }

    @Override
    public void writeLeaf(int shift, long index) {
        int bits = BitBuffer.getGolombRiceSize(shift, index);
        out = new BitBuffer(bits);
        out.writeGolombRice(shift, index);
    }

    @Override
    public void split(int shift, long index, long startIndex, T[][] data, long[][] hashes) {
        int split = data.length;
        @SuppressWarnings("unchecked")
        MultiThreadedProcessor<T>[] list = new MultiThreadedProcessor[split];
        for (int i = 0; i < split; i++) {
            list[i] = new MultiThreadedProcessor<T>(generator, data[i], hashes[i],
                    startIndex);
        }
        invokeAll(list);
        int bits = BitBuffer.getGolombRiceSize(shift, index);
        for (MultiThreadedProcessor<T> p : list) {
            if (p.out != null) {
                bits += p.out.position();
            }
        }
        out = new BitBuffer(bits);
        out.writeGolombRice(shift, index);
        for (MultiThreadedProcessor<T> p : list) {
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
    protected void compute() {
        generator.generate(data, hashes, startIndex, this);
    }

    @Override
    public void dispose() {
        pool.shutdown();
    }

}