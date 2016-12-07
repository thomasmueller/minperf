package org.minperf;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;

/**
 * Methods to estimate the space needed to generate a MPHF.
 */
public class SpaceEstimator {

    public static final boolean OPTIMAL_SPLIT_RULE = false;
    public static final boolean EXACT_ASYMMETRIC_SPLIT = true;

    public static double getExpectedSpace(int leafSize, int loadFactor) {
        // System.out.println("  Estimated space for leafSize " + leafSize + " / loadFactor " + loadFactor);
        // System.out.println("  Bucket sizes");
        Settings s = new Settings(leafSize, loadFactor);
        double totalBits = 0;
        double inRegularBucket = 0;
        HashMap<Integer, Double> cache = new HashMap<Integer, Double>();
        double worst = 0;
        for (int i = 0; i <= s.getMaxBucketSize(); i++) {
            double probBucketSize = Probability.getProbabilityOfBucketSize(
                    loadFactor, i);
            double bits = getExpectedBucketSpace(s, i, 0, cache);
            if (bits > 0 && bits / i > worst) {
                worst = bits / i;
            }
            inRegularBucket += probBucketSize * i;
            totalBits += bits * probBucketSize;
            // System.out.println("   " + i + ": " + bits * probBucketSize);
        }

        // worst case (disregarding probabilities)
        // loadFactor 1024 leafSize 20 calc 1.5842701617288442
        // leaf 20 lf 1024 **********
        // totalBits = worst * loadFactor;

        // System.out.println("  total average bits for a bucket: " + totalBits);
        // System.out.println("  size > than max, p=" + overflow);
        // System.out.println("  size > than max, p=" + overflow + " (adjusted)");
        double pInOverflow = 1.0 - loadFactor / inRegularBucket;
        // System.out.println("  probability that an entry is in an overflow bucket: " + pInOverflow);
        // pInOverflow = Math.max(minP, pInOverflow);
        // System.out.println("  probability that an entry is in an overflow bucket: " + pInOverflow + " (adjusted)");
        double bitsPerEntryInOverflow = 4.0;
        totalBits += pInOverflow * bitsPerEntryInOverflow;
        double bitsPerBucketStartOverhead = 2 + Math.log(totalBits) / Math.log(2);
        double bitsPerBucketOffsetOverhead = 2 + Math.log(loadFactor) / Math.log(2);
        // bitsPerBucketStartOverhead = 0;
        // bitsPerBucketOffsetOverhead = 0;
        double bitsPerKeyCalc =  (totalBits + bitsPerBucketStartOverhead + bitsPerBucketOffsetOverhead) / loadFactor;
        // System.out.println("loadFactor " + loadFactor + " leafSize " + leafSize + " calc " + bitsPerKeyCalc);
        // int size = 10000 * loadFactor;
        // FunctionInfo info = RandomizedTest.test(leafSize, loadFactor, size, false);
        // int sizeBits = BitCodes.getEliasDelta(size).length();
        // double bitsPerKeyReal = (info.bitsPerKey * size - sizeBits) / size;
        // System.out.println(" real " + bitsPerKeyReal);
        return bitsPerKeyCalc;
    }

    private static double getExpectedBucketSpace(Settings s, int size, int indent, HashMap<Integer, Double> cache) {
        if (size <= 1) {
            return 0;
        }
        Double cached = cache.get(size);
        if (cached != null) {
            return cached;
        }
        double p;
        int k;
        // String spaces = new String(new char[2 + indent * 2]).replace((char) 0, ' ');
        if (size <= s.getLeafSize()) {
            p = Probability.probabilitySplitIntoMSubsetsOfSizeN(size, 1);
            k = BitCodes.calcBestGolombRiceShift(p);
            double bits = BitCodes.calcAverageRiceGolombBits(k, p);
            // System.out.println(spaces + "leaf size " + size + " bits " + bits);
            cache.put(size, bits);
            return bits;
        }
        int split = s.getSplit(size);
        double result = getExpectedBucketSpace(s, size, indent, cache, split);
        if (!OPTIMAL_SPLIT_RULE) {
            cache.put(size, result);
            return result;
        }
        int split2 = split;
        for (int i = -size / 2; i < 10; i++) {
            if (i == 0 || i == 1 || i > s.getLeafSize() / 2) {
                continue;
            }
            if (i > 0) {
                if (split > 0 && i > split) {
                    continue;
                }
                if (size / i * i != size) {
                    continue;
                }
            }
            double x = getExpectedBucketSpace(s, size, indent, cache, i);
            if (x < result) {
                result = x;
                split2 = i;
            }
        }
        if (split != split2) {
            if (split2 < 0) {
                System.out.println("   size " + size + " split " + -split2 + ":" + (size+split2) + "; " + result);
            } else {
                System.out.println("   size " + size + " split by " + split2 + "; " + result / size + " *** ");
            }
        } else {
            if (split2 < 0) {
                System.out.println("   size " + size + " split " + -split2 + ":" + (size+split2) + "; " + result);
            } else {
                System.out.println("   size " + size + " split by " + split2 + "; " + result / size);
            }
        }
        cache.put(size, result);
        return result;

    }

    public static double getSplitProbability(int size, int split) {
        if (split < 0) {
            double p = Probability.calcAsymmetricSplitProbability(size, -split);
            if (p == 0 || p == 1 || Double.isNaN(p)) {
                p = Probability.calcApproxAsymmetricSplitProbability(size, -split);
            }
            return p;
        }
        return Probability.probabilitySplitIntoMSubsetsOfSizeN(split, size / split);
    }

    private static double getExpectedBucketSpace(Settings s, int size, int indent, HashMap<Integer, Double> cache, int split) {
        double p = getSplitProbability(size, split);
        int k = BitCodes.calcBestGolombRiceShift(p);
        double bits = BitCodes.calcAverageRiceGolombBits(k, p);
        // System.out.println(spaces + "node size " + size + " split " + split + " bits " + bits);
        if (split < 0) {
            bits += getExpectedBucketSpace(s, -split, indent + 1, cache);
            bits += getExpectedBucketSpace(s, size + split, indent + 1, cache);
        } else {
            for (int i = 0; i < split; i++) {
                bits += getExpectedBucketSpace(s, size / split, indent + 1, cache);
            }
        }
        return bits;
    }

    public static void listEvalulationTimes() {
        System.out.println("4.5 Evaluation times");
        int size = 100000;
        for (int loadFactor = 25; loadFactor <= 20000; loadFactor *= 10) {
            System.out.println("    \\addplot");
            System.out.println("        plot coordinates {");
            double minBitsPerKey = 10, maxBitsPerKey = 0;
            for (int leafSize = 8; leafSize < 16; leafSize++) {
                FunctionInfo info = RandomizedTest.test(leafSize, loadFactor,
                        size, true);
                System.out.println("        (" + leafSize + ", " + info.evaluateNanos + ")");
                minBitsPerKey = Math.min(minBitsPerKey, info.bitsPerKey);
                maxBitsPerKey = Math.max(maxBitsPerKey, info.bitsPerKey);
            }
            System.out.println("   };");
            System.out.printf("   \\addlegendentry{$loadFactor$ %d; from %.2f to %.2f bits/key}\n",
                    loadFactor, maxBitsPerKey, minBitsPerKey);
        }
    }

    public static void listMaxRecursionDepth() {
        System.out.println("5.2 Time and Space Complexity of Evaluation - maximum recursion depth");
        int leafSize = 2;
        for (int loadFactor = 8; loadFactor < 100000; loadFactor *= 2) {
            int recursionDepth = 1;
            int size = loadFactor * 20;
            Settings settings = new Settings(leafSize, loadFactor);
            while (size > leafSize) {
                int split = settings.getSplit(size);
                if (split < 0) {
                    split = 2;
                }
                size /= split;
                recursionDepth++;
            }
            System.out.println("loadFactor=" + loadFactor + " max recursion depth: " + recursionDepth);
        }
    }

    public static void spaceUsageEstimateSmallSet() {
        System.out.println("4.9 Space Usage and Generation Time");
        for (int leafSize = 8; leafSize <= 14; leafSize++) {
            long hashesPerKey = TimeEstimator.calcEstimatedHashCallsPerKey(leafSize);
            double p = Probability.probabilitySplitIntoMSubsetsOfSizeN(leafSize, 1);
            int k = BitCodes.calcBestGolombRiceShift(p);
            double bitsPerKey = BitCodes.calcAverageRiceGolombBits(k, p) / leafSize;
            System.out.printf("  %d & %d & %.2f \\\\\n", leafSize, hashesPerKey, bitsPerKey);
        }
    }

    public static void spaceUsageEstimate() {
        System.out.println("4.9 Space Usage and Generation Time");
        System.out.println("Estimations");
        for (int leafSize = 6; leafSize <= 23; leafSize++) {
            FunctionInfo info = spaceUsageEstimate(leafSize, 65536);
            System.out.printf("        (%d, %.4f)\n", (long) info.generateNanos, info.bitsPerKey);
        }
        System.out.println("Theoretical Limit: " + Math.log(Math.E) / Math.log(2));
        System.out.println("Reality");
        for (int leafSize = 6; leafSize < 14; leafSize++) {
            FunctionInfo info = RandomizedTest.test(leafSize, 4 * 1024, 4 * 1024, false);
            System.out.printf("        (%d, %.4f)\n", (long) info.generateNanos * 221, info.bitsPerKey);
        }
    }

    public static void calcBestSizes() {
        ArrayList<FunctionInfo> bestPlans = new ArrayList<FunctionInfo>();
        int maxLeafSize = 25;
        for (int leafSize = 14; leafSize <= maxLeafSize; leafSize++) {
            int factor = leafSize;
            for (int size = leafSize; size <= 16 * 1024;) {
                FunctionInfo info = estimateTimeAndSpace(leafSize, size, size);
                info.size = size;
                boolean add = true;
                for (int i = 0; i < bestPlans.size(); i++) {
                    FunctionInfo info2 = bestPlans.get(i);
                    if (info.generateNanos * info.size < info2.generateNanos * info2.size &&
                            info.bitsPerKey < info2.bitsPerKey) {
                        bestPlans.remove(i);
                        i--;
                        continue;
                    } else if (info2.generateNanos * info2.size < info.generateNanos * info.size &&
                        info2.bitsPerKey < info.bitsPerKey) {
                        add = false;
                        break;
                    }
                }
                if (add) {
                    bestPlans.add(info);
                }
                factor = Settings.calcNextSplit(factor);
                size *= factor;
            }
        }
        Collections.sort(bestPlans, new Comparator<FunctionInfo>() {

            @Override
            public int compare(FunctionInfo o1, FunctionInfo o2) {
                if (o1.leafSize != o2.leafSize) {
                    return Integer.compare(o1.leafSize, o2.leafSize);
                }
                return Integer.compare(o1.loadFactor, o2.loadFactor);
            }

        });
        for (int i = 1; i < bestPlans.size() - 1; i++) {
            FunctionInfo info = bestPlans.get(i);
            FunctionInfo prev = bestPlans.get(i - 1);
            FunctionInfo next = bestPlans.get(i + 1);
            if (info.leafSize == prev.leafSize && info.leafSize == next.leafSize) {
         //       bestPlans.remove(i);
          //      i--;
            }
        }
        for (int i = 0; i < bestPlans.size(); i++) {
            FunctionInfo info = bestPlans.get(i);
            System.out.println(info.leafSize + " " + info.size + " " + info.bitsPerKey + " " + info.generateNanos * info.size);
        }
        StringBuilder buff = new StringBuilder();
        buff.append("{");
        for (int i = 0; i < bestPlans.size(); i++) {
            if (i > 0) {
                buff.append(", ");
            }
            FunctionInfo info = bestPlans.get(i);
            buff.append(info.leafSize).append(", ").append(info.size);
        }
        buff.append("};");
        System.out.println(buff);
    }

    public static void calcGoodLoadFactors() {
        ArrayList<FunctionInfo> bestPlans = new ArrayList<FunctionInfo>();
        int maxLeafSize = 25;
        for (int loadFactor = 128; loadFactor <= 8 * 1024; loadFactor *= 2) {
            System.out.println("loadFactor " + loadFactor);
            for (int leafSize = 6; leafSize <= maxLeafSize; leafSize++) {
                FunctionInfo info = estimateTimeAndSpace(leafSize, loadFactor);
                boolean add = true;
                for (int i = 0; i < bestPlans.size(); i++) {
                    FunctionInfo info2 = bestPlans.get(i);
                    if (info.generateNanos < info2.generateNanos &&
                            info.bitsPerKey < info2.bitsPerKey) {
                        bestPlans.remove(i);
                        i--;
                        continue;
                    } else if (info2.generateNanos < info.generateNanos &&
                            info2.bitsPerKey < info.bitsPerKey) {
                        add = false;
                        break;
                    }
                }
                if (add) {
                    bestPlans.add(info);
                }
            }
        }
        Collections.sort(bestPlans, new Comparator<FunctionInfo>() {

            @Override
            public int compare(FunctionInfo o1, FunctionInfo o2) {
                if (o1.leafSize != o2.leafSize) {
                    return Integer.compare(o1.leafSize, o2.leafSize);
                }
                return Integer.compare(o1.loadFactor, o2.loadFactor);
            }

        });
        FunctionInfo[][] minMax = new FunctionInfo[maxLeafSize + 1][2];
        int leafIndex = 0;
        for (int i = 0; i < bestPlans.size(); i++) {
            FunctionInfo info2 = bestPlans.get(i);
            if (info2.leafSize > leafIndex) {
                leafIndex = info2.leafSize;
            }
            if (minMax[leafIndex][0] == null) {
                FunctionInfo last = null;
                if (leafIndex > 0) {
                    last = minMax[leafIndex - 1][0];
                }
                if (last != null && last.loadFactor > info2.loadFactor) {
                    info2 = null;
                }
                minMax[leafIndex][0] = info2;
            }
            minMax[leafIndex][1] = info2;
        }
        System.out.println("Reasonable Parameter Values");
        for (int i = 6; i <= maxLeafSize; i++) {
            FunctionInfo min = minMax[i][0];
            if (min == null) {
                break;
            }
            System.out.printf("    %d & %d & %1.2f \\\\\n", i, min.loadFactor, min.bitsPerKey);
        }
    }

    private static FunctionInfo spaceUsageEstimate(int leafSize, int maxSize) {
        int size = leafSize;
        int factor = size;
        int partSize = 1;
        double total = 0;
        double totalCalls = 0;
        double lastP = Probability.probabilitySplitIntoMSubsetsOfSizeN(factor, partSize);
        StringBuilder buff = new StringBuilder();
        while (size <= maxSize) {
            double p;
            double calls;
            while (true) {
                p = Probability.probabilitySplitIntoMSubsetsOfSizeN(factor, partSize);
                calls = TimeEstimator.calcEstimatedHashCallsPerKey(factor * partSize, factor);
                if (p >= lastP || factor <= 2) {
                    break;
                }
                System.out.println(" - no, too low prob");
                size /= factor;
                factor--;
                size *= factor;
            }
            if (factor > 2) {
                buff.append(factor).append(" ");
            }
            lastP = p;
            int k = BitCodes.calcBestGolombRiceShift(p);
            double est = BitCodes.calcAverageRiceGolombBits(k, p);
            // double entropy = BitCodes.calcEntropy(p);
            total +=  est / size;
            totalCalls += calls;
            partSize = size;
            factor = Settings.calcNextSplit(factor);
            size *= factor;
        }
        FunctionInfo info = new FunctionInfo();
        info.leafSize = leafSize;
        info.bitsPerKey = total;
        info.generateNanos = totalCalls;
        return info;
    }

    static FunctionInfo estimateTimeAndSpace(int leafSize, int loadFactor) {
        return estimateTimeAndSpace(leafSize, loadFactor, 1024 * 1024);
    }

    static FunctionInfo estimateTimeAndSpace(int leafSize, int loadFactor,
            int size) {
        int bucketCount = size / loadFactor;
        FunctionInfo info = spaceUsageEstimate(leafSize, loadFactor);
        info.loadFactor = loadFactor;
        if (bucketCount > 1) {
            // assuming 20 bit overhead per bucket
            info.bitsPerKey = (info.bitsPerKey * size + bucketCount * 20.0) / size;
        }
        int sizeBits = BitCodes.getEliasDelta(leafSize).length();
        info.bitsPerKey = (info.bitsPerKey * size + sizeBits) / size;
        return info;
    }


}
