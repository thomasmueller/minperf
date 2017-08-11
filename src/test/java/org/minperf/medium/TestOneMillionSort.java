package org.minperf.medium;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collections;
import java.util.Random;

import org.minperf.BitBuffer;
import org.minperf.monotoneList.EliasFanoMonotoneList;
import org.minperf.sorted.BinaryArithmeticStream;

public class TestOneMillionSort {

    int size = 1030000;
    BitBuffer buff = new BitBuffer(size);
    ArrayList<Integer> list = new ArrayList<Integer>();

    void add(int n) {
        list.add(n);
        if (list.size() * 32 + buff.position() > size - 100) {
            merge();
        }
    }

    private void merge() {
        Collections.sort(list);
        BitBuffer newBuff = new BitBuffer(size);
        int max = 101000000;
        int prob = (int) (BinaryArithmeticStream.MAX_PROBABILITY * 1000000L / max);
        buff.seek(0);
        BinaryArithmeticStream.In in = new BinaryArithmeticStream.In(buff);
        BinaryArithmeticStream.Out out = new BinaryArithmeticStream.Out(newBuff);
        for (int i = 0, setCount = 0, j = 0; i < max; i++) {
            boolean x = in.readBit(prob);
            if (x) {
                setCount++;
            }
            int next = list.get(j);
            if (x) {
                out.writeBit(false, prob);
            } else if (next == setCount) {
                out.writeBit(true, prob);
                j++;
            } else {
                out.writeBit(false, prob);
            }
        }
    }

    public static void main(String... args) {
        // int lowBits = 5 * 1000000;
        // BitBuffer buff = new BitBuffer(10000000);
        // BinaryArithmeticStream.Out bs = new BinaryArithmeticStream.Out(buff);
        // Random r = new Random(1);
        // for (int i = 0; i < 4124998; i++) {
        // boolean bit = r.nextInt(412) < 100;
        // bs.writeBit(bit, BinaryArithmeticStream.MAX_PROBABILITY * 100 / 412);
        // }
        // System.out.println("bytes used: " + (lowBits + buff.position()) / 8);
        // main2();

        Random r = new Random(1);
        for (int test = 0; test < 10; test++) {
            int max = 99999999;
            int size = max + 1000000;
            BitSet set = new BitSet(size);
            for (int i = 0; i < 1000000; i++) {
                int x = r.nextInt(size);
                if (set.get(x)) {
                    i--;
                }
                set.set(x);
            }
            // set.clear();
            // set.set(0, 1000000);
            BitBuffer buff = new BitBuffer(40000000);

            // EliasFanoList: 1'276'086
            // int[] gaps = new int[1000000];
            // for (int i = 0, j = 0, last = 0; j < 1000000; i++) {
            // if (set.get(i)) {
            // gaps[j++] = i - last;
            // last = i;
            // }
            // }
            // EliasFanoList.generate(gaps, buff);

            // EliasFanoMonotoneList: 1'115'279; select overhead is 17'619
            // bytes; = 1'097'660
            // ArrayList<Integer> list = new ArrayList<>();
            // for (int i = 0; i < 1000000; i++) {
            // list.add(r.nextInt(100000000));
            // }
            // Collections.sort(list);
            // int[] array = new int[1000000];
            // for (int i = 0; i < 1000000; i++) {
            // array[i] = list.get(i);
            // }
            // EliasFanoMonotoneList.generate(array, buff);

            // simple binary arithmetic coding: 1'011'766
            int prob = (int) (BinaryArithmeticStream.MAX_PROBABILITY * 1000000L / size);
            BinaryArithmeticStream.Out bs = new BinaryArithmeticStream.Out(buff);
            for (int i = 0; i < size; i++) {
                bs.writeBit(set.get(i), prob);
            }

            System.out.println("bytes used: " + (buff.position()) / 8);
        }
        // 1 MB = 1'048'576 bytes
        System.out.println("max: " + 1024 * 1024);
        // bytes used: 1011764, 1011734

        // boolean bit = r.nextInt(412) < 100;
        // bs.writeBit(bit, BinaryArithmeticStream.MAX_PROBABILITY * 100 / 412);
        // }

    }

    // lowBitCount 7
    // set length 2562499 card 1000000

    public static void main2(String... args) {
        // https://stackoverflow.com/questions/12748246/sorting-1-million-8-digit-numbers-in-1-mb-of-ram
        // max is 1048576 bytes, minus 2 KB
        // best is 1045000 bytes
        // 40'000 bytes, at 32 bit per key, is 10'000 entries (100 rounds)
        // int min = 0;
        int max = 99999999;
        int count = 1000000;
        double averageGap = (double) (max + 1) / count;
        System.out.println("avg gap " + averageGap);
        // 7 + 1
        int lowBitCount = 32 - Integer.numberOfLeadingZeros(Integer.highestOneBit((max + 1) / count));
        System.out.println("lowBitCount " + lowBitCount);
        // 56 bytes for 7 entries
        // int lowBitCount = 32 -
        // Integer.numberOfLeadingZeros(Integer.highestOneBit((max+1) / count));

        Random r = new Random(1);
        int[] data = new int[ONE_MILLION];
        BitBuffer buff = new BitBuffer(16 * ONE_MILLION);
        for (int i = 0; i < ONE_MILLION; i++) {
            data[i] = r.nextInt(max + 1);
        }
        Arrays.sort(data);
        // EliasFanoMonotoneList list =
        EliasFanoMonotoneList.generate(data, buff);
        System.out.println(buff.position());
    }

    final static int ONE_MILLION = 1000000;

    // long[] highBits = new long[ONE_MILLION * 7 / 64];
    //
    // long[] lowBits = new long[ONE_MILLION * 7 / 64];
    //
    // // 125000 longs
    // long[] data = new long[1000000 / 8];

}
