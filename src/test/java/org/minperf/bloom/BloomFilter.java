package org.minperf.bloom;

import java.util.HashSet;
import java.util.Random;

import org.minperf.hem.RandomGenerator;

public class BloomFilter {

    public static void main(String... args) {
        for(int bitsPerKey = 4; bitsPerKey < 20; bitsPerKey++) {
            test(bitsPerKey);
        }
    }

    public static void test(int bitsPerKey) {
        int n = 1024 * 1024;
        int m = n * bitsPerKey;
        int k = getBestK(m, n);
        int testCount = 1;
        int len = 1 * 1024 * 1024;
        long[] list = new long[len * 2];
        RandomGenerator.createRandomUniqueListFast(list, len);
        // System.out.println("BloomFilter " + len + " bits/key " + bitsPerKey + " k " + k);
        BloomFilter f = new BloomFilter(len, bitsPerKey, k);
        long time = System.nanoTime();
        for (int test = 0; test < testCount; test++) {
            for (int i = 0; i < len; i++) {
                f.add(list[i]);
            }
        }
        time = System.nanoTime() - time;
        // System.out.println("generate: " + time / len / testCount + " ns/key");
        time = System.nanoTime();
        int falsePositives = 0, falseNegatives = 0;
        for (int test = 0; test < testCount; test++) {
            for (int i = len; i < len * 2; i++) {
                if (f.mayContain(list[i])) {
                    falsePositives++;
                }
            }
            for (int i = 0; i < len; i++) {
                if (!f.mayContain(list[i])) {
                    falseNegatives++;
                }
            }
        }
        time = System.nanoTime() - time;
        if (falseNegatives > 0) {
            throw new AssertionError("false negatives: " + falseNegatives);
        }
        // System.out.println("get: " + time / len / testCount + " ns/key");
        System.out.println("BloomFilter falsePositives: " + (100. / testCount / len * falsePositives) +
                "% " + (double) f.getBitCount() / len + " bits/key " + k + " k");
    }

    static void testBloomFilter() {
        int len = 1000;
        BloomFilter f = new BloomFilter(len);
        Random r = new Random(1);
        HashSet<Integer> set = new HashSet<Integer>();
        while (set.size() < len) {
            set.add(r.nextInt());
        }
        for (int x : set) {
            f.add(x);
        }
        for (int x : set) {
            if (!f.mayContain(x)) {
                throw new Error();
            }
        }
        int wrong = 0;
        int test = 0;
        for (int i = 0; i < len * 100;) {
            int x = r.nextInt();
            if (set.contains(x)) {
                continue;
            }
            test++;
            if (f.mayContain(x)) {
                wrong++;
            }
            i++;
        }
        System.out.println("wrong: " + wrong + " test: " + test);
        double percentWrong = 100.0 * wrong / (len * 100);
        System.out.println(percentWrong + "%");

    }

    static int getBestK(int m, int n) {
        return Math.max(1, (int) Math.round((double) m / n * Math.log(2)));
    }

    private final int k;
    private final int bits;
    private final long[] data;

    BloomFilter(int entryCount) {
        this(entryCount, 8, 6);
    }

    private double getBitCount() {
        return data.length * 64;
    }

    BloomFilter(int entryCount, int bitsPerKey, int k) {
        entryCount = Math.max(1, entryCount);
        this.k = k;
        this.bits = entryCount * bitsPerKey;
        data = new long[(int) ((bits + 63) / 64)];
    }

    void add(long hashCode) {
        long h = hash64(hashCode);
        int a = (int) (h >>> 32);
        int b = (int) h;
        for (int i = 0; i < k; i++) {
            int index = reduce(a, bits);
            data[getArrayIndex(index)] |= getBit(index);
            a += b;
        }
    }

    boolean mayContain(long hashCode) {
        long h = hash64(hashCode);
        int a = (int) (h >>> 32);
        int b = (int) h;
        for (int i = 0; i < k; i++) {
            int index = reduce(a, bits);
            if ((data[getArrayIndex(index)] & getBit(index)) == 0) {
                return false;
            }
            a += b;
        }
        return true;
    }

    private static int getArrayIndex(int index) {
        return index / 64;
    }

    private static long getBit(int index) {
        return 1L << (index & 63);
    }

    private static int reduce(int hash, int n) {
        // http://lemire.me/blog/2016/06/27/a-fast-alternative-to-the-modulo-reduction/
        return (int) (((hash & 0xffffffffL) * n) >>> 32);
    }

    private static long hash64(long x) {
        x = (x ^ (x >>> 30)) * 0xbf58476d1ce4e5b9L;
        x = (x ^ (x >>> 27)) * 0x94d049bb133111ebL;
        x = x ^ (x >>> 31);
        return x;
    }

}
