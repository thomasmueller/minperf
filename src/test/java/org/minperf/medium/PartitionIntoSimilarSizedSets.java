package org.minperf.medium;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

import org.minperf.utils.RandomSetGenerator;

public class PartitionIntoSimilarSizedSets {

    public static void main(String... args) {
        for (long size = 100; size <= 1000000000L; size *= 2) {
            test(size);
        }
    }

    private static void test(long size) {
        size *= 11;
        Iterable<Long> set = randomSet(size, size);
        long time = System.nanoTime();
        partition(set, size, 11, 22);
        time = System.nanoTime() - time;
        time /= size;
        System.out.println("size " + size + " " + time + " ns/key");
    }

    private static void partition(Iterable<Long> set, long size, int average, int max) {
        int bucketCount = (int) ((size + average - 1) / average);
        if (bucketCount * average != size) {
            throw new RuntimeException("TODO only exact average bucket sizes supported right now");
        }
        ArrayList<Bucket<Long>> buckets = new ArrayList<>();
        for(int i=0; i<bucketCount; i++) {
            buckets.add(new Bucket<Long>());
        }
        for (long x : set) {
            int bucketId = reduce((int) x, bucketCount);
            buckets.get(bucketId).initial.add(x);
        }
        ArrayList<Bucket<Long>> largeBuckets = new ArrayList<>();
        for (int i = 0; i < bucketCount; i++) {
            Bucket<Long> b = buckets.get(i);
            if (b.initial.size() > average) {
                largeBuckets.add(b);
            }
        }
        Collections.sort(largeBuckets, new Comparator<Bucket<Long>>() {
            @Override
            public int compare(Bucket<Long> o1, Bucket<Long> o2) {
                return -Integer.compare(o1.initial.size(), o2.initial.size());
            }
        });
        for(Bucket<Long> b : largeBuckets) {
            if (b.initial.size() <= average) {
                break;
            }
            distribute(buckets, average, b);
        }
//
//        int[] indexList = new int[bucketCount];
//        int maxIndex = 0;
//        for (int i = 0; i < largeBuckets.size(); i++) {
//            ArrayList<Long> list = largeBuckets.get(i);
//            int[] sizes;
//            for (int index = 1;; index++) {
//                boolean fail = false;
//                sizes = new int[bucketCount];
//                for (long x : list) {
//                    int y = supplementalHash(x, index);
//                    int b;
////                    if ((y & 1) == 0) {
////                        b = reduce((int) x, bucketCount);
////                    } else {
//                        b = reduce(y, bucketCount);
////                    }
//                    if (available[b] - sizes[b] >= 0) {
//                        sizes[b]++;
//                    } else {
//                        fail = true;
//                        break;
//                    }
//                }
//                if (!fail) {
//                    indexList[i] = index;
//                    maxIndex = Math.max(maxIndex, index);
//                    break;
//                }
//            }
//            for (int j = 0; j < bucketCount; j++) {
//                available[j] -= sizes[j];
//            }
//        }
//        System.out.println("max index: " + maxIndex);
    }

    private static void distribute(ArrayList<Bucket<Long>> buckets, int targetSize, Bucket<Long> b) {
        b.moveProbability = 2 * (b.initial.size() + b.foreign.size() - targetSize);
        while (true) {
            ArrayList<Long> move = new ArrayList<>();
            while (true) {
                for (long x : b.initial) {
                    int h = supplementalHash(x, b.moveIndex);
                    int y = reduce(h, 2 * targetSize);
                    if (y >= b.moveProbability) {
                        continue;
                    }
                    move.add(x);
                }
                if (b.initial.size() - move.size() + b.foreign.size() == targetSize) {
                    break;
                }
                b.moveIndex++;
            }
            Bucket<Long> bestNext = b;
            for (long x : move) {
                int h = supplementalHash(x, b.moveIndex);
                int id = reduce(h, buckets.size());
                Bucket<Long> b2 = buckets.get(id);
                if (b2.moveIndex < b.moveIndex) {
                    bestNext = b2;
                }
            }
            if (bestNext != b) {
                for (long x : move) {
                    int h = supplementalHash(x, b.moveIndex);
                    int id = reduce(h, buckets.size());
                    Bucket<Long> b2 = buckets.get(id);
                    b2.foreign.add(x);
                    if (b2.moveIndex < b.moveIndex) {
                        bestNext = b2;
                    }
                }
            }
        }
    }

    public static int supplementalHash(long hash, long index) {
        int x = (int) (Long.rotateLeft(hash, (int) index) ^ index);
        x = ((x >>> 16) ^ x) * 0x45d9f3b;
        x = ((x >>> 16) ^ x) * 0x45d9f3b;
        x = (x >>> 16) ^ x;
        return x;
    }

    static Iterable<Long> randomSet(long size, long seed) {
//        System.out.println("  random set " + size);
//        Random r = new Random(seed);
//        long time = System.nanoTime();
//        HashSet<Long> set = new HashSet<Long>(size);
//        while (set.size() < size) {
//            set.add(r.nextLong());
//        }
        Iterable<Long> set = RandomSetGenerator.randomSequence(size);
//        long sum = 0;
//        for(long x : set) {
//            sum += x;
//        }
//        time = System.nanoTime() - time;
//        System.out.println(size + " " + time / size + " ns/key " + sum);
        return set;
    }

    public static int reduce(int hash, int n) {
        // http://lemire.me/blog/2016/06/27/a-fast-alternative-to-the-modulo-reduction/
        return (int) (((hash & 0xffffffffL) * n) >>> 32);
    }

    static class Bucket<T> {
        // the list of entries that are initially mapped to this bucket
        ArrayList<T> initial = new ArrayList<>();
        // the list mapped after re-distribution
        ArrayList<T> foreign = new ArrayList<>();
        ArrayList<T> moved = new ArrayList<>();
        // the probability to move entries initially mapped to a different bucket
        int moveProbability;
        // the number of times entries were tried to move
        int moveIndex;
    }

}
