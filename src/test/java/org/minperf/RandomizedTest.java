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
                // Fast, Java 7, size: 200000
//                4, 64, 2.27419, 778.6, 307.316,
//                4, 128, 2.195835, 885.565, 482.31,
//                4, 256, 2.165375, 1055.19, 763.062,
//                5, 24, 2.31892, 664.97, 164.947,
//                5, 28, 2.22579, 693.73, 171.278,
//                5, 32, 2.25182, 755.86, 184.255,
//                5, 64, 2.08905, 808.92, 250.001,
//                5, 128, 2.00241, 921.73, 354.392,
//                5, 256, 1.964985, 1001.02, 558.922,
//                5, 512, 1.94725, 1170.005, 944.865,
//                6, 20, 2.2949, 767.77, 141.877,
//                6, 24, 2.25512, 834.87, 151.536,
//                6, 28, 2.19755, 861.44, 156.714,
//                6, 32, 2.188905, 869.455, 171.955,
//                6, 64, 2.01865, 972.0, 225.83,
//                6, 128, 1.944125, 1146.26, 318.341,
//                6, 256, 1.894245, 1285.74, 493.98,
//                6, 512, 1.8771, 1522.0, 809.176,
//                7, 18, 2.305775, 1095.14, 134.15,
//                7, 20, 2.243545, 1039.31, 135.167,
//                7, 24, 2.19695, 1158.995, 142.597,
//                7, 28, 2.0936, 1180.975, 148.928,
//                7, 32, 2.12722, 1166.805, 158.11,
//                7, 64, 1.93509, 1382.785, 205.764,
//                7, 128, 1.871295, 1482.175, 286.669,
//                7, 256, 1.829345, 1606.245, 443.838,
//                7, 512, 1.814075, 1863.39, 705.998,
//                8, 18, 2.2832, 1718.115, 130.083,
//                8, 20, 2.222345, 1817.665, 138.416,
//                8, 24, 2.1774, 1924.075, 141.243,
//                8, 28, 2.065145, 2082.66, 146.393,
//                8, 32, 2.041685, 2252.59, 151.319,
//                8, 64, 1.890055, 2648.195, 186.079,
//                8, 128, 1.801095, 2878.17, 256.589,
//                8, 256, 1.758265, 3132.75, 369.078,
//                8, 512, 1.739195, 3365.57, 600.079,
//                9, 16, 2.32751, 3261.395, 123.958,
//                9, 18, 2.251685, 3446.55, 125.452,
//                9, 20, 2.19271, 3555.77, 128.904,
//                9, 24, 2.15392, 3584.24, 138.666,
//                9, 28, 2.050315, 3839.905, 143.091,
//                9, 32, 2.03405, 4046.185, 148.978,
//                9, 64, 1.855765, 4630.665, 173.275,
//                9, 128, 1.780735, 5066.625, 235.15,
//                9, 256, 1.736275, 5342.215, 334.625,
//                9, 512, 1.71725, 5588.415, 551.528,
//                9, 1024, 1.707475, 5917.695, 911.768,
//                10, 16, 2.30249, 6834.455, 121.839,
//                10, 18, 2.22542, 7226.88, 125.981,
//                10, 20, 2.163075, 7422.405, 124.496,
//                10, 24, 2.119195, 7573.26, 130.912,
//                10, 28, 2.02163, 7768.64, 139.264,
//                10, 32, 2.016585, 8005.495, 145.568,
//                10, 64, 1.80469, 13962.91, 195.55,
//                10, 128, 1.730455, 14746.825, 246.268,
//                10, 256, 1.685825, 16349.83, 327.97,
//                10, 512, 1.66406, 16969.985, 480.98,
//                10, 1024, 1.653575, 17717.34, 787.538,
//                11, 16, 2.296095, 15477.915, 115.14,
//                11, 18, 2.21236, 15960.525, 118.261,
//                11, 20, 2.148475, 16816.78, 122.025,
//                11, 24, 2.104505, 17669.34, 127.377,
//                11, 28, 2.000715, 18417.305, 136.732,
//                11, 32, 1.99284, 18265.385, 142.31,
//                11, 64, 1.79298, 26846.23, 189.014,
//                11, 128, 1.718975, 28162.27, 232.878,
//                11, 256, 1.67244, 29308.335, 302.21,
//                11, 512, 1.648465, 30868.185, 424.75,
//                11, 1024, 1.636845, 31028.665, 723.775,
//                12, 16, 2.26985, 36517.745, 115.147,
//                12, 18, 2.1958, 37624.675, 118.879,
//                12, 20, 2.129875, 38215.0, 122.063,
//                12, 24, 2.082085, 41801.7, 130.872,
//                12, 28, 1.982975, 42886.78, 136.141,
//                12, 32, 1.977645, 43986.17, 138.478,
//                12, 64, 1.78157, 54856.0, 190.817,
//                12, 128, 1.696575, 58695.535, 221.377,
//                12, 256, 1.651005, 60863.675, 278.214,
//                12, 512, 1.635525, 61838.58, 417.163,
//                12, 1024, 1.625365, 64454.995, 645.018,
//                13, 16, 2.26359, 88168.23, 151.304,
//                13, 18, 2.18776, 90745.31, 156.263,
//                13, 20, 2.12022, 91760.91, 153.637,
//                13, 24, 2.067875, 98426.815, 167.131,
//                13, 28, 1.965565, 102854.68, 175.108,
//                13, 32, 1.95817, 106446.41, 171.828,
//                13, 64, 1.80079, 121692.06, 196.342,
//                13, 128, 1.694955, 182149.655, 218.559,
//                13, 256, 1.633985, 216306.365, 262.449,
//                13, 512, 1.612275, 217597.165, 385.598,
//                13, 1024, 1.601895, 223354.595, 603.371,
//                14, 16, 2.264195, 219217.125, 112.087,
//                14, 18, 2.178925, 219299.295, 115.182,
//                14, 20, 2.119225, 226210.96, 118.58,
//                14, 24, 2.061215, 231452.83, 126.787,
//                14, 28, 1.95807, 249337.575, 130.421,
//                14, 32, 1.949995, 258869.13, 136.907,
//                14, 64, 1.79181, 292125.525, 205.401,
//                14, 128, 1.67713, 378901.79, 213.395,
//                14, 256, 1.630835, 413901.725, 271.904,
//                14, 512, 1.608025, 421628.205, 373.357,
//                14, 1024, 1.597195, 428590.635, 576.876,
//                15, 16, 2.25542, 509064.85, 118.313,
//                15, 18, 2.114345, 546334.795, 119.39,
//                15, 20, 2.09974, 555092.25, 123.253,
//                15, 24, 2.046805, 562496.78, 127.561,
//                15, 28, 1.9392, 596951.47, 135.161,
//                15, 32, 1.9315, 643960.975, 139.145,
//                15, 64, 1.77616, 703989.325, 195.228,
//                15, 128, 1.660115, 857272.98, 214.021,
//                15, 256, 1.623185, 888644.86, 256.49,
//                15, 512, 1.59643, 900149.925, 386.217,
//                15, 1024, 1.583435, 913539.885, 565.493,
//                16, 14, 2.348885, 872761.595, 131.945,
//                16, 16, 2.24228, 1177469.3, 147.293,
//                16, 18, 2.161245, 1337700.935, 159.886,
//                16, 20, 2.103195, 1405272.635, 165.953,
//                16, 24, 2.04239, 1426919.32, 151.669,
//                16, 28, 1.9296, 1438883.07, 136.552,
//                16, 32, 1.9198, 1527077.625, 138.947,
//                16, 64, 1.763955, 1781939.015, 168.812,
//                16, 128, 1.64776, 3045737.68, 210.772,
//                16, 256, 1.606695, 3211395.1, 260.656,
//                16, 512, 1.58468, 3213090.91, 369.565,
//                16, 1024, 1.570285, 3352102.195, 526.211,

                // Elias-Fano, Java 7, size: 200000
                4, 64, 2.25532, 796.485, 361.232,
                4, 128, 2.20063, 951.03, 519.915,
                4, 256, 2.165265, 1074.745, 872.284,
                5, 16, 2.342615, 642.56, 170.048,
                5, 18, 2.294745, 675.325, 170.371,
                5, 20, 2.264425, 677.74, 177.737,
                5, 24, 2.256545, 735.365, 187.533,
                5, 28, 2.212615, 720.835, 196.28,
                5, 32, 2.17572, 716.7, 207.171,
                5, 64, 2.06986, 775.195, 271.794,
                5, 128, 2.00192, 946.94, 385.422,
                5, 256, 1.96425, 1019.495, 585.607,
                6, 14, 2.330065, 727.17, 160.775,
                6, 16, 2.29019, 787.465, 166.043,
                6, 18, 2.236865, 799.24, 167.177,
                6, 20, 2.19716, 810.49, 169.014,
                6, 24, 2.165795, 806.645, 180.802,
                6, 28, 2.144795, 832.145, 179.131,
                6, 32, 2.108155, 865.595, 195.976,
                6, 64, 1.998845, 946.625, 247.72,
                6, 128, 1.939795, 1079.5, 337.539,
                6, 256, 1.897665, 1276.1, 507.856,
                6, 512, 1.8764, 1496.68, 828.747,
                7, 12, 2.342695, 989.995, 150.88,
                7, 14, 2.26174, 1026.46, 154.042,
                7, 16, 2.19675, 1055.175, 158.755,
                7, 18, 2.14949, 1128.555, 167.702,
                7, 20, 2.1394, 1116.51, 165.304,
                7, 24, 2.10026, 1149.83, 174.206,
                7, 28, 2.05182, 1267.23, 181.182,
                7, 32, 2.043115, 1267.06, 187.192,
                7, 64, 1.93151, 1346.34, 238.004,
                7, 128, 1.869545, 1536.23, 326.387,
                7, 256, 1.83283, 1691.875, 481.86,
                7, 512, 1.813055, 1834.63, 751.032,
                8, 12, 2.264635, 1666.045, 146.493,
                8, 14, 2.225565, 1754.41, 155.364,
                8, 16, 2.158865, 1751.665, 157.743,
                8, 18, 2.120285, 1943.55, 165.848,
                8, 20, 2.11564, 1854.435, 167.578,
                8, 24, 2.07913, 1920.505, 174.146,
                8, 28, 2.02041, 2241.975, 179.554,
                8, 32, 1.968485, 2275.46, 181.162,
                8, 64, 1.870505, 2769.41, 217.924,
                8, 128, 1.79805, 2917.715, 293.195,
                8, 256, 1.759115, 3208.365, 406.19,
                8, 512, 1.739745, 3406.505, 663.52,
                9, 12, 2.226595, 3085.055, 140.871,
                9, 14, 2.14925, 3398.19, 150.614,
                9, 16, 2.12472, 3455.66, 156.303,
                9, 18, 2.081975, 3703.15, 160.581,
                9, 20, 2.054705, 3550.755, 162.883,
                9, 24, 2.052445, 3722.03, 183.311,
                9, 28, 2.003985, 3841.445, 171.861,
                9, 32, 1.964115, 4159.79, 182.341,
                9, 64, 1.839755, 4655.775, 207.641,
                9, 128, 1.77676, 5209.47, 260.55,
                9, 256, 1.737295, 5286.18, 354.119,
                9, 512, 1.71782, 5419.12, 565.104,
                9, 1024, 1.70989, 5835.565, 890.625,
                10, 12, 2.203145, 6328.135, 135.364,
                10, 14, 2.123075, 6688.9, 142.855,
                10, 16, 2.061175, 6887.325, 145.685,
                10, 18, 2.04899, 7220.245, 149.235,
                10, 20, 2.01662, 7484.71, 155.753,
                10, 24, 1.99237, 7674.11, 163.396,
                10, 28, 1.971455, 7717.73, 163.641,
                10, 32, 1.94392, 8065.42, 169.561,
                10, 64, 1.78863, 14015.505, 221.498,
                10, 128, 1.73417, 14774.15, 265.781,
                10, 256, 1.686285, 16314.24, 358.273,
                10, 512, 1.66461, 17171.19, 498.821,
                10, 1024, 1.654935, 17541.795, 813.42,
                11, 12, 2.19011, 13994.02, 133.851,
                11, 14, 2.114615, 15119.03, 140.005,
                11, 16, 2.05158, 15334.955, 145.029,
                11, 18, 2.032725, 15792.18, 145.671,
                11, 20, 1.9985, 16581.01, 149.339,
                11, 24, 1.974165, 17180.405, 157.051,
                11, 28, 1.947975, 18040.43, 163.855,
                11, 32, 1.91779, 18138.545, 169.172,
                11, 64, 1.77501, 26540.73, 212.76,
                11, 128, 1.71564, 28383.825, 256.399,
                11, 256, 1.672645, 29204.12, 331.131,
                11, 512, 1.64874, 30530.245, 436.101,
                11, 1024, 1.63825, 30925.825, 733.711,
                12, 12, 2.125085, 31211.115, 170.024,
                12, 14, 2.089735, 34724.46, 141.437,
                12, 16, 2.025175, 36465.27, 142.883,
                12, 18, 1.98237, 37436.665, 146.256,
                12, 20, 1.975095, 38085.755, 149.73,
                12, 24, 1.94599, 41042.08, 154.851,
                12, 28, 1.92801, 42482.245, 158.902,
                12, 32, 1.900715, 43236.445, 164.278,
                12, 64, 1.76328, 53917.56, 212.277,
                12, 128, 1.696135, 57732.435, 238.328,
                12, 256, 1.65419, 59807.28, 283.255,
                12, 512, 1.635005, 60740.035, 427.114,
                12, 1024, 1.627535, 63912.395, 676.007,
                13, 12, 2.1104, 68504.79, 146.634,
                13, 14, 2.077675, 82738.795, 158.871,
                13, 16, 2.01442, 86710.34, 169.505,
                13, 18, 1.97168, 89292.865, 172.84,
                13, 20, 1.963985, 90115.09, 181.658,
                13, 24, 1.928275, 95257.905, 197.363,
                13, 28, 1.88896, 100899.1, 205.627,
                13, 32, 1.87867, 103599.55, 190.059,
                13, 64, 1.785665, 119019.89, 219.466,
                13, 128, 1.69196, 178772.505, 237.057,
                13, 256, 1.633815, 211174.05, 290.377,
                13, 512, 1.612825, 212483.19, 411.379,
                13, 1024, 1.604045, 221125.49, 628.229,
                14, 12, 2.10507, 140509.145, 131.879,
                14, 14, 2.073365, 184492.815, 149.451,
                14, 16, 2.016965, 213915.74, 134.833,
                14, 18, 1.96732, 215619.595, 144.26,
                14, 20, 1.937635, 219119.57, 146.379,
                14, 24, 1.92065, 224709.595, 156.731,
                14, 28, 1.879545, 245810.31, 159.025,
                14, 32, 1.86951, 256578.155, 165.513,
                14, 64, 1.776045, 286621.705, 235.886,
                14, 128, 1.681955, 370081.385, 230.483,
                14, 256, 1.631625, 403871.16, 294.923,
                14, 512, 1.608575, 417717.42, 395.616,
                14, 1024, 1.59865, 424309.49, 577.602,
                15, 12, 2.08414, 277167.0, 145.749,
                15, 14, 2.014895, 404951.515, 158.091,
                15, 16, 1.992055, 500886.47, 182.84,
                15, 18, 1.94609, 536672.08, 174.868,
                15, 20, 1.91769, 541426.03, 188.658,
                15, 24, 1.902085, 553570.9, 203.5,
                15, 28, 1.85588, 586261.7, 195.512,
                15, 32, 1.83224, 626131.625, 214.965,
                15, 64, 1.759215, 694487.53, 239.935,
                15, 128, 1.659995, 839804.71, 237.313,
                15, 256, 1.626685, 875829.735, 287.3,
                15, 512, 1.59591, 879181.91, 385.718,
                15, 1024, 1.58494, 904671.89, 550.075,
                16, 12, 2.0753, 532901.365, 167.284,
                16, 14, 2.003885, 859435.25, 135.557,
                16, 16, 1.98257, 1150407.285, 192.372,
                16, 18, 1.942205, 1318963.63, 183.815,
                16, 20, 1.913605, 1388145.315, 147.328,
                16, 24, 1.897345, 1395838.03, 150.97,
                16, 28, 1.8446, 1396453.72, 162.065,
                16, 32, 1.818155, 1493361.105, 163.467,
                16, 64, 1.746265, 1732618.6, 211.455,
                16, 128, 1.637865, 2995152.49, 234.229,
                16, 256, 1.60676, 3158867.44, 266.575,
                16, 512, 1.585255, 3149610.8, 381.739,
                16, 1024, 1.572505, 3297924.255, 535.257,
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

//        for (int leafSize = 4; leafSize <= 16; leafSize++) {
//            for (int loadFactor : new int[] { 12, 14, 16, 18, 20, 24, 28, 32, 64, 128, 256, 512, 1024 }) {
//                double expectedBits = SpaceEstimator.getExpectedSpace(leafSize, loadFactor);
//                if (expectedBits > 2.6) {
//                    continue;
//                }
//                FunctionInfo info = test(leafSize, loadFactor, size, true, 5, true);
//                if (info.bitsPerKey > 2.35) {
//                    continue;
//                }
//                if (info.evaluateNanos >  950) {
//                    continue;
//                }
//                System.out.println("  " + info.leafSize + ", " +
//                        info.loadFactor + ", " + info.bitsPerKey + ", " +
//                        info.generateNanos + ", " + info.evaluateNanos + ",");
//                list.add(info);
//            }
//        }

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
        int loadFactor = 1024;
        System.out.println("loadFactor " + loadFactor);
        System.out.println("leafSize, bits/key");
        System.out.println("calculated");
        for (int leafSize = 2; leafSize <= 24; leafSize++) {
            double bitsPerKey = SpaceEstimator.getExpectedSpace(leafSize, loadFactor);
            System.out.println("        (" + leafSize + ", " + bitsPerKey + ")");
            // System.out.println("size: " + size);
        }
        System.out.println("experimental");
        for (int leafSize = 2; leafSize <= 18; leafSize++) {
            int size = 1024 * 1024;
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
