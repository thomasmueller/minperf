package org.minperf;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Tests the constants, and generate the constants used in the Settings class.
 */
public class SettingsTest {

    /**
     * Calculate the constants from scratch. This it quite slow.
     */
    public static void main(String... args) {
        System.out.println("Constants");
        printSplit();
        generateEstimatedSpaceUsage();
        generateRiceLeaf();
        generateRiceSplitMore();
        generateRiceSplit2();
    }

    static void printSplit() {
        for (int leafSize = 8; leafSize < 20; leafSize++) {
            Settings s = new Settings(leafSize, 8 * 1024);
            System.out.println("leafSize " + leafSize);
            int last  = 0;
            for (int i = leafSize + 1; i < 8 * 1024; i++) {
                int split = s.getSplit(i);
                if (split == 2) {
                    split = -(i / 2);
                }
                if (split > 0) {
                    System.out.println("  size " + i + " even split " + split);
                    last = 0;
                } else {
                    if (last != split) {
                        System.out.println("  size " + i + " split " + -split + ":remainder");
                        last = split;
                    }
                }
            }
        }
    }

    @Test
    public void testScale() {
        for (int split = 2; split < 32; split++) {
            int maxZero = Integer.MAX_VALUE / split;
            assertEquals(0, Settings.scaleSmallSize(0, split));
            assertEquals(0, Settings.scaleSmallSize(maxZero / 2, split));
            assertEquals(0, Settings.scaleSmallSize(maxZero, split));
            assertEquals(1, Settings.scaleSmallSize(maxZero + 2, split));
            int max = Integer.MAX_VALUE;
            assertEquals(split - 1, Settings.scaleSmallSize(max, split));
            int at = max - maxZero;
            for (int sub = maxZero; sub > 0; sub = sub == 1 ? 0 : (sub + 1) / 2) {
                if (Settings.scaleSmallSize(at, split) < split - 1) {
                    at += sub;
                } else {
                    at -= sub;
                }
            }
            int got = max - at;
            double probability = 100. * got / maxZero;
            assertTrue(probability >= 99.9999);
        }
    }

    public static void printSplitRule() {
        System.out.println("4.3 Recursion: Split Rule");
        int x = 14;
        int f = x;
        while (x < 1024) {
            f = Settings.calcNextSplit(f);
            System.out.println("size " + x + ", now " + f + " such sets are combined");
            x *= f;
        }
        x = 6;
        f = x;
        while (x < 32) {
            f = Settings.calcNextSplit(f);
            System.out.println("size " + x + ", now " + f + " such sets are combined");
            x *= f;
        }
    }

    @Test
    public void verifyRiceLeaf() {
        for (int i = 2; i < 25; i++) {
            Settings s = new Settings(i, 65536);
            double p = Probability.probabilitySplitIntoMSubsetsOfSizeN(i, 1);
            int k = BitCodes.calcBestGolombRiceShift(p);
            assertEquals(k, s.getGolombRiceShift(i));
        }
    }

    @Test
    public void verifyRiceSplitMore() {
        for (int leafSize = 2; leafSize <= 25; leafSize++) {
            Settings s = new Settings(leafSize, 65536);
            int split = Settings.calcNextSplit(leafSize);
            double lastP = Probability.probabilitySplitIntoMSubsetsOfSizeN(leafSize, 1);
            for (int i = leafSize; i < 64 * 1024;) {
                i *= split;
                int m = split, n = i / m;
                double p = Probability
                        .probabilitySplitIntoMSubsetsOfSizeN(m, n);
                // System.out.println("leafSize " + leafSize + " size=" + i +
                //         " split " + split + " p=" + p);
                if (leafSize > 2) {
                    assertTrue(p > lastP);
                }
                lastP = p;
                if (split <= 2) {
                    break;
                }

                int k = BitCodes.calcBestGolombRiceShift(p);
                int k2 = s.getGolombRiceShift(i);
                assertEquals(k2, k);
                assertEquals(split, s.getSplit(i));
                split = Settings.calcNextSplit(split);
            }
        }
    }

    @Test
    public void verifyUniversalHashIndex() {
        long div = 1 << Settings.SUPPLEMENTAL_HASH_SHIFT;
        for (long i = div - 100; i <= div + 100; i++) {
            long index = Settings.getUniversalHashIndex(i);
            assertEquals("i: " + i, i / div, index);
            boolean needNew = Settings.needNewUniversalHashIndex(index);
            assertEquals(index % div == 0, needNew);
        }
    }

    @Test
    public void verifyRiceSplit2() {
        Settings s = new Settings(1, 65536);
        int last = 0;
        for (int i = 1; i < 65; i++) {
            int split = s.getSplit(i);
            if (split > 2) {
                continue;
            }
            int rice = s.getGolombRiceShift(i);
            if (rice != last) {
                double p = Probability.probabilitySplitIntoMSubsetsOfSizeN(2,
                        i / 2);
                int k = BitCodes.calcBestGolombRiceShift(p);
                double p2 = Probability.probabilitySplitIntoMSubsetsOfSizeN(2,
                        (i - 1) / 2);
                int k2 = BitCodes.calcBestGolombRiceShift(p2);
                // System.out.println(i + " " + k + " at " + (i / 2) +
                // " previous k " + k2 + " at " + ((i - 1) / 2));
                assertTrue("i " + i + " k " + k + " k2 " + k2, k2 + 1 == k);
                last = rice;
            }
        }
    }

    private static void generateEstimatedSpaceUsage() {
        StringBuilder buff = new StringBuilder();
        buff.append("int[] ESTIMATED_SPACE = {0");
        for (int leafSize = 1; leafSize <= 25; leafSize++) {
            buff.append(", ");
            FunctionInfo info = TimeAndSpaceEstimator.estimateTimeAndSpace(leafSize, 1024, 1024);
            buff.append((int) (1000 * info.bitsPerKey));
        }
        System.out.println(buff.append("};"));
    }

    private static void generateRiceLeaf() {
        StringBuilder buff = new StringBuilder();
        buff.append("int[] RICE_LEAF = {");
        for (int leafSize = 0; leafSize <= 25; leafSize++) {
            double p = Probability.probabilitySplitIntoMSubsetsOfSizeN(
                    leafSize, 1);
            int k = BitCodes.calcBestGolombRiceShift(p);
            if (leafSize > 0) {
                buff.append(", ");
            }
            buff.append(k);
        }
        System.out.println(buff.append("};"));
    }

    private static void generateRiceSplitMore() {
        StringBuilder buff = new StringBuilder();
        buff.append("int[][] RICE_SPLIT_MORE = { ");
        for (int leafSize = 0; leafSize <= 25; leafSize++) {
            if (leafSize > 0) {
                buff.append(", ");
            }
            buff.append("{ ");
            int split = Settings.calcNextSplit(leafSize);
            for (int i = leafSize; i < 64 * 1024;) {
                if (split <= 2) {
                    break;
                }
                if (i > leafSize) {
                    buff.append(", ");
                }
                i *= split;
                int m = split, n = i / m;
                double p = Probability
                        .probabilitySplitIntoMSubsetsOfSizeN(m, n);
                int k = BitCodes.calcBestGolombRiceShift(p);
                buff.append(k);
                split = Settings.calcNextSplit(split);
                // split = split / 2;
            }
            buff.append("}");
        }
        System.out.println(buff.append("};"));
    }

    private static void generateRiceSplit2() {
        StringBuilder buff = new StringBuilder();
        buff.append("private static final int[] RICE_SPLIT_2 = {0");
        for (int testk = 1; testk < 10; testk++) {
            int expected = (int) Math.pow(10, 0.5862358 * testk - 0.3675082);
            System.out.println("for " + testk + " search at " +
                    (int) (expected * 0.9) + ".." + (int) (expected * 1.2 + 2));
            int border = binarySearchFirstSizeWithRice((int) (expected * 0.9),
                    (int) (expected * 1.2) + 2, testk);
            int m = 2, n = border;
            double p = Probability.probabilitySplitIntoMSubsetsOfSizeN(m, n);
            int k = BitCodes.calcBestGolombRiceShift(p);
            System.out.println("  at " + border + ": " + k);
            n = border + 1;
            p = Probability.probabilitySplitIntoMSubsetsOfSizeN(m, n);
            k = BitCodes.calcBestGolombRiceShift(p);
            System.out.println("  at " + (border + 1) + ": " + k);
            buff.append(", ");
            buff.append((border + 1) * 2);
        }
        buff.append("};");
        System.out.println(buff);
    }

    private static int binarySearchFirstSizeWithRice(int min, int max,
            int search) {
        for (int i = 0; i < 30; i++) {
            int n = (min + max) / 2;
            double p = Probability.probabilitySplitIntoMSubsetsOfSizeN(2, n);
            int k = BitCodes.calcBestGolombRiceShift(p);
            if (k < search) {
                min = n;
            } else {
                max = n;
            }
            if (min + 1 >= max) {
                break;
            }
        }
        return (min + max) / 2;
    }

    @Test
    public void testSplit() {
        Settings s = new Settings(8, 1024);
        for (int i = 9; i < 200; i++) {
            int split = s.getSplit(i);
            int expected;
            if (i <= 16) {
                expected = -(i - (i / 2));
            } else if (i < 32) {
                expected = -8;
            } else if (i == 32) {
                expected = 4;
            } else if (i < 64) {
                expected = -32;
            } else if (i == 64) {
                expected = 2;
            } else if (i < 128) {
                expected = -64;
            } else if (i == 128) {
                expected = 2;
            } else {
                expected = -128;
            }
            assertEquals("i:" + i, expected, split);
            if (i > 0) {
                if (split < 0) {
                    split = 2;
                }
                // System.out.println(i + " into m subsets: " + split + " of size " + i / split);
                double p = Probability.probabilitySplitIntoMSubsetsOfSizeN(split, i / split);
                int riceExp = BitCodes.calcBestGolombRiceShift(p);
                int rice = s.getGolombRiceShift(i);
                assertEquals(riceExp, rice);
            }
        }
    }

}
