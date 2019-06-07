package org.minperf;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.TreeMap;
import java.util.TreeSet;

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
        printSplitRulesList();
        generateSplitRules();
        generateRiceLeaf();
        generateRiceSplitMore();
        generateRiceSplit2();
    }

    public static void printSplitRulesList() {
        System.out.println("Split Rules Used");
        int max = 10 * 1024;
        TreeSet<Integer> sizes = new TreeSet<>();
        int minLeafSize = 1000;
        for (int leafSize = 2; leafSize <= 18; leafSize++) {
            Settings s = new Settings(leafSize, max);
            for (int i = leafSize + 1; i < max; i++) {
                int split = s.getSplit(i);
                if (split > 0) {
                    minLeafSize = Math.min(minLeafSize, leafSize);
                    sizes.add(i);
                }
            }
        }
        for (int leafSize = minLeafSize; leafSize <= 18; leafSize++) {
            TreeMap<Integer, ArrayList<Integer>> splitMap = new TreeMap<>();
            Settings s = new Settings(leafSize, max);
            System.out.print("\\item $leafSize$ " + leafSize + ": ");
            for (int i = leafSize + 1; i < max; i++) {
                int split = s.getSplit(i);
                if (split > 0) {
                    ArrayList<Integer> list = splitMap.get(split);
                    if (list == null) {
                        list = new ArrayList<>();
                        splitMap.put(split, list);
                    }
                    list.add(i);
                }
            }
            boolean first = true;
            for (int split : splitMap.keySet()) {
                ArrayList<Integer> list = splitMap.get(split);
                StringBuilder buff = new StringBuilder();
                for (int i = 0; i < list.size(); i++) {
                    if (i > 0) {
                        buff.append(",~");
                    }
                    buff.append(list.get(i));
                }
                if (!first) {
                    System.out.print("; ");
                }
                first = false;
                System.out.print("split~into~" + split + ":~" + buff);
            }
            System.out.println();
        }
        if(true) return;
        for (int x : sizes) {
            if (x > 50) {
                continue;
            }
            System.out.print(" & " + x);
        }
        System.out.println(" \\\\");
        for (int leafSize = minLeafSize; leafSize <= 18; leafSize++) {
            System.out.print(leafSize);
            for (int x : sizes) {
                if (x > 50) {
                    continue;
                }
                System.out.print(" & ");
                Settings s = new Settings(leafSize, max);
                int a = s.getSplit(x);
                if (a > 0 && x > leafSize) {
                    System.out.print(a);
                }
            }
            System.out.println(" \\\\");
        }
        for (int x : sizes) {
            if (x <= 50) {
                continue;
            }
            System.out.print(" & " + x);
        }
        System.out.println(" \\\\");
        for (int leafSize = minLeafSize; leafSize <= 18; leafSize++) {
            System.out.print(" & " + leafSize);
        }
        System.out.println(" \\\\");
        for (int x : sizes) {
            System.out.print(x);
            for (int leafSize = minLeafSize; leafSize <= 18; leafSize++) {
                System.out.print(" & ");
                Settings s = new Settings(leafSize, max);
                int a = s.getSplit(x);
                if (a > 0 && x > leafSize) {
                    System.out.print(a);
                }
            }
            System.out.println(" \\\\");
        }
        for (int leafSize = 2; leafSize <= 18; leafSize++) {
            String[] list = new String[7];
            Settings s = new Settings(leafSize, max);
            for (int i = leafSize + 1; i < max; i++) {
                int split = s.getSplit(i);
                if (split > 0) {
                    String x = list[split];
                    if (x == null) {
                        x = "" + i;
                    } else {
                        x = x + ", " + i;
                    }
                    list[split] = x;
                }
            }
            System.out.print(leafSize);
            for (int i = 2; i <= 6; i++) {
                System.out.print(" & " + (list[i] == null ? "" : list[i]));
            }
            System.out.println(" \\\\");
        }
        for (int leafSize = 2; leafSize <= 18; leafSize++) {
            TreeMap<Integer, ArrayList<Integer>> splitMap = new TreeMap<>();
            Settings s = new Settings(leafSize, max);
            System.out.println("\\item With $leafSize$ " + leafSize + ":");
            for (int i = leafSize + 1; i < max; i++) {
                int split = s.getSplit(i);
                if (split > 0) {
                    ArrayList<Integer> list = splitMap.get(split);
                    if (list == null) {
                        list = new ArrayList<>();
                        splitMap.put(split, list);
                    }
                    list.add(i);
                }
            }
            for (int split : splitMap.keySet()) {
                ArrayList<Integer> list = splitMap.get(split);
                StringBuilder buff = new StringBuilder();
                for (int i = 0; i < list.size(); i++) {
                    if (i > 0) {
                        buff.append(", ");
                    }
                    buff.append(list.get(i));
                }
                System.out.println("\\subitem split into " + split + ": " + buff);
            }
        }
    }

    static void generateSplitRules() {
        System.out.println("    private static final int[][] SPLIT_RULES = {");
        for (int leafSize = 0; leafSize <= 32; leafSize++) {
            if (leafSize > 0) {
                System.out.println(",");
            }
            System.out.println("    // leafSize " + leafSize);
            System.out.print("    { ");
            generateSplitRules(leafSize);
            System.out.print(" }");
        }
        System.out.println();
        System.out.println("    };");
    }

    private static void generateSplitRules(int leafSize) {
        if (leafSize < 2) {
            return;
        }
        int max = 1000;
        double[] bitsPerKeyList = new double[max];
        for (int i = 2; i <= leafSize; i++) {
            bitsPerKeyList[i] = getBitsPerKey(bitsPerKeyList, i, i);
        }
        int[] splitList = new int[max];
        int last = -1;
        splitList[leafSize] = leafSize;
        double pLeaf = Probability.probabilitySplitIntoMSubsetsOfSizeN(
                leafSize, 1);
        int lineLen = 0;
        boolean first = true;
        for (int size = leafSize + 1; size < max; size++) {
            int bestSplit = 0;
            double bestBits = 0;
            for (int i = size - 1; i >= leafSize; i--) {
                if (splitList[i] > 0) {
                    bestSplit = -i;
                    bestBits = getBitsPerKey(bitsPerKeyList, size, -i);
                    // System.out.println("        " + size + " " + -bestSplit + ":" + (size+bestSplit) + " " + bestBits);
                    break;
                }
            }
            if (size < leafSize * 10) {
                for (int i = -1; i > -leafSize; i--) {
                    double pSplit = getProbabilitySplit(size, i);
                    if (size / pSplit > 2 * leafSize / pLeaf) {
                        // average operations of split should be less than
                        // average operations of leaf processing
                        continue;
                    }
                    double bits = getBitsPerKey(bitsPerKeyList, size, i);
                    if (bits < bestBits) {
                        bestSplit = i;
                        bestBits = bits;
                    }
                }
            }
            for (int split = 2; split < leafSize; split++) {
                if (size % split != 0) {
                    continue;
                }
                double pSplit = Probability.probabilitySplitIntoMSubsetsOfSizeN(
                        split, size / split);
                if (size / pSplit > 2 * leafSize / pLeaf) {
                    // average operations of split should be less than
                    // average operations of leaf processing
                    continue;
                }
                double bits = getBitsPerKey(bitsPerKeyList, size, split);
                if (bits < bestBits) {
                    if (size < leafSize * 2 || bits < 0.99 * bestBits) {
                        bestSplit = split;
                        bestBits = bits;
                    }
                }
            }
            splitList[size] = bestSplit;
            bitsPerKeyList[size] = getBitsPerKey(bitsPerKeyList, size, bestSplit);
            //System.out.println(" " + size + ", " + bestSplit + ", ");
            // if(bestSplit > 0) {
            //    System.out.println(" " + size + " /" + bestSplit + " " + bitsPerKeyList[size]);
            // } else {
            //    System.out.println(" " + size + " " + -bestSplit + ":" + (size+bestSplit) + " " + bitsPerKeyList[size]);
            // }
            if (bestSplit > 0) {
                double p = getProbabilitySplit(size, bestSplit);
                int k = BitCodes.calcBestGolombRiceShift(p);
                if (!first) {
                    System.out.print(", ");
                }
                if (lineLen > 3) {
                    System.out.println();
                    System.out.print("        ");
                    lineLen = 0;
                }
                lineLen++;
                first = false;
                System.out.print(size + ", " + bestSplit + ", " + k);
            }
            if (bestSplit != last) {
                last = bestSplit;
            }
        }
    }

    private static double getBitsPerKey(double[] bitsPerKeyList, int size, int split) {
        double p = getProbabilitySplit(size, split);
        double p2 = getSimplifiedProbabilitySplit(size, split);
        int k = BitCodes.calcBestGolombRiceShift(p2);
        double bitsPerKey = BitCodes.calcAverageRiceGolombBits(k, p) / size;
        if (split > 0) {
            bitsPerKey += bitsPerKeyList[size / split];
        } else {
            int a = -split;
            int b = size - a;
            bitsPerKey += bitsPerKeyList[a] * a / size;
            bitsPerKey += bitsPerKeyList[b] * b / size;
        }
        return bitsPerKey;
    }

    private static double getSimplifiedProbabilitySplit(int size, int split) {
        if (split > 0) {
            return Probability.probabilitySplitIntoMSubsetsOfSizeN(
                    split, size / split);
        }
        return Probability.probabilitySplitIntoMSubsetsOfSizeN(
                2, size / 2);
    }

    private static double getProbabilitySplit(int size, int split) {
        if (split > 0) {
            return Probability.probabilitySplitIntoMSubsetsOfSizeN(
                    split, size / split);
        }
        return Probability.calcExactAsymmetricSplitProbability(size, -split);
    }

    static void printSplit() {
        for (int leafSize = 8; leafSize < 20; leafSize++) {
            Settings s = new Settings(leafSize, 8 * 1024);
            System.out.println("With leafSize " + leafSize + ":");
            String lastRule = null;
            int lastRuleStart = 1;
            for (int i = 1; i < 8 * 1024; i++) {
                String rule = null;
                if (i <= leafSize) {
                    rule = "map directly.";
                } else {
                    int split = s.getSplit(i);
                    if (split > 0) {
                        rule = "split evenly into " + split + " subsets.";
                    } else {
                        rule = "split into two subsets such that the first has " + -split + " keys.";
                    }
                }
                if (lastRule != null && !lastRule.equals(rule)) {
                    String range;
                    if (lastRuleStart == i - 1) {
                        range = "" + lastRuleStart;
                    } else {
                        range = lastRuleStart + " to " + (i - 1);
                    }
                    System.out.println("Sets of size " + range + ": " + lastRule);
                    lastRuleStart = i;
                }
                lastRule = rule;
            }
        }
    }

    public static void printSplitRule() {
        if (Settings.IMPROVED_SPLIT_RULES) {
            return;
        }
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

    public void verifyRiceLeaf() {
        for (int i = 2; i < 25; i++) {
            Settings s = new Settings(i, 65536);
            double p = Probability.probabilitySplitIntoMSubsetsOfSizeN(i, 1);
            int k = BitCodes.calcBestGolombRiceShift(p);
            assertEquals(k, s.getGolombRiceShift(i));
        }
    }

    //@Test
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

    static void generateRiceLeaf() {
        StringBuilder buff = new StringBuilder();
        buff.append("int[] RICE_LEAF = {");
        for (int leafSize = 0; leafSize <= 32; leafSize++) {
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

    // this test fails!!!
    // @Test
    public void testSplit() {
        Settings s = new Settings(8, 1024);
        for (int i = 9; i < 200; i++) {
            int split = s.getSplit(i);
            int expected;
            if (i < 32) {
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
