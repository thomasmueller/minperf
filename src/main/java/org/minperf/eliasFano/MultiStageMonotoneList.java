package org.minperf.eliasFano;

import org.minperf.BitBuffer;

/**
 * This implementation uses a linear regression, and 3 levels of offsets.
 */
public class MultiStageMonotoneList extends MonotoneList {

    private static final int GROUP_SHIFT = 4;
    private static final int GROUP_COUNT = 1 << GROUP_SHIFT;
    private static final int GROUP1_FACTOR = 8;
    private static final int GROUP2_FACTOR = 4;

    private final BitBuffer buffer;
    private final int startLevel1, startLevel2, startLevel3;
    private final int bitCount1, bitCount2, bitCount3;
    private final int count1, count2, count3;
    private final long factor;
    private final int add;

    private MultiStageMonotoneList(BitBuffer buffer) {
        this.buffer = buffer;
        this.count3 = (int) buffer.readEliasDelta() - 1;
        int diff = (int) buffer.readEliasDelta() - 1;
        this.factor = getScaleFactor(diff, count3);
        this.add = (int) BitBuffer.unfoldSigned(buffer.readEliasDelta() - 1);
        this.bitCount1 = (int) buffer.readEliasDelta() - 1;
        this.bitCount2 = (int) buffer.readEliasDelta() - 1;
        this.bitCount3 = (int) buffer.readEliasDelta() - 1;
        startLevel1 = buffer.position();
        count2 = (count3 + GROUP_COUNT - 1) / GROUP_COUNT;
        count1 = (count2 + GROUP_COUNT - 1) / GROUP_COUNT;
        startLevel2 = startLevel1 + count1 * bitCount1;
        startLevel3 = startLevel2 + count2 * bitCount2;
        buffer.seek(startLevel3 + bitCount3 * count3);
    }

    private static long getScaleFactor(int multiply, int divide) {
        return divide == 0 ? 0 : ((long) multiply << 32) / divide + 1;
    }

    public static MultiStageMonotoneList generate(int[] data, BitBuffer buffer) {
         int start = buffer.position();
         int count3 = data.length;
         // verify it is monotone
         for (int i = 1; i < count3; i++) {
             if (data[i - 1] > data[i]) {
                 throw new IllegalArgumentException();
             }
         }
         int diff = data[count3 - 1] - data[0];
         long factor = getScaleFactor(diff, count3);
         int add = data[0];
         for (int i = 1; i < count3; i++) {
             int expected = (int) ((i * factor) >>> 32);
             int x = data[i];
             add = Math.min(add, x - expected);
         }
         buffer.writeEliasDelta(count3 + 1);
         buffer.writeEliasDelta(diff + 1);
         buffer.writeEliasDelta(BitBuffer.foldSigned(add) + 1);
         int count2 = (count3 + GROUP_COUNT - 1) / GROUP_COUNT;
         int count1 = (count2 + GROUP_COUNT - 1) / GROUP_COUNT;
         int[] group1 = new int[count1];
         int[] group2 = new int[count2];
         int[] group3 = new int[count3];
         for (int i = 0; i < count3; i++) {
             // int expected = (int) (i * max / count3);
             int expected = (int) ((i * factor) >>> 32) + add;
             int got = data[i];
             int x = got - expected;
             if (x < 0) {
                 throw new AssertionError();
             }
             group3[i] = x;
         }
         int a = Integer.MAX_VALUE;
         for (int i = 0; i < count3; i++) {
             int x = group3[i];
             a = Math.min(a, x);
             if (i % GROUP_COUNT == (GROUP_COUNT - 1) || i == count3 - 1) {
                 group2[i / GROUP_COUNT] = a / GROUP2_FACTOR;
                 a = Integer.MAX_VALUE;
             }
         }
         a = Integer.MAX_VALUE;
         for (int i = 0; i < count3; i++) {
             int d = group2[i / GROUP_COUNT] * GROUP2_FACTOR;
             int x = group3[i];
             group3[i] -= d;
             if (group3[i] < 0) {
                 throw new AssertionError();
             }
             a = Math.min(a, x);
             if (i % (GROUP_COUNT * GROUP_COUNT) == (GROUP_COUNT * GROUP_COUNT - 1) || i == count3 - 1) {
                 group1[i / GROUP_COUNT / GROUP_COUNT] = a / GROUP1_FACTOR;
                 a = Integer.MAX_VALUE;
             }
         }
         int last = -1;
         for (int i = 0; i < count3; i++) {
             int j = i / GROUP_COUNT / GROUP_COUNT;
             int d = group1[j] * GROUP1_FACTOR;
             int n = i / GROUP_COUNT;
             if (n == last) {
                 continue;
             }
             group2[n] -= d / GROUP2_FACTOR;
             last = n;
         }
         int max1 = 0, max2 = 0, max3 = 0;
         for (int i = 0; i < group3.length; i++) {
             max3 = Math.max(max3, group3[i]);
         }
         for (int i = 0; i < group2.length; i++) {
             max2 = Math.max(max2, group2[i]);
         }
         for (int i = 0; i < group1.length; i++) {
             max1 = Math.max(max1, group1[i]);
         }
         int bitCount1 = 32 - Integer.numberOfLeadingZeros(max1);
         int bitCount2 = 32 - Integer.numberOfLeadingZeros(max2);
         int bitCount3 = 32 - Integer.numberOfLeadingZeros(max3);
         buffer.writeEliasDelta(bitCount1 + 1);
         buffer.writeEliasDelta(bitCount2 + 1);
         buffer.writeEliasDelta(bitCount3 + 1);
         for (int x : group1) {
           buffer.writeNumber(x, bitCount1);
         }
         for (int x : group2) {
             buffer.writeNumber(x, bitCount2);
         }
         for (int x : group3) {
             buffer.writeNumber(x, bitCount3);
         }
         buffer.seek(start);
         return new MultiStageMonotoneList(buffer);
     }

    public static MultiStageMonotoneList load(BitBuffer buffer) {
        return new MultiStageMonotoneList(buffer);
    }

    @Override
    public int get(int i) {
        int expected = (int) ((i * factor) >>> 32) + add;
        long a = buffer.readNumber(startLevel1 + (i >>> (2 * GROUP_SHIFT)) * bitCount1, bitCount1);
        long b = buffer.readNumber(startLevel2 + (i >>> GROUP_SHIFT) * bitCount2, bitCount2);
        long c = buffer.readNumber(startLevel3 + i * bitCount3, bitCount3);
        return (int) (expected + a * GROUP1_FACTOR + b * GROUP2_FACTOR + c);
    }

    @Override
    public long getPair(int i) {
        return ((long) get(i) << 32) | (get(i + 1));
    }

}
