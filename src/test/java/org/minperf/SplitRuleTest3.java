package org.minperf;

import java.util.Arrays;
import java.util.HashMap;

/**
 * Generate and test split rules.
 */
public class SplitRuleTest3 {

    static final int MAX_SIZE = 2 * 1024;
    static final int MAX_LEAF_SIZE = 18;

    // map of size, split
    HashMap<Integer, Split> bestSplits = new HashMap<Integer, Split>();
    int leafSize;
    double baseTime;
    double scaleFactor = 1.1;

    public static void main(String... args) {
        generateSplitRules();
    }

    static void generateSplitRules() {
        String[] result;
        for(double sf = 1.0; sf <= 1.0; sf += 0.1) {
            SplitRuleTest3 s = new SplitRuleTest3();
            s.scaleFactor = sf;
            int[][] splitRules = new int[MAX_LEAF_SIZE + 1][];
            for(int leafSize = 0; leafSize <= MAX_LEAF_SIZE; leafSize++) {
                if (leafSize < 2) {
                    splitRules[leafSize] = new int[0];
                } else {
                    s.bestSplits.clear();
                    s.generate(leafSize);
                    splitRules[leafSize] = s.getBest();
                }
            }
            Settings.SPLIT_RULES = splitRules;
            result = TimeEstimator.test();
            System.out.println(sf + " " + Arrays.toString(result));
        }

        System.out.println("    private static final int[][] SPLIT_RULES = {");
        for (int leafSize = 0; leafSize <= 18; leafSize++) {
            if (leafSize > 0) {
                System.out.println(",");
            }
            System.out.println("    // leafSize " + leafSize);
            System.out.print("    splitIntegerList(\"");
            int[] rules = Settings.SPLIT_RULES[leafSize];
            for (int i = 0; i < rules.length; i++) {
                if (i > 0) {
                    System.out.print(",");
                }
                System.out.print(rules[i]);
            }
            System.out.print("\")");
        }
        System.out.println();
        System.out.println("    };");

    }

    int[] getBest() {
        Split[] splits = new Split[MAX_SIZE + 1];
        for (int i = MAX_SIZE - 1; i > 0; i--) {
            if (splits[i] != null) {
                continue;
            }
            Split best = bestSplits.get(i);
            if (best == null) {
                break;
            }
            splits[i] = best;
            if (best.left != null) {
                if (splits[best.left.size] == null) {
                    splits[best.left.size] = best.left;
                }
            }
            if (best.right != null) {
                if (splits[best.right.size] == null) {
                    splits[best.right.size] = best.right;
                }
            }
        }
        int[] splitRules = new int[MAX_SIZE * 3];
//        System.out.println("    // leafSize " + leafSize);
//        System.out.print("    splitIntegerList(\"");
        for (int i = 1, j = 0; i < MAX_SIZE; i++) {
            Split s = splits[i];
            if (s == null) {
                break;
            }
//            if (i > 1) {
//                System.out.print(",");
//            }
//            System.out.print(i + "," + s.splitBy + "," + s.k);
            splitRules[j++] = i;
            splitRules[j++] = s.splitBy;
            splitRules[j++] = s.k;
        }
        return splitRules;
//        System.out.println("\"),");
//        for (int i = 1; i < MAX_SIZE; i++) {
//            Split s = splits[i];
//            System.out.println(i + ": " + s.splitBy + " " + s.bitsPerKey + " " + s.getTimePerKey());
//        }
    }

    void generate(int leafSize) {
        this.leafSize = leafSize;
        baseTime = Double.POSITIVE_INFINITY;
        for (int i = 0; i <= leafSize; i++) {
            Split s = new Split();
            s.size = i;
            s.splitBy = i;
            double p = Probability.probabilitySplitIntoMSubsetsOfSizeN(i, 1);
            double averageTries = 1 / p;
            s.generatePerKey = i * averageTries;
            int k = BitCodes.calcBestGolombRiceShift(p);
            s.k = k;
            s.bitsPerKey = BitCodes.calcAverageRiceGolombBits(k, p) / i;
            merge(s);
            if (i == leafSize) {
                baseTime = s.getTimePerKey();
//                System.out.println("leafSize " + leafSize + " base " + baseTime);
            }
        }
        for (int size = 2; size < MAX_SIZE; size++) {
//            System.out.println("size " + size);
            for (int split = 2; split < size; split++) {
                if (size % split != 0) {
                    continue;
                }
                Split s1 = bestSplits.get(size / split);
                Split s = new Split();
                s.size = size;
                s.splitBy = split;
                s.left = s1;
                double p = Probability.probabilitySplitIntoMSubsetsOfSizeN(split, size / split);
                double averageTries = 1 / p;
                s.generatePerKey = size * averageTries;
                int k = BitCodes.calcBestGolombRiceShift(p);
                s.k = k;
                s.bitsPerKey = s1.bitsPerKey + BitCodes.calcAverageRiceGolombBits(k, p) / size;
                merge(s);
            }
            for (int first = 1; first <= size / 2; first++) {
                Split s1 = bestSplits.get(first);
                Split s2 = bestSplits.get(size - first);
                Split s = new Split();
                s.size = size;
                s.left = s1;
                s.right = s2;
                s.splitBy = -first;
                double p = Probability.calcExactAsymmetricSplitProbability(size, first);
                double averageTries = 1 / p;
                s.generatePerKey = size * averageTries;
                int k = BitCodes.calcBestGolombRiceShift(p);
                s.k = k;
                s.bitsPerKey = BitCodes.calcAverageRiceGolombBits(k, p) / size +
                        (first * s1.bitsPerKey + (size - first) * s2.bitsPerKey) / size;
                merge(s);
            }
            Split s = bestSplits.get(size);
            if (s == null) {
                System.out.println("  leafSize " + leafSize + " end at size " + (size - 1));
                break;
            }
            if (s.splitBy > 2 && s.size > leafSize) {
                System.out.println("  leafSize " + leafSize + " best " + s + " base " + baseTime);
            }
        }
    }

    boolean merge(Split s) {
        Split old = bestSplits.get(s.size);

        // power rule, best scaleFactor = 0.96
        // double limit = Math.pow(baseTime / leafSize * s.size, scaleFactor);

        // linear rule, bestScaleFactor is about 1 +/- 0.5
        double limit = baseTime / leafSize * s.size * scaleFactor;

        // System.out.println("  " + s.size + " budget " + limit + " base " + baseTime + " now " + s.getTimePerKey() + " " + s);
        if (s.getTimePerKey() > limit) {
            return false;
        }
        if (old != null && old.bitsPerKey < s.bitsPerKey) {
            return true;
        }
        bestSplits.put(s.size, s);
        return true;
    }

    static class Split {
        int size;
        int k;
        int splitBy;
        double generatePerKey;
        double bitsPerKey;
        int evaluatePerKey;
        Split left, right;
        double time;

        int getFirstSize() {
            return splitBy < 0 ? -splitBy : size / splitBy;
        }

        int getOtherSize() {
            return splitBy < 0 ? size + splitBy : size / splitBy;
        }

        public String toString() {
            return "s:" + size + " bits:" + bitsPerKey + " split:" + splitBy + " time:" + getTimePerKey();
        }

        double getTimePerKey() {
            if (time != 0.0) {
                return time;
            }
            double result = generatePerKey;
            if (right != null) {
                result += (getFirstSize() * left.getTimePerKey() + getOtherSize() * right.getTimePerKey()) / size;
            } else {
                if (left != null) {
                    result += left.getTimePerKey();
                }
            }
            time = result;
            return result;
        }
    }

}
