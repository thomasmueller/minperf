package org.minperf.medium;

import org.minperf.BitCodes;
import org.minperf.Probability;

public class RecSplitEliasFano {

    public static void main(String... args) {
        // can we say the maximum meaningful bucket size (for leafSize 6 - x) is 2048?
        // and for bucket size > 370, splitting by two doesn't save much space - instead just store size of first subset
//        int size = 10;
//        HashSet<Long> set = RandomizedTest.createSet(size, size);
        for(int leafSize = 2; leafSize < 16; leafSize++) {
//            int leafSize = 4;
            double p = Probability.probabilitySplitIntoMSubsetsOfSizeN(leafSize, 1);
            System.out.println(leafSize + " direct map, p=" + p);
            for (int i = leafSize;; i = (int) ((i * 1.2) + 1)) {
                double bitsForSize = (32.0 - Integer.numberOfLeadingZeros(1 + i)) / i;
                double p2 = Probability.probabilitySplitIntoMSubsetsOfSizeN(2, (i / 2) & 0xffffe);
                int k = BitCodes.calcBestGolombRiceShift(p2);
                double bitsForRice = BitCodes.calcAverageRiceGolombBits(k, p2) / i;
                if (Math.abs(bitsForSize - bitsForRice) < 0.01) {
//                if (Math.abs(2 * bitsForSize - bitsForRice) < 0.01) {
                    System.out.println("  " + i + " bits for 0.." + i + ": " + bitsForSize + " riceBits: " + bitsForRice);
                    System.out.println("  split into 2 subsets of size " + i + ": p=" + p2);
                    break;
                }
                if (p2 < p) {
                    System.out.println("  split into 2 subsets of size " + i + ": p=" + p2);
                    break;
                }
            }
        }
    }

}
