package org.minperf.hybrid;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.BitSet;
import java.util.HashSet;

import org.junit.Test;
import org.minperf.BitBuffer;
import org.minperf.RandomizedTest;
import org.minperf.RecSplitBuilder;
import org.minperf.RecSplitEvaluator;
import org.minperf.Settings;
import org.minperf.generator.ConcurrencyTool;
import org.minperf.generator.Generator;
import org.minperf.universal.LongHash;
import org.minperf.universal.UniversalHash;

/**
 * Tests the hybrid algorithm.
 */
public class HybridTest {

    public static void main(String... args) {
        for (int size = 10000; size < 10000000; size *= 10) {
            test(size);
        }
    }

    @Test
    public void test() {
        test(10000);
    }

    private static void test(int size) {
        HashSet<Long> set = RandomizedTest.createSet(size, 1);
        UniversalHash<Long> hash = new LongHash();

        int leafSize = 10;

        for (int averageBucketSize = 32; averageBucketSize >= 8; averageBucketSize -= 4) {

            Settings settings = new Settings(leafSize, averageBucketSize);
            Generator<Long> generator;

            ConcurrencyTool pool = new ConcurrencyTool(8);
            generator = new Generator<Long>(pool, hash, settings, true,
                    Integer.MAX_VALUE);
            BitBuffer buffer0 = generator.generate(set);
            int bitCount0 = buffer0.position();

            BitBuffer buffer2 = RecSplitBuilder.newInstance(hash)
                    .leafSize(leafSize).averageBucketSize(averageBucketSize).generate(set);
            int bitCount2 = buffer2.position();

            System.out.println("size " + size + " averageBucketSize " + averageBucketSize +
                    " hybrid " + (double) bitCount0 / size +
                    " old " + +(double) bitCount2 /
                    size);

            buffer2.seek(0);
            RecSplitEvaluator<Long> evaluatorOld = RecSplitBuilder.newInstance(hash)
                    .leafSize(leafSize).averageBucketSize(averageBucketSize).buildEvaluator(buffer2);
            long time = System.nanoTime();
            int sum = 0;
            for (int i = 0; i < 10; i++) {
                for (long x : set) {
                    sum += evaluatorOld.evaluate(x);
                }
            }
            time = System.nanoTime() - time;
            System.out.println(time / 10 / size + " ns Old dummy " + sum);

            buffer0.seek(0);
            RecSplitEvaluator<Long> evaluator = new RecSplitEvaluator<Long>(buffer0, hash, settings, true);

            BitSet test = new BitSet();
            for (long x : set) {
                int i = evaluator.evaluate(x);
                assertTrue(i >= 0 && i < size);
                assertFalse(test.get(i));
                test.set(i);
            }

            time = System.nanoTime();
            sum = 0;
            for (int i = 0; i < 10; i++) {
                for (long x : set) {
                    sum += evaluator.evaluate(x);
                }
            }
            time = System.nanoTime() - time;
            System.out.println(time / 10 / size + " ns Hybrid dummy " + sum);

        }

    }

}
