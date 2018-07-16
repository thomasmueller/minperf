package org.minperf.bloom;


import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.concurrent.*;

@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
public class JmhBenchXor16 {

    @State(Scope.Benchmark)
    public static class StateHolder {

        @Param({ "100000", "1000000", "2000000", "4000000", "6000000", "8000000", "10000000", "20000000", "40000000", "60000000", "80000000", "100000000" })
        int N;

        @Param({ "16" })
        int bits;

        XorFilter_16bit xor16;
        final int Ntest = 1000000;
        long[] testkeys = new long[Ntest];

        @Setup(Level.Trial)
        public void setup() {
            populate();
        }

        public void populate() {
            ThreadLocalRandom r = ThreadLocalRandom.current();
            System.out.println("Generating large random array");
            long[] keys = new long[N];
            for (int i = 0; i < N; i++) {
                keys[i] = r.nextLong();
            }
            xor16 = XorFilter_16bit.construct(keys);
            System.out.println("Generating test random array");
            for (int i = 0; i < Ntest; i++) {
                testkeys[i] = r.nextLong();
            }

        }

    }

   @Benchmark
   public int xortest_16_batch(StateHolder s) {
        int sum = 0;
        if (s.bits == 16) {
            final int batchsize = 128;
            final long[] buffer = new long[batchsize];
            final int[] tmp = new int[4 * batchsize];
            int k = 0;
            for (; k+batchsize <= s.Ntest; k+= batchsize) {
                sum+= (s.xor16.mayContainBatch(s.testkeys, k, batchsize,buffer, tmp));
            }
            sum+= (s.xor16.mayContainBatch(s.testkeys, k, s.Ntest % batchsize, buffer, tmp));
        }
       return sum;
   } 

   @Benchmark
    public int xortest_16(StateHolder s) {
        int sum = 0;
        if (s.bits == 16) {
            for (int k = 0; k < s.Ntest; k++) {
                if (s.xor16.mayContain(s.testkeys[k]))
                    sum++;
            }
        }
        return sum;
    }

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder().include(JmhBenchXor16.class.getSimpleName()).warmupIterations(5)
                .measurementIterations(5).forks(1).build();

        new Runner(opt).run();
    }

}
