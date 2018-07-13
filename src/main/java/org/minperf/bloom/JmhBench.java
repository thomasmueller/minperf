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
		BloomFilter bloom;
		CuckooFilter cuckoo;
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
			System.out.println("Constructing Xor Filter");
			xor = (XorFilter) XorFilter.construct(keys, bits);
			System.out.println("Constructing Bloom Filter");
			bloom = (BloomFilter) BloomFilter.construct(keys, bits);
			System.out.println("Constructing cuckoo Filter");
			cuckoo = (CuckooFilter) CuckooFilter.construct(keys, bits);
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
	public int xortestfast(StateHolder s) {
		int sum = 0;
                if(s.bits == 32) {
		  for (int k = 0; k < s.Ntest; k++) {
			if (s.xor.mayContain32(s.testkeys[k])) sum++;
                  }
                } else if (s.bits == 16) {
       		  for (int k = 0; k < s.Ntest; k++) {
			if (s.xor.mayContain16(s.testkeys[k])) sum++;
                  }
                } else if (s.bits == 8) {
       		  for (int k = 0; k < s.Ntest; k++) {
			if (s.xor.mayContain8(s.testkeys[k])) sum++;
                  }
                } else {
                  // "unsupported "
                }
		return sum;
	}

	@Benchmark
	public int cuckootest(StateHolder s) {
		int sum = 0;
		for (int k = 0; k < s.Ntest; k++)
			if (s.cuckoo.mayContain(s.testkeys[k]))
				sum++;
		return sum;
	}

	@Benchmark
	public int cuckootestatonce(StateHolder s) {
		int sum = 0;
		for (int k = 0; k < s.Ntest; k++)
			if (s.cuckoo.mayContainAtOnce(s.testkeys[k]))
				sum++;
		return sum;
	}

	public static void main(String[] args) throws RunnerException {
		Options opt = new OptionsBuilder().include(JmhBench.class.getSimpleName()).warmupIterations(5)
				.measurementIterations(5).forks(1).build();

		new Runner(opt).run();
	}

}
