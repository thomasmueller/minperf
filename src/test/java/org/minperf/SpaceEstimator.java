package org.minperf;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;

import org.minperf.universal.LongHash;
import org.minperf.universal.UniversalHash;

/**
 * Methods to estimate the space needed to generate a MPHF.
 */
public class SpaceEstimator {

    public static final boolean OPTIMAL_SPLIT_RULE = false;
    public static final boolean EXACT_ASYMMETRIC_SPLIT = true;

    private static final HashMap<String, Double> SPLIT_PROBABILITY_CACHE = new HashMap<String, Double>();

    public static void main(String... args) {
        int size = 100000;
        for(int leafSize = 3; leafSize < 10; leafSize++) {
            for(int averageBucketSize = 4; averageBucketSize < 4 * 1024; averageBucketSize *= 2) {
                int testCount = 10;
                long offsetListSum = 0;
                long startListSum = 0;
                long bucketBitsSum = 0;
                for(int test = 0; test < testCount; test++) {
                    HashSet<Long> set = RandomizedTest.createSet(size, size);
                    UniversalHash<Long> hash = new LongHash();
                    RecSplitBuilder<Long> builder = RecSplitBuilder.newInstance(hash).
                            leafSize(leafSize).averageBucketSize(averageBucketSize);
                    BitBuffer buff;
                    buff = builder.generate(set);
                    int bits = buff.position();
                    byte[] description = buff.toByteArray();
                    RecSplitEvaluator<Long> eval =
                            RecSplitBuilder.newInstance(hash).leafSize(leafSize).averageBucketSize(averageBucketSize).
                            buildEvaluator(new BitBuffer(description));
                    int headerBits = eval.getHeaderSize();
                    int offsetListSize = eval.getOffsetListSize();
                    int startListSize = eval.getStartListSize();
                    int bucketBits = bits - offsetListSize - startListSize - headerBits;
                    offsetListSum += offsetListSize;
                    startListSum += startListSize;
                    bucketBitsSum += bucketBits;
                }
                System.out.println(leafSize + " " + averageBucketSize + " " +
                        (double) bucketBitsSum / size / testCount + " " +
                        (double) offsetListSum / size / testCount + " " +
                        (double) startListSum / size / testCount);
            }
        }
    }

    public static double getExpectedSpaceEstimate(int leafSize, int averageBucketSize) {
        HashMap<Integer, Double> cache = new HashMap<Integer, Double>();
        Settings s = new Settings(leafSize, averageBucketSize);
        double totalBits = 0;
        for (int i = 0; i <= s.getMaxBucketSize(); i++) {
            double probBucketSize = Probability.getProbabilityOfBucketSize(
                    averageBucketSize, i);
            double bits = getExpectedBucketSpace(s, i, 0, cache);
            totalBits += bits * probBucketSize;
        }
        // System.out.println("  probability that an entry is in an overflow bucket: " + pInOverflow);
        // pInOverflow = Math.max(minP, pInOverflow);
        // System.out.println("  probability that an entry is in an overflow bucket: " + pInOverflow + " (adjusted)");
        double bitsPerBucketStartOverhead = 2 + Math.log(totalBits) / Math.log(2);
        double bitsPerBucketOffsetOverhead = 2 + Math.log(averageBucketSize) / Math.log(2);
        // System.out.println("offset list overhead " + bitsPerBucketOffsetOverhead / averageBucketSize);
        // System.out.println("start list overhead " + bitsPerBucketStartOverhead / averageBucketSize);
        // System.out.println("lists overhead " + (bitsPerBucketOffsetOverhead + bitsPerBucketStartOverhead) / averageBucketSize);
        double bitsPerKeyCalc =  (totalBits + bitsPerBucketStartOverhead + bitsPerBucketOffsetOverhead) / averageBucketSize;
        // System.out.println("averageBucketSize " + averageBucketSize + " leafSize " + leafSize + " calc " + bitsPerKeyCalc);
        // int size = 10000 * averageBucketSize;
        // FunctionInfo info = RandomizedTest.test(leafSize, averageBucketSize, size, false);
        // int sizeBits = BitCodes.getEliasDelta(size).length();
        // double bitsPerKeyReal = (info.bitsPerKey * size - sizeBits) / size;
        // System.out.println(" real " + bitsPerKeyReal);
        return bitsPerKeyCalc;
    }

    public static double getExpectedSpace(int leafSize, int averageBucketSize) {
        HashMap<Integer, Double> cache = new HashMap<Integer, Double>();
        return getExpectedSpace(leafSize, averageBucketSize, cache);
    }

    public static double getExpectedSpace(int leafSize, int averageBucketSize, HashMap<Integer, Double> cache) {
        // System.out.println("  Estimated space for leafSize " + leafSize + " / averageBucketSize " + averageBucketSize);
        // System.out.println("  Bucket sizes");
        Settings s = new Settings(leafSize, averageBucketSize);
        double totalBits = 0;
        double inRegularBucket = 0;
        double worst = 0;
        // int worstSize = -1;
        for (int i = 0; i <= s.getMaxBucketSize(); i++) {
            // System.out.println("size " + i);
            double probBucketSize = Probability.getProbabilityOfBucketSize(
                    averageBucketSize, i);
            double bits = getExpectedBucketSpace(s, i, 0, cache);
            if (bits > 0 && bits / i > worst) {
                worst = bits / i;
                // worstSize = i;
            }
            inRegularBucket += probBucketSize * i;
            totalBits += bits * probBucketSize;
            // if(bits * probBucketSize > 1)
            //     System.out.println("   " + i + " " + bits * probBucketSize);
        }
        // System.out.println("worst case space: " + worst + " at size " + worstSize + " max " + s.getMaxBucketSize());

        // worst case (disregarding probabilities)
        // averageBucketSize 1024 leafSize 20 calc 1.5842701617288442
        // leaf 20 lf 1024 **********
        // totalBits = worst * averageBucketSize;

        // System.out.println("  total average bits for a bucket: " + totalBits);
        // System.out.println("  size > than max, p=" + overflow);
        // System.out.println("  size > than max, p=" + overflow + " (adjusted)");
        double pInOverflow = 1.0 - averageBucketSize / inRegularBucket;
        // System.out.println("  probability that an entry is in an overflow bucket: " + pInOverflow);
        // pInOverflow = Math.max(minP, pInOverflow);
        // System.out.println("  probability that an entry is in an overflow bucket: " + pInOverflow + " (adjusted)");
        double bitsPerEntryInOverflow = 4.0;
        totalBits += pInOverflow * bitsPerEntryInOverflow;
        double bitsPerBucketStartOverhead = 2 + Math.log(totalBits) / Math.log(2);
        double bitsPerBucketOffsetOverhead = 2 + Math.log(averageBucketSize) / Math.log(2);
        // System.out.println("offset list overhead " + bitsPerBucketOffsetOverhead / averageBucketSize);
        // System.out.println("start list overhead " + bitsPerBucketStartOverhead / averageBucketSize);
        // System.out.println("lists overhead " + (bitsPerBucketOffsetOverhead + bitsPerBucketStartOverhead) / averageBucketSize);
        double bitsPerKeyCalc =  (totalBits + bitsPerBucketStartOverhead + bitsPerBucketOffsetOverhead) / averageBucketSize;
        // System.out.println("averageBucketSize " + averageBucketSize + " leafSize " + leafSize + " calc " + bitsPerKeyCalc);
        // int size = 10000 * averageBucketSize;
        // FunctionInfo info = RandomizedTest.test(leafSize, averageBucketSize, size, false);
        // int sizeBits = BitCodes.getEliasDelta(size).length();
        // double bitsPerKeyReal = (info.bitsPerKey * size - sizeBits) / size;
        // System.out.println(" real " + bitsPerKeyReal);
        return bitsPerKeyCalc;
    }

    public static double getExpectedBucketSpace(Settings s, int size, int indent, HashMap<Integer, Double> cache) {
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
            if (i > 0) {
                double p2 = Probability.probabilitySplitIntoMSubsetsOfSizeN(i, size / i);
                double p3 = Probability.probabilitySplitIntoMSubsetsOfSizeN(s.getLeafSize(), 1);
                if (p2 < p3) {
                    // at least more probably than direct mapping
                    continue;
                }
                if (s.getLeafSize() / p3 < size / p2) {
                    continue;
                }
            }
            double x = getExpectedBucketSpace(s, size, indent, cache, i);
            if (x < result) {
                result = x;
                split2 = i;
            }
        }
//        if (split != split2 || split > 0) {
//            double p2 = 0, p3 = 0;
//            if (split2 > 0) {
//                p2 = Probability.probabilitySplitIntoMSubsetsOfSizeN(split2, size / split2);
//                p3 = Probability.probabilitySplitIntoMSubsetsOfSizeN(s.getLeafSize(), 1);
//                if (p2 < p3) {
//
//                }
//                System.out.println(size + ", " + split2 + ", ");
////                System.out.println("    size " + size + " oldSplit " + split + " newSplit " + split2 + " p2=" + p2 + " p3=" + p3);
//            }
//        }
//        if (split != split2) {
            if (split2 < 0) {
                System.out.println("   size " + size + " split " + -split2 + ":" + (size+split2) + "; " + result);
            } else {
                System.out.println("   size " + size + " split by " + split2 + "; " + result / size + " *** old:" + split);
            }
//        } else {
//            if (split2 < 0) {
//                System.out.println("   size " + size + " split " + -split2 + ":" + (size+split2) + "; " + result);
//            } else {
//                System.out.println("   size " + size + " split by " + split2 + "; " + result / size);
//            }
//        }
        cache.put(size, result);
        return result;

    }

    public static double getSplitProbability(int size, int split) {
        String key = "split-" + size + "/" + split;
        Double cached = SPLIT_PROBABILITY_CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        double p;
        if (split < 0) {
            p = Probability.calcExactAsymmetricSplitProbability(size, -split);
        } else {
            p = Probability.probabilitySplitIntoMSubsetsOfSizeN(split, size / split);
        }
        SPLIT_PROBABILITY_CACHE.put(key, p);
        return p;
    }

    private static double getExpectedBucketSpace(Settings s, int size, int indent, HashMap<Integer, Double> cache, int split) {
        double p = getSplitProbability(size, split);
        int k = s.getGolombRiceShift(size); //  BitCodes.calcBestGolombRiceShift(p);
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
        for (int averageBucketSize = 16; averageBucketSize <= 1024; averageBucketSize *= 8) {
            System.out.println("    \\addplot");
            System.out.println("        plot coordinates {");
            double minBitsPerKey = 10, maxBitsPerKey = 0;
            for (int leafSize = 6; leafSize <= 12; leafSize++) {
                FunctionInfo info = RandomizedTest.test(leafSize, averageBucketSize,
                        size, true);
                System.out.println("        (" + leafSize + ", " + info.evaluateNanos + ")");
                minBitsPerKey = Math.min(minBitsPerKey, info.bitsPerKey);
                maxBitsPerKey = Math.max(maxBitsPerKey, info.bitsPerKey);
            }
            System.out.println("   };");
            System.out.printf("   \\addlegendentry{$averageBucketSize$ %d; from %.2f to %.2f bits/key}\n",
                    averageBucketSize, maxBitsPerKey, minBitsPerKey);
        }
    }

    public static void listMaxRecursionDepth() {
        System.out.println("5.2 Time and Space Complexity of Evaluation - maximum recursion depth");
        int leafSize = 2;
        for (int averageBucketSize = 8; averageBucketSize < 100000; averageBucketSize *= 2) {
            int recursionDepth = 1;
            int size = averageBucketSize * 20;
            Settings settings = new Settings(leafSize, averageBucketSize);
            while (size > leafSize) {
                int split = settings.getSplit(size);
                if (split < 0) {
                    split = 2;
                }
                size /= split;
                recursionDepth++;
            }
            System.out.println("averageBucketSize=" + averageBucketSize + " max recursion depth: " + recursionDepth);
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
        System.out.println("Reality");
        for (int leafSize = 4; leafSize < 14; leafSize++) {
            FunctionInfo info = RandomizedTest.test(leafSize, 4 * 1024, 4 * 1024, false);
            System.out.printf("        (%d, %.4f)\n", (long) info.generateNanos * 221, info.bitsPerKey);
        }
    }

    public static void calcGoodAverageBucketSizes() {
        ArrayList<FunctionInfo> bestPlans = new ArrayList<FunctionInfo>();
        int maxLeafSize = 12;
        for (int averageBucketSize = 16; averageBucketSize <= 4 * 1024; averageBucketSize *= 2) {
            System.out.println("averageBucketSize " + averageBucketSize);
            for (int leafSize = 6; leafSize <= maxLeafSize; leafSize++) {
                FunctionInfo info = new FunctionInfo();
                info.leafSize = leafSize;
                info.averageBucketSize = averageBucketSize;
                info.bitsPerKey = SpaceEstimator.getExpectedSpace(leafSize, averageBucketSize);
                if (info.bitsPerKey > 2.4) {
                    continue;
                }
                info.generateNanos = TimeEstimator.getExpectedGenerationTime(leafSize, averageBucketSize);
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
                return Integer.compare(o1.averageBucketSize, o2.averageBucketSize);
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
                if (last != null && last.averageBucketSize > info2.averageBucketSize) {
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
            System.out.printf("    %d & %d & %1.2f \\\\\n", i, min.averageBucketSize, min.bitsPerKey);
        }
    }

}
