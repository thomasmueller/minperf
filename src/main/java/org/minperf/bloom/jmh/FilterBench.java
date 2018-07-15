package org.minperf.bloom.jmh;

import java.util.Arrays;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import org.minperf.bloom.Filter;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
public class FilterBench {

    @State(Scope.Benchmark)
    public static class StateHolder {

//        @Param({ "1000000", "10000000", "100000000" })
        @Param({ "100000000" })
        int n;

//        @Param({ "BLOOM", "CUCKOO16_4", "CUCKOO8_4", "XOR16", "XOR8", "CUCKOO", "XOR" })
        @Param({ "BLOOM", "CUCKOO8_4", "XOR8" })
        Filter.Type type;

//        @Param({ "8", "16" })
        @Param({ "8" })
        int bits;

        @Param({ "0", "1" })
        int cleanL3Cache = 0;

        Filter filter;
        final int Ntest = 1000000;
        long[] inSetKeys = new long[Ntest];
        long[] notInSetKeys = new long[Ntest];

        long[] l3cacheKillerArray = new long[2 * 1024 * 1024];

        @Setup(Level.Iteration)
        public void cleanL3Cache() {
            if (cleanL3Cache == 0) {
                return;
            }
            if (cleanL3Cache == 0) {
                return;
            }
            System.out.println("Cleaning L3 Cache");
            for (int test = 0; test < 10; test++) {
                int len = l3cacheKillerArray.length;
                createRandomUniqueListFast(l3cacheKillerArray, test);
                Arrays.sort(l3cacheKillerArray);
                int sum = 0;
                for (int i = 0; i < len; i++) {
                    sum += l3cacheKillerArray[i];
                }
                if (sum == 0) {
                    throw new AssertionError();
                }
            }
        }

        public static void createRandomUniqueListFast(long[] list, int seed) {
            int len = list.length;
            for (int i = 0; i < len; i++) {
                list[i] = hash64(seed + i);
            }
        }

        public static long hash64(long x) {
            x = (x ^ (x >>> 30)) * 0xbf58476d1ce4e5b9L;
            x = (x ^ (x >>> 27)) * 0x94d049bb133111ebL;
            x = x ^ (x >>> 31);
            return x;
        }

        @Setup(Level.Trial)
        public void populate() {
            ThreadLocalRandom r = ThreadLocalRandom.current();
            System.out.println("Generating large random array");
            long[] keys = new long[n];
            for (int i = 0; i < n; i++) {
                keys[i] = r.nextLong();
            }
            filter = type.construct(keys, bits);
            System.out.println("Generating test random array");
            for (int i = 0; i < Ntest; i++) {
                inSetKeys[i] = keys[i];
                notInSetKeys[i] = -keys[i];
            }
            System.out.println("Sleep 5s");
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

    }

    @Benchmark
    public int notInSet(StateHolder s) {
        int sum = 0;
        if (s.bits == 8) {
            for (int k = 0; k < s.Ntest; k++) {
                if (s.filter.mayContain(s.notInSetKeys[k]))
                    sum++;
            }
        }
        return sum;
    }

    @Benchmark
    public void inSet(StateHolder s) {
        if (s.bits == 8) {
            for (int k = 0; k < s.Ntest; k++) {
                if (!s.filter.mayContain(s.inSetKeys[k])) {
                    throw new AssertionError();
                }
            }
        }
    }

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder().include(FilterBench.class.getSimpleName()).warmupIterations(5)
                .measurementIterations(5).forks(1).build();
        new Runner(opt).run();
    }

}
