package org.minperf.hem;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;

public class Sort extends RecursiveAction {

    private static final long serialVersionUID = 1L;

    public static void main(String... args) {
        testSortUnsigned();
        int count = 128 * 1024 * 1024; // * 1024 * 1024;
        for(int test = 0; test < 4; test++) {
            System.out.println("test " + test);
            Random r = new Random(test);
            long[] data = new long[count];
            long sum = 0;
            for(int i = 0; i < data.length; i++) {
                long x = r.nextLong();
                sum += x;
                data[i] = x;
            }
            long time = System.nanoTime();

            parallelSort(data);
            // 16M: sorted in 0.497069951 secs
            // 128M: sorted in 2.943904999 secs
            // 256M: sorted in 5.710293362 secs

            // sortUnsignedSimple(data);
            // 16M: sorted in 0.46763204 secs
            // 128M: sorted in 4.179389644 secs
            // 256M: sorted in 8.616092879 secs

            // Arrays.parallelSort(data);
            // 16M: sorted in 0.399732495 secs
            // 128M: sorted in 3.498315688 secs
            // 256M: sorted in 7.834619304 secs

            // Arrays.sort(data);
            // 16M: sorted in 1.50651212 secs
            // 128M: sorted in 14.864402323 secs

            time = System.nanoTime() - time;
            System.out.println("sorted in " + (time / 1_000_000_000.) + " secs");
            long sum2 = 0;
            for(long x : data) {
                sum2 += x;
            }
            if (sum != sum2) {
                throw new AssertionError("sum changed");
            }
            for (int i = 1; i < data.length; i++) {
                if (Long.compareUnsigned(data[i - 1], data[i]) > 0) {
                    throw new AssertionError("index " + i);
                }
            }
            System.out.println("compared");
        }
    }

    private static void testSortUnsigned() {
        Random r = new Random(1);
        for (int test = 0; test < 1000; test++) {
            int len = r.nextInt(10);
            long[] data = new long[len];
            for (int i = 0; i < len; i++) {
                data[i] = r.nextInt(5) - 2;
            }
            parallelSort(data);
            // sortUnsignedSimple(data);
            for (int i = 1; i < data.length; i++) {
                if (Long.compareUnsigned(data[i - 1], data[i]) > 0) {
                    throw new AssertionError("index " + i);
                }
            }
        }
    }

    private static int S = 8;
    private static final long MASK = (1 << S) - 1;
    private static final int BUCKETS = 1 << S;

    private final long[] data;
    private final int start;
    private final int end;
    private final int shift;
    private final int level;

    Sort(long[] data, int start, int end, int shift, int level) {
        this.data = data;
        this.start = start;
        this.end = end;
        this.shift = shift;
        this.level = level;
    }

    public static void parallelSort(long[] data) {
        if (data.length < BUCKETS) {
            sortUnsignedSimple(data);
            return;
        }
        ForkJoinPool.commonPool().invoke(new Sort(data, 0, data.length, 64 - S, 0));
    }

    @Override
    protected void compute() {
        if (level > 0 && end - start < BUCKETS) {
            Arrays.sort(data, start, end);
            return;
        }
        int[] pos = new int[BUCKETS];
        for (int i = start; i < end; i++) {
            long x = data[i];
            int b = (int) ((x >>> shift) & MASK);
            pos[b]++;
        }
        int[] stop = new int[BUCKETS];
        int sum = start;
        for (int i = 0; i < pos.length; i++) {
            int count = pos[i];
            pos[i] = sum;
            sum += count;
            stop[i] = sum;
        }
        int i = start;
        long x = data[i];
        outer:
        for(int bucket = 0;;) {
            int targetBucket = (int) ((x >>> shift) & MASK);
            int index = pos[targetBucket]++;
            long next = data[index];
            data[index] = x;
            x = next;
            if (index == i) {
                while (true) {
                    index = pos[bucket];
                    if (index < stop[bucket]) {
                        break;
                    }
                    bucket++;
                    if (bucket >= BUCKETS) {
                        break outer;
                    }
                }
                i = index;
                x = data[index];
            }
        }
        ArrayList<RecursiveAction> tasks = new ArrayList<>();
        int startBucket = start;
        for (int bucket = 0; bucket < BUCKETS; bucket++) {
            int stopBucket = pos[bucket];
            if (stopBucket - startBucket < BUCKETS) {
                Arrays.sort(data, startBucket, stopBucket);
            } else {
                tasks.add(new Sort(data, startBucket, stopBucket, shift - S, level + 1));
            }
            startBucket = stopBucket;
        }
        if (!tasks.isEmpty()) {
            invokeAll(tasks);
        }
    }

    static void sortUnsignedSimple(long[] data) {
        int left = 0, right = data.length - 1;
        while(true) {
            while (left < data.length && data[left] >= 0) {
                left++;
            }
            while (right > 0 && data[right] < 0) {
                right--;
            }
            if (left >= right) {
                break;
            }
            long temp = data[left];
            data[left++] = data[right];
            data[right--] = temp;
        }
        Arrays.parallelSort(data, 0, left);
        Arrays.parallelSort(data, left, data.length);
    }

    static void sort(long[] data) {
        sort(data, 0, data.length, 64 - S);
    }

    static void sort(long[] data, int start, int end, int shift) {
        if (end - start < BUCKETS) {
            Arrays.sort(data, start, end);
            return;
        }
        int[] pos = new int[BUCKETS];
        for(int i=start; i<end; i++) {
            long x = data[i];
            int b = (int) ((x >>> shift) & MASK);
            pos[b]++;
        }
        int[] stop = new int[BUCKETS];
        int sum = start;
        for (int i = 0; i < pos.length; i++) {
            int count = pos[i];
            pos[i] = sum;
            sum += count;
            stop[i] = sum;
        }
        int i = start;
        long x = data[i];
        outer:
        for(int bucket = 0;;) {
            int targetBucket = (int) ((x >>> shift) & MASK);
            int index = pos[targetBucket]++;
            long next = data[index];
            data[index] = x;
            x = next;
            if (index == i) {
                while (true) {
                    index = pos[bucket];
                    if (index < stop[bucket]) {
                        break;
                    }
                    bucket++;
                    if (bucket >= BUCKETS) {
                        break outer;
                    }
                }
                i = index;
                x = data[index];
            }
        }
        int startBucket = start;
        for (int bucket = 0; bucket < BUCKETS; bucket++) {
            int stopBucket = pos[bucket];
            sort(data, startBucket, stopBucket, shift - S);
            startBucket = stopBucket;
        }
    }

}
