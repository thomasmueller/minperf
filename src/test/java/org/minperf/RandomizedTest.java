package org.minperf;

import static org.junit.Assert.assertTrue;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Random;

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
        int size = 100000;
        System.out.println("size: " + size);
        ArrayList<FunctionInfo> list = new ArrayList<FunctionInfo>();

        double[] data = {

        // generated at 2016-12-10 (real bits/key)
//                6, 24, 2.27946726915595, 364.42, 188.65,
//                6, 28, 2.23494794186157, 401.28, 198.096,
//                6, 32, 2.1980756293076, 287.74, 198.345,
//                6, 48, 2.10255231950242, 400.57, 207.964,
//                6, 64, 2.05073189758765, 370.68, 269.911,
//                6, 96, 1.99514371714009, 416.67, 314.196,
//                6, 128, 1.96551751342746, 338.74, 374.458,
//                6, 192, 1.93401819949992, 459.62, 448.161,
//                6, 256, 1.91710344609529, 367.42, 564.653,
//                6, 512, 1.88998662706379, 444.85, 884.12,
//                6, 1024, 1.87488166493454, 469.55, 1570.178,
//                7, 20, 2.28932845034067, 412.57, 168.962,
//                7, 24, 2.21615877153018, 377.12, 190.018,
//                7, 28, 2.16806989590391, 404.19, 195.889,
//                7, 32, 2.13387664427198, 376.81, 201.146,
//                7, 48, 2.03771996927017, 432.33, 227.288,
//                7, 64, 1.98700645951746, 427.63, 253.525,
//                7, 96, 1.93106219945868, 496.4, 298.513,
//                7, 128, 1.90102708940443, 408.19, 351.004,
//                7, 192, 1.8694170307821, 457.45, 439.1,
//                7, 256, 1.8526282961396, 468.24, 510.873,
//                7, 512, 1.82519244188956, 429.94, 791.011,
//                7, 1024, 1.8101725802034, 596.42, 1366.481,
//                8, 20, 2.26657144476067, 614.06, 173.845,
//                8, 24, 2.19871337379357, 639.12, 187.951,
//                8, 28, 2.13652579242216, 664.71, 191.845,
//                8, 32, 2.08072948824734, 635.14, 157.926,
//                8, 48, 1.9795513747855, 655.18, 209.326,
//                8, 64, 1.92354075108856, 835.09, 194.259,
//                8, 96, 1.86396056358285, 805.67, 258.354,
//                8, 128, 1.83244375443003, 771.6, 318.05,
//                8, 192, 1.79900603917136, 791.52, 309.261,
//                8, 256, 1.7809490695056, 804.41, 444.035,
//                8, 512, 1.75229458297153, 889.09, 604.537,
//                8, 1024, 1.7365166309036, 977.78, 1033.5,
//                9, 20, 2.23715767118517, 1151.39, 155.513,
//                9, 24, 2.17488307222327, 1131.75, 170.349,
//                9, 28, 2.12235014495578, 1151.69, 181.887,
//                9, 32, 2.07256032640466, 1183.02, 192.689,
//                9, 48, 1.95186978151384, 1148.63, 186.984,
//                9, 64, 1.90792126000629, 1247.49, 220.639,
//                9, 96, 1.84520695714766, 1425.85, 246.886,
//                9, 128, 1.81211801357005, 1481.38, 247.698,
//                9, 192, 1.77811079628322, 1235.52, 348.54,
//                9, 256, 1.76037550846359, 1369.84, 394.038,
//                9, 512, 1.73180163856088, 1570.81, 556.459,
//                9, 1024, 1.71595250336223, 1396.13, 1032.102,
//                10, 16, 2.28783475432275, 2105.74, 153.412,
//                10, 20, 2.19881407359436, 2302.77, 157.215,
//                10, 24, 2.13821944584172, 2335.73, 172.914,
//                10, 28, 2.0916288465532, 2596.63, 140.11,
//                10, 32, 2.0551082034782, 2521.44, 179.055,
//                //10, 48, 1.93108929381379, 3032.27, 166.93,
//                10, 64, 1.85560881772208, 3394.93, 186.636,
//                10, 96, 1.80612986782715, 3731.74, 219.512,
//                10, 128, 1.7705911627196, 3801.25, 245.763,
//                10, 192, 1.72804184637002, 4358.42, 280.272,
//                10, 256, 1.70943848677592, 4394.05, 313.278,
//                10, 512, 1.67706658763277, 4197.03, 473.137,
//                10, 1024, 1.66027456065327, 4551.8, 732.927,
//                11, 16, 2.26584418476329, 5003.58, 146.367,
//                11, 20, 2.17431189275111, 5063.66, 165.231,
//                11, 24, 2.11335500284733, 5284.3, 159.117,
//                11, 28, 2.06758660655306, 5520.6, 171.666,
//                11, 32, 2.03091178239551, 5534.57, 180.509,
//                11, 48, 1.92628424395088, 6237.83, 198.653,
//                11, 64, 1.83664843075487, 6384.63, 189.673,
//                11, 96, 1.79310648165594, 6285.41, 219.777,
//                11, 128, 1.74920725178319, 7080.34, 210.411,
//                11, 192, 1.70991551773607, 8672.78, 268.539,
//                11, 256, 1.69377935820397, 7980.24, 301.279,
//                11, 512, 1.66058982040545, 7091.77, 437.913,
//                11, 1024, 1.64385356608176, 8282.67, 721.475,
//                12, 16, 2.248286333284, 11534.9, 147.278,
//                12, 20, 2.1568984354521, 11925.92, 131.182,
//                12, 24, 2.09359141952042, 12889.55, 155.511,
//                12, 28, 2.04911688194921, 13157.08, 171.152,
//                12, 32, 2.01264224439764, 13050.46, 171.268,
//                12, 48, 1.91790414756063, 14225, 181.242,
//                12, 64, 1.82818268563102, 13015.46, 169.971,
//                12, 96, 1.77702396053688, 13702.93, 190.617,
//                12, 128, 1.73526047766221, 14222.41, 222.254,
//                12, 192, 1.69540804604853, 17232.87, 258.767,
//                12, 256, 1.67830653060736, 18064.66, 301.994,
//                12, 512, 1.64886023363742, 14580.68, 432.813,
//                12, 1024, 1.63130622484721, 17910.07, 662.176,
//                13, 16, 2.22647580408997, 26393.73, 141.189,
//                13, 20, 2.13867002915565, 28567.01, 149.756,
//                13, 24, 2.07177186365093, 30190.52, 149.291,
//                13, 28, 2.02704333978479, 30887.53, 157.254,
//                13, 32, 1.99172038052494, 31726.48, 163.434,
//                13, 48, 1.90080594718652, 33966.9, 169.47,
//                13, 64, 1.84755951710909, 35645.13, 196.533,
//                13, 96, 1.74209107646233, 44361.14, 190.894,
//                13, 128, 1.72577781842798, 42777.24, 217.239,
//                13, 192, 1.68158885462066, 46761.29, 255.029,
//                13, 256, 1.65465338802339, 57616.28, 284.678,
//                13, 512, 1.62579920844178, 51501.63, 399.075,
//                13, 1024, 1.60815396390918, 57965.82, 608.666,
//                14, 16, 2.21046227105807, 63994.19, 112.19,
//                14, 20, 2.12924315205941, 68300.89, 144.641,
//                14, 24, 2.06034110278186, 72297.22, 146.916,
//                14, 28, 2.01367481922769, 76909.32, 134.801,
//                14, 32, 1.97995081764499, 79683.78, 161.63,
//                14, 48, 1.88942738976888, 83784.09, 180.742,
//                14, 64, 1.83977686013975, 87499.2, 193.962,
//                14, 96, 1.73501575449633, 88462.19, 195,
//                14, 128, 1.71478630322035, 88583.43, 213.671,
//                14, 192, 1.67139825493895, 97102.34, 248.686,
//                14, 256, 1.65195065108169, 109755.63, 287.562,
//                14, 512, 1.61974419920261, 104481.65, 360.19,
//                14, 1024, 1.60227247945826, 113440.03, 589.061,
//                15, 16, 2.1902691055824, 150292.71, 135.189,
//                15, 20, 2.11574940471534, 173321.27, 143.461,
//                15, 24, 2.04739060920444, 179935.52, 126.281,
//                15, 28, 1.99721493200357, 182963.37, 135.066,
//                15, 32, 1.9635283394077, 192335.56, 168.433,
//                15, 48, 1.87370123476214, 207497.19, 177.651,
//                15, 64, 1.82487347097393, 217582, 174.775,
//                15, 96, 1.72829111923569, 202417.32, 200.334,
//                15, 128, 1.69867536888047, 201743.41, 225.486,
//                15, 192, 1.65959458580376, 211473.28, 254.966,
//                15, 256, 1.64695468934778, 223345.27, 278.151,
//                15, 512, 1.61067814431212, 248329.3, 375.887,
//                15, 1024, 1.59117358422041, 220143.28, 561.676,
//                16, 12, 2.29144967702837, 140421.04, 127.089,
//                16, 16, 2.17243608597777, 342752.15, 128.369,
//                16, 20, 2.10353583650696, 429684.06, 147.579,
//                16, 24, 2.03850866396269, 444536.25, 155.913,
//                16, 28, 1.98537619899686, 448540.56, 161.009,
//                16, 32, 1.94991463067984, 475623.25, 167.587,
//                16, 48, 1.86128743806712, 509341.59, 184.519,
//                16, 64, 1.81288533721169, 539666.82, 199.038,
//                16, 96, 1.75572860832183, 566023.33, 206.946,
//                16, 128, 1.67872618731245, 691295.24, 219.548,
//                16, 192, 1.66283677905008, 642724.63, 257.837,
//                16, 256, 1.62845033943738, 742684.44, 283.181,
//                16, 512, 1.59859467495892, 868699.17, 370.017,
//                16, 1024, 1.57841058329274, 796071.41, 538.774,

        // generated at 2016-12-10 (real bits/key)
                6, 24, 2.27946726915595, 380.94, 307.597,
                6, 28, 2.23494794186157, 657.64, 299.128,
                6, 32, 2.1980756293076, 294.73, 217.161,
                6, 48, 2.10255231950242, 472.69, 242.865,
                6, 64, 2.05073189758765, 396.59, 363.545,
                6, 96, 1.99514371714009, 417.19, 381.717,
                6, 128, 1.96551751342746, 372.65, 465.444,
                6, 192, 1.93401819949992, 430.48, 509.559,
                6, 256, 1.91710344609529, 370.39, 644.743,
                6, 512, 1.88998662706379, 512.62, 925.722,
                6, 1024, 1.87488166493454, 448.49, 1651.677,
                7, 20, 2.28932845034067, 374.6, 223.372,
                7, 24, 2.21615877153018, 354.81, 210.283,
                7, 28, 2.16806989590391, 423.74, 228.585,
                7, 32, 2.13387664427198, 370.71, 238.654,
                7, 48, 2.03771996927017, 390.93, 267.522,
                7, 64, 1.98700645951746, 444.63, 263.282,
                7, 96, 1.93106219945868, 398.54, 343.647,
                7, 128, 1.90102708940443, 383.27, 384.556,
                7, 192, 1.8694170307821, 460.82, 442.018,
                7, 256, 1.8526282961396, 405.73, 538.113,
                7, 512, 1.82519244188956, 420.47, 842.38,
                7, 1024, 1.8101725802034, 494.67, 1399.475,
                8, 20, 2.26657144476067, 532.25, 215.595,
                8, 24, 2.19871337379357, 546.87, 229.245,
                8, 28, 2.13652579242216, 553.78, 222.154,
                8, 32, 2.08072948824734, 618.57, 235.157,
                8, 48, 1.9795513747855, 690.8, 246.18,
                8, 64, 1.92354075108856, 671.58, 274.963,
                8, 96, 1.86396056358285, 714.57, 295.623,
                8, 128, 1.83244375443003, 753.86, 319.841,
                8, 192, 1.79900603917136, 773.24, 352.99,
                8, 256, 1.7809490695056, 730.07, 464.649,
                8, 512, 1.75229458297153, 745.59, 641.481,
                8, 1024, 1.7365166309036, 902.42, 1150.884,
                9, 20, 2.23715767118517, 851.28, 187.593,
                9, 24, 2.17488307222327, 932.8, 214.609,
                9, 28, 2.12235014495578, 1019.57, 214.851,
                9, 32, 2.07256032640466, 1004.32, 227.88,
                9, 48, 1.95186978151384, 1163.29, 226.518,
                9, 64, 1.90792126000629, 1147.08, 255.807,
                9, 96, 1.84520695714766, 1238.38, 271.435,
                9, 128, 1.81211801357005, 1287.65, 301.803,
                9, 192, 1.77811079628322, 1220.65, 363.723,
                9, 256, 1.76037550846359, 1262.31, 427.425,
                9, 512, 1.73180163856088, 1299.4, 605.912,
                9, 1024, 1.71595250336223, 1381.01, 1024.007,
                10, 16, 2.28783475432275, 1584.34, 201.2,
                10, 20, 2.19881407359436, 1813.19, 212.955,
                10, 24, 2.13821944584172, 1896.35, 220.062,
                10, 28, 2.0916288465532, 1870.27, 222.56,
                10, 32, 2.0551082034782, 2022.51, 219.178,
                10, 48, 1.93108929381379, 2698.51, 224.627,
                10, 64, 1.85560881772208, 3294.31, 237.304,
                10, 96, 1.80612986782715, 3236.06, 258.138,
                10, 128, 1.7705911627196, 3445.13, 265.229,
                10, 192, 1.72804184637002, 3722.41, 310.842,
                10, 256, 1.70943848677592, 3749.27, 337.813,
                10, 512, 1.67706658763277, 3852.33, 521.812,
                10, 1024, 1.66027456065327, 3953.07, 786.476,
                11, 16, 2.26584418476329, 3464.87, 195.004,
                11, 20, 2.17431189275111, 4011.47, 203.867,
                11, 24, 2.11335500284733, 3957.16, 170.087,
                11, 28, 2.06758660655306, 4095.49, 216.389,
                11, 32, 2.03091178239551, 4681.39, 217.625,
                11, 48, 1.92628424395088, 4767.16, 237.265,
                11, 64, 1.83664843075487, 5975.16, 225.475,
                11, 96, 1.79310648165594, 5854.92, 250.935,
                11, 128, 1.74920725178319, 6484.67, 266.695,
                11, 192, 1.70991551773607, 6864.66, 301.931,
                11, 256, 1.69377935820397, 6963.76, 306.792,
                11, 512, 1.66058982040545, 6780.11, 485.406,
                11, 1024, 1.64385356608176, 6789.37, 759.632,
                12, 16, 2.248286333284, 8084.99, 192.388,
                12, 20, 2.1568984354521, 8602.61, 197.452,
                12, 24, 2.09359141952042, 9326.37, 212.889,
                12, 28, 2.04911688194921, 9966.3, 194.556,
                12, 32, 2.01264224439764, 9815.95, 220.985,
                12, 48, 1.91790414756063, 10554.78, 242.697,
                12, 64, 1.82818268563102, 12002.82, 223.278,
                12, 96, 1.77702396053688, 13254.33, 230.084,
                12, 128, 1.73526047766221, 12851.11, 255.982,
                12, 192, 1.69540804604853, 13114.73, 297.002,
                12, 256, 1.67830653060736, 13504.35, 319.918,
                12, 512, 1.64886023363742, 13352.85, 460.872,
                12, 1024, 1.63130622484721, 13741.15, 715.191,
                13, 16, 2.22647580408997, 18532.71, 185.752,
                13, 20, 2.13867002915565, 19795.62, 193.048,
                13, 24, 2.07177186365093, 21389.2, 199.37,
                13, 28, 2.02704333978479, 22407.93, 195.175,
                13, 32, 1.99172038052494, 23634.07, 202.575,
                13, 48, 1.90080594718652, 25816.58, 226.041,
                13, 64, 1.84755951710909, 26719.14, 228.613,
                13, 96, 1.74209107646233, 41704.48, 229.929,
                13, 128, 1.72577781842798, 38556.81, 249.652,
                13, 192, 1.68158885462066, 43886.29, 287.417,
                13, 256, 1.65465338802339, 46236.11, 309.631,
                13, 512, 1.62579920844178, 48079.29, 433.879,
                13, 1024, 1.60815396390918, 47592.17, 662.067,
                14, 16, 2.21046227105807, 44998.76, 163.581,
                14, 20, 2.12924315205941, 47428.2, 195.792,
                14, 24, 2.06034110278186, 49956.77, 164.364,
                14, 28, 2.01367481922769, 55117.23, 203.526,
                14, 32, 1.97995081764499, 57780.12, 178.586,
                14, 48, 1.88942738976888, 61917.02, 226.522,
                14, 64, 1.83977686013975, 66013.58, 206.941,
                14, 96, 1.73501575449633, 83573.97, 233.316,
                14, 128, 1.71478630322035, 84665.35, 257.609,
                14, 192, 1.67139825493895, 88145.22, 272.967,
                14, 256, 1.65195065108169, 89208.5, 321.816,
                14, 512, 1.61974419920261, 92797.25, 401.441,
                14, 1024, 1.60227247945826, 92440.56, 647.275,
                15, 16, 2.1902691055824, 108564.24, 152.775,
                15, 20, 2.11574940471534, 117480.49, 191.125,
                15, 24, 2.04739060920444, 122943.72, 196.167,
                15, 28, 1.99721493200357, 127984.03, 203.779,
                15, 32, 1.9635283394077, 137533.5, 176.126,
                15, 48, 1.87370123476214, 151082.83, 226.395,
                15, 64, 1.82487347097393, 161123.73, 238.669,
                15, 96, 1.72829111923569, 185252.49, 238.406,
                15, 128, 1.69867536888047, 184193.09, 242.738,
                15, 192, 1.65959458580376, 191458.45, 267.846,
                15, 256, 1.64695468934778, 187747.91, 313.318,
                15, 512, 1.61067814431212, 198918.13, 394.858,
                15, 1024, 1.59117358422041, 194008.53, 580.027,
                16, 12, 2.29144967702837, 111671.34, 165.677,
                16, 16, 2.17243608597777, 244160.05, 190.062,
                16, 20, 2.10353583650696, 295086.08, 199.549,
                16, 24, 2.03850866396269, 298653.43, 202.154,
                16, 28, 1.98537619899686, 308745.8, 207.027,
                16, 32, 1.94991463067984, 334646.26, 212.344,
                16, 48, 1.86128743806712, 375414.54, 225.289,
                16, 64, 1.81288533721169, 392923.21, 236.079,
                16, 96, 1.75572860832183, 433485.13, 228.301,
                16, 128, 1.67872618731245, 664886.27, 266.4,
                16, 192, 1.66283677905008, 586807.51, 286.958,
                16, 256, 1.62845033943738, 702303.96, 272.081,
                16, 512, 1.59859467495892, 712369.76, 402.06,
                16, 1024, 1.57841058329274, 735723.44, 590.082,
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
//        for (int leafSize = 6; leafSize <= 16; leafSize++) {
//            for (int loadFactor : new int[] { 8, 12, 16, 20, 24, 28, 32, 48,
//                    64, 96, 128, 192, 256, 512, 1024 }) {
//                // int loadFactor = (int) Math.pow(Math.sqrt(2), leafSize);
//                FunctionInfo info = test(leafSize, loadFactor, size, true);
//                // calculated space estimation
//                info.bitsPerKey = SpaceEstimator.getExpectedSpace(leafSize, loadFactor);
//                if (info.bitsPerKey > 2.3) {
//                    continue;
//                }
//                System.out.println(info);
//                list.add(info);
//            }
//        }
        Collections.sort(list, new Comparator<FunctionInfo>() {
            @Override
            public int compare(FunctionInfo o1, FunctionInfo o2) {
                return Double.compare(o1.bitsPerKey, o2.bitsPerKey);
            }
        });
        FunctionInfo last = null;
        for (Iterator<FunctionInfo> it = list.iterator(); it.hasNext();) {
            FunctionInfo info = it.next();
            if (info.evaluateNanos < 160) {
                it.remove();
                continue;
            }
            if (last == null) {
                last = info;
                continue;
            }
            if (last.evaluateNanos < info.evaluateNanos) {
                it.remove();
                continue;
            }
            if (last.generateNanos < info.generateNanos) {
                it.remove();
                continue;
            }
            last = info;
        }
        System.out.println("Entries");
        for (FunctionInfo info : list) {
            System.out.println(info);
        }
        System.out.println("Evaluation time");
        for (FunctionInfo info : list) {
            System.out.println("        (" + info.bitsPerKey + ", " + info.evaluateNanos + ")");
        }
        System.out.println("Generation time");
        for (FunctionInfo info : list) {
            System.out.println("        (" + info.bitsPerKey + ", " + info.generateNanos + ")");
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
        // measure
        // Profiler prof = new Profiler().startCollecting();
        long best = Long.MAX_VALUE;
        for (int i = 0; i < measureCount; i++) {
            long evaluateNanos = System.nanoTime();
            for (int j = 0; j < measureCount; j++) {
                for (T x : set) {
                    int index = eval.evaluate(x);
                    if (index > set.size() || index < 0) {
                        Assert.fail("wrong entry: " + x + " " + index +
                                " leafSize " + leafSize +
                                " loadFactor " + loadFactor +
                                " hash " + convertBytesToHex(description));
                    }
                }
            }
            evaluateNanos = System.nanoTime() - evaluateNanos;
            best = Math.min(best, evaluateNanos);
        }
        // System.out.println(prof.getTop(5));
        return best / measureCount;
    }

    public static FunctionInfo testAndMeasure(int leafSize, int loadFactor, int size) {
        return test(leafSize, loadFactor, size, true, 1_000_000_000 / size);
    }

    public static FunctionInfo test(int leafSize, int loadFactor, int size, boolean evaluate) {
        return test(leafSize, loadFactor, size, evaluate, 10);
    }

    public static FunctionInfo test(int leafSize, int loadFactor, int size, boolean evaluate, int measureCount) {
        HashSet<Long> set = createSet(size, 1);
        UniversalHash<Long> hash = new LongHash();
        long generateNanos = System.nanoTime();
        BitBuffer buff;
        buff = RecSplitBuilder.newInstance(hash).
                leafSize(leafSize).loadFactor(loadFactor).
                generate(set);
        int bits = buff.position();
        byte[] data = buff.toByteArray();
        generateNanos = System.nanoTime() - generateNanos;
        assertTrue(bits <= data.length * 8);
        long evaluateNanos = 0;
        if (evaluate) {
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
