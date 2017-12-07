package org.minperf.medium;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import org.junit.Assert;
import org.minperf.BitBuffer;
import org.minperf.FunctionInfo;
import org.minperf.RandomizedTest;
import org.minperf.Settings;
import org.minperf.chd.EliasFanoList;
import org.minperf.monotoneList.EliasFanoMonotoneList;
import org.minperf.universal.LongHash;

public class MediumTest {

    public static void main(String... args) {
        compareSpace();
        test();
    }

    private static void compareSpace() {
        Set<Long> set = new HashSet<Long>();
        int size = 10000;
        Random r = new Random(size);
        while (set.size() < size) {
            set.add(r.nextLong());
        }
        LongHash hash = new LongHash();
        for(int leafSize = 3; leafSize < 12; leafSize++) {
            for(int avgBucketSize = 10; avgBucketSize < 2000; avgBucketSize *= 1.5) {
                Settings settings = new Settings(leafSize, avgBucketSize);
                MediumRecSplit.Generator<Long> gen = new MediumRecSplit.Generator<Long>(hash, settings);
                int[] description = gen.generate(set);

                BitBuffer buff = compressBucketData(settings, true, true, description);
                int bitCountMonotone = buff.position();
                buff.seek(0);
                int[] d2 = expandBucketData(settings, true, buff);
                Assert.assertEquals(description.length, d2.length);
                Assert.assertArrayEquals(description, d2);
                buff = compressBucketData(settings, true, false, description);
                int bitCountMonotoneMin = buff.position();

                buff = compressBucketData(settings, false, true, description);
                int bitCountList = buff.position();
                buff.seek(0);
                d2 = expandBucketData(settings, false, buff);
                Assert.assertEquals(description.length, d2.length);
                Assert.assertArrayEquals(description, d2);
                buff = compressBucketData(settings, false, false, description);
                int bitCountListMin = buff.position();

                double efMonotone= (double) bitCountMonotone / size;
                double efList = (double) bitCountList / size;
                double efMonotoneMin = (double) bitCountMonotoneMin / size;
                double efListMin = (double) bitCountListMin / size;

                FunctionInfo info = RandomizedTest.test(leafSize, avgBucketSize, size, false);
                double regular = info.bitsPerKey;
                System.out.println("leafSize " + leafSize + " avg " + avgBucketSize + " regular " + regular +
                        " efm " + efMonotoneMin + " ..  " + efMonotone + " efl " + efListMin + " .. " + efList);
            }
        }
    }

    private static BitBuffer compressBucketData(Settings settings, boolean monotone, boolean includeStart, int[] description) {
        int size = description[0];
        int averageBucketSize = settings.getAverageBucketSize();
        int bucketCount = (size + averageBucketSize - 1) / averageBucketSize;
        int bucketPos = 1 + 2 * bucketCount;
        int[] bucketData = new int[description.length - bucketPos];
        System.arraycopy(description, bucketPos, bucketData, 0, bucketData.length);
        BitBuffer buff = new BitBuffer(1000 + 10 * size);
        buff.writeEliasDelta(1 + size);
        buff.writeEliasDelta(1 + description.length);
        int[] bucketStart = new int[bucketCount];
        System.arraycopy(description, 1, bucketStart, 0, bucketCount);
        int[] bucketOffsets = new int[bucketCount];
        System.arraycopy(description, 1 + bucketCount, bucketOffsets, 0, bucketCount);
        EliasFanoMonotoneList.generate(bucketStart, buff);
        if (includeStart) {
            EliasFanoMonotoneList.generate(bucketOffsets, buff);
        }
        if (monotone) {
            eliasFanoList2(bucketData, buff);
        } else {
            EliasFanoList.generate(bucketData, buff);
        }
        return buff;
    }

    private static int[] expandBucketData(Settings settings, boolean monotone, BitBuffer buff) {
        int size = (int) (buff.readEliasDelta() - 1);
        int descriptionSize = (int) (buff.readEliasDelta() - 1);
        int averageBucketSize = settings.getAverageBucketSize();
        int bucketCount = (size + averageBucketSize - 1) / averageBucketSize;
        int[] description = new int[descriptionSize];
        description[0] = size;
        EliasFanoMonotoneList bucketStartList = EliasFanoMonotoneList.load(buff);
        EliasFanoMonotoneList bucketOffsetsList = EliasFanoMonotoneList.load(buff);
        for (int i = 0; i < bucketCount; i++) {
            description[1 + i] = bucketStartList.get(i);
            description[1 + bucketCount + i] = bucketOffsetsList.get(i);
        }
        int bucketPos = 1 + 2 * bucketCount;
        int[] bucketData = new int[description.length - bucketPos];
        if (monotone) {
            EliasFanoMonotoneList l = EliasFanoMonotoneList.load(buff);
            for (int i = 0; i < bucketData.length; i++) {
                bucketData[i] = l.get(i) - (i == 0 ? 0 : l.get(i - 1));
            }
        } else {
            EliasFanoList l = EliasFanoList.load(buff);
            for (int i = 0; i < bucketData.length; i++) {
                bucketData[i] = l.get(i);
            }
        }
        System.arraycopy(bucketData, 0, description, bucketPos, bucketData.length);
        return description;
    }

    private static void eliasFanoList2(int[] bucketData, BitBuffer buff) {
        int sum = 0;
        // System.out.println(Arrays.toString(bucketData));
        for (int i = 0; i < bucketData.length; i++) {
            int x = bucketData[i];
            bucketData[i] += sum;
            sum += x;
        }
        // System.out.println(Arrays.toString(bucketData));
        EliasFanoMonotoneList.generate(bucketData, buff);
    }

    public static void test() {
        Set<Long> set = new HashSet<Long>();
        int size = 100000;
        Random r = new Random(size);
        while (set.size() < size) {
            set.add(r.nextLong());
        }
        LongHash hash = new LongHash();
        Settings settings = new Settings(8, 100);
        MediumRecSplit.Generator<Long> gen = new MediumRecSplit.Generator<Long>(hash, settings);
        int[] data = gen.generate(set);
        HashSet<Integer> used = new HashSet<Integer>();
        MediumRecSplit.Evaluator<Long> eval = new MediumRecSplit.Evaluator<Long>(hash, settings, data);
        for (Long x : set) {
            int e = eval.evaluate(x);
            if (!used.add(e)) {
                e = eval.evaluate(x);
                throw new AssertionError();
            }
        }
    }

}
