package org.minperf.utils;

import java.util.Random;

/**
 * A PRNG that returns unique (distinct) 64-bit entries in somewhat sorted
 * order. Not fully sorted, for speed, but the largest entry of a block is
 * guaranteed to be smaller of the smallest entry of the next block. The set
 * doesn't need to be kept fully in memory.
 */
public class RandomSetGenerator {

    public static void main(String... args) {

        long size = 1_000_000_000_000L;
        System.out.println(size * 150 / 1000000 / 1000 / 60 / 60. + " h");
        // sorted: with 24 ns / key, it would take 6 h to generate
        // not fully sorted: with 4 ns / key, it would take 1 h to generate

        System.out.println((double) Long.MAX_VALUE + " max long");
        long[] data = new long[2_000_000];
        Random r = new Random(1);
        RandomBlockProducer it = randomHashProducer(r, size);
        long len = 0;
        long start = System.nanoTime();
        long maxLastBlock = -1;
        for (long remaining = size; remaining >= 0;) {
            int produced = it.produce(data, 0, data.length, 0);
            len += produced;
            if (produced == 0) {
                break;
            }
            long minBlock = Long.MAX_VALUE, maxBlock = -1;
            for (int i = 0; i < produced; i++) {
                long x = data[i];
                minBlock = Math.min(minBlock, x);
                maxBlock = Math.max(maxBlock, x);
            }
            if (minBlock > maxBlock) {
                throw new AssertionError();
            }
            if (minBlock < maxLastBlock) {
                throw new AssertionError();
            }
            maxLastBlock = maxBlock;
            System.out.println("produced " + produced);
            remaining -= produced;
            long time = System.nanoTime() - start;
            System.out.println(size + " " + (double) time / len + " ns/key len " + len);
        }
    }

    public static RandomBlockProducer randomHashProducer(Random r, long size) {
        return randomProducer(r, size, 63);
    }

    private static RandomBlockProducer randomProducer(final Random r, final long size, final int shift) {
        if (shift <= 44) {
            if (shift != 44) {
                throw new IllegalArgumentException();
            }
            return new RandomBlockProducer() {
                long remaining = size;

                @Override
                public int produce(long[] data, int offset, int len, long add) {
                    if (len < size) {
                        return 0;
                    }
                    for (int i = 0; i < size; i++) {
                        data[i + offset] = hash44(i + offset + add + 1) + add;
                        // break;
                    }
                    // Arrays.parallelSort(data, offset, offset + (int) size);
                    // Arrays.sort(data, offset, offset + (int) size);
                    remaining = 0;
                    return (int) size;
                }

                @Override
                public long remaining() {
                    return remaining;
                }
            };
        }

        return new RandomBlockProducer() {
            long remaining = size, zeros = randomHalf(r, size);
            long bitMask;
            RandomBlockProducer child = randomProducer(r, zeros, shift - 1);

            @Override
            public int produce(long[] data, int offset, int len, long add) {
                int produced = 0;
                while (true) {
                    if (child.remaining() == 0) {
                        if (bitMask != 0) {
                            return produced;
                        }
                        bitMask = 1L << shift;
                        child = randomProducer(r, size - zeros, shift - 1);
                    }
                    int p = child.produce(data, offset, len, bitMask + add);
                    if (p == 0) {
                        return produced;
                    }
                    produced += p;
                    offset += p;
                    len -= p;
                    remaining -= p;
                }
            }

            @Override
            public long remaining() {
                return remaining;
            }
        };
    }

    /**
     * Get the number of entries that are supposed to be below the half,
     * according to the probability theory. For example, for a number of coin
     * flips, how many are heads.
     *
     * @param r the random generator
     * @param samples the total number of entries
     * @return the number of entries that should be used for one half
     */
    static long randomHalf(Random r, long samples) {
        long low = 0, high = samples;
        double x = r.nextDouble();
        while (low + 1 < high) {
            long mid = (low + high) / 2;
            double p = probabilityBucketAtMost(samples, mid);
            if (x > p) {
                low = mid;
            } else {
                high = mid;
            }
        }
        return (low + high) / 2;
    }

    static double probabilityBucketAtMost(long flips, long heads) {
        // https://www.fourmilab.ch/rpkp/experiments/statistics.html
        long x = heads;
        long n = flips;
        double variance = Math.sqrt(n / 4);
        // mean
        long mu = n / 2;
        // https://en.wikipedia.org/wiki/Normal_distribution
        // Numerical approximations for the normal CDF
        // the probability that the value of a standard normal random variable X
        // is <= x
        return phi((x - mu) / variance);
    }

    static double phi(double x) {
        return 0.5 * (1 + Math.signum(x) * Math.sqrt(1 - Math.exp(-2 * x * x / Math.PI)));
    }

    public static long hash64(long x) {
        x = (x ^ (x >>> 30)) * 0xbf58476d1ce4e5b9L;
        x = (x ^ (x >>> 27)) * 0x94d049bb133111ebL;
        x = x ^ (x >>> 31);
        return x;
    }

    public static long hash44(long x) {
        x = (x ^ (x >>> 20)) * 0xbf58476d1ce4e5b9L;
        x &= (1L << 44) - 1;
        x = (x ^ (x >>> 17)) * 0x94d049bb133111ebL;
        x &= (1L << 44) - 1;
        x = x ^ (x >>> 21);
        x &= (1L << 44) - 1;
        return x;
    }

    public static long hash32(long x) {
        x = (x ^ (x >>> 16)) * 0xbf58476d1ce4e5b9L;
        x &= 0xffffffffL;
        x = (x ^ (x >>> 16)) * 0x94d049bb133111ebL;
        x &= 0xffffffffL;
        x = x ^ (x >>> 16);
        return x & 0xffffffffL;
    }

    public static long hash16(long x) {
        x = (x ^ (x >>> 7)) * 0xbf58476d1ce4e5b9L;
        x &= 0xffff;
        x = (x ^ (x >>> 5)) * 0x94d049bb133111ebL;
        x &= 0xffff;
        x = x ^ (x >>> 9);
        return x & 0xffff;
    }

    public interface RandomBlockProducer {

        int produce(long[] data, int offset, int len, long add);

        long remaining();
    }

}
