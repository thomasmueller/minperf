package org.minperf.hem;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Random;

import org.minperf.RandomizedTest;
import org.minperf.hash.Mix;

public class RandomGenerator {

    public static long[] createRandomUniqueListSlow(int len, int seed) {
        HashSet<Long> set = RandomizedTest.createSet(len, 1);
        long[] list = new long[len];
        int i = 0;
        for (long x : set) {
            list[i++] = x;
        }
        return list;
    }

    public static long[] createRandomUniqueList(int len, int seed) {
        Random r = new Random(seed);
        long[] list = new long[len];
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
            return list;
        }
        outer: for (int s = 0;; s++) {
            long[] l2 = createRandomUniqueList(duplicateIndexList.size(), s);
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
        return list;
    }

    public static long[] createRandomUniqueListFast(int len, int seed) {
        long[] list = new long[len];
        for (int i = 0; i < len; i++) {
            list[i] = Mix.hash64(seed + i);
        }
        return list;
    }

}
