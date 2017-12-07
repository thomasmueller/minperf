package org.minperf;

import java.util.ArrayList;

/**
 * Generate and test split rules.
 */
public class SplitRuleTest {

    public static void main(String... args) {
        generateSplitRules();
    }

    static void generateSplitRules() {
        System.out.println("    private static final int[][] SPLIT_RULES = {");
        for (int leafSize = 0; leafSize <= 18; leafSize++) {
            if (leafSize > 0) {
                System.out.println(",");
            }
            System.out.println("    // leafSize " + leafSize);
            System.out.print("    splitIntegerList(\"");
            generateSplitRules(leafSize);
            System.out.print("\")");
        }
        System.out.println();
        System.out.println("    };");
    }

    private static void generateSplitRules(int leafSize) {
        if (leafSize < 2) {
            return;
        }
        int max = 1024;
        double[] bitsPerKeyList = new double[max];
        for (int i = 2; i <= leafSize; i++) {
            bitsPerKeyList[i] = getBitsPerKey(bitsPerKeyList, i, i);
        }
        int[] splitList = new int[max];
        splitList[leafSize] = leafSize;
        boolean first = true;
        for (int size = leafSize + 1; size < max; size++) {
            int bestSplit = 0;
            double bestBits = Double.POSITIVE_INFINITY;
            for (int i = size - 1; i >= leafSize; i--) {
                double b = getBitsPerKey(bitsPerKeyList, size, -i);
                if (b < bestBits) {
                    bestSplit = -i;
                    bestBits = b;
                }
            }
            for (int split = 2; split < leafSize; split++) {
                if (size % split != 0) {
                    continue;
                }
                double bits = getBitsPerKey(bitsPerKeyList, size, split);
                if (bits < bestBits) {
                    bestSplit = split;
                    bestBits = bits;
                }
            }
            splitList[size] = bestSplit;
            bitsPerKeyList[size] = getBitsPerKey(bitsPerKeyList, size, bestSplit);
            double p = getProbabilitySplit(size, bestSplit);
            int k = BitCodes.calcBestGolombRiceShift(p);
            if (!first) {
                System.out.print(", ");
            }
            first = false;
            System.out.print(size + ", " + bestSplit + ", " + k);
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

    private static void calcBest() {
        int maxSize = 1024;
        int maxLeaf = 20;
        ArrayList<ArrayList<FunctionInfo>> list = new ArrayList<>();
        for (int i = 0; i < maxSize; i++) {
            list.add(new ArrayList<FunctionInfo>());
        }
        for (int leafSize = 2; leafSize <= maxLeaf; leafSize++) {
            FunctionInfo info = new FunctionInfo();
            info.leafSize = leafSize;
            info.size = leafSize;
            info.evaluateNanos = 1;
            double p = Probability.probabilitySplitIntoMSubsetsOfSizeN(
                leafSize, 1);
            int k = BitCodes.calcBestGolombRiceShift(p);
            double bitsPerKey = BitCodes.calcAverageRiceGolombBits(k, p) / leafSize;
            info.bitsPerKey = bitsPerKey;
            info.generateNanos = leafSize / p;
            list.get(leafSize).add(info);
        }
        for (int size = 2; size < maxSize; size++) {
            for (int split = -(size - 1); split < maxLeaf; split++) {
                if (split == 0 || split == 1 || (split > 0 && size % split != 0)) {
                    continue;
                }
                if (split > 0) {
                    double p = Probability.probabilitySplitIntoMSubsetsOfSizeN(
                            split, size / split);
                    int k = BitCodes.calcBestGolombRiceShift(p);
                    double bitsPerKey = BitCodes.calcAverageRiceGolombBits(k, p) / size;
                    if (Double.isInfinite(bitsPerKey) || bitsPerKey > 1000) {
                        continue;
                    }
                    ArrayList<FunctionInfo> partList = list.get(size / split);
                    for (FunctionInfo part : partList) {
                        FunctionInfo info = new FunctionInfo();
                        info.size = size;
                        info.split = split;
                        info.leafSize = part.leafSize;
                        info.evaluateNanos = part.evaluateNanos + 1;
                        info.generateNanos = part.generateNanos + size / p;
                        info.bitsPerKey = part.bitsPerKey + bitsPerKey;
                        addOrReplace(list.get(size), info);
                    }
                } else {
                    int p1 = -split;
                    int p2 = size + split;
                    if (p1 == p2) {
                        continue;
                    }
                    double p = Probability.calcExactAsymmetricSplitProbability(size, p1);
                    // this is the "wrong" k, as used by the implementation
                    int k = Settings.calcRiceParamSplitByTwo(size);
                    double bitsPerKey = BitCodes.calcAverageRiceGolombBits(k, p) / size;
                    if (Double.isInfinite(bitsPerKey)) {
                        continue;
                    }
                    ArrayList<FunctionInfo> part1List = list.get(p1);
                    ArrayList<FunctionInfo> part2List = list.get(p2);
                    for (FunctionInfo part1 : part1List) {
                        for (FunctionInfo part2 : part2List) {
                            FunctionInfo info = new FunctionInfo();
                            info.size = size;
                            info.split = split;
                            info.leafSize = Math.max(part1.leafSize, part2.leafSize);
                            info.evaluateNanos = (p1 * part1.evaluateNanos + p2 * part2.evaluateNanos) / size + 1;
                            info.generateNanos = (p1 * part1.generateNanos + p2 * part2.generateNanos) / size + size / p;
                            info.bitsPerKey = (p1 * part1.bitsPerKey + p2 * part2.bitsPerKey) / size + bitsPerKey;
                            addOrReplace(list.get(size), info);
                        }
                    }
                }
            }
            ArrayList<FunctionInfo> l = list.get(size);
            System.out.println("size " + size);
            double bestBits = 10;
            for (FunctionInfo info : l) {
                bestBits = Math.min(bestBits, info.bitsPerKey);
                if (info.leafSize == 10 && info.split > 0) {
                    System.out.println("  " + info);
                }
                if (Double.isInfinite(info.bitsPerKey)) {
                    return;
                }
                if (info.bitsPerKey > 1000) {
                    return;
                }
            }
            if (bestBits < 1.5 && size > 1024) {
                break;
            }
        }
    }

    static void addOrReplace(ArrayList<FunctionInfo> list, FunctionInfo info) {
        boolean add = list.size() == 0;
        for (int i = 0; i < list.size(); i++) {
            FunctionInfo old = list.get(i);
            if (old.bitsPerKey <= info.bitsPerKey &&
                    old.generateNanos <= info.generateNanos &&
                    old.evaluateNanos <= info.evaluateNanos) {
                return;
            }
            if (old.bitsPerKey >= info.bitsPerKey &&
                    old.generateNanos >= info.generateNanos &&
                    old.evaluateNanos >= info.evaluateNanos) {
                add = true;
                list.remove(i);
                i--;
                continue;
            }
            if (info.bitsPerKey < old.bitsPerKey ||
                    info.generateNanos < old.generateNanos ||
                    info.evaluateNanos < old.evaluateNanos) {
                add = true;
            }
            if (info.leafSize == old.leafSize) {
                // for the same leaf size, favor faster evaluation
                if (info.evaluateNanos <= old.evaluateNanos) {
                    add = true;
                    list.remove(i);
                    i--;
                    continue;
                }
                return;
            }
        }
        if (add) {
            list.add(info);
        }
    }

}
