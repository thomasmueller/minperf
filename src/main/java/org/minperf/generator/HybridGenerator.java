package org.minperf.generator;

import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;

import org.minperf.BitBuffer;
import org.minperf.Settings;
import org.minperf.bdz.BDZ;
import org.minperf.eliasFano.EliasFanoMonotoneList;
import org.minperf.universal.UniversalHash;

/**
 * Generator of a hybrid MPHF. It is guaranteed to use linear space, because
 * large buckets are encoded using an alternative algorithm.
 *
 * @param <T> the type
 */
public class HybridGenerator<T> extends Generator<T> {

    public static final int MAX_FILL = 8;
    public static final int MAX_BITS_PER_ENTRY = 8;

    static final ForkJoinPool POOL = new ForkJoinPool();

    public HybridGenerator(UniversalHash<T> hash, Settings settings) {
        super(hash, settings);
    }

    @Override
    public BitBuffer generate(Collection<T> collection) {
        int size = collection.size();
        BitBuffer all = new BitBuffer(size * 10 + 1000);
        all.writeEliasDelta(size + 1);
        int bucketCount = (size + (settings.getLoadFactor() - 1)) /
                settings.getLoadFactor();
        if (size > 1) {
            generateBuckets(collection, size, bucketCount, all);
        }
        return all;
    }

    private void generateParallel(long size, int bucketCount, final ArrayList<Bucket> buckets) {

        int averageBucketSize = (int) (size / bucketCount);
        final int maxBucketSize = averageBucketSize * MAX_FILL;
        final int maxBits = maxBucketSize * MAX_BITS_PER_ENTRY;

        ForkJoinPool pool = new ForkJoinPool();

        pool.invoke(new RecursiveAction() {
            private static final long serialVersionUID = 1L;

            @Override
            protected void compute() {
                RecursiveAction[] list = new RecursiveAction[buckets.size()];
                for (int i = 0; i < buckets.size(); i++) {
                    final Bucket b = buckets.get(i);
                    list[i] = new RecursiveAction() {

                        private static final long serialVersionUID = 1L;

                        @Override
                        protected void compute() {
                            b.generateBucket(hash, maxBucketSize, maxBits);
                        }

                    };
                }
                invokeAll(list);

            }
        });
    }

    private void generateBuckets(Collection<T> collection, long size, int bucketCount,
            BitBuffer d) {
        ArrayList<Bucket> buckets = new ArrayList<Bucket>(bucketCount);
        for (int i = 0; i < bucketCount; i++) {
            buckets.add(new Bucket());
        }
        for (T t : collection) {
            int b;
            if (bucketCount == 1) {
                b = 0;
            } else {
                long h = hash.universalHash(t, 0);
                b = Settings.scaleLong(h, bucketCount);
            }
            buckets.get(b).add(t);
        }

        generateParallel(size, bucketCount, buckets);

        int[] startList = new int[buckets.size() + 1];
        int[] offsetList = new int[buckets.size() + 1];
        int start = 0, offset = 0;
        boolean alternativeHashOption = false;
        for (int i = 0; i < buckets.size(); i++) {
            Bucket b = buckets.get(i);
            if (start - offset < 0) {
                throw new AssertionError();
            }
            startList[i] = start;
            offsetList[i] = offset;
            if (b.buff != null) {
                start += b.buff.position();
            }
            offset += b.entryCount;
            alternativeHashOption |= b.alternativeHashOption;
        }
        d.writeBit(alternativeHashOption ? 1 : 0);
        startList[buckets.size()] = start;
        offsetList[buckets.size()] = offset;
        shrinkList(startList, offsetList);
        int minOffsetDiff = shrinkList(offsetList);
        int minStartDiff = shrinkList(startList);
        d.writeEliasDelta(minOffsetDiff + 1);
        EliasFanoMonotoneList.generate(offsetList, d);
        d.writeEliasDelta(minStartDiff + 1);
        EliasFanoMonotoneList.generate(startList, d);
        if (minStartDiff < 0) {
            throw new AssertionError();
        }
        for (int i = 0; i < buckets.size(); i++) {
            Bucket b = buckets.get(i);
            if (b.buff != null) {
                d.write(b.buff);
            }
        }
    }

    void shrinkList(int[] targetList, int[] sourceList) {
        int sum = 0;
        for (int i = 1; i < sourceList.length; i++) {
            int d = sourceList[i] - sourceList[i - 1];
            sum += d;
            targetList[i] -= scaleSize(sum);
            if (targetList[i] < targetList[i - 1]) {
                throw new IllegalArgumentException("");
            }
        }
    }

    int shrinkList(int[] list) {
        int min = Integer.MAX_VALUE;
        for (int i = 0; i < list.length - 1; i++) {
            int d = list[i + 1] - list[i];
            min = Math.min(min, d);
        }
        for (int i = 1; i < list.length; i++) {
            list[i] -= i * min;
        }
        return min;
    }

    public static int scaleSize(int size) {
        // assume at least 1.375 bits per key
        return (size  * 11 + 7) >> 3;
        // return (size  * 10 + 7) >> 3;
        // return size;
    }

    /**
     * A bucket.
     */
    class Bucket {
        boolean alternativeHashOption;
        ArrayList<T> list = new ArrayList<T>();
        BitBuffer buff;
        int entryCount;

        @Override
       public String toString() {
            return "" + entryCount;
        }

        void add(T obj) {
            list.add(obj);
        }

        void generateBucket(UniversalHash<T> hash, int maxBucketSize, int maxBits) {
            int size = list.size();
            entryCount = size;
            int minSize = scaleSize(size);
            if (size <= 1) {
                // zero or one entry
                buff = new BitBuffer(minSize);
                while (buff.position() < minSize) {
                    buff.writeBit(1);
                }
                return;
            }
            if (size > maxBucketSize) {
                generateAlternative(hash, maxBits + 1);
                return;
            }
            @SuppressWarnings("unchecked")
            T[] data = list.toArray((T[]) new Object[0]);
            long[] hashes = new long[size];
            for (int i = 0; i < size; i++) {
                hashes[i] = hash.universalHash(data[i], 0);
            }
            Processor<T> p = new Processor<T>(
                    HybridGenerator.this, data, hashes, 0);
            generate(data, hashes, 0, p);
            buff = p.out;
            if (buff.position() < minSize) {
                buff = new BitBuffer(minSize);
                buff.write(p.out);
                while (buff.position() < minSize) {
                    buff.writeBit(1);
                }
            }
            if (buff.position() > maxBits) {
                generateAlternative(hash, maxBits + 1);
            }
        }

        private void generateAlternative(UniversalHash<T> hash, int minBits) {
            buff = BDZ.generate(hash, list);
            while (buff.position() < minBits) {
                // fill with empty space until it is larger, to ensure this
                // is detected when reading (an alternative would be to use
                // one bit per bucket as a flag, but that would mean one
                // additional bit per bucket)
                buff.writeBit(0);
            }
            alternativeHashOption = true;
        }

    }

}
