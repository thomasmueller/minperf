package org.minperf.bloom;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashSet;
import java.util.LinkedList;

import org.minperf.BitBuffer;
import org.minperf.hash.Mix;
import org.minperf.hem.RandomGenerator;

/**
 * An improved Cuckoo filter. Unlike the regular Cuckoo filter, it needs 1.23
 * log(1/fpp). It basically combines a Cuckoo filter with BDZ (a minimal perfect
 * hash function algorithm, see http://cmph.sourceforge.net/papers/wads07.pdf
 * "Simple and Space-Efficient Minimal Perfect Hash Functions"). See also
 * https://brilliant.org/wiki/cuckoo-filter/.
 */
public class CuckooPlusFilter {

    public static void main(String... args) {
        for(int bitsPerKey = 4; bitsPerKey < 20; bitsPerKey++) {
            test(bitsPerKey);
        }
    }

    public static void test(int bitsPerKey) {
        int testCount = 1;
        int len = 1024 * 1024;
        long[] list = new long[len * 2];
        RandomGenerator.createRandomUniqueListFast(list, len);
        long time = System.nanoTime();
        CuckooPlusFilter f = new CuckooPlusFilter(list, len, bitsPerKey);
        long addTime = (System.nanoTime() - time) / len;
        time = System.nanoTime();
        int falsePositives = 0;
        for (int test = 0; test < testCount; test++) {
            for (int i = 0; i < len; i++) {
                if (!f.mayContain(list[i])) {
                    f.mayContain(list[i]);
                    throw new AssertionError();
                }
            }
            for (int i = len; i < len * 2; i++) {
                if (f.mayContain(list[i])) {
                    falsePositives++;
                }
            }
        }
        long getTime = (System.nanoTime() - time) / len / testCount;
        double falsePositiveRate = (100. / testCount / len * falsePositives);
        System.out.println("CuckooPlus false positives: " + falsePositiveRate +
                "% " + (double) f.getBitCount() / len + " bits/key " +
                "add: " + addTime + " get: " + getTime + " ns/key");

    }

//    private static final int HASHES = 3;
//    private static final int FACTOR_TIMES_100 = 123;
//    CuckooPlus false positives: 1.5738487243652344% 7.380014419555664 bits/key add: 2813 get: 44 ns/key
//    CuckooPlus false positives: 0.7904052734375% 8.610016822814941 bits/key add: 3038 get: 50 ns/key

    private static final int HASHES = 3;
    private static final int FACTOR_TIMES_100 = 123;

    private final int size;
    private final int arrayLength;
    private final int bitsPerKey;
    private int hashIndex;
    private BitBuffer fingerprints;

    private double getBitCount() {
        return fingerprints.position();
    }

    private static int getArrayLength(int size) {
        return HASHES + FACTOR_TIMES_100 * size / 100;
    }

    CuckooPlusFilter(long[] list, int entryCount, int bitsPerKey) {
        this.size = entryCount;
        this.bitsPerKey = bitsPerKey;
        arrayLength = getArrayLength(size);
        int m = arrayLength;
        ArrayList<Long> order = new ArrayList<Long>();
        HashSet<Long> done = new HashSet<Long>();
        long[] at;
        int hashIndex = 0;
        while (true) {
            order.clear();
            done.clear();
            at = new long[m];
            ArrayList<HashSet<Long>> list2 = new ArrayList<HashSet<Long>>();
            for (int i = 0; i < m; i++) {
                list2.add(new HashSet<Long>());
            }
            for (int i = 0; i < size; i++) {
                long x = list[i];
                for (int hi = 0; hi < HASHES; hi++) {
                    int h = getHash(x, hashIndex, hi, arrayLength);
                    HashSet<Long> l = list2.get(h);
                    l.add(x);
                }
            }
            LinkedList<Integer> alone = new LinkedList<Integer>();
            for (int i = 0; i < arrayLength; i++) {
                if (list2.get(i).size() == 1) {
                    alone.add(i);
                }
            }
            while (!alone.isEmpty()) {
                int i = alone.removeFirst();
                HashSet<Long> l = list2.get(i);
                if (l.isEmpty()) {
                    continue;
                }
                long x = l.iterator().next();
                if (done.contains(x)) {
                    continue;
                }
                order.add(x);
                done.add(x);
                boolean found = false;
                for (int hi = 0; hi < HASHES; hi++) {
                    int h = getHash(x, hashIndex, hi, arrayLength);
                    l = list2.get(h);
                    l.remove(x);
                    if (l.isEmpty()) {
                        if (!found) {
                            at[h] = x;
                            found = true;
                        }
                    } else if (l.size() == 1) {
                        alone.add(h);
                    }
                }
            }
            if (order.size() == size) {
                break;
            }
            hashIndex++;
System.out.println("new hash");
        }
        this.hashIndex = hashIndex;
        BitSet visited = new BitSet();
        long[] fp = new long[m];
        for (int i = order.size() - 1; i >= 0; i--) {
            long x = order.get(i);
            long sum = fingerprint(x);
            int change = 0;
            for (int hi = 0; hi < HASHES; hi++) {
                int h = getHash(x, hashIndex, hi, arrayLength);
                if (visited.get(h)) {
                    sum ^= fp[h];
                } else {
                    visited.set(h);
                    if (at[h] == x) {
                        change = h;
                    }
                }
            }
            fp[change] = sum;
        }
        fingerprints = new BitBuffer(bitsPerKey * m);
        for(long f : fp) {
            fingerprints.writeNumber(f, bitsPerKey);
        }
    }

    public boolean mayContain(long key) {
        long x = 0;
        long fp = fingerprint(key);
        for (int hi = 0; hi < HASHES; hi++) {
            int h = getHash(key, hashIndex, hi, arrayLength);
            x ^= fingerprints.readNumber(h * bitsPerKey, bitsPerKey);
        }
        return x == fp;
    }

    private static <T> int getHash(long x, int hashIndex, int index, int arrayLength) {
        long r = supplementalHash(x, hashIndex + index);
        r = reduce((int) r, arrayLength / HASHES);
        r = r + index * arrayLength / HASHES;
        return (int) r;
    }

    private long fingerprint(long key) {
        return hash64(key) & ((1L << bitsPerKey) - 1);
    }

    private static int supplementalHash(long x, int index) {
        return Mix.supplementalHashWeyl(x, index);
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
