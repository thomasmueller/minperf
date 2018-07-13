/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.minperf.utils;

/**
 * Poisson distribution. This code is based on the
 * implementation at Apache Commons Math 4.
 *
 * See http://en.wikipedia.org/wiki/Poisson_distribution,
 * http://mathworld.wolfram.com/PoissonDistribution.html
 */
public class PoissonDistribution {

    private static final double PI = 105414357.0 / 33554432.0 + 1.984187159361080883e-9;

    private static final double TWO_PI = 2 * PoissonDistribution.PI;

    // Exact Stirling expansion error for certain values.
    private static final double[] EXACT_STIRLING_ERRORS = { 0.0,
            0.1534264097200273452913848, 0.0810614667953272582196702,
            0.0548141210519176538961390, 0.0413406959554092940938221,
            0.03316287351993628748511048, 0.02767792568499833914878929,
            0.02374616365629749597132920, 0.02079067210376509311152277,
            0.01848845053267318523077934, 0.01664469118982119216319487,
            0.01513497322191737887351255, 0.01387612882307074799874573,
            0.01281046524292022692424986, 0.01189670994589177009505572,
            0.01110455975820691732662991, 0.010411265261972096497478567,
            0.009799416126158803298389475, 0.009255462182712732917728637,
            0.008768700134139385462952823, 0.008330563433362871256469318,
            0.007934114564314020547248100, 0.007573675487951840794972024,
            0.007244554301320383179543912, 0.006942840107209529865664152,
            0.006665247032707682442354394, 0.006408994188004207068439631,
            0.006171712263039457647532867, 0.005951370112758847735624416,
            0.005746216513010115682023589, 0.005554733551962801371038690 };

    public static double probability(double p, int x) {
        double logProbability = logProbability(p, x);
        if (logProbability == Double.NEGATIVE_INFINITY) {
            return 0;
        }
        return Math.exp(logProbability);
    }

    private static double logProbability(double p, int x) {
        if (x < 0 || x == Integer.MAX_VALUE) {
            return Double.NEGATIVE_INFINITY;
        } else if (x == 0) {
            return -p;
        }
        return -getStirlingError(x) - getDeviancePart(x, p) - 0.5 *
                    Math.log(PoissonDistribution.TWO_PI) - 0.5 * Math.log(x);
    }

    /**
     * Compute the error of Stirling's series at the given value.
     * <p>
     * Reference: Eric W. Weisstein. "Stirling's Series." From MathWorld - A
     * Wolfram Web Resource. http://mathworld.wolfram.com/StirlingsSeries.html
     *
     * @param z the value.
     * @return the Striling's series error.
     */
    private static double getStirlingError(double z) {
        if (z < 15.0) {
            double z2 = 2.0 * z;
            if (Math.floor(z2) == z2) {
                return EXACT_STIRLING_ERRORS[(int) z2];
            }
            throw new IllegalArgumentException("Unsupported z value " + z);
        }
        double z2 = z * z;
        return (0.083333333333333333333 -
                (0.00277777777777777777778 -
                (0.00079365079365079365079365 -
                (0.000595238095238095238095238 -
                0.0008417508417508417508417508 / z2) /
                z2) /
                z2) /
                z2) /
                z;
    }

    /**
     * A part of the deviance portion of the saddle point approximation.
     * <p>
     * Reference: Catherine Loader (2000). "Fast and Accurate Computation of
     * Binomial Probabilities." http://www.herine.net/stat/papers/dbinom.pdf
     *
     * @param x the x value.
     * @param mu the average.
     * @return a part of the deviance.
     */
    private static double getDeviancePart(double x, double mu) {
        if (Math.abs(x - mu) < 0.1 * (x + mu)) {
            double d = x - mu;
            double v = d / (x + mu);
            double s1 = v * d;
            double s = Double.NaN;
            double ej = 2.0 * x * v;
            v *= v;
            int j = 1;
            while (s1 != s) {
                s = s1;
                ej *= v;
                s1 = s + ej / ((j * 2) + 1);
                ++j;
            }
            return s1;
        }
        if (x == 0) {
            return mu;
        }
        return x * Math.log(x / mu) + mu - x;
    }

}