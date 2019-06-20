package org.minperf;

import java.util.Arrays;
import java.util.Locale;

public class TestSplitStrategy {
    
    Split[] splitList;
    
    public static int reduce(int hash, int n) {
        return (int) (((hash & 0xffffffffL) * n) >>> 32);
    }

    public static void main(String... args) {
        new TestSplitStrategy().test();
    }
    
    public void test() {
        int leafSize = 8;
        int averageBucketSize = 256;
        System.out.println("Using the default split strategy ================");
        showSplitStrategy(leafSize, averageBucketSize, false, OptimizeTarget.SPACE);
        System.out.println(splitList[averageBucketSize - 1]);
        System.out.println();
      System.out.println("Using the \"best\" split strategy for everything (pareto) ================");
      showSplitStrategy(leafSize, averageBucketSize, true, OptimizeTarget.PARETO);
      System.out.println(splitList[averageBucketSize - 1]);
//        System.out.println("Using the \"best\" split strategy for space ================");
//        showSplitStrategy(leafSize, averageBucketSize, true, OptimizeTarget.SPACE);
//        System.out.println(splitList[averageBucketSize - 1]);
//        System.out.println("Using the \"best\" split strategy for generation time ================");
//        showSplitStrategy(leafSize, averageBucketSize, true, OptimizeTarget.GENERATION_TIME);
//        System.out.println(splitList[averageBucketSize - 1]);
//        System.out.println("Using the \"best\" split strategy for evaluation time ================");
//        showSplitStrategy(leafSize, averageBucketSize, true, OptimizeTarget.EVALUATION_TIME);
//        System.out.println(splitList[averageBucketSize - 1]);
        System.out.println();
    }

    private void showSplitStrategy(int leafSize, int maxBucketSize, boolean useBest, OptimizeTarget target) {
        splitList = new Split[maxBucketSize];        
        for (int m = 0; m < maxBucketSize; m++) {
            int[] parts;
            double p;
            if (m <= 1) {
                Split s = new Split();
                s.parts = new int[0];
                s.evaluation = 0;
                s.generation = 0;
                s.size = m;
                s.spaceUsage = 0;
                splitList[m] = s;
                continue;
            } else if (m <= leafSize) {
                p = Probability.probabilitySplitIntoMSubsetsOfSizeN(m, 1);
                parts = new int[0];
            } else {
                parts = getParts(m, leafSize);
                p = approximateProbability(parts);
            }
//System.out.println("size " + m + " parts " + Arrays.toString(parts));            
            Split s = getSplit(m, p, parts);
//            System.out.println("split " + s);
            if (m > leafSize) {
                for(int partCount = 3; partCount < 10 && partCount < m; partCount++) {
                    if (m % partCount == 0) {
                        parts = new int[partCount];
                        Arrays.fill(parts, m / partCount);
                        p = Probability.probabilitySplitIntoMSubsetsOfSizeN(partCount, m / partCount);
                        Split alt = getSplit(m, p, parts);
                        if (useBest && target.isBetter(alt, s)) {
                            System.out.println("alt is better, s=" + s + "\nalt=" + alt + "\n");
                            s = alt;
                        }
                    } else {
                        parts = new int[partCount];
                        Arrays.fill(parts, (m + partCount - 1) / partCount);
                        parts[partCount - 1] = m - (partCount - 1) * parts[0];
                        if (parts[partCount - 1] < 0) {
                            continue;
                        }
                        int sum = 0;
                        for(int pp: parts) {
                            sum += pp;
                        }
                        if (sum != m || parts[0] < parts[partCount - 1]) {
                            throw new AssertionError();
                        }
                        p = approximateProbability(parts);
                        Split alt = getSplit(m, p, parts);
                        if (useBest && target.isBetter(alt, s)) {
                            System.out.println("alt is better, s=" + s + "\nalt=" + alt + "\n");
                            s = alt;
                        }
                    }
                }
                for(int firstPart = m / 2; firstPart < m; firstPart++) {
                    parts = new int[] {firstPart, m - firstPart};
                    p = Probability.calcExactAsymmetricSplitProbability(m, parts[0]);
                    Split alt = getSplit(m, p, parts);
                    if (useBest && target.isBetter(alt, s)) {
                        System.out.println("alt is better, s=" + s + "\nalt=" + alt + "\n");
                        s = alt;
                    }
                }
            }
            splitList[m] = s;
        }
    }
    
    enum OptimizeTarget {
        PARETO
        {
            @Override
            boolean isBetter(Split alt, Split old) {
                return alt.spaceUsage < old.spaceUsage && alt.generation < old.generation && alt.evaluation < old.evaluation;
            }
        },
        SPACE
        {
            @Override
            boolean isBetter(Split alt, Split old) {
                return alt.spaceUsage < old.spaceUsage && alt.generation < old.generation;
            }
        },
        SPACE2
        {
            @Override
            boolean isBetter(Split alt, Split old) {
                return alt.spaceUsage < old.spaceUsage;
            }
        },
        EVALUATION_TIME
        {
            @Override
            boolean isBetter(Split alt, Split old) {
                return alt.evaluation < old.evaluation;
            }
        },
        GENERATION_TIME
        {
            @Override
            boolean isBetter(Split alt, Split old) {
                return alt.generation < old.generation;
            }
        };
        
        abstract boolean isBetter(Split alt, Split old);
    }

    private Split getSplit(int m, double p, int[] parts) {
        int k = BitCodes.calcBestGolombRiceShift(p);
        double bits = BitCodes.calcAverageRiceGolombBits(k, p);
        Split s = new Split();
        s.parts = parts;
        s.size = m;
        double eval = 1;
        double space = bits;
        double gen = m / p;
        for (int i = 0; i < parts.length; i++) {
            int part = parts[i];
            Split sub = splitList[part];
            eval += 1. * part / m * sub.evaluation;
            space += sub.spaceUsage;
            gen += sub.generation;
        }
        s.evaluation = eval;
        s.spaceUsage = space;
        s.generation = gen;
        return s;
    }

    private static int[] getParts1(int m, int leafSize, int fanout) {
        int[] parts = new int[fanout];
        if (m <= leafSize) {
            parts[0] = m;
            return parts;
        }
        int part, partlim, unit;
        parts[fanout - 1] = m;
        int l = (int) (m + leafSize - 1) / leafSize;
        int bij_threshold = (int) Math.ceil(leafSize / 3. + 1. / 2);
        if (l > bij_threshold) {
            int bsplit = bij_threshold * leafSize;
            int p = (int) (m + bsplit - 1) / bsplit;
            part = p / fanout;
            partlim = p % fanout;
            unit = bsplit;
        } else {
            part = l / fanout;
            partlim = l % fanout;
            unit = leafSize;
        }
        for (int i = 0; i < fanout - 1; ++i) {
            int plus = (fanout - i <= partlim) ? 1 : 0;
            parts[i] = (part + plus) * unit;
            parts[fanout - 1] -= parts[i];
        }
        return parts;
    }
    
    private static int[] getParts(int m, int leafSize) {
        if (m <= leafSize) {
            throw new AssertionError();
        }
        int level1PartCount = Math.max(2, (int) Math.ceil(leafSize / 3. + 1. / 2));
        int level1Size = level1PartCount * leafSize;
        if (m <= level1Size) {
            int parts[] = new int[(m + leafSize - 1) / leafSize];
            for (int i = 0; i < parts.length - 1; i++) {
                parts[i] = leafSize;
            }
            parts[parts.length - 1] = m - leafSize * (parts.length - 1);
            return parts;
        }
        int level2PartCount = Math.max(2, (int) Math.ceil(leafSize / 4. + 1. / 3));  
        int level2Size = level2PartCount * level1Size;
        if (m <= level2Size) {
            int parts[] = new int[(m + level1Size - 1) / level1Size];
            for (int i = 0; i < parts.length - 1; i++) {
                parts[i] = level1Size;
            }
            parts[parts.length - 1] = m - level1Size * (parts.length - 1);
            return parts;
        }
        int parts[] = new int[2];
        // TODO m / 2 ?
        parts[0] = (int) Math.ceil((m / 2.) / level2Size) * level2Size;
        parts[1] = m - parts[0];
        return parts;
    }
    
    public static void main2(String... args) {
        for (int size = 2; size < 300; size += 2) {
            double p = Probability.probabilitySplitIntoMSubsetsOfSizeN(2, size / 2);
            int[] parts = new int[] { size / 2, size / 2 };
            double p2 = approximateProbability(parts);
            System.out.println(size + " " + p + " " + p2 + " diff " + (100. / p * p2 - 100) + " " + " " + Arrays.toString(parts));
        }
        for (int size = 3; size < 300; size += 3) {
            double p = Probability.probabilitySplitIntoMSubsetsOfSizeN(3, size / 3);
            int[] parts = new int[] { size / 3, size / 3, size / 3 };
            double p2 = approximateProbability(parts);
            System.out.println(size + " " + p + " " + p2 + " diff " + (100. / p * p2 - 100) + " " + " " + Arrays.toString(parts));
        }
        for(int size = 20; size < 300; size += 10) {
            for(int first = 1; first < size / 2; first++) {
                double p = Probability.calcExactAsymmetricSplitProbability(size, first);
                int[] parts = new int[] {first, size - first};
                double p2 = approximateProbability(parts);
                System.out.println(size + " " + p + " " + p2 + " diff " + (100. / p * p2 - 100) + " " + " " + Arrays.toString(parts));
            }
        }
    }
    
    // probability to find a function that splits a set into the given parts
    static double approximateProbability(int[] parts) {
        double x = 1;
        int m = 0;
        for(int part : parts) {
            x *= part;
            m += part;
        }
        int s = parts.length;
        if (m < 1000) {
            double result = 1 / Math.pow(m, m);
            for (int i = 0; i < s; i++) {
                int sum = 0;
                for (int j = i; j < s; j++) {
                    sum += parts[j];
                }
                double c = Probability.calcCombinations(sum, parts[i]);
                result *= c;
            }
            for (int i = 0; i < s; i++) {
                double p = Math.pow(parts[i], parts[i]);
                result *= p;
            }
            double r2 = Math.sqrt(m / Math.pow(2 * Math.PI, s - 1) / x);
            if (result == 0 || Double.isNaN(result)) {
//                System.out.println("approx " + r2 + " exact? " + result);
                return r2;
            }
            return result;
        }
        return Math.sqrt(m / Math.pow(2 * Math.PI, s - 1) / x);
    }

    
    /*


        Random r = new Random(1);
        for (int size = 2; size < 1024; size++) {
//            System.out.println("size " + size);
            for (int fanout = 2; fanout < 8; fanout++) {
                if (size < fanout) {
                    continue;
                }
                int minFirstPart = (size + fanout - 1) / fanout;
                for (int firstPart = minFirstPart; firstPart * (fanout - 1) < size; firstPart++) {
                    int incorrect = 0;
                    int trials = 1;
                    long invDivMulSize = ((((long) size << 50) / firstPart) + 1) >>> 22;
                    long[] counts = new long[fanout];
//                    for(long i = 0; i < 0x100000000L; i++) {
////                        int cf_idx = (int) (((i & 0xffffffffL) * invDivMulSize) >>> 60);
//                        
//                        long hmod = reduce((int) i, size);
//                        int cf_idx = (int) hmod / firstPart;
//                        counts[cf_idx]++;
//                    }
//                    double[] prob = new double[fanout];
//                    for(int i=0; i<fanout; i++) {
//                        prob[i] = counts[i] / (double) 0x100000000L;
//                    }
//                    double[] prob2 = new double[fanout];
//                    for(int i=0; i<fanout; i++) {
//                        prob2[i] = (double) firstPart / size;
//                    }
//                    prob2[fanout - 1] = 1 - (fanout - 1) * prob2[0];
//                    System.out.println("size " + size + " fanout " + fanout + " firstPart " + firstPart);
//                    System.out.println("counts " + Arrays.toString(counts));
//                    System.out.println("prob " + Arrays.toString(prob));
//                    System.out.println("prob2 " + Arrays.toString(prob2));
//                    if (prob[0] != prob2[0]) {
//                        System.out.println("expected " + prob2[0] + " got " + prob[0]);
//                    }
//                    if (prob[fanout - 1] != prob2[fanout - 1]) {
//                        System.out.println("expected " + prob2[fanout - 1] + " got " + prob[fanout - 1]);
//                    }
                    
                    double[] prob2 = new double[fanout];
                    double[] prob = new double[fanout];
                    for (int i = 0; i < fanout - 1; i++) {
                        prob2[i] = (double) firstPart / size;
                    }
                    // prob2[fanout - 1] = 1 - (fanout - 1) * prob2[0];
                    long last = 0;
                    
                    for (int i = 1; i < fanout; i++) {
                        long min = (((long) i << 60) / invDivMulSize) & 0xffffffffL;
//                        long max = (((long) i << 60) + ((1L << 60) - 1)) / invDivMulSize;
//                        int a = reduce((int) min - 10, size) / firstPart;
//                        int b = reduce((int) min + 10, size) / firstPart;
//                        if (a == b) {
//                            System.out.println("no boundary: " + min + " a " + a + " b " + b);
//                        }
                        int aa = (int) ((((min - 1) & 0xffffffffL) * invDivMulSize) >>> 60);
                        int zero = (int) ((((min - 0) & 0xffffffffL) * invDivMulSize) >>> 60);
                        int bb = (int) ((((min + 1) & 0xffffffffL) * invDivMulSize) >>> 60);
                        if (aa == bb) {
                            System.out.println("no boundary: " + min + " aa " + aa + " bb " + bb);
                        }
                        if (zero == aa) {
                            min++;
                        }
                        prob[i - 1] = (double) (min - last) / (1L << 32);
                        last = min;
                        
//                        if (aa != a) {
//                            System.out.println("unexpected: " + aa + " expected: " + a);
//                        }
//                        if (bb != b) {
//                            System.out.println("unexpected: " + bb + " expected: " + b);
//                        }
                    }
                    if (100000000 * (prob[0] - prob2[0]) > 1) {
                        System.out.println("expected " + Arrays.toString(prob2) + " got " + Arrays.toString(prob));
                    }

                    
                    for (int i = 0; i < trials; i++) {
                        long x = r.nextLong();
                        long hmod = reduce((int) x, size);
                        int cf_idx = (int) hmod / firstPart;
//                        long invDiv = (0x100000000L / firstPart) + 1;
//                        long invDivMulSize = (invDiv * size) >>> 6;

                        // int cf_idx2 = (int) (((x & 0xffffffffL) * size / firstPart) >>> 32);
                        // int cf_idx2 = (int) (((((x & 0xffffffffL) * size) >>> 32) * invDiv) >>> 32);
                        int cf_idx2 = (int) (((x & 0xffffffffL) * invDivMulSize) >>> 60);
                        if (cf_idx2 != cf_idx) {
                            incorrect++;
                             System.out.println("     size " + size + " fanout " + fanout + 
                                     " firstPart " + firstPart + " expected " + cf_idx + " got " + cf_idx2);
                        }
                        if (cf_idx2 < 0 || cf_idx2 >= fanout) {
                            throw new AssertionError();
                        }
                    }
                    if (incorrect > 0) {
                        System.out.println("size " + size + " fanout " + fanout + 
                                " firstPart " + firstPart + " incorrect: " + incorrect
                                + " of " + trials);
                    }
                }
            }
        }

     */
    
    static class Split {
        int size;
        int[] parts;
        double evaluation;
        double generation;
        double spaceUsage;

        public String toString() {
            return String.format(Locale.ENGLISH, "size %d eval %3.2f gen %3.2f space %3.2f (bits/key: %3.2f) parts %s", size, evaluation,
                    generation, spaceUsage, spaceUsage / size, Arrays.toString(parts));
        }
    }
    
}
