package org.minperf;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;

/**
 * Generate and test split rules.
 */
public class SplitRuleTest2 {

    static final int MAX_SIZE = 2 * 1024;
    static final int MAX_LEAF_SIZE = 17;

    // map of size, split
    HashMap<Integer, ArrayList<Split>> bestSplits = new HashMap<Integer, ArrayList<Split>>();
    int leafSize;
    double baseTime;
    double scaleFactor = 1.1;

    public static void main(String... args) {
        generateSplitRules();
    }

    static void generateSplitRules() {
        String[] result;
        result = TimeEstimator.test();
        System.out.println("current: " + Arrays.toString(result));

        for(double sf = 0.09; sf <= 0.4; sf += 0.04) {
            SplitRuleTest2 s = new SplitRuleTest2();
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
    }

    int[] getBest() {
        Split[] splits = new Split[MAX_SIZE + 1];
        for (int i = MAX_SIZE - 1; i > 0; i--) {
            if (splits[i] != null) {
                continue;
            }
            ArrayList<Split> list = bestSplits.get(i);
            Split best = null;
            for (Split s : list) {
                if (best == null) {
                    best = s;
                } else {
                    if (s.getTimePerKey() < best.getTimePerKey()) {
                        best = s;
                    }
                }
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
            }
        }
        for (int size = 2; size < MAX_SIZE; size++) {
            for (int split = 2; split < size; split++) {
                if (size % split != 0) {
                    continue;
                }
                for(Split s1 : bestSplits.get(size / split)) {
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
            }
            for (int first = 1; first <= size / 2; first++) {
                for(Split s1 : bestSplits.get(first)) {
                    for(Split s2 : bestSplits.get(size - first)) {
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
                }
            }
        }
    }

    void merge(Split s) {
        ArrayList<Split> list = bestSplits.get(s.size);
        if (list == null) {
            list = new ArrayList<Split>();
            list.add(s);
            bestSplits.put(s.size, list);
            return;
        }
        double t = s.getTimePerKey();
        for (int i = 0; i < list.size(); i++) {
            Split s2 = list.get(i);
            if (s2.getTimePerKey() < t && s2.bitsPerKey < s.bitsPerKey) {
                return;
            } else if (s2.getTimePerKey() > t && s2.bitsPerKey > s.bitsPerKey) {
                list.remove(i);
                i--;
            }
        }
        if (list.isEmpty()) {
            list.add(s);
            return;
        }
        double maxTime = (leafSize * leafSize / s.size * baseTime) +
                (baseTime / leafSize * s.size * scaleFactor);
        if (list.size() > 0 && s.getTimePerKey() > maxTime) {
            return;
        }
        list.add(s);
        int maxSize = 2;
        if (list.size() > maxSize) {
            Collections.sort(list, new Comparator<Split>() {
                @Override
                public int compare(Split o1, Split o2) {
                    return Double.compare(o1.bitsPerKey, o2.bitsPerKey);
                    // return Double.compare(o1.getTimePerKey(), o2.getTimePerKey());
                }
            });
            while(list.size() > maxSize) {
                list.remove(list.size() - 1);
            }
        }
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
