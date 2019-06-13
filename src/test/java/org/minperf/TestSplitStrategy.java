package org.minperf;

import java.util.Arrays;
import java.util.Locale;

public class TestSplitStrategy {

    public static void main(String... args) {
        int leafSize = 8;
        int averageBucketSize = 128;
        int fanout = 2;
        System.out.println("Using the default split strategy ================");
        showSplitStrategy(leafSize, averageBucketSize, fanout, false);
        System.out.println();
        System.out.println("Using the \"best\" split strategy ================");
        showSplitStrategy(leafSize, averageBucketSize, fanout, true);
        System.out.println();
    }

    private static void showSplitStrategy(int leafSize, int averageBucketSize, int fanout, boolean useBest) {
        Split[] splitList = new Split[averageBucketSize * 2];
        for (int m = 0; m < averageBucketSize * 2; m++) {
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
                parts = getParts(m, leafSize, averageBucketSize, fanout);
                p = Probability.calcExactAsymmetricSplitProbability(m, parts[0]);
            }
            Split s = getSplit(splitList, m, p, parts);
            System.out.println("split " + s);
            if (m > leafSize) {
                for(int partCount = 3; partCount < m; partCount++) {
                    if (m % partCount == 0) {
                        parts = new int[partCount];
                        Arrays.fill(parts, m / partCount);
                        p = Probability.probabilitySplitIntoMSubsetsOfSizeN(partCount, m / partCount);
                        Split alt = getSplit(splitList, m, p, parts);
                        if (alt.spaceUsage < s.spaceUsage && alt.generation < s.generation && alt.evaluation < s.evaluation) {
                            System.out.println("  found alternative multi-way " + alt);
                            if (useBest) {
                                s = alt;
                            }
                        }
                    }
                }
                for(int firstPart = 1; firstPart < m / 2; firstPart++) {
                    parts = new int[] {firstPart, m - firstPart};
                    p = Probability.calcExactAsymmetricSplitProbability(m, parts[0]);
                    Split alt = getSplit(splitList, m, p, parts);
                    if (alt.spaceUsage < s.spaceUsage && alt.generation < s.generation && alt.evaluation < s.evaluation) {
                        System.out.println("  found alternative asymetric " + alt);
                        if (useBest) {
                            s = alt;
                        }
                    }
                }
            }
            splitList[m] = s;
        }
    }

    private static Split getSplit(Split[] splitList, int m, double p, int[] parts) {
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

    private static int[] getParts(int m, int leafSize, int averageBucketSize, int fanout) {
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
