package org.minperf.medium;

import java.math.BigInteger;

import org.minperf.Probability;

import javafx.util.Duration;

public class EstimateTimeForHugeSets {

    public static void main(String... args) {
        System.out.println("Read all entries, write signatures to a file, sort it, and construct the MPHF");

        int bitsPerKey = 64; //  8 * 100;
        System.out.println("key size: " + formatBits(bitsPerKey));

        // timing data for disk here:
        // http://docs.aws.amazon.com/AWSEC2/latest/UserGuide/EBSVolumeTypes.html

        // RecSplit
//                (1.6139085584683803, 262655.0)
//                (1.647726743677378, 250957.0)
//                (1.6847065892037447, 104284.0)
//                (1.7663794926201724, 48849.0)
//                (1.9282185286288644, 5108.0)
//                (2.131266063511737, 1593.0)
                // CHD
//        %        (2.501514530222621, 1899)
//                (2.2679004188762297, 2445)
//                (2.159221442493381, 3968)
//                (2.061488705059407, 13154)
//                (2.0043687601433935, 57080)

        // CHD
//      double mphfBitsPerKey = 2.159;
//      long mphfNsPerKey = 3968;

        double mphfBitsPerKey = 2.13;
        long mphfNsPerKey = 1593;

//        double mphfBitsPerKey = 1.928;
//        long mphfNsPerKey = 5108;

        // m4.4xlarge: 16 vCPUs, 64 GiB RAM, 2000 Mbps
        // m4.16xlarge: 64 vCPUs, 256 GiB RAM, 10,000 Mbps <==
        // 2.3 GHz Intel Xeon

        long keyReadBitsPerSecond = 10_000 * 1_000_000L;
        System.out.println("key data (HDD) reads: " + formatBits(keyReadBitsPerSecond) + "/s");
        long signatureWriteBitsPerSecond = keyReadBitsPerSecond;
        System.out.println("signature writes: " + formatBits(signatureWriteBitsPerSecond) + "/s");
        long signatureReadBitsPerSecond = keyReadBitsPerSecond;
        System.out.println("signature reads: " + formatBits(signatureReadBitsPerSecond) + "/s");

        long ramBits = 64 * 8L * 1024 * 1024 * 1024;

        // no GPUs
        long mphfCPUCores = 64;

        System.out.println("RAM: " + formatBits(ramBits));
        System.out.println("cpu cores: " + mphfCPUCores);
        System.out.println("MPHF bits/key: " + mphfBitsPerKey);
        System.out.println("MPHF ns/key: " + mphfNsPerKey);
        double probDuplicates = 1.0 / 64;

//        System.out.print("bits ");
//        int minProb = 16;
//        int maxProb = 32;
//        for (int x = minProb; x < maxProb; x *= 2) {
//            System.out.print(" 1/" + x);
//        }
//        System.out.println();

        for (long size = 10_000_000_000L; size < 1_000_000_000_000L; size *= 10) {
            System.out.println();
            System.out.printf("size: %,d \n", size);
            long keyBits = bitsPerKey * size;
            System.out.println("key size: " + formatBits(keyBits));
            long readKeySeconds = keyBits / keyReadBitsPerSecond;
            System.out.println("time to read keys: " + formatSeconds(readKeySeconds));
            long mphfBits = (long) (mphfBitsPerKey * size);
            long mphfSeconds = mphfNsPerKey * size / 1000000 / 1000 / mphfCPUCores;
            System.out.println("MPHF size: " + formatBits(mphfBits));
            System.out.println("MPHF cpu time: " + formatSeconds(mphfSeconds));

            long bestTime = Long.MAX_VALUE;
            double bestProb = 0;
            int bestBitsPerSignature = 0;
            for(int x = 2; x < 1000000; x*=2) {
                probDuplicates = 1.0 / x;
                int bitPerSignature = getBitsForProbability(size, probDuplicates);
                long signatureBits = bitPerSignature * size;

                long signaturesInMemory = ramBits / bitPerSignature;
                int signatureChunks = (int) ((size + signaturesInMemory - 1) / signaturesInMemory);

                long avgGap = BigInteger.ONE.shiftLeft(bitPerSignature).
                        divide(BigInteger.valueOf(signaturesInMemory)).longValue();
                int bitsPerGap = bitsPerGapEliasFano(avgGap);
                long entriesWithGap = ramBits / bitsPerGap;
                int gapChunks = (int) ((size + entriesWithGap - 1) / entriesWithGap);


                long signatureWriteSeconds = signatureBits / signatureWriteBitsPerSecond;
                long signatureReadSeconds = signatureBits / signatureReadBitsPerSecond;

                // concurrent signature writes and data reads
                // signatureWriteSeconds = 0;

                // concurrent signature reads and MPHF generation
                signatureReadSeconds = 0;

                long totalTimeSeconds = readKeySeconds + signatureWriteSeconds + signatureReadSeconds + mphfSeconds;
                long avgAddTime = getAverageAdditionalTime(totalTimeSeconds, probDuplicates);
                long avgTotalTime = totalTimeSeconds + avgAddTime;

                if (avgTotalTime < bestTime) {
                    bestTime = avgTotalTime;
                    bestProb = probDuplicates;
                    bestBitsPerSignature = bitPerSignature;
                }

                if (x == 1024) {
                    System.out.println("  probabilitxy of duplicates: " + probDuplicates);
                    System.out.println("  bits per signature: " + bitPerSignature);
                    System.out.println("  avg gap: " + avgGap);
                    System.out.println("  bits per gap: " + bitsPerGap);
                    System.out.println("  signature size: " + formatBits(signatureBits));
                    System.out.println("  signature chunks: " + signatureChunks);
                    System.out.println("  gap chunks: " + gapChunks);
                    System.out.println("  signature writes: " + formatSeconds(signatureWriteSeconds));
                    System.out.println("  signature reads: " + formatSeconds(signatureReadSeconds));
                    System.out.println("  total time (no duplicates): " + formatSeconds(totalTimeSeconds));
                    System.out.println("  total time (average for given probability of duplicates): " + formatSeconds(avgTotalTime));
                }
            }

            System.out.println("best bits per signature: " + bestBitsPerSignature);
            System.out.println("best probabilitxy of duplicates: " + bestProb + " (1/" + (1.0 / bestProb) + ")");
            System.out.println("best total time (average for given probability of duplicates): " + formatSeconds(bestTime));


//            // available RAM in bits per key
//            long ramBits = (long) (3 * size);
//            System.out.println("size " + size);
//            for (int x = minProb; x < maxProb; x *= 2) {
//                int bits = getBitsForProbability(size, (double) 1 / x);
//                long entries = ramBits / bits;
//                int stripes = (int) (size / entries);
//                long avgGap = BigInteger.ONE.shiftLeft(bits).divide(BigInteger.valueOf(entries)).longValue();
//                int bitsPerGap = bitsPerGapEliasFano(avgGap);
//                long entriesWithGap = ramBits / bitsPerGap;
//                int stripesWithGap = (int) (size / entriesWithGap);
//                System.out.println("  entries=" + entries + " bits=" + bits);
//                System.out.println("  avgGap=" + avgGap + " bitsPerGap=" +
//                            bitsPerGap + " entriesWithGap=" + entriesWithGap + " stripes=" + stripes + " stripes2=" + stripesWithGap + ")");
//                System.out.println("  writeNormal " + size * bits / 1024 / 1024 / 1024 / 8 + " GB");
//                System.out.println("  writeGaps " + size * bitsPerGap / 1024 / 1024 / 1024 / 8 + " GB");
//            }
//            System.out.print(" (" + (2 * size / 8 / 1024 / 1024 / 1024) + " GB MPHF)");
//            System.out.println();
        }
    }

    private static long getAverageAdditionalTime(long totalTimeSeconds, double probDuplicates) {
        long add = 0;
        while (totalTimeSeconds > 0) {
            totalTimeSeconds *= probDuplicates;
            add += totalTimeSeconds;
        }
        return add;
    }

    public static String formatBits(long bits) {
        long b = Math.abs(bits);
        long byt = 8, kib = byt * 1024, mib = kib * 1024;
        long gib = mib * 1024, tib = gib * 1024;
        String positive = String.format(
            " %d TiB %d GiB %d MiB %d KiB %d byte %d bit",
            b / tib, (b % tib) / gib, (b % gib) / mib, (b % mib) / kib,
            (b % kib) / byt, b % byt);
        positive = positive.replaceAll(" 0 [^ ]*", "");
        return (bits < 0 ? "-" + positive : positive).trim();
    }

    public static String formatSeconds(long seconds) {
        long absSeconds = Math.abs(seconds);
        String positive = String.format(
            "%d:%02d:%02d",
            absSeconds / 3600,
            (absSeconds % 3600) / 60,
            absSeconds % 60);
        return seconds < 0 ? "-" + positive : positive;
    }


//
//
//
//
//        if (bits <= 0) {
//            return bits + " bits";
//        }
//        if (bits > 10 * bitsPerTB) {
//            return (bits + bitsPerTB - 1) / bitsPerTB + " TiB";
//        }
//        if (bits > 10 * bitsPerGB) {
//            return (bits + bitsPerGB - 1) / bitsPerGB + " GiB";
//        }
//        if (bits > 10 * bitsPerMB) {
//            return (bits + bitsPerMB - 1) / bitsPerMB + " MiB";
//        }
//        if (bits > 10 * bitsPerKB) {
//            return (bits + bitsPerKB - 1) / bitsPerKB + " KiB";
//        }
//        if (bits % 8 == 0) {
//            return (bits / 8) + " bytes";
//        }
//        return bits + " bits";
//    }

    public static String formatDuration(Duration duration) {
        long seconds = (long) duration.toSeconds();
        long absSeconds = Math.abs(seconds);
        String positive = String.format(
            "%d:%02d:%02d",
            absSeconds / 3600,
            (absSeconds % 3600) / 60,
            absSeconds % 60);
        return seconds < 0 ? "-" + positive : positive;
    }

    public static void mainChunked(String... args) {
        int chunks = 10;
        System.out.println("Read in " + chunks + " chunks, append MPHF to file");
        System.out.print("bits ");
        int minProb = 2;
        int maxProb = 4;
        for (int x = minProb; x < maxProb; x *= 2) {
            System.out.print(" 1/" + x);
        }
        System.out.println();
        for (long size = 10_000_000_000L; size <= 1_000_000_000_000L; size *= 10) {
            // available RAM in bits per key
            long ramBits = (long) (4 * size);
            System.out.println("size " + size);
            for (int x = minProb; x < maxProb; x *= 2) {
                long size2 = size / chunks;
                int bits = getBitsForProbability(size2, (double) 1 / x);
                long entries = ramBits / bits;
                int stripes = (int) (size2 / entries);
                long avgGap = BigInteger.ONE.shiftLeft(bits).divide(BigInteger.valueOf(entries)).longValue();
                int bitsPerGap = bitsPerGapEliasFano(avgGap);
                long entriesWithGap = ramBits / bitsPerGap;
                int stripesWithGap = (int) (size2 / entriesWithGap);
                System.out.println("  entries=" + entries + " bits=" + bits);
                System.out.println("  avgGap=" + avgGap + " bitsPerGap=" +
                            bitsPerGap + " entriesWithGap=" + entriesWithGap + " stripes=" + stripes + " stripes2=" + stripesWithGap + ")");
                System.out.println("  writeNormal " + size2 * bits / 1024 / 1024 / 1024 / 8 + " GB");
                System.out.println("  writeGaps " + size2 * bitsPerGap / 1024 / 1024 / 1024 / 8 + " GB");
            }
            System.out.print(" (" + (ramBits / 8 / 1024 / 1024 / 1024) + " GB RAM)");
            System.out.print(" (" + (2 * size / 8 / 1024 / 1024 / 1024) + " GB MPHF)");
            System.out.println();
        }
    }

    public static void mainFullWithStripes(String... args) {
        System.out.println("Read all entries, write to striped files; merge and construct");
        System.out.print("bits ");
        int minProb = 16;
        int maxProb = 32;
        for (int x = minProb; x < maxProb; x *= 2) {
            System.out.print(" 1/" + x);
        }
        System.out.println();
        for (long size = 10_000_000_000L; size <= 1_000_000_000_000L; size *= 10) {
            // available RAM in bits per key
            long ramBits = (long) (3 * size);
            System.out.println("size " + size);
            for (int x = minProb; x < maxProb; x *= 2) {
                int bits = getBitsForProbability(size, (double) 1 / x);
                long entries = ramBits / bits;
                int stripes = (int) (size / entries);
                long avgGap = BigInteger.ONE.shiftLeft(bits).divide(BigInteger.valueOf(entries)).longValue();
                int bitsPerGap = bitsPerGapEliasFano(avgGap);
                long entriesWithGap = ramBits / bitsPerGap;
                int stripesWithGap = (int) (size / entriesWithGap);
                System.out.println("  entries=" + entries + " bits=" + bits);
                System.out.println("  avgGap=" + avgGap + " bitsPerGap=" +
                            bitsPerGap + " entriesWithGap=" + entriesWithGap + " stripes=" + stripes + " stripes2=" + stripesWithGap + ")");
                System.out.println("  writeNormal " + size * bits / 1024 / 1024 / 1024 / 8 + " GB");
                System.out.println("  writeGaps " + size * bitsPerGap / 1024 / 1024 / 1024 / 8 + " GB");
            }
            System.out.print(" (" + (2 * size / 8 / 1024 / 1024 / 1024) + " GB MPHF)");
            System.out.println();
        }
    }

    public static int getBitsForProbability(long size, double d) {
        for (int bits = 1;; bits++) {
            double p = Probability.probabilityOfDuplicates(size, bits);
            if (p < d) {
                return bits;
            }
        }
    }

    private static int bitsPerGapEliasFano(long avgGap) {
        long len = 1;
        long max = len * avgGap;
        int lowBitCount = 64 - Long.numberOfLeadingZeros(Long.highestOneBit(max / len));
        long x = len + (max >>> lowBitCount);
        return (int) ((x + lowBitCount * len) / len) + 1;
    }

}
