package org.minperf.hem;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Random;

import org.minperf.RandomizedTest;
import org.minperf.hash.Mix;

public class RandomGenerator {

    public static void main(String... args) {
        for(int test =0; test < 10; test++) {
            test();
        }
    }

    private static void test() {
        int count = 1000000;
        long[] data = new long[count];
        long time;
        time = System.nanoTime();
        createRandomUniqueListSlow(data, 0);
        time = System.nanoTime() - time;
        System.out.println("slow: " + (time / count) + " ns/key " + sum(data));
        time = System.nanoTime();
        createRandomUniqueList(data, 0);
        time = System.nanoTime() - time;
        System.out.println("normal: " + (time / count) + " ns/key " + sum(data));
        time = System.nanoTime();
        createRandomUniqueListFast(data, 0);
        time = System.nanoTime() - time;
        System.out.println("fast: " + (time / count) + " ns/key " + sum(data));
    }

    public static long sum(long[] data) {
        long x = 0;
        for(long y : data) {
            x += y;
        }
        return x;
    }

    public static void createRandomUniqueListSlow(long[] list, int seed) {
        int len = list.length;
        HashSet<Long> set = RandomizedTest.createSet(len, 1);
        int i = 0;
        for (long x : set) {
            list[i++] = x;
        }
    }

    public static void createRandomUniqueList(long[] list, int seed) {
        int len = list.length;
        Random r = new Random(seed);
        for (int i = 0; i < len; i++) {
            list[i] = r.nextLong();
        }
        ArrayList<Integer> duplicateIndexList = new ArrayList<Integer>();
        duplicateIndexList.clear();
        Sort.parallelSort(list);
        for (int i = 1; i < len; i++) {
            if (list[i - 1] == list[i]) {
                duplicateIndexList.add(i);
            }
        }
        if (duplicateIndexList.isEmpty()) {
            return;
        }
        outer: for (int s = 0;; s++) {
            long[] l2 = new long[duplicateIndexList.size()];
            createRandomUniqueList(l2, s);
            for (long x : l2) {
                if (Arrays.binarySearch(list, x) >= 0) {
                    continue outer;
                }
            }
            for (int i = 0; i < duplicateIndexList.size(); i++) {
                list[duplicateIndexList.get(i)] = l2[i];
            }
            break;
        }
        Sort.parallelSort(list);
        for (int i = 1; i < len; i++) {
            if (list[i - 1] == list[i]) {
                throw new AssertionError();
            }
        }
    }

    public static void createRandomUniqueListFast(long[] list, int seed) {
        int len = list.length;
        for (int i = 0; i < len; i++) {
            list[i] = Mix.hash64(seed + i);
        }
    }

}
