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
public class JmhBenchCuckoo16_4 {

    @State(Scope.Benchmark)
    public static class StateHolder {

        @Param({ "100000", "1000000", "10000000", "20000000", "40000000", "60000000", "680000000", "100000000" })
        int N;

        @Param({ "16" })
        int bits;

        CuckooFilter_16bit_4entries cuckoo16_4;
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
            cuckoo16_4 = CuckooFilter_16bit_4entries.construct(keys);
            System.out.println("Generating test random array");
            for (int i = 0; i < Ntest; i++) {
                testkeys[i] = r.nextLong();
            }

        }

    }

    @Benchmark
    public int cuckoo_16(StateHolder s) {
        int sum = 0;
        if (s.bits == 16) {
            for (int k = 0; k < s.Ntest; k++) {
                if (s.cuckoo16_4.mayContain(s.testkeys[k]))
                    sum++;
            }
        }
        return sum;
    }

    @Benchmark
    public int cuckoo_16_atOnce(StateHolder s) {
        int sum = 0;
        if (s.bits == 16) {
            for (int k = 0; k < s.Ntest; k++) {
                if (s.cuckoo16_4.mayContainAtOnce(s.testkeys[k]))
                    sum++;
            }
        }
        return sum;
    }

    @Benchmark
    public int cuckoo_16_reallyAtOnce(StateHolder s) {
        int sum = 0;
        if (s.bits == 16) {
            for (int k = 0; k < s.Ntest; k++) {
                if (s.cuckoo16_4.mayContainReallyAtOnce(s.testkeys[k]))
                    sum++;
            }
        }
        return sum;
    }

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder().include(JmhBenchCuckoo16_4.class.getSimpleName()).warmupIterations(5)
                .measurementIterations(5).forks(1).build();

        new Runner(opt).run();
    }

}
