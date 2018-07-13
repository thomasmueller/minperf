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
public class JmhBench {

    @State(Scope.Benchmark)
    public static class StateHolder {

        @Param({ "100000", "1000000", "10000000" })
        int N;

        @Param({ "8", "16", "32" })
        int bits;

        XorFilter xor;
        XorFilter_8bit xor8;
        XorFilter_16bit xor16;
        BloomFilter bloom;
        CuckooFilter cuckoo;
        CuckooFilter_8bit_4entries cuckoo8_4;
        CuckooFilter_16bit_4entries cuckoo16_4;
        final int Ntest = 10000;
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
            System.out.println("Constructing Xor filters");
            xor = XorFilter.construct(keys, bits);
            xor8 = XorFilter_8bit.construct(keys);
            xor16 = XorFilter_16bit.construct(keys);
            System.out.println("Constructing Bloom filter");
            bloom = BloomFilter.construct(keys, bits);
            System.out.println("Constructing Cuckoo filters");
            if (bits < 16) {
                // reading 64 bits at once is not supported by the BitBuffer
                cuckoo = CuckooFilter.construct(keys, bits);
            }
            cuckoo8_4 = CuckooFilter_8bit_4entries.construct(keys);
            cuckoo16_4 = CuckooFilter_16bit_4entries.construct(keys);
            System.out.println("Generating test random array");
            for (int i = 0; i < Ntest; i++) {
                testkeys[i] = r.nextLong();
            }

        }

    }

    @Benchmark
    public int bloomtest(StateHolder s) {
        int sum = 0;
        for (int k = 0; k < s.Ntest; k++)
            if (s.bloom.mayContain(s.testkeys[k]))
                sum++;
        return sum;
    }

    @Benchmark
    public int xortest(StateHolder s) {
        int sum = 0;
        for (int k = 0; k < s.Ntest; k++)
            if (s.xor.mayContain(s.testkeys[k]))
                sum++;
        return sum;
    }

   @Benchmark
    public int xortest_8_16(StateHolder s) {
        int sum = 0;
        if (s.bits == 8) {
            for (int k = 0; k < s.Ntest; k++) {
                if (s.xor8.mayContain(s.testkeys[k]))
                    sum++;
            }
        } else if (s.bits == 16) {
            for (int k = 0; k < s.Ntest; k++) {
                if (s.xor16.mayContain(s.testkeys[k]))
                    sum++;
            }
        }
        return sum;
    }

    @Benchmark
    public int xortestfast(StateHolder s) {
        int sum = 0;
        if (s.bits == 32) {
            for (int k = 0; k < s.Ntest; k++) {
                if (s.xor.mayContain32(s.testkeys[k]))
                    sum++;
            }
        } else if (s.bits == 16) {
            for (int k = 0; k < s.Ntest; k++) {
                if (s.xor.mayContain16(s.testkeys[k]))
                    sum++;
            }
        } else if (s.bits == 8) {
            for (int k = 0; k < s.Ntest; k++) {
                if (s.xor.mayContain8(s.testkeys[k]))
                    sum++;
            }
        } else {
            // "unsupported "
        }
        return sum;
    }

    @Benchmark
    public int cuckootest(StateHolder s) {
        int sum = 0;
        if (s.bits < 16) {
            // reading 64 bits at once is not supported by the BitBuffer
                for (int k = 0; k < s.Ntest; k++)
                    if (s.cuckoo.mayContain(s.testkeys[k]))
                        sum++;
        }
        return sum;
    }

    @Benchmark
    public int cuckootestatonce(StateHolder s) {
        int sum = 0;
        if (s.bits < 16) {
            // reading 64 bits at once is not supported by the BitBuffer
                for (int k = 0; k < s.Ntest; k++)
                    if (s.cuckoo.mayContainAtOnce(s.testkeys[k]))
                        sum++;
        }
        return sum;
    }

    @Benchmark
    public int cuckoo_8_16(StateHolder s) {
        int sum = 0;
        if (s.bits == 8) {
            for (int k = 0; k < s.Ntest; k++) {
                if (s.cuckoo8_4.mayContain(s.testkeys[k]))
                    sum++;
            }
        } else if (s.bits == 16) {
            for (int k = 0; k < s.Ntest; k++) {
                if (s.cuckoo16_4.mayContain(s.testkeys[k]))
                    sum++;
            }
        }
        return sum;
    }

    @Benchmark
    public int cuckoo_8_16_atOnce(StateHolder s) {
        int sum = 0;
        if (s.bits == 8) {
            for (int k = 0; k < s.Ntest; k++) {
                if (s.cuckoo8_4.mayContainAtOnce(s.testkeys[k]))
                    sum++;
            }
        } else if (s.bits == 16) {
            for (int k = 0; k < s.Ntest; k++) {
                if (s.cuckoo16_4.mayContainAtOnce(s.testkeys[k]))
                    sum++;
            }
        }
        return sum;
    }

    @Benchmark
    public int cuckoo_8_16_reallyAtOnce(StateHolder s) {
        int sum = 0;
        if (s.bits == 8) {
            for (int k = 0; k < s.Ntest; k++) {
                if (s.cuckoo8_4.mayContainReallyAtOnce(s.testkeys[k]))
                    sum++;
            }
        } else if (s.bits == 16) {
            for (int k = 0; k < s.Ntest; k++) {
                if (s.cuckoo16_4.mayContainReallyAtOnce(s.testkeys[k]))
                    sum++;
            }
        }
        return sum;
    }

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder().include(JmhBench.class.getSimpleName()).warmupIterations(5)
                .measurementIterations(5).forks(1).build();

        new Runner(opt).run();
    }

}
