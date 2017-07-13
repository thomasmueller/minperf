package org.minperf.utils;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Random;

/**
 * A PRNG that returns unique (distinct) entries in somewhat sorted order (not
 * fully sorted, for speed). The set doesn't need to be kept fully in memory.
 * Also, with more than 100'000 entries, this is faster that generating the set
 * fully in memory (due to not having to keep the whole set in memory, to check
 * for duplicates).
 */
public class RandomSetGenerator {

    public static void main(String... args) {
        Random r = new Random();
        for(long size = 1000; size > 0; size *= 10) {
            for(int limit = 10; limit < size; limit*=10) {
                long time = System.nanoTime();
                Iterator<Long> it = randomSequence(r, size, 64, limit);
                while(it.hasNext()) {
                    it.next();
                }
                time = System.nanoTime() - time;
                System.out.println(size + " " + time / size + " ns/key limit " + limit);
            }
        }
//        Iterator<Long> it = randomSequence(r, 10, 32, 100);
//        while(it.hasNext()) {
//            System.out.println(it.next());
//        }

    }

    public static Iterable<Long> randomSequence(final long size) {
        return new Iterable<Long>() {
            @Override
            public Iterator<Long> iterator() {
                return randomSequence(new Random(size), size, 64, 10000);
            }
        };
    }

    /**
     * Random sequence generator.
     *
     * @param r the random generator
     * @param size the number of entries to generate
     * @param shift the number of bits of the result
     * @return the iterator
     */
    static Iterator<Long> randomSequence(final Random r, final long size, final int shift, final int limit) {
        if (size < limit) {
            // small lists are generated using a regular hash set
            HashSet<Long> set = new HashSet<Long>((int) size);
            // this would ensure the list is fully sorted
            // TreeSet<Long> set = new TreeSet<Long>();
            if (shift == 64) {
                while (set.size() < size) {
                    set.add(r.nextLong());
                }
            } else {
                while (set.size() < size) {
                    set.add(r.nextLong() & ((2L << shift) - 1));
                }
            }
            return set.iterator();
        }
        // large lists are created recursively
        return new Iterator<Long>() {
            long remaining = size, zeros = randomHalf(r, size);
            Iterator<Long> lowBits0 = randomSequence(r, zeros, shift - 1, limit);
            Iterator<Long> lowBits1;
            @Override
            public boolean hasNext() {
                return remaining > 0;
            }
            @Override
            public Long next() {
                remaining--;
                if (lowBits0 != null) {
                    if (lowBits0.hasNext()) {
                        return lowBits0.next();
                    }
                    lowBits0 = null;
                }
                if (lowBits1 == null) {
                    lowBits1 = randomSequence(r, size - zeros, shift - 1, limit);
                }
                return (1L << shift) + lowBits1.next();
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
        double variance = Math.sqrt(n/4);
        // mean
        long mu = n / 2;
        // https://en.wikipedia.org/wiki/Normal_distribution
        // Numerical approximations for the normal CDF
        // the probability that the value of a standard normal random variable X is <= x
        return phi((x - mu) / variance);
    }

    static double phi(double x) {
        return 0.5 * (1 + Math.signum(x) * Math.sqrt(1 - Math.exp(-2 * x * x / Math.PI)));
    }


//  for(long x : randomSet(10, 0)) {
//      System.out.println(x);
//  }
//  for (int i=0; i<10; i++) {
//      System.out.println(randomNumbersBelowZero(r, 1024L * 1024 * 1024));
//  }
//  double sum = 0;
//  for(int i=0; i <= 128; i++) {
//      double p = probabilityBucketEqual(128, i);
//      double p2 = probabilityBucketEqual2(128, i);
//      double sum2 = probabilityBucketAtMost(128, i);
//      System.out.println(p + sum + " " + sum2);
//      sum += p;
//  }

//    static Iterable<Long> randomSet(int size, int seed) {
//        Random r = new Random(seed);
//        TreeSet<Long> set = new TreeSet<Long>();
//        while (set.size() < size) {
//            set.add(r.nextLong());
//        }
//        return set;
//    }

//    static int randomNumbersBelowZero(Random r, int samples) {
//        int low = 0, high = 2 * samples;
//        for(int i=0; i<10; i++) {
//            int mid = (low + high) / 2;
//            double p = probabilityLargeBucket(samples, mid);
//            if (r.nextDouble() < p) {
//                low = mid;
//            } else {
//                high = mid;
//            }
//        }
//        return (low + high) / 4;
//    }

//    static double getNormalTailProbability(int samples, int aboveZero) {
////        int a = averageBucketSize;
////        int x = bucketSize;
//        // p, x
////        return PoissonDistribution.probability(a, x);
//    }

//    public static double probabilityLargeBucket(int lambda, int x) {
//        return Probability.probabilityLargeBucket2(lambda, x);
//        // lambda is expected bucket size
//        // x is bucket size
//        // result is probability that a bucket gets that large
//        // Poisson distribution, tail probability
//        // return Math.exp(-lambda) * Math.pow(Math.E * lambda, x) / Math.pow(x, x);
//    }

//    static double probabilityBucketEqual(int flips, int heads) {
//        // https://www.fourmilab.ch/rpkp/experiments/statistics.html
//        int x = heads;
//        int n = flips;
//        double variance = Math.sqrt(n/4);
//        // mean
//        int mu = n / 2;
//        double exp = (x - mu) * (x - mu) / (2 * variance * variance);
//        return 1/(Math.sqrt(2 * Math.PI) * variance) * Math.exp(-(exp));
//    }
//
//    static double probabilityBucketEqual2(int flips, int heads) {
//        // https://www.fourmilab.ch/rpkp/experiments/statistics.html
//        int x = heads;
//        int n = flips;
//        double variance = Math.sqrt(n/4);
//        // mean
//        int mu = n / 2;
//        return generalNormal(x, mu, variance);
//    }

//    // probability density,
//    static double standardNormal(double x) {
//        return Math.exp(-(0.5*x*x)) / Math.sqrt(2*Math.PI);
//    }
//
//    static double generalNormal(int x, double mu, double variance) {
//        return (1/variance) * standardNormal((x-mu) / variance);
//    }
//
//    static double phiApprox2(double x) {
//        // https://en.wikipedia.org/wiki/Normal_distribution
//        double sum=x;
//        double value=x;
//        for(int i=1; i<=100; i++) {
//            value=(value*x*x/(2*i+1));
//            sum=sum+value;
//        }
//        return 0.5+(sum/Math.sqrt(2*Math.PI))*Math.exp(-(x*x)/2);
//    }

}
