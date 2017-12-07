package org.minperf.medium;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.HashSet;

import org.minperf.RandomizedTest;
import org.minperf.Settings;
import org.minperf.universal.LongHash;

public class TestBBHash {

    public static void main(String... args) {
        for (long size = 1_000_000_000_000L; size <= 1_000_000_000_000L; size *= 10) {
            double probDuplicates = 1.0 / 100;
            int bitPerSignature = EstimateTimeForHugeSets.getBitsForProbability(size, probDuplicates);
            System.out.println(
                    size + " 1:" + 1 / probDuplicates + " prob of a duplicate bits/signature: " + bitPerSignature);
            long signatureBits = bitPerSignature * size;
            System.out.println("  signature size: " + EstimateTimeForHugeSets.formatBits(signatureBits));
        }

        for (int len = 10; len <= 100000000; len *= 10) {
            test(len);
        }
    }

    private static void test(int size) {
        System.out.println("size " + size);
        HashSet<Long> set = RandomizedTest.createSet(size, 1);
        TestBBHash t = new TestBBHash();
        t.generate(set);
        System.out.println("cardinality " + t.bits.cardinality());
    }

    BitSet bits;
    ArrayList<Integer> remainder;
    private static final double LEVEL_BITS = 1 / Math.log(2);

    public void generate(Collection<Long> set) {
        int size = set.size();
        bits = new BitSet((int) (2 * size * LEVEL_BITS));
        BitSet duplicates = new BitSet((int) (size * LEVEL_BITS));
        remainder = new ArrayList<Integer>();
        int offset = 0;
        int level = 0;
        int len = (int) (LEVEL_BITS * size);
        int lastLen = len;
        int lastOffset = 0;
        boolean newDuplicates = false;
        do {
            newDuplicates = false;
            for(long x : set) {
                boolean wasAdded = false;
                if (level > 0) {
                    wasAdded = true;
                    long hash = LongHash.universalHash(x, level - 1);
                    int index = lastOffset + Settings.reduce((int) hash, lastLen);
                    wasAdded = !duplicates.get(index);
                }
                if (wasAdded) {
                    continue;
                }
                long hash = LongHash.universalHash(x, level);
                int index = offset + Settings.reduce((int) hash, len);
                if (duplicates.get(index)) {
                    // nothing to do
                } else if (bits.get(index)) {
                    bits.clear(index);
                    duplicates.set(index);
                    newDuplicates = true;
                } else {
                    bits.set(index);
                }
            }
            lastOffset = offset;
            offset += len;
            lastLen = len;
            len /= 2;
            level++;
            System.out.println("level " + level + " len " + len + " cardinality " + bits.cardinality() + " duplicates " + duplicates.cardinality());
        } while (newDuplicates);
    }

}
