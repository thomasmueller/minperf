package org.minperf.hem.recsplit;

import java.util.BitSet;

import org.minperf.BitBuffer;
import org.minperf.BitCodes;
import org.minperf.Probability;
import org.minperf.hem.RandomGenerator;

public class TestFast {

    public static void main(String... args) {
        test();
        System.out.println();
        test();
    }

    static void test() {
        for (int leafSize = 4; leafSize <= 6; leafSize++) {
            for (int averageBucketSize = 4; averageBucketSize <= 32; averageBucketSize *= 2) {
                int[] sizes = new int[64];
                for (int size = 2; size <= leafSize; size++) {
                    double p = Probability.probabilitySplitIntoMSubsetsOfSizeN(size, 1);
                    int k = BitCodes.calcBestGolombRiceShift(p);
                    sizes[size] = k;
                }
                for (int size = leafSize + 1; size < 64; size++) {
                    double p = Probability.probabilitySplitIntoMSubsetsOfSizeN(2, size / 2);
                    int k = BitCodes.calcBestGolombRiceShift(p);
                    sizes[size] = k;
                }
                for (int len = 1048576; len <= 1048576; len *= 4) {
                    long time;
                    time = System.nanoTime();
                    long[] list = new long[len];
                    RandomGenerator.createRandomUniqueListFast(list, len);
                    time = System.nanoTime() - time;
                    // System.out.println("create " + time / len);
                    Builder builder = new Builder().leafSize(leafSize).averageBucketSize(averageBucketSize);
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    time = System.nanoTime();
                    BitBuffer buff = builder.generate(list);
                    time = System.nanoTime() - time;
                    System.out.print("leafSize " + leafSize + " avg " + averageBucketSize + " bits/key "
                            + (double) buff.position() / len + " gen " + time / len);
                    buff.seek(0);
                    FastEvaluator eval = builder.evaluator(buff);
                    BitSet s = new BitSet();
                    // TODO document: result is somewhat ascending parallel to
                    // hash function - this might be good for some use cases (locality)
                    long sum = 0;
                    time = System.nanoTime();
                    for (long x : list) {
                        int y = eval.evaluate(x);
                        sum += y;
                    }
                    time = System.nanoTime() - time;
                    System.out.println(" eval " + time / list.length + " dummy " + sum);
                    for (long x : list) {
                        int y = eval.evaluate(x);
                        if (s.get(y) || y >= len) {
                            throw new AssertionError("y=" + y + " len=" + len + " " + eval.evaluate(x));
                        }
                        s.set(y);
                    }
                }
            }
        }
    }

}
