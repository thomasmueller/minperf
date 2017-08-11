package org.minperf.medium;

import java.util.Random;

public class SimulateProbFallIntoLarge {
    public static void main(String... args) {
        int avgSize = 10;
        Random r = new Random(0);
        for (int maxSize = 1; maxSize < 20; maxSize++) {
            System.out.println("maxSize=" + maxSize);
            for (int size = 100000; size <= 10000000; size *= 10) {
                for (int test = 0; test < 10; test++) {
                    int bucketCount = size / avgSize;
                    int[] bucketSizes = new int[bucketCount];
                    for (int i = 0; i < size; i++) {
                        bucketSizes[r.nextInt(bucketCount)]++;
                    }
                    int large = 0;
                    for (int i = 0; i < bucketCount; i++) {
                        if (bucketSizes[i] > maxSize) {
                            large += bucketSizes[i];
                        }
                    }
                    System.out
                            .println("   size=" + size + " large=" + large + " " + 100. / size * large + "% in large");
                }
            }
        }
    }
}
