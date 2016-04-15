package org.minperf;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.Random;

// import org.apache.commons.math3.distribution.PoissonDistribution;

/**
 * Probability methods.
 */
public class Probability {
    
    private static final HashMap<Long, BigInteger> FACTORIALS = new HashMap<Long, BigInteger>();

    public static void bucketTooLarge() {
        long shaN = 100_000_000_000L, shaBits = 160;
        double pSha1Collision = ((double) shaN * (shaN - 1) / 2) * (1.0 / Math.pow(2, shaBits));
        System.out.println("SHA1 hash collision with " + shaN + " entries " + pSha1Collision);
        long uuidN = 100_000_000_000L, uuidBits = 122;
        double pUuidCollision = ((double) uuidN * (uuidN - 1) / 2) * (1.0 / Math.pow(2, uuidBits));
        System.out.println("UUID hash collision with " + uuidN + " entries " + pUuidCollision);
        // http://math.stackexchange.com/questions/167441/probability-that-no-side-of-a-dice-is-rolled-more-than-k-times?rq=1
        // 6.5 Probability Calculations in Hashing
        // page 251
        // "the probability that the maximum list length is t, is at most ne^t/t^t."
        System.out.println("4.7 Probabilities");
        System.out.println("Bucket Too Large");
/*        
        double minP = Math.pow(10, -15);
        for (int loadFactor = 1; loadFactor < 16 * 1024; loadFactor *= 2) {
            int l = 1;
            for (;; l++) {
                double p = poissonLarger(loadFactor, l);
                if (p <= 0.0) {
                    break;
                }
            }
            double loadMax = (double) l / loadFactor;
            System.out.println("loadFactor " + loadFactor + " limit " + l + " maxLoad " + loadMax);
            // double n = Math.pow(10, 7);
            // double max = loadFactor + Math.sqrt(2 * loadFactor * Math.log(n/loadFactor));
            // System.out.println("... " + max + " " + (max / loadFactor));
        }
        System.out.println("Estimated Max Load with probability < 10 ^ -15");
        for (int loadFactor = 1; loadFactor < 16 * 1024; loadFactor *= 2) {
            int l = 1;
            for (;; l++) {
                double p = poissonLarger(loadFactor, l);
                if (p <= minP) {
                    break;
                }
            }
            double loadMax = (double) l / loadFactor;
            System.out.println("        (" + loadFactor + ", " + loadMax + ")");
        }
*/
        int size = (int) (1 / Math.pow(10, -7));
        System.out.println("Tested Max Load with probability < 10 ^ -7, size=" + size);
        for (int loadFactor = 1; loadFactor < 16 * 1024; loadFactor *= 2) {
            int buckets = size / loadFactor;
            int len = testBallsIntoBins(size, buckets);
            if (len < 0) {
                continue;
            }
            double load = (double) len / loadFactor;
            System.out.println("        (" + loadFactor + ", " + load + ")");
        }
    }
    
    private static int testBallsIntoBins(int mBalls, int nBins) {
        int tests = 10000000 / mBalls;
        if (tests < 1) {
            return -1;
        }
        Random r = new Random(mBalls * nBins);
        // r = new SecureRandom();
        int max = 0;
        for (int i = 0; i < tests; i++) {
            int[] counts = new int[nBins];
            for (int j = 0; j < mBalls; j++) {
                max = Math.max(max, ++counts[r.nextInt(nBins)]);
            }
        }
        return max;
    }

    public static void asymmetricCase() {
        System.out.println("4.7 Probabilities");
        System.out.println("Asymmetric Split");
        int size = 64, step = 2;
        for (int i = 0; i <= size; i += step) {
            double n = size, k = i;
            double p = calcCombinations((int) n, (int) k) * Math.pow(k / n, k) *
                    Math.pow(1 - (k / n), n - k);
            System.out.println("        (" + i + ", " + p + ")");
        }
    }

/*    
    private static double poissonLarger(int averageNumberOfEventsPerInterval,
            int eventsInIntervalOrMore) {
        // Wikipedia: Bounds for the tail probabilities of a Poisson random variable  X 
        // \sim \operatorname{Pois}(\lambda) can be derived 
        // using a Chernoff bound argument
        int a = averageNumberOfEventsPerInterval;
        int x = eventsInIntervalOrMore;
        PoissonDistribution p = new PoissonDistribution(a);
        return 1 - p.cumulativeProbability(x);
        // return Math.exp(-a) * Math.pow(Math.E * a, x) / Math.pow(x, x);
    }
*/

    static double poisson(int averageNumberOfEventsPerInterval,
            int eventsInInterval) {
        int a = averageNumberOfEventsPerInterval;
        int k = eventsInInterval;
        return Math.pow(a, k) * Math.exp(-a) / factorial(k).doubleValue();
    }
    
    /**
     * Probability, with a certain bucket count, that the bucket is of size
     * bucketSize, if size entries were added.
     * 
     * @param bucketCount the bucket count
     * @param size the number of entries
     * @param bucketSize the bucket size
     * @return the probability (0..1)
     */
    static double calcProbabilityOfBucketSize(int bucketCount, int size, int bucketSize) {
        return estProb(1.0 / bucketCount, size, bucketSize);
    }
    
    static double estProbabilityOfBucketSize(int bucketCount, int size, int bucketSize) {
        // Gonnet, page 10, separate chaining
        double k = bucketSize;
        double n = size;
        double m = bucketCount;
        double a = n / m;
        return Math.exp(-a) * Math.pow(a, k) /
                factorial((long) k).doubleValue();
    }
    
    // Binomial Probability Formula
    private static double estProb(double prob, int trials, int successes) {
        // Probability formula for Bernoulli trials. 
        // The probability of achieving exactly a number of successes in a number of trials.
        double comb = Probability.calcCombinations(trials, successes);
        return comb *
                Math.pow(prob, successes) * Math.pow(1. - prob, trials - successes);
    }
    
    static double calcCombinations(int n, int k) {
        BigInteger nf = factorial(n); 
        BigInteger kf = factorial(k);
        BigInteger nmkf = factorial(n - k);
        BigInteger u = kf.multiply(nmkf);
        BigDecimal r = new BigDecimal(nf).divide(
                new BigDecimal(u), 30, BigDecimal.ROUND_HALF_UP);
        // System.out.println("nCk n=" + n + " k=" + k + " = " + r.doubleValue());
        return r.doubleValue();
    }

    static double probabilitySplitIntoMSubsetsOfSizeN(int m, int n) {
        BigInteger mm = BigInteger.valueOf(m);
        BigInteger mnf = factorial(n * m); 
        BigInteger nf = factorial(n); 
        BigInteger u = nf.pow(m).multiply(mm.pow(m * n));
        BigDecimal r = new BigDecimal(mnf).divide(
                new BigDecimal(u), 100, BigDecimal.ROUND_HALF_UP);
        return r.doubleValue();
    }
    
    private static BigInteger factorial(long n) {
        BigInteger f = FACTORIALS.get(n);
        if (f == null) {
            f = recursiveFactorial(1, n);
            FACTORIALS.put(null, f);
        }
        return f;
    }

    private static BigInteger recursiveFactorial(long start, long n) {
        long i;
        if (n <= 16) {
            BigInteger r = BigInteger.valueOf(start);
            for (i = start + 1; i < start + n; i++) {
                r = r.multiply(BigInteger.valueOf(i));
            }
            return r;
        }
        i = n / 2;
        return recursiveFactorial(start, i).multiply(recursiveFactorial(start + i, n - i));
    }

}
