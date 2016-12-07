package org.minperf;

import static org.junit.Assert.assertTrue;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Random;

import org.junit.Assert;
import org.minperf.universal.LongHash;
import org.minperf.universal.UniversalHash;

/**
 * Methods to test the MPHF with random data.
 */
public class RandomizedTest {

    private static final char[] HEX = "0123456789abcdef".toCharArray();
    private static final int[] HEX_DECODE = new int['f' + 1];

    static {
        for (int i = 0; i < HEX_DECODE.length; i++) {
            HEX_DECODE[i] = -1;
        }
        for (int i = 0; i <= 9; i++) {
            HEX_DECODE[i + '0'] = i;
        }
        for (int i = 0; i <= 5; i++) {
            HEX_DECODE[i + 'a'] = HEX_DECODE[i + 'A'] = i + 10;
        }
    }

    public static void printLargeSet() {
        for (int i = 10; i <= 1_000_000_000; i *= 10) {
            FunctionInfo info = RandomizedTest.test(8, 128, i, false);
            System.out.println(info);
        }
    }

    public static void printTimeVersusSpace() {
        System.out.println("A Time Versus Space");
        final double evaluateWeight = 20;
        int size = 100000;
        System.out.println("size: " + size);
        ArrayList<FunctionInfo> list = new ArrayList<FunctionInfo>();
        outer:
        for (int leafSize = 2; leafSize <= 12; leafSize++) {
            int minLoadFactor = 4;
            for (int loadFactor = minLoadFactor; loadFactor <= 1024;) {
                System.out.println("leafSize " + leafSize + " " + loadFactor);
                FunctionInfo info = test(leafSize, loadFactor, size, true);
                if (info.evaluateNanos >= 10000) {
                    if (loadFactor == minLoadFactor) {
                        // done
                        break outer;
                    }
                    // next leaf size
                    break;
                }
                if (info.bitsPerKey < 4.0) {
                    list.add(info);
                }
                if (loadFactor < 16) {
                    loadFactor += 2;
                } else if (loadFactor < 32) {
                    loadFactor += 4;
                } else {
                    loadFactor *= 2;
                }
            }
        }
        Collections.sort(list, new Comparator<FunctionInfo>() {

            @Override
            public int compare(FunctionInfo o1, FunctionInfo o2) {
                double time1 = o1.evaluateNanos * evaluateWeight + o1.generateNanos;
                double time2 = o2.evaluateNanos * evaluateWeight + o2.generateNanos;
                int comp = Double.compare(time1, time2);
                if (comp == 0) {
                    comp = Double.compare(o1.bitsPerKey, o2.bitsPerKey);
                }
                return comp;
            }

        });
        FunctionInfo last = null;
        int minLoadFactor = Integer.MAX_VALUE, maxLoadFactor = 0;
        int minLeafSize = Integer.MAX_VALUE, maxLeafSize = 0;
        for (FunctionInfo info : list) {
            if (last != null && info.bitsPerKey > last.bitsPerKey) {
                continue;
            }
            System.out.println("        (" + info.bitsPerKey + ", " + info.evaluateNanos + ")");
            minLoadFactor = Math.min(minLoadFactor, info.loadFactor);
            maxLoadFactor = Math.max(maxLoadFactor, info.loadFactor);
            minLeafSize = Math.min(minLeafSize, info.leafSize);
            maxLeafSize = Math.max(maxLeafSize, info.leafSize);
            last = info;
        }
        System.out.println("for loadFactor between " + minLoadFactor + " and " + maxLoadFactor);
        System.out.println("and leafSize between " + minLeafSize + " and " + maxLeafSize);
        last = null;
        System.out.println("bits/key leafSize loadFactor evalTime genTime tableBitsPerKey");
        for (FunctionInfo info : list) {
            if (last != null && info.bitsPerKey > last.bitsPerKey) {
                continue;
            }
            System.out.println(info.bitsPerKey + " " + info.leafSize + " " + info.loadFactor +
                    " " + info.evaluateNanos + " " + info.generateNanos);
            last = info;
        }
    }

    public static void printEvaluationTimeVersusSpaceMedium() {
        System.out.println("A Evaluation Time Versus Space");
        int size = 100000;
        System.out.println("size: " + size);
        ArrayList<FunctionInfo> list = new ArrayList<FunctionInfo>();
        for (int i = 2; i < 22; i++) {
            int leafSize = (int) Math.round(0.18 * i + 6.83);
            int loadFactor = (int) Math.round(Math.pow(2, 0.3 * i + 2.79));
            // FunctionInfo info =
            test(leafSize, loadFactor, size / 10, true);
            // System.out.println("leafSize " + leafSize + " " + loadFactor + " " +
            //        info.evaluateNanos + " " + info.generateNanos + " " + info.bitsPerKey);
        }
        for (int leafSize = 8; leafSize < 14; leafSize++) {
            System.out.println("leafSize " + leafSize);
            // int leafSize = (int) Math.round(0.18 * i + 6.83);
            for (int loadFactor : new int[] { 4, 6, 8, 10, 12, 14, 16, 20, 24,
                    28, 32, 40, 48, 56, 64 }) {
                // int loadFactor = (int) Math.round(Math.pow(2, 0.3 * i + 2.79));
                test(leafSize, loadFactor, size, true);
                FunctionInfo info = test(leafSize, loadFactor, size, true);
                if (info.bitsPerKey < 2.4 && info.evaluateNanos < 250) {
                    System.out.println("leafSize " + leafSize + " loadFactor " + loadFactor +
                            " " + info.evaluateNanos + " " + info.generateNanos +
                            " " + info.bitsPerKey);
                    list.add(info);
                    break;
                }
            }
        }
        System.out.println("A Evaluation Time Versus Space");
        for (FunctionInfo info : list) {
            System.out.println("        (" + info.bitsPerKey + ", " + info.evaluateNanos + ")");
        }
        System.out.println("B Generation Time Versus Space");
        for (FunctionInfo info : list) {
            System.out.println("        (" + info.bitsPerKey + ", " + info.generateNanos + ")");
        }
    }

    public static void printEvaluationAndGenerationTimeVersusSpace() {
        System.out.println("A Evaluation Time Versus Space");
        int size = 100000;
        System.out.println("size: " + size);
        ArrayList<FunctionInfo> list = new ArrayList<FunctionInfo>();
        for (int leafSize = 8; leafSize <= 14; leafSize++) {
            for (int loadFactor : new int[] { 8, 12, 16, 20, 24, 28, 32, 48,
                    64, 128, 256, 1024, 2048, 4096, 8 * 1024 }) {
                // int loadFactor = (int) Math.pow(Math.sqrt(2), leafSize);
                FunctionInfo best = null;
                FunctionInfo info = test(leafSize, loadFactor, size, true);
                if (best == null || info.evaluateNanos < best.evaluateNanos) {
                    best = info;
                }
                System.out.println(best);
                list.add(best);
            }
        }
        System.out.println("Evaluation time");
        for (FunctionInfo info : list) {
            System.out.println("        (" + info.bitsPerKey + ", " + info.evaluateNanos + ")");
        }
        System.out.println("Generation time");
        for (FunctionInfo info : list) {
            System.out.println("        (" + info.bitsPerKey + ", " + info.generateNanos + ")");
        }
    }

    public static void printGenerationTimeVersusSpace() {
        System.out.println("B Generation Time Versus Space");
        int size = 10000;
        System.out.println("size: " + size);
        ArrayList<FunctionInfo> list = new ArrayList<FunctionInfo>();
        outer:
        for (int leafSize = 2; leafSize <= 20; leafSize++) {
            int minLoadFactor = 16;
            for (int loadFactor = minLoadFactor; loadFactor < 8 * 1024; loadFactor *= 2) {
                System.out.println("leafSize " + leafSize + " " + loadFactor);
                FunctionInfo info = test(leafSize, loadFactor, size, true);
                if (info.generateNanos >= 1000000) {
                    if (loadFactor == minLoadFactor) {
                        // done
                        break outer;
                    }
                    // next leaf size
                    break;
                }
                if (info.bitsPerKey < 2.4) {
                    list.add(info);
                }
            }
        }
        Collections.sort(list, new Comparator<FunctionInfo>() {

            @Override
            public int compare(FunctionInfo o1, FunctionInfo o2) {
                int comp = Double.compare(o1.generateNanos, o2.generateNanos);
                if (comp == 0) {
                    comp = Double.compare(o1.bitsPerKey, o2.bitsPerKey);
                }
                return comp;
            }

        });
        FunctionInfo last = null;
        int minLoadFactor = Integer.MAX_VALUE, maxLoadFactor = 0;
        int minLeafSize = Integer.MAX_VALUE, maxLeafSize = 0;
        for (FunctionInfo info : list) {
            if (last != null && info.bitsPerKey > last.bitsPerKey) {
                continue;
            }
            System.out.println("        (" + info.bitsPerKey + ", " + info.generateNanos + ")");
            minLoadFactor = Math.min(minLoadFactor, info.loadFactor);
            maxLoadFactor = Math.max(maxLoadFactor, info.loadFactor);
            minLeafSize = Math.min(minLeafSize, info.leafSize);
            maxLeafSize = Math.max(maxLeafSize, info.leafSize);
            last = info;
        }
        System.out.println("for loadFactor between " + minLoadFactor + " and " + maxLoadFactor);
        System.out.println("and leafSize between " + minLeafSize + " and " + maxLeafSize);
        last = null;
        System.out.println("bits/key leafSize loadFactor evalTime genTime");
        for (FunctionInfo info : list) {
            if (last != null && info.bitsPerKey > last.bitsPerKey) {
                continue;
            }
            System.out.println(info.bitsPerKey + " " + info.leafSize + " " + info.loadFactor +
                    " " + info.evaluateNanos + " " + info.generateNanos);
            last = info;
        }
    }

    public static void runTests() {
        int[] pairs = {
                23, 828, 23, 1656, 23, 3312,
                23, 6624, 25, 1250, 25,
                3750, 25, 7500, 25, 15000 };
        for (int i = 0; i < pairs.length; i += 2) {
            int leafSize = pairs[i], size = pairs[i + 1];
            FunctionInfo info = test(leafSize, size, size, true);
            System.out.println(new Timestamp(System.currentTimeMillis()).toString());
            System.out.println(info);
        }
    }

    static void verifyParameters() {
        System.out.println("4.1 Parameters");
        // size 100000
        // CHD: generated in 1.52 seconds, 2.257 bits/key, eval 219 nanoseconds/key
        // GOV: generated in 0.32 seconds, 2.324 bits/key, eval 207 nanoseconds/key
        // size 1000000
        // CHD:
        // GOV:
        RandomizedTest.test(8, 1024, 8 * 1024, true);
        for (int i = 0; i < 5; i++) {
            if (verifyOneTest()) {
                return;
            }
            RandomizedTest.test(8, 1024, 8 * 1024, true);
        }
        Assert.fail();
    }

    static void verifyParametersBestSize() {
        // System.out.println(RandomizedTest.test(23, 828, 828, true));
        System.out.println(RandomizedTest.test(23, 1656, 1656, true));
        // System.out.println(RandomizedTest.test(23, 3312, 3312, true));
        // System.out.println(RandomizedTest.test(23, 6624, 6624, true));
        // System.out.println(RandomizedTest.test(25, 1250, 1250, true));
        // System.out.println(RandomizedTest.test(25, 3750, 3750, true));
        // System.out.println(RandomizedTest.test(25, 7500, 7500, true));
        // System.out.println(RandomizedTest.test(25, 15000, 15000, true));

        // size: 1656 leafSize: 23 loadFactor: 1656 bitsPerKey: 1.517512077294686
        // generateSeconds: 907.279643 evaluateNanosPerKey: 554.3478260869565
        // size: 1250 leafSize: 25 loadFactor: 1250 bitsPerKey: 1.5112
        // generateSeconds: 7416.210937 evaluateNanosPerKey: 312.8
    }

    private static boolean verifyOneTest() {
        int size = 100_000;
        int leafSize = 11;
        int loadFactor = 12;
        for (int j = 0; j < 5; j++) {
            System.gc();
        }
        System.out.println("  size " + size + " leafSize " + leafSize + " loadFactor " + loadFactor);
        FunctionInfo info = RandomizedTest.test(leafSize, loadFactor, size, true);
        System.out.println("  " + info.bitsPerKey + " bits/key");
        System.out.println("  " + info.generateNanos * size / 1_000_000_000 +
                " seconds to generate");
        System.out.println("  " + info.evaluateNanos +
                " nanoseconds to evaluate");
        if (info.bitsPerKey < 2.27 &&
                info.generateNanos * size / 1_000_000_000 < 0.5 &&
                info.evaluateNanos < 250) {
            // all tests passed
            return true;
        }
        return false;
    }

    public static void experimentalResults() {
        System.out.println("6 Experimental Results");
        int loadFactor = 16 * 1024;
        System.out.println("loadFactor " + loadFactor);
        System.out.println("leafSize, bits/key");
        System.out.println("estimated");
        for (int leafSize = 2; leafSize <= 64; leafSize++) {
            int size = 1024 * 1024 / leafSize;
            size = Math.max(size, 32 * 1024);
            FunctionInfo info = SpaceEstimator.estimateTimeAndSpace(leafSize, loadFactor, size);
            System.out.println("        (" + info.leafSize + ", " + info.bitsPerKey + ")");
            // System.out.println("size: " + size);
        }
        System.out.println("experimental");
        for (int leafSize = 2; leafSize <= 23; leafSize++) {
            int size = 1024 * 1024 / leafSize;
            size = Math.max(size, 32 * 1024);
            FunctionInfo info = test(leafSize, loadFactor, size, false);
            System.out.println("        (" + info.leafSize + ", " + info.bitsPerKey + ")");
        }
        System.out.println("leafSize, generation time in nanos/key");
        ArrayList<FunctionInfo> infos = new ArrayList<FunctionInfo>();
        for (int leafSize = 2; leafSize <= 12; leafSize++) {
            int size = 1024 * 1024 / leafSize;
            size = Math.max(size, 32 * 1024);
            FunctionInfo info = test(leafSize, 128, size, true);
            infos.add(info);
            System.out.println("        (" + info.leafSize + ", " +
                    info.generateNanos + ")");
        }
        System.out
                .println("leafSize, evaluation time in nanos/key");
        for (FunctionInfo info : infos) {
            System.out.println("        (" + info.leafSize + ", " +
                    info.evaluateNanos + ")");
        }
    }

    public static void reasonableParameterValues() {
        System.out.println("6.1 Reasonable Parameter Values");
        int leafSize = 10;
        int size = 16 * 1024;
        System.out.println("(leafSize=" + leafSize + ", size=" + size +
                "): loadFactor, generation time in nanos/key");
        ArrayList<FunctionInfo> infos = new ArrayList<FunctionInfo>();
        for (int loadFactor = 8; loadFactor <= 16 * 1024; loadFactor *= 2) {
            FunctionInfo info = test(leafSize, loadFactor, 16 * 1024, true);
            infos.add(info);
            System.out.println("        (" + info.loadFactor + ", " +
                    info.generateNanos + ")");
        }
        System.out
                .println("loadFactor, evaluation time in nanos/key");
        for (FunctionInfo info : infos) {
            System.out.println("        (" + info.loadFactor + ", " +
                    info.evaluateNanos + ")");
        }
        System.out
                .println("loadFactor, bits/key");
        for (FunctionInfo info : infos) {
            System.out.println("        (" + info.loadFactor + ", " +
                    info.bitsPerKey + ")");
        }
    }

    private static <T> long test(HashSet<T> set, UniversalHash<T> hash,
            byte[] description, int leafSize, int loadFactor, int measureCount) {
        BitSet known = new BitSet();
        RecSplitEvaluator<T> eval =
                RecSplitBuilder.newInstance(hash).leafSize(leafSize).loadFactor(loadFactor).
                buildEvaluator(new BitBuffer(description));
        // verify
        for (T x : set) {
            int index = eval.evaluate(x);
            if (index > set.size() || index < 0) {
                Assert.fail("wrong entry: " + x + " " + index +
                        " leafSize " + leafSize +
                        " loadFactor " + loadFactor +
                        " hash " + convertBytesToHex(description));
            }
            if (known.get(index)) {
                eval.evaluate(x);
                Assert.fail("duplicate entry: " + x + " " + index +
                        " leafSize " + leafSize +
                        " loadFactor " + loadFactor +
                        " hash " + convertBytesToHex(description));
            }
            known.set(index);
        }
        // measure
        // Profiler prof = new Profiler().startCollecting();
        long best = Long.MAX_VALUE;
        for (int i = 0; i < measureCount; i++) {
            long evaluateNanos = System.nanoTime();
            for (int j = 0; j < measureCount; j++) {
                for (T x : set) {
                    int index = eval.evaluate(x);
                    if (index > set.size() || index < 0) {
                        Assert.fail("wrong entry: " + x + " " + index +
                                " leafSize " + leafSize +
                                " loadFactor " + loadFactor +
                                " hash " + convertBytesToHex(description));
                    }
                }
            }
            evaluateNanos = System.nanoTime() - evaluateNanos;
            best = Math.min(best, evaluateNanos);
        }
        // System.out.println(prof.getTop(5));
        return best / measureCount;
    }

    public static FunctionInfo testAndMeasure(int leafSize, int loadFactor, int size) {
        return test(leafSize, loadFactor, size, true, 1_000_000_000 / size);
    }

    public static FunctionInfo test(int leafSize, int loadFactor, int size, boolean evaluate) {
        return test(leafSize, loadFactor, size, evaluate, 10);
    }

    public static FunctionInfo test(int leafSize, int loadFactor, int size, boolean evaluate, int measureCount) {
        HashSet<Long> set = createSet(size, 1);
        UniversalHash<Long> hash = new LongHash();
        long generateNanos = System.nanoTime();
        BitBuffer buff;
        buff = RecSplitBuilder.newInstance(hash).
                leafSize(leafSize).loadFactor(loadFactor).
                generate(set);
        int bits = buff.position();
        byte[] data = buff.toByteArray();
        generateNanos = System.nanoTime() - generateNanos;
        assertTrue(bits <= data.length * 8);
        long evaluateNanos = 0;
        if (evaluate) {
            evaluateNanos = test(set, hash, data, leafSize, loadFactor, measureCount);
        }
        FunctionInfo info = new FunctionInfo();
        info.leafSize = leafSize;
        info.size = size;
        info.loadFactor = loadFactor;
        info.bitsPerKey = (double) bits / size;

        if (evaluate) {
            info.evaluateNanos = (double) evaluateNanos / size;
        }
        info.generateNanos = (double) generateNanos / size;
        return info;
    }

    public static HashSet<Long> createSet(int size, int seed) {
        Random r = new Random(seed);
        HashSet<Long> set = new HashSet<Long>(size);
        while (set.size() < size) {
            set.add(r.nextLong());
        }
        return set;
    }

    /**
     * Convert a byte array to a hex encoded string.
     *
     * @param value the byte array
     * @return the hex encoded string
     */
    public static String convertBytesToHex(byte[] value) {
        int len = value.length;
        char[] buff = new char[len + len];
        char[] hex = HEX;
        for (int i = 0; i < len; i++) {
            int c = value[i] & 0xff;
            buff[i + i] = hex[c >> 4];
            buff[i + i + 1] = hex[c & 0xf];
        }
        return new String(buff);
    }

    /**
     * Convert a hex encoded string to a byte array.
     *
     * @param s the hex encoded string
     * @return the byte array
     */
    public static byte[] convertHexToBytes(String s) {
        int len = s.length();
        if (len % 2 != 0) {
            throw new IllegalArgumentException(s);
        }
        len /= 2;
        byte[] buff = new byte[len];
        int[] hex = HEX_DECODE;
        for (int i = 0; i < len; i++) {
            int d = hex[s.charAt(i + i)] << 4 | hex[s.charAt(i + i + 1)];
            buff[i] = (byte) d;
        }
        return buff;
    }

}
