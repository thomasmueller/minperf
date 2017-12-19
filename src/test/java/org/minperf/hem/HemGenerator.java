package org.minperf.hem;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Random;

import org.minperf.BitBuffer;
import org.minperf.hem.recsplit.Builder;
import org.minperf.utils.RandomSetGenerator;
import org.minperf.utils.RandomSetGenerator.RandomBlockProducer;

public class HemGenerator {

    public static void main(String... args) throws Exception {
        new HemGenerator().test();
    }

    public void test() throws Exception {
        String userHome = System.getProperty("user.home");
        FileOutputStream fOut = new FileOutputStream(userHome + "/temp/hash.bin");
        // ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
        out = new DataOutputStream(new BufferedOutputStream(fOut));
        totalSize = 1_000_000_000_000L;
        generator = new RandomHashGenerator(totalSize);
        lowBitCount = 64;
        long expectedBlockSize = totalSize;
        final int maxBlockSize = 2_000_000;
        while (expectedBlockSize > maxBlockSize) {
            lowBitCount--;
            expectedBlockSize >>>= 1;
        }
        int highBitCount = 64 - lowBitCount;
        System.out.println("lowBits: " + lowBitCount);
        System.out.println("highBits: " + highBitCount);
        System.out.println("expectedBlockSize: " + expectedBlockSize);
        max = 1L << highBitCount;
        startTime = System.nanoTime();
        int threadCount = 8;
        Thread[] threads = new Thread[threadCount];
        for (int id = 0; id < threadCount; id++) {
            Thread t = new Thread() {
                @Override
                public void run() {
                    long[] keys = new long[maxBlockSize * 2];
                    while (true) {
                        BlockInfo info = generateKeys(keys);
                        if (info == null) {
                            break;
                        }
                        BitBuffer buff = new Builder().generate(keys, info.len);
                        write(info, buff);
                    }
                }
            };
            t.start();
            threads[id] = t;
        }
        for (Thread t : threads) {
            t.join();
        }
        System.out.println("done");
    }

    long totalSize;
    DataOutputStream out;
    HashGenerator generator;
    int lowBitCount;
    long startTime;
    long high;
    long max;
    long total;
    long totalBits;

    synchronized void write(BlockInfo info, BitBuffer buff) {
        total += info.len;
        byte[] data = buff.toByteArray();
        totalBits += data.length * 8;
        long time = System.nanoTime() - startTime;
        double percentDone = 100. * total / totalSize;
        System.out.println(time / total + " ns/key " + (totalBits / 8 / 1024 / 1024) + " MB " + percentDone + "% "
                + (double) totalBits / total + " bits/key");
        // System.out.println("write " + info.high + " len " + info.len + " "
        // +data.length);
        try {
            out.writeLong(info.high);
            out.writeInt(info.len);
            out.write(buff.toByteArray());
        } catch (IOException e) {
            throw new RuntimeException();
        }
    }

    synchronized BlockInfo generateKeys(long[] data) {
        if (high >= max) {
            return null;
        }
        BlockInfo info = new BlockInfo();
        info.high = high++;
        info.len = generator.generateBlock(data, lowBitCount);
        if (info.len == 0) {
            System.out.println("not generated! " + data.length + " " + lowBitCount);
        }
        return info;
    }

    static class BlockInfo {
        int len;
        long high;
    }

    static class RandomHashGenerator implements HashGenerator {

        private final long[] buffer;
        private final RandomBlockProducer producer;
        private final int blockSize = 8 * 1024 * 1024;
        private int offset, end;
        private long highBits;

        public RandomHashGenerator(long size) {
            this.producer = RandomSetGenerator.randomHashProducer(new Random(1), size);
            buffer = new long[blockSize];
        }

        @Override
        public int generateBlock(long[] data, int lowBitCount) {
            int len = 0;
            // TODO why not 64 - lowBitCount?
            int shift = 65 - lowBitCount;
            while (true) {
                while (offset < end) {
                    long x = buffer[offset];
                    if ((x >>> lowBitCount) != highBits) {
                        highBits++;
                        return len;
                    }
                    offset++;
                    data[len++] = x << shift;
                }
                offset = 0;
                end = producer.produce(buffer, 0, blockSize, 0);
                if (end == 0) {
                    return len;
                }
            }
        }

    }

    interface HashGenerator {
        int generateBlock(long[] data, int lowBits);
    }

}
