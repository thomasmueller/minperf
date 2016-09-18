package org.minperf.hybrid;

import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.minperf.BitBuffer;
import org.minperf.Settings;
import org.minperf.bdz.BDZ;
import org.minperf.eliasFano.EliasFanoMonotoneList;
import org.minperf.generator.Generator;
import org.minperf.universal.UniversalHash;

/**
 * Generator of a hybrid MPHF. It is guaranteed to use linear space, because
 * large buckets are encoded using an alternative algorithm.
 *
 * @param <T> the type
 */
public class HybridGenerator<T> extends Generator<T> {

    static final int PARALLELISM = Runtime.getRuntime().availableProcessors();
    private static final double MAX_FILL = 1.1;
    private static final double MAX_BITS_PER_ENTRY = 1.7;

    /**
     * Entries of buckets that are either too large, or where the description is
     * too large.
     */
    ConcurrentLinkedQueue<T> additional = new ConcurrentLinkedQueue<T>();

    public HybridGenerator(UniversalHash<T> hash, Settings settings, boolean multiThreaded) {
        super(hash, settings, multiThreaded);
    }

    @Override
    public BitBuffer generate(Collection<T> collection) {
        int size = collection.size();
        BitBuffer all = new BitBuffer(size * 100 + 1000);
        all.writeEliasDelta(size + 1);
        int bucketCount = (size + (settings.getLoadFactor() - 1)) /
                settings.getLoadFactor();
        if (size > 1) {
            generateBuckets(collection, size, bucketCount, all);
        }
        return all;
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
        int averageBucketSize = (int) (size / bucketCount);
        for (int i = 0; i < buckets.size(); i++) {
            Bucket b = buckets.get(i);
            boolean success = b.generateAll(hash, averageBucketSize);
            d.writeBit(success ? 1 : 0);
        }
        BitBuffer bdz = BDZ.generate(hash, additional);
        d.write(bdz);

        int[] startList = new int[buckets.size()];
        int[] offsetList = new int[buckets.size()];
        int start = 0, offset = 0;
        for (int i = 0; i < buckets.size(); i++) {
            Bucket b = buckets.get(i);
            startList[i] = start;
            offsetList[i] = offset;
            if (b.buff != null) {
                start += b.buff.position();
            }
            offset += b.entryCount;
        }
        EliasFanoMonotoneList.generate(startList, d);
        EliasFanoMonotoneList.generate(offsetList, d);
        for (int i = 0; i < buckets.size(); i++) {
            Bucket b = buckets.get(i);
            if (b.buff != null) {
                d.write(b.buff);
            }
        }
    }

    /** A bucket.
     */
    class Bucket {
        ArrayList<T> list = new ArrayList<T>();
        BitBuffer buff;
        int entryCount;
        boolean processed;

        @Override
       public String toString() {
            return "" + entryCount;
        }

        void add(T obj) {
            list.add(obj);
        }

        boolean generateAll(UniversalHash<T> hash, int averageBucketSize) {
            int size = list.size();
            if (size <= 1) {
                // zero or one entry
                entryCount = size;
                return true;
            }
            int maxBucketSize = (int) (averageBucketSize * MAX_FILL);
            if (size > maxBucketSize) {
                additional.addAll(list);
                list.clear();
                return false;
            }
            @SuppressWarnings("unchecked")
            T[] data = list.toArray((T[]) new Object[0]);
            long[] hashes = new long[size];
            for (int i = 0; i < size; i++) {
                hashes[i] = hash.universalHash(data[i], 0);
            }
            ArrayList<BitBuffer> outList = new ArrayList<BitBuffer>();
            @SuppressWarnings("unchecked")
            T[][] lists = (T[][]) new Object[1][];
            lists[0] = data;
            long[][] hashLists = new long[1][];
            hashLists[0] = hashes;
            generate(lists, hashLists, outList);
            buff = outList.get(0);
            if (buff == null) {
                buff = new BitBuffer(0);
            }
            int maxBits = (int) (size * MAX_BITS_PER_ENTRY);
            if (buff.position() > maxBits) {
                buff = null;
                additional.addAll(list);
                list.clear();
                return false;
            }
            entryCount = size;
            return true;
        }

    }

}
