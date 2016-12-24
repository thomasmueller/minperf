package org.minperf;

import static org.junit.Assert.assertTrue;

import java.lang.ref.WeakReference;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Assert;
import org.minperf.universal.LongHash;
import org.minperf.universal.UniversalHash;

/**
 * Methods to test the MPHF with random data.
 */
public class RandomizedTest {

    private static final char[] HEX = "0123456789abcdef".toCharArray();
    private static final int[] HEX_DECODE = new int['f' + 1];

    static {
        for (int i = 0; i < HEX_DECODE.length; i++) {
            HEX_DECODE[i] = -1;
        }
        for (int i = 0; i <= 9; i++) {
            HEX_DECODE[i + '0'] = i;
        }
        for (int i = 0; i <= 5; i++) {
            HEX_DECODE[i + 'a'] = HEX_DECODE[i + 'A'] = i + 10;
        }
    }

    public static void printLargeSet() {
        for (int i = 10; i <= 100_000_000; i *= 10) {
            FunctionInfo info = RandomizedTest.test(8, 128, i, false);
            System.out.println(info);
        }
    }

    public static void printTimeVersusSpace() {
        System.out.println("A Time Versus Space");
        final double evaluateWeight = 20;
        int size = 100000;
        System.out.println("size: " + size);
        ArrayList<FunctionInfo> list = new ArrayList<FunctionInfo>();
        outer:
        for (int leafSize = 2; leafSize <= 12; leafSize++) {
            int minLoadFactor = 4;
            for (int loadFactor = minLoadFactor; loadFactor <= 1024;) {
                System.out.println("leafSize " + leafSize + " " + loadFactor);
                FunctionInfo info = test(leafSize, loadFactor, size, true);
                if (info.evaluateNanos >= 10000) {
                    if (loadFactor == minLoadFactor) {
                        // done
                        break outer;
                    }
                    // next leaf size
                    break;
                }
                if (info.bitsPerKey < 4.0) {
                    list.add(info);
                }
                if (loadFactor < 16) {
                    loadFactor += 2;
                } else if (loadFactor < 32) {
                    loadFactor += 4;
                } else {
                    loadFactor *= 2;
                }
            }
        }
        Collections.sort(list, new Comparator<FunctionInfo>() {

            @Override
            public int compare(FunctionInfo o1, FunctionInfo o2) {
                double time1 = o1.evaluateNanos * evaluateWeight + o1.generateNanos;
                double time2 = o2.evaluateNanos * evaluateWeight + o2.generateNanos;
                int comp = Double.compare(time1, time2);
                if (comp == 0) {
                    comp = Double.compare(o1.bitsPerKey, o2.bitsPerKey);
                }
                return comp;
            }

        });
        FunctionInfo last = null;
        int minLoadFactor = Integer.MAX_VALUE, maxLoadFactor = 0;
        int minLeafSize = Integer.MAX_VALUE, maxLeafSize = 0;
        for (FunctionInfo info : list) {
            if (last != null && info.bitsPerKey > last.bitsPerKey) {
                continue;
            }
            System.out.println("        (" + info.bitsPerKey + ", " + info.evaluateNanos + ")");
            minLoadFactor = Math.min(minLoadFactor, info.loadFactor);
            maxLoadFactor = Math.max(maxLoadFactor, info.loadFactor);
            minLeafSize = Math.min(minLeafSize, info.leafSize);
            maxLeafSize = Math.max(maxLeafSize, info.leafSize);
            last = info;
        }
        System.out.println("for loadFactor between " + minLoadFactor + " and " + maxLoadFactor);
        System.out.println("and leafSize between " + minLeafSize + " and " + maxLeafSize);
        last = null;
        System.out.println("bits/key leafSize loadFactor evalTime genTime tableBitsPerKey");
        for (FunctionInfo info : list) {
            if (last != null && info.bitsPerKey > last.bitsPerKey) {
                continue;
            }
            System.out.println(info.bitsPerKey + " " + info.leafSize + " " + info.loadFactor +
                    " " + info.evaluateNanos + " " + info.generateNanos);
            last = info;
        }
    }

    public static void printEvaluationTimeVersusSpaceMedium() {
        System.out.println("A Evaluation Time Versus Space");
        int size = 100000;
        System.out.println("size: " + size);
        ArrayList<FunctionInfo> list = new ArrayList<FunctionInfo>();
        for (int i = 2; i < 22; i++) {
            int leafSize = (int) Math.round(0.18 * i + 6.83);
            int loadFactor = (int) Math.round(Math.pow(2, 0.3 * i + 2.79));
            // FunctionInfo info =
            test(leafSize, loadFactor, size / 10, true);
            // System.out.println("leafSize " + leafSize + " " + loadFactor + " " +
            //        info.evaluateNanos + " " + info.generateNanos + " " + info.bitsPerKey);
        }
        for (int leafSize = 8; leafSize < 14; leafSize++) {
            System.out.println("leafSize " + leafSize);
            // int leafSize = (int) Math.round(0.18 * i + 6.83);
            for (int loadFactor : new int[] { 4, 6, 8, 10, 12, 14, 16, 20, 24,
                    28, 32, 40, 48, 56, 64 }) {
                // int loadFactor = (int) Math.round(Math.pow(2, 0.3 * i + 2.79));
                test(leafSize, loadFactor, size, true);
                FunctionInfo info = test(leafSize, loadFactor, size, true);
                if (info.bitsPerKey < 2.4 && info.evaluateNanos < 250) {
                    System.out.println("leafSize " + leafSize + " loadFactor " + loadFactor +
                            " " + info.evaluateNanos + " " + info.generateNanos +
                            " " + info.bitsPerKey);
                    list.add(info);
                    break;
                }
            }
        }
        System.out.println("A Evaluation Time Versus Space");
        for (FunctionInfo info : list) {
            System.out.println("        (" + info.bitsPerKey + ", " + info.evaluateNanos + ")");
        }
        System.out.println("B Generation Time Versus Space");
        for (FunctionInfo info : list) {
            System.out.println("        (" + info.bitsPerKey + ", " + info.generateNanos + ")");
        }
    }

    public static void printEvaluationAndGenerationTimeVersusSpace() {
        System.out.println("A Evaluation Time Versus Space");
        int size = 200000;
        System.out.println("size: " + size);
        ArrayList<FunctionInfo> list = new ArrayList<FunctionInfo>();
        double[] data = {
                // Elias Fano, Java 7, 1 mio, *
                4, 20, 2.470905, 531.61, 197.398,
                4, 24, 2.41963, 568.145, 207.133,
                4, 28, 2.398395, 567.185, 225.119,
                4, 32, 2.37902, 604.32, 239.061,
                4, 64, 2.249965, 642.395, 335.572,
                4, 96, 2.21621, 715.185, 409.494,
                4, 128, 2.194625, 742.52, 488.872,
                4, 192, 2.17983, 804.17, 640.52,
                4, 256, 2.168565, 877.52, 788.277,
                5, 12, 2.458, 602.225, 158.36,
                5, 14, 2.375055, 608.48, 164.219,
                5, 16, 2.34033, 620.12, 166.763,
                5, 18, 2.295295, 630.01, 174.235,
                5, 20, 2.261475, 657.19, 166.937,
                5, 24, 2.232125, 606.675, 181.288,
                5, 28, 2.21222, 623.59, 186.955,
                5, 32, 2.172215, 672.1, 199.667,
                5, 64, 2.064325, 691.135, 260.398,
                5, 96, 2.019225, 770.985, 306.411,
                5, 128, 1.99705, 758.04, 374.651,
                5, 192, 1.98014, 885.875, 450.713,
                5, 256, 1.964155, 883.88, 569.645,
                5, 512, 1.94885, 995.81, 938.224,
                6, 12, 2.41533, 670.18, 144.191,
                6, 14, 2.32433, 754.12, 149.013,
                6, 16, 2.29281, 701.435, 155.742,
                6, 18, 2.236085, 709.57, 158.417,
                6, 20, 2.19864, 750.54, 164.125,
                6, 24, 2.143515, 753.055, 169.618,
                6, 28, 2.14354, 777.41, 173.123,
                6, 32, 2.1053, 756.74, 187.027,
                6, 64, 1.99434, 912.135, 237.705,
                6, 96, 1.9514, 886.66, 281.506,
                6, 128, 1.929665, 960.31, 346.94,
                6, 192, 1.911185, 1037.55, 397.602,
                6, 256, 1.896875, 1092.905, 484.627,
                6, 512, 1.87757, 1294.395, 826.612,
                7, 10, 2.407235, 929.085, 133.486,
                7, 12, 2.34057, 976.235, 140.682,
                7, 14, 2.25626, 1022.26, 146.484,
                7, 16, 2.194895, 1016.405, 155.009,
                7, 18, 2.147625, 1030.625, 155.313,
                7, 20, 2.139175, 1064.085, 158.7,
                7, 24, 2.075395, 1100.655, 158.711,
                7, 28, 2.05703, 1126.74, 169.578,
                7, 32, 2.040645, 1119.86, 176.318,
                7, 64, 1.92282, 1201.13, 224.148,
                7, 96, 1.885075, 1255.05, 268.332,
                7, 128, 1.861525, 1354.2, 303.348,
                7, 192, 1.841595, 1422.635, 373.644,
                7, 256, 1.832, 1457.54, 437.773,
                7, 512, 1.813535, 1659.425, 718.17,
                8, 10, 2.374405, 1524.01, 128.843,
                8, 12, 2.26062, 1619.3, 138.92,
                8, 14, 2.21941, 1682.965, 141.848,
                8, 16, 2.15729, 1702.655, 150.036,
                8, 18, 2.11754, 1745.77, 154.274,
                8, 20, 2.116515, 1790.735, 157.817,
                8, 24, 2.056035, 1875.415, 159.602,
                8, 28, 2.021495, 2049.04, 165.693,
                8, 32, 1.97279, 2253.855, 168.18,
                8, 64, 1.86501, 2519.11, 203.176,
                8, 96, 1.81827, 2678.035, 232.168,
                8, 128, 1.79172, 2743.885, 264.59,
                8, 192, 1.76805, 2862.525, 325.277,
                8, 256, 1.76191, 2964.595, 386.131,
                8, 512, 1.741125, 3186.06, 579.771,
                8, 1024, 1.734195, 3431.95, 992.839,
                9, 10, 2.33308, 2921.67, 126.492,
                9, 12, 2.220135, 3079.505, 131.824,
                9, 14, 2.14388, 3228.985, 135.728,
                9, 16, 2.12304, 3336.66, 143.909,
                9, 18, 2.079285, 3363.255, 144.216,
                9, 20, 2.053395, 3495.825, 149.323,
                9, 24, 2.029165, 3667.125, 151.586,
                9, 28, 2.00448, 3832.785, 162.268,
                9, 32, 1.96081, 4133.775, 166.158,
                9, 64, 1.83966, 4695.99, 194.566,
                9, 96, 1.7966, 4795.8, 225.375,
                9, 128, 1.771115, 5047.68, 251.695,
                9, 192, 1.750705, 5154.9, 305.649,
                9, 256, 1.74, 5394.745, 346.547,
                9, 512, 1.721885, 5566.685, 524.276,
                9, 1024, 1.70978, 5713.12, 908.291,
                10, 10, 2.26205, 5947.835, 123.408,
                10, 12, 2.19927, 6757.7, 130.215,
                10, 14, 2.11739, 6956.82, 133.934,
                10, 16, 2.063445, 7278.955, 136.91,
                10, 18, 2.04491, 7254.605, 138.898,
                10, 20, 2.01783, 7595.075, 148.459,
                10, 24, 1.99413, 8046.975, 145.846,
                10, 28, 1.97355, 8273.41, 152.016,
                10, 32, 1.94444, 8527.48, 161.704,
                10, 64, 1.786515, 14256.225, 226.411,
                10, 96, 1.75497, 14041.155, 258.34,
                10, 128, 1.73047, 14866.12, 270.534,
                10, 192, 1.70212, 16027.155, 306.938,
                10, 256, 1.68637, 16439.44, 344.142,
                10, 512, 1.664455, 17227.03, 498.827,
                10, 1024, 1.652735, 17443.67, 727.342,
                11, 10, 2.252615, 12481.5, 122.729,
                11, 12, 2.1877, 14806.225, 124.975,
                11, 14, 2.1082, 15984.395, 132.277,
                11, 16, 2.0481, 16268.345, 134.219,
                11, 18, 2.033125, 16860.275, 138.096,
                11, 20, 2.000705, 17646.71, 143.937,
                11, 24, 1.953535, 18600.325, 148.093,
                11, 28, 1.94897, 18719.355, 151.751,
                11, 32, 1.92184, 19596.005, 159.312,
                11, 64, 1.769915, 27585.715, 218.831,
                11, 96, 1.747495, 26992.02, 249.497,
                11, 128, 1.71072, 28903.025, 264.075,
                11, 192, 1.68457, 30589.52, 278.156,
                11, 256, 1.670325, 30208.16, 311.294,
                11, 512, 1.65012, 31505.045, 444.507,
                11, 1024, 1.638485, 32038.875, 688.599,
                12, 10, 2.22231, 24705.74, 165.762,
                12, 12, 2.119925, 32694.4, 126.67,
                12, 14, 2.082335, 36846.79, 130.963,
                12, 16, 2.025845, 38237.275, 139.186,
                12, 18, 1.98414, 39442.495, 159.341,
                12, 20, 1.975065, 40671.705, 192.188,
                12, 24, 1.924715, 44007.85, 192.783,
                12, 28, 1.930985, 45227.92, 207.617,
                12, 32, 1.898935, 46104.27, 203.27,
                12, 64, 1.76138, 56904.4, 213.47,
                12, 96, 1.723725, 57794.79, 222.003,
                12, 128, 1.69288, 61194.555, 243.219,
                12, 192, 1.66851, 62373.59, 280.033,
                12, 256, 1.65517, 62835.26, 304.335,
                12, 512, 1.634315, 64540.61, 440.262,
                12, 1024, 1.625505, 65182.255, 652.904,
                13, 10, 2.210815, 46968.995, 121.201,
                13, 12, 2.104695, 70740.235, 131.175,
                13, 14, 2.06871, 85441.665, 148.519,
                13, 16, 2.02016, 94314.83, 182.841,
                13, 18, 1.96975, 92865.16, 182.024,
                13, 20, 1.96359, 95121.82, 184.699,
                13, 24, 1.906365, 102406.62, 158.303,
                13, 28, 1.892015, 107833.15, 197.621,
                13, 32, 1.87882, 109796.23, 219.467,
                13, 64, 1.78156, 128046.31, 217.352,
                13, 96, 1.689325, 192932.635, 201.226,
                13, 128, 1.686015, 177628.955, 214.069,
                13, 192, 1.653795, 199516.91, 245.216,
                13, 256, 1.631885, 208383.39, 285.236,
                13, 512, 1.614825, 213491.69, 392.089,
                13, 1024, 1.60361, 215338.425, 590.956,
                14, 10, 2.20516, 83841.54, 113.454,
                14, 12, 2.09932, 145263.425, 123.554,
                14, 14, 2.066495, 185532.01, 156.049,
                14, 16, 2.01251, 212825.645, 175.047,
                14, 18, 1.97061, 222303.735, 175.905,
                14, 20, 1.93663, 226160.615, 182.177,
                14, 24, 1.897995, 231756.305, 142.045,
                14, 28, 1.88113, 248882.52, 147.99,
                14, 32, 1.867775, 262275.93, 151.812,
                14, 64, 1.77499, 291933.53, 214.264,
                14, 96, 1.691255, 385253.28, 209.619,
                14, 128, 1.67255, 367162.19, 230.257,
                14, 192, 1.64475, 398807.275, 260.316,
                14, 256, 1.63077, 400178.26, 266.605,
                14, 512, 1.609675, 420093.275, 393.8,
                14, 1024, 1.597995, 421539.69, 565.925,
                15, 10, 2.19504, 143709.415, 123.098,
                15, 12, 2.077205, 281957.6, 148.828,
                15, 14, 2.00704, 407922.98, 165.173,
                15, 16, 1.99023, 515948.185, 177.669,
                15, 18, 1.94782, 543192.28, 181.059,
                15, 20, 1.91518, 565792.075, 143.719,
                15, 24, 1.878835, 563904.165, 141.983,
                15, 28, 1.85811, 598985.05, 148.883,
                15, 32, 1.832645, 623808.285, 158.145,
                15, 64, 1.754495, 706206.44, 182.353,
                15, 96, 1.676545, 828029.2, 217.235,
                15, 128, 1.654275, 844462.385, 230.093,
                15, 192, 1.629975, 869058.975, 264.801,
                15, 256, 1.624035, 862970.86, 293.028,
                15, 512, 1.596255, 881522.125, 373.269,
                15, 1024, 1.58612, 920050.94, 537.217,
                16, 10, 2.19248, 231490.225, 148.896,
                16, 12, 2.072425, 550874.24, 129.056,
                16, 14, 1.998785, 914380.8, 126.612,
                16, 16, 1.982985, 1188278.2, 133.524,
                16, 18, 1.942255, 1325198.03, 136.67,
                16, 20, 1.913635, 1401516.895, 140.11,
                16, 24, 1.87398, 1403906.515, 142.839,
                16, 28, 1.84712, 1459527.195, 147.986,
                16, 32, 1.819705, 1566934.57, 154.035,
                16, 64, 1.74261, 1738419.015, 183.85,
                16, 96, 1.702985, 1894356.81, 229.92,
                16, 128, 1.63374, 2955705.28, 219.608,
                16, 192, 1.633705, 2692669.76, 262.579,
                16, 256, 1.605415, 3106663.265, 289.028,
                16, 512, 1.586955, 3130124.865, 360.31,
                16, 1024, 1.573265, 3225744.865, 539.198,
        };
        for (int i = 0; i < data.length; i += 5) {
            FunctionInfo info = new FunctionInfo();
            info.leafSize = (int) data[i];
            info.loadFactor = (int) data[i + 1];
            info.bitsPerKey = data[i + 2];
            info.generateNanos = data[i + 3];
            info.evaluateNanos = data[i + 4];
            list.add(info);
        }

//        int[] pairs = new int[] { 4, 20, 4, 24, 5, 20, 5, 24, 5, 64, 5, 128, 6, 128, 6, 256,
//                7, 256, 7, 512, 8, 128, 8, 256, 8, 512, 9, 256, 9, 512, 10, 12,
//                10, 256, 10, 512, 10, 1024, 11, 14, 11, 24, 11, 28, 11, 32, 11,
//                64, 11, 128, 11, 1024, 12, 32, 12, 64, 12, 128, 12, 512, 12,
//                1024, 13, 28, 13, 512, 13, 1024, 14, 28, 14, 128, 14, 256, 14,
//                1024, 15, 14, 15, 20, 15, 28, 15, 32, 15, 64, 15, 128, 15, 256,
//                15, 512, 16, 16, 16, 20, 16, 28, 16, 32, 16, 64, 16, 128, 16,
//                256, 16, 512, };
//        for (int i = 0; i < pairs.length; i += 2) {
//            int leafSize = pairs[i], loadFactor = pairs[i + 1];
//            double expectedBits = SpaceEstimator.getExpectedSpace(leafSize,
//                    loadFactor);
//            if (expectedBits > 2.6) {
//                continue;
//            }
//            FunctionInfo info = test(leafSize, loadFactor, size, true, 5, true);
//            if (info.bitsPerKey > 2.35) {
//                continue;
//            }
//            if (info.evaluateNanos > 950) {
//                continue;
//            }
//            System.out.println("  " + info.leafSize + ", " + info.loadFactor +
//                    ", " + info.bitsPerKey + ", " + info.generateNanos + ", " +
//                    info.evaluateNanos + ",");
//            list.add(info);
//        }

        int lastBucketCount = 0;
; //         for (int leafSize = 4; leafSize <= 17; leafSize++) {
        for (int leafSize = 7; leafSize <= 17; leafSize+=10) {
            for (int loadFactor : new int[] {
                    10, 12, 14, 16, 18, 20, 24, 28, 32,
                    64, 96, 128, 192, 256, 512, 1024 }) {
                int bucketCount = Settings.getBucketCount(size, loadFactor);
                if (bucketCount == lastBucketCount) {
                    continue;
                }
                lastBucketCount = bucketCount;
                double expectedBits = SpaceEstimator.getExpectedSpace(leafSize, loadFactor);
                if (expectedBits > 3.2) {
                    continue;
                }
                FunctionInfo info = test(leafSize, loadFactor, size, true, 5, true);
                if (info.bitsPerKey > 2.5) {
                    continue;
                }
                if (info.evaluateNanos >  1200) {
                    continue;
                }
                System.out.println("  " + info.leafSize + ", " +
                        info.loadFactor + ", " + info.bitsPerKey + ", " +
                        info.generateNanos + ", " + info.evaluateNanos + ",");
                list.add(info);
            }
        }

        Collections.sort(list, new Comparator<FunctionInfo>() {
            @Override
            public int compare(FunctionInfo o1, FunctionInfo o2) {
                return Double.compare(o1.bitsPerKey, o2.bitsPerKey);
            }
        });
        FunctionInfo last;
        ArrayList<FunctionInfo> evaluate = new ArrayList<FunctionInfo>();
        last = null;
        for (FunctionInfo info : list) {
            if (last == null) {
                last = info;
                continue;
            }
            if (info.evaluateNanos < last.evaluateNanos) {
                evaluate.add(info);
                last = info;
            }
        }
        last = null;
        ArrayList<FunctionInfo> generate = new ArrayList<FunctionInfo>();
        for (FunctionInfo info : list) {
            if (last == null) {
                last = info;
                continue;
            }
            if (info.generateNanos < last.generateNanos) {
                generate.add(info);
                last = info;
            }
        }
        last = null;
        ArrayList<FunctionInfo> balanced = new ArrayList<FunctionInfo>();
        for (FunctionInfo info : list) {
            // avoid favoring small evaluation time
            // 8, 10, 2.374405, 1524.01, 128.843,
            // 8, 12, 2.26062, 1619.3, 138.92,
            if (info.bitsPerKey > 2.26 && info.generateNanos > 2000) {
                continue;
            }
            if (last == null) {
                last = info;
                continue;
            }
            if (last.evaluateNanos < info.evaluateNanos) {
                continue;
            }
            if (last.generateNanos < info.generateNanos) {
                continue;
            }
            last = info;
            balanced.add(info);
        }
        HashSet<FunctionInfo> used = new HashSet<FunctionInfo>();
        System.out.println("Balanced: evaluation time");
        for (FunctionInfo info : balanced) {
            used.add(info);
            System.out.println("        (" + info.bitsPerKey + ", " + info.evaluateNanos + ")");
        }
        System.out.println("Best Generation: evaluation time");
        for (FunctionInfo info : generate) {
            used.add(info);
            System.out.println("        (" + info.bitsPerKey + ", " + info.evaluateNanos + ")");
        }
        System.out.println("Best Evaluation: evaluation time");
        for (FunctionInfo info : evaluate) {
            used.add(info);
            System.out.println("        (" + info.bitsPerKey + ", " + info.evaluateNanos + ")");
        }
        System.out.println("Balanced: generation time");
        for (FunctionInfo info : balanced) {
            System.out.println("        (" + info.bitsPerKey + ", " + info.generateNanos + ")");
        }
        System.out.println("Best Generation: generation time");
        for (FunctionInfo info : generate) {
            System.out.println("        (" + info.bitsPerKey + ", " + info.generateNanos + ")");
        }
        System.out.println("Best Evaluation: generation time");
        for (FunctionInfo info : evaluate) {
            System.out.println("        (" + info.bitsPerKey + ", " + info.generateNanos + ")");
        }
        ArrayList<FunctionInfo> usedList = new ArrayList<FunctionInfo>(used);
        Collections.sort(usedList, new Comparator<FunctionInfo>() {
            @Override
            public int compare(FunctionInfo o1, FunctionInfo o2) {
                int comp = Integer.compare(o1.leafSize, o2.leafSize);
                if (comp != 0) {
                    return comp;
                }
                return Integer.compare(o1.loadFactor, o2.loadFactor);
            }
        });
        System.out.println("All used");
        for (FunctionInfo info : usedList) {
            System.out.println(info.leafSize + ", " + info.loadFactor + ", ");
        }
    }

    public static void printGenerationTimeVersusSpace() {
        System.out.println("B Generation Time Versus Space");
        int size = 10000;
        System.out.println("size: " + size);
        ArrayList<FunctionInfo> list = new ArrayList<FunctionInfo>();
        outer:
        for (int leafSize = 2; leafSize <= 20; leafSize++) {
            int minLoadFactor = 16;
            for (int loadFactor = minLoadFactor; loadFactor < 8 * 1024; loadFactor *= 2) {
                System.out.println("leafSize " + leafSize + " " + loadFactor);
                FunctionInfo info = test(leafSize, loadFactor, size, true);
                if (info.generateNanos >= 1000000) {
                    if (loadFactor == minLoadFactor) {
                        // done
                        break outer;
                    }
                    // next leaf size
                    break;
                }
                if (info.bitsPerKey < 2.4) {
                    list.add(info);
                }
            }
        }
        Collections.sort(list, new Comparator<FunctionInfo>() {

            @Override
            public int compare(FunctionInfo o1, FunctionInfo o2) {
                int comp = Double.compare(o1.generateNanos, o2.generateNanos);
                if (comp == 0) {
                    comp = Double.compare(o1.bitsPerKey, o2.bitsPerKey);
                }
                return comp;
            }

        });
        FunctionInfo last = null;
        int minLoadFactor = Integer.MAX_VALUE, maxLoadFactor = 0;
        int minLeafSize = Integer.MAX_VALUE, maxLeafSize = 0;
        for (FunctionInfo info : list) {
            if (last != null && info.bitsPerKey > last.bitsPerKey) {
                continue;
            }
            System.out.println("        (" + info.bitsPerKey + ", " + info.generateNanos + ")");
            minLoadFactor = Math.min(minLoadFactor, info.loadFactor);
            maxLoadFactor = Math.max(maxLoadFactor, info.loadFactor);
            minLeafSize = Math.min(minLeafSize, info.leafSize);
            maxLeafSize = Math.max(maxLeafSize, info.leafSize);
            last = info;
        }
        System.out.println("for loadFactor between " + minLoadFactor + " and " + maxLoadFactor);
        System.out.println("and leafSize between " + minLeafSize + " and " + maxLeafSize);
        last = null;
        System.out.println("bits/key leafSize loadFactor evalTime genTime");
        for (FunctionInfo info : list) {
            if (last != null && info.bitsPerKey > last.bitsPerKey) {
                continue;
            }
            System.out.println(info.bitsPerKey + " " + info.leafSize + " " + info.loadFactor +
                    " " + info.evaluateNanos + " " + info.generateNanos);
            last = info;
        }
    }

    public static void runTests() {
        int[] pairs = {
                23, 828, 23, 1656, 23, 3312,
                23, 6624, 25, 1250, 25,
                3750, 25, 7500, 25, 15000 };
        for (int i = 0; i < pairs.length; i += 2) {
            int leafSize = pairs[i], size = pairs[i + 1];
            FunctionInfo info = test(leafSize, size, size, true);
            System.out.println(new Timestamp(System.currentTimeMillis()).toString());
            System.out.println(info);
        }
    }

    static void verifyParameters() {
        System.out.println("4.1 Parameters");
        // size 100000
        // CHD: generated in 1.52 seconds, 2.257 bits/key, eval 219 nanoseconds/key
        // GOV: generated in 0.32 seconds, 2.324 bits/key, eval 207 nanoseconds/key
        // size 1000000
        // CHD:
        // GOV:
        RandomizedTest.test(8, 1024, 8 * 1024, true);
        for (int i = 0; i < 5; i++) {
            if (verifyOneTest()) {
                return;
            }
            RandomizedTest.test(8, 1024, 8 * 1024, true);
        }
        Assert.fail();
    }

    static void verifyParametersBestSize() {
        // System.out.println(RandomizedTest.test(23, 828, 828, true));
        System.out.println(RandomizedTest.test(23, 1656, 1656, true));
        // System.out.println(RandomizedTest.test(23, 3312, 3312, true));
        // System.out.println(RandomizedTest.test(23, 6624, 6624, true));
        // System.out.println(RandomizedTest.test(25, 1250, 1250, true));
        // System.out.println(RandomizedTest.test(25, 3750, 3750, true));
        // System.out.println(RandomizedTest.test(25, 7500, 7500, true));
        // System.out.println(RandomizedTest.test(25, 15000, 15000, true));

        // size: 1656 leafSize: 23 loadFactor: 1656 bitsPerKey: 1.517512077294686
        // generateSeconds: 907.279643 evaluateNanosPerKey: 554.3478260869565
        // size: 1250 leafSize: 25 loadFactor: 1250 bitsPerKey: 1.5112
        // generateSeconds: 7416.210937 evaluateNanosPerKey: 312.8
    }

    private static boolean verifyOneTest() {
        int size = 100_000;
        int leafSize = 11;
        int loadFactor = 12;
        for (int j = 0; j < 5; j++) {
            System.gc();
        }
        System.out.println("  size " + size + " leafSize " + leafSize + " loadFactor " + loadFactor);
        FunctionInfo info = RandomizedTest.test(leafSize, loadFactor, size, true);
        System.out.println("  " + info.bitsPerKey + " bits/key");
        System.out.println("  " + info.generateNanos * size / 1_000_000_000 +
                " seconds to generate");
        System.out.println("  " + info.evaluateNanos +
                " nanoseconds to evaluate");
        if (info.bitsPerKey < 2.27 &&
                info.generateNanos * size / 1_000_000_000 < 0.5 &&
                info.evaluateNanos < 250) {
            // all tests passed
            return true;
        }
        return false;
    }

    public static void experimentalResults() {
        System.out.println("6 Experimental Results");
        int size = 1_000_000;
        int loadFactor = 1024;
        int bucketCount = Settings.getBucketCount(size, loadFactor);
        loadFactor = size / bucketCount;
        System.out.println("size " + size);
        System.out.println("loadFactor " + loadFactor);
        System.out.println("leafSize, bits/key");
        System.out.println("calculated");
        for (int leafSize = 2; leafSize <= 24; leafSize++) {
            double bitsPerKey = SpaceEstimator.getExpectedSpace(leafSize, loadFactor);
            System.out.println("        (" + leafSize + ", " + bitsPerKey + ")");
            // System.out.println("size: " + size);
        }
        System.out.println("experimental");
        for (int leafSize = 2; leafSize <= 20; leafSize++) {
            FunctionInfo info = test(leafSize, loadFactor, size, false);
            System.out.println("        (" + info.leafSize + ", " + info.bitsPerKey + ")");
        }
    }

    public static void reasonableParameterValues() {
        System.out.println("6.1 Reasonable Parameter Values");
        int leafSize = 10;
        int size = 16 * 1024;
        System.out.println("(leafSize=" + leafSize + ", size=" + size +
                "): loadFactor, generation time in nanos/key");
        ArrayList<FunctionInfo> infos = new ArrayList<FunctionInfo>();
        for (int loadFactor = 8; loadFactor <= 16 * 1024; loadFactor *= 2) {
            FunctionInfo info = test(leafSize, loadFactor, 16 * 1024, true);
            infos.add(info);
            System.out.println("        (" + info.loadFactor + ", " +
                    info.generateNanos + ")");
        }
        System.out
                .println("loadFactor, evaluation time in nanos/key");
        for (FunctionInfo info : infos) {
            System.out.println("        (" + info.loadFactor + ", " +
                    info.evaluateNanos + ")");
        }
        System.out
                .println("loadFactor, bits/key");
        for (FunctionInfo info : infos) {
            System.out.println("        (" + info.loadFactor + ", " +
                    info.bitsPerKey + ")");
        }
    }

    private static <T> long test(HashSet<T> set, UniversalHash<T> hash,
            byte[] description, int leafSize, int loadFactor, int measureCount) {
        BitSet known = new BitSet();
        RecSplitEvaluator<T> eval =
                RecSplitBuilder.newInstance(hash).leafSize(leafSize).loadFactor(loadFactor).
                buildEvaluator(new BitBuffer(description));
        // verify
        for (T x : set) {
            int index = eval.evaluate(x);
            if (index > set.size() || index < 0) {
                Assert.fail("wrong entry: " + x + " " + index +
                        " leafSize " + leafSize +
                        " loadFactor " + loadFactor +
                        " hash " + convertBytesToHex(description));
            }
            if (known.get(index)) {
                eval.evaluate(x);
                Assert.fail("duplicate entry: " + x + " " + index +
                        " leafSize " + leafSize +
                        " loadFactor " + loadFactor +
                        " hash " + convertBytesToHex(description));
            }
            known.set(index);
        }
        known.clear();
        known = null;
        attemptGc();
        // measure
        // Profiler prof = new Profiler().startCollecting();
        long best = Long.MAX_VALUE;
        ArrayList<T> list = new ArrayList<T>(set);
        for (int i = 0; i < measureCount; i++) {
            if (list.size() > 100000) {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
            long evaluateNanos = System.nanoTime();
            for (int j = 0; j < measureCount; j++) {
                for (T x : list) {
                    int index = eval.evaluate(x);
                    if (index > list.size() || index < 0) {
                        Assert.fail("wrong entry: " + x + " " + index +
                                " leafSize " + leafSize +
                                " loadFactor " + loadFactor +
                                " hash " + convertBytesToHex(description));
                    }
                }
            }
            evaluateNanos = System.nanoTime() - evaluateNanos;
            // System.out.println("    eval " + evaluateNanos / set.size());
            best = Math.min(best, evaluateNanos);
        }
        // System.out.println(prof.getTop(5));
        return best / measureCount;
    }

    public static int attemptGc() {
        AtomicInteger obj = new AtomicInteger();
        WeakReference<Object> ref = new WeakReference<Object>(obj);
        // some dummy operation
        int count = obj.getAndIncrement();
        obj = null;
        while (ref.get() != null) {
            System.gc();
            count++;
        }
        // System.out.println("count: " + count);
        return count;
    }

    public static FunctionInfo testAndMeasure(int leafSize, int loadFactor, int size) {
        return test(leafSize, loadFactor, size, true, 1_000_000_000 / size, false);
    }

    public static FunctionInfo test(int leafSize, int loadFactor, int size, boolean evaluate) {
        return test(leafSize, loadFactor, size, evaluate, 5, false);
    }

    public static FunctionInfo test(int leafSize, int loadFactor, int size, boolean evaluate, int measureCount, boolean singleThreadedGeneration) {
        HashSet<Long> set = createSet(size, 1);
        UniversalHash<Long> hash = new LongHash();
        long generateNanos = System.nanoTime();
        RecSplitBuilder<Long> builder = RecSplitBuilder.newInstance(hash).
                leafSize(leafSize).loadFactor(loadFactor);
        if (singleThreadedGeneration) {
            builder.parallelism(1);
        }
        BitBuffer buff;
        buff = builder.generate(set);
        int bits = buff.position();
        byte[] data = buff.toByteArray();
        generateNanos = System.nanoTime() - generateNanos;
        assertTrue(bits <= data.length * 8);
        long evaluateNanos = 0;
        if (evaluate) {
            if (size > 100000) {
                // let the CPU cool or something...
                // if this is not done, the evaluation time is much slower
                int generateSeconds = (int) (generateNanos / 1000000) / 1000;
                try {
                    Thread.sleep((5 + generateSeconds)  * 1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            evaluateNanos = test(set, hash, data, leafSize, loadFactor, measureCount);
        }
        FunctionInfo info = new FunctionInfo();
        info.leafSize = leafSize;
        info.size = size;
        info.loadFactor = loadFactor;
        info.bitsPerKey = (double) bits / size;

        if (evaluate) {
            info.evaluateNanos = (double) evaluateNanos / size;
        }
        info.generateNanos = (double) generateNanos / size;
        return info;
    }

    public static HashSet<Long> createSet(int size, int seed) {
        Random r = new Random(seed);
        HashSet<Long> set = new HashSet<Long>(size);
        while (set.size() < size) {
            set.add(r.nextLong());
        }
        return set;
    }

    /**
     * Convert a byte array to a hex encoded string.
     *
     * @param value the byte array
     * @return the hex encoded string
     */
    public static String convertBytesToHex(byte[] value) {
        int len = value.length;
        char[] buff = new char[len + len];
        char[] hex = HEX;
        for (int i = 0; i < len; i++) {
            int c = value[i] & 0xff;
            buff[i + i] = hex[c >> 4];
            buff[i + i + 1] = hex[c & 0xf];
        }
        return new String(buff);
    }

    /**
     * Convert a hex encoded string to a byte array.
     *
     * @param s the hex encoded string
     * @return the byte array
     */
    public static byte[] convertHexToBytes(String s) {
        int len = s.length();
        if (len % 2 != 0) {
            throw new IllegalArgumentException(s);
        }
        len /= 2;
        byte[] buff = new byte[len];
        int[] hex = HEX_DECODE;
        for (int i = 0; i < len; i++) {
            int d = hex[s.charAt(i + i)] << 4 | hex[s.charAt(i + i + 1)];
            buff[i] = (byte) d;
        }
        return buff;
    }

}
