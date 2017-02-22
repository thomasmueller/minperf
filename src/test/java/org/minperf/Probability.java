package org.minperf;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.Random;

import org.minperf.utils.PoissonDistribution;

/**
 * Probability methods.
 */
public class Probability {

    private static final HashMap<Long, BigInteger> FACTORIALS = new HashMap<Long, BigInteger>();
    private static final HashMap<String, Double> PROBABILITY_CACHE = new HashMap<String, Double>();

    public static void veryLargeBucketProbability() {
        for (int averageBucketSize = 8; averageBucketSize < 16 * 1024; averageBucketSize *= 2) {
            for (int multiple = 2; multiple <= 20; multiple *= 10) {
                double p = probabilityLargeBucket(averageBucketSize, multiple * averageBucketSize);
                double p2 = probabilityLargeBucket2(averageBucketSize, multiple * averageBucketSize);
                double simulated = Double.NaN;
                if (p > 0.000001) {
                    simulated = simulateProbabilityBucketLargerOrEqualTo(averageBucketSize, multiple * averageBucketSize);
                }
                System.out.println("averageBucketSize " + averageBucketSize +
                        " p[bucketSize >= " + multiple * averageBucketSize + "] ~ " +
                        p2 + "; <= " + p + "; simulated " + simulated);
            }
        }
    }

    private static double probabilityLargeBucket2(int averageBucketSize, int atLeast) {
        double p = 1.0;
        if (averageBucketSize > 128) {
            return Double.NaN;
        }
        for (int size = 0; size < atLeast; size++) {
            p -= getProbabilityOfBucketSize(averageBucketSize, size);
        }
        return p;
    }

    public static void simulateKeyInOverflow() {
        int size = 1000000;
        int averageBucketSize = 3;
        int maxLoad = 5;
        System.out.println("size " + size);
        // calculated
        double pBalls = 0;
        double pTooLarge = 1;
        for (int i = 0; i <= maxLoad; i++) {
            double p = getProbabilityOfBucketSize(averageBucketSize, i);
            System.out.println("bucket size " + i + ": p=" + p);
            pBalls += i * p;
            pTooLarge -= p;
        }
        System.out.println("average expected number of balls in regular buckets: " + pBalls);
        System.out.println("probability of bucket too large: " + pTooLarge);
        System.out.println("probability of ball in overflow: " + (1. - (pBalls / averageBucketSize)));
        // simulated
        Random r = new Random();
        int buckets = size / averageBucketSize;
        int[] counts = new int[buckets];
        for (int i = 0; i < size; i++) {
            counts[r.nextInt(buckets)]++;
        }
        int ballsInOverflow = 0;
        int bucketsOverflow = 0;
        int totalInRegularBuckets = 0;
        for (int i = 0; i < buckets; i++) {
            int c = counts[i];
            if (c > maxLoad) {
                bucketsOverflow++;
                ballsInOverflow += c;
                counts[i] = 0;
            } else {
                totalInRegularBuckets += c;
            }
        }
        System.out.println("simulated balls in overflow: " + ballsInOverflow);
        System.out.println("simulated balls in regular buckets: " + totalInRegularBuckets);
        System.out.println("simulated average expected number of balls in regular buckets: " + (double) totalInRegularBuckets / buckets);
        System.out.println("simulated probability of bucket too large: " + (double) bucketsOverflow / buckets);
        System.out.println("simulated probability of ball in overflow: " + (double) ballsInOverflow / size);
    }

    private static double simulateProbabilityBucketLargerOrEqualTo(int lambda, int x) {
        int count = 100000000;
        Random r = new Random(x);
        int larger = 0;
        int testCount = count / 1000;
        int loop = count / testCount;
        int bucketCount = loop / lambda;
        for (int j = 0; j < testCount; j++) {
            int c = 0;
            for (int i = 0; i < loop; i++) {
                if (r.nextInt(bucketCount) == 0) {
                    c++;
                }
            }
            if (c >= x) {
                larger++;
            }
        }
        return (double) larger / testCount;
    }

    private static double probabilityLargeBucket(int lambda, int x) {
        // Poisson distribution, tail probability
        return Math.exp(-lambda) * Math.pow(Math.E * lambda, x) / Math.pow(x, x);
    }

    public static double getProbabilityOfBucketSize(int averageBucketSize, int bucketSize) {
        int a = averageBucketSize;
        int x = bucketSize;
        return PoissonDistribution.probability(a, x);
    }

    public static double getProbabilityOfBucketFallsIntoBinOfSize(int averageBucketSize, int bucketSize) {
        int a = averageBucketSize;
        int x = bucketSize;
        double average = bucketSize * PoissonDistribution.probability(a, x);
        return average / averageBucketSize;
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

    public static void asymmetricSplitProbability() {
        Random r = new Random(1);
        for (int size = 20; size < 1000000; size *= 10) {
            for (int i = 1; i < size / 2; i += Math.max(1, size / 20)) {
                System.out.println("size " + size + " first " + i + " p approx " +
                        simulateAsymmetricSplitProbability(size, i, r) + " calc " + calcAsymmetricSplitProbability(size, i));
            }
        }
    }

    public static double calcExactAsymmetricSplitProbability(int size, int firstSet) {
        double p;
        if (size <= 143) {
            p = Probability.calcAsymmetricSplitProbability(size, firstSet);
            if (p == 0 || p == 1 || Double.isNaN(p)) {
                System.out.println("fail at " + size + " split " + firstSet);
            }
        } else {
            p = 0;
        }
        if (p == 0 || p == 1 || Double.isNaN(p)) {
            p = Probability.calcApproxAsymmetricSplitProbability(size, firstSet);
        }
        return p;
    }

    private static double calcApproxAsymmetricSplitProbability(int size, int firstSet) {
        // http://math.stackexchange.com/questions/64716/approximating-the-logarithm-of-the-binomial-coefficient
        int n = size;
        int k = firstSet;
        double logComb = (n + .5) * Math.log(n) - (k + .5) * Math.log(k) -
                (n - k + .5) * Math.log(n - k) - .5 * Math.log(2 * Math.PI);
        return Math.exp(logComb + Math.log(k) * k - Math.log(n) * n + Math.log(n - k) * (n - k));
    }

    private static double calcAsymmetricSplitProbability(int size, int firstSet) {
        // http://math.stackexchange.com/questions/951236/probability-of-exactly-2-low-rolls-in-5-throws-of-a-die
        // (n k) p^k q^(n-k)
        // p: probability of the event (prob of r.nextInt(size) < firstSet)
        // q=1−p
        // n: number of trials (size)
        // k: the number of times the event occurs during those n trials (firstSet)
        // Here, the probability that the outcome of a roll is less than 33 is 1/3
        int n = size;
        int k = firstSet;
        // double p = (double) firstSet / size;
        // double q = 1 - p;
        // (n k) =  (n!) / ((k! * (n-k)!))
        // ((n!) / (k! * (n-k)!)) * ((k/n)^k) * ((1-(k/n))^(n-k))
        // (k^k)*(n^-n)*n!*(n-k)^(n-k) / (k!*(n-k)!)
        // return calcCombinations(n, k) * Math.pow(p, k) * Math.pow(q, n - k);
        // (k/n)^k * (1-(k/n))^(n - k)  =  (k^k) / (n^n) * (n-k)^(n-k)
        return calcCombinations(n, k) * Math.pow(k, k) / Math.pow(n, n) *
                Math.pow(n - k, n - k);
    }

    private static double simulateAsymmetricSplitProbability(int size, int firstSet, Random r) {
        double good = 0;
        int trials = Math.max(1, 100000000 / size);
        for (int j = 0; j < trials; j++) {
            int count = 0;
            boolean success = true;
            for (int i = 0; i < size; i++) {
                if (r.nextInt(size) < firstSet) {
                    count++;
                    if (count > firstSet) {
                        success = false;
                        break;
                    }
                }
            }
            if (success) {
                if (count == firstSet) {
                    good++;
                }
            }
        }
        return good / trials;
    }

    public static double calcCombinations(int n, int k) {
        String key = "comb-" + n + "/" + k;
        Double cached = PROBABILITY_CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        BigInteger nf = factorial(n);
        BigInteger kf = factorial(k);
        BigInteger nmkf = factorial(n - k);
        BigInteger u = kf.multiply(nmkf);
        BigDecimal r = new BigDecimal(nf).divide(
                new BigDecimal(u), 30, BigDecimal.ROUND_HALF_UP);
        // System.out.println("nCk n=" + n + " k=" + k + " = " + r.doubleValue());

        // approximation:
        // log(n k) ~ (n log n) - (k log k) - (n-k) log(n-k)
        double result = r.doubleValue();
        PROBABILITY_CACHE.put(key, result);
        return result;
    }

    public static double probabilitySplitIntoMSubsetsOfSizeN(int m, int n) {
        String key = "split-" + m + "/" + n;
        Double cached = PROBABILITY_CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        BigInteger mm = BigInteger.valueOf(m);
        BigInteger mnf = factorial(n * m);
        BigInteger nf = factorial(n);
        BigInteger u = nf.pow(m).multiply(mm.pow(m * n));
        BigDecimal r = new BigDecimal(mnf).divide(
                new BigDecimal(u), 100, BigDecimal.ROUND_HALF_UP);
        double result = r.doubleValue();
        PROBABILITY_CACHE.put(key, result);
        return result;
    }

    static BigInteger factorial(long n) {
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
