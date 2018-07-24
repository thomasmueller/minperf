package org.minperf.bloom;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.RandomAccessFile;

import org.minperf.hem.RandomGenerator;

public class Xor8Generator {

    public static void main(String... args) throws IOException {
        int size = 1_000;
        long[] keys = new long[size];
        RandomGenerator.createRandomUniqueListFast(keys, size);
        XorFilter_8bit filter = XorFilter_8bit.construct(keys);

        String hashFile = "hash.bin";
        RandomAccessFile f = new RandomAccessFile(hashFile, "rw");
        f.writeInt(size);
        f.writeInt(filter.getHashIndex());
        f.write(filter.getFingerprints());
        f.close();

        String keyFile = "keys.txt";
        PrintWriter writer = new PrintWriter(new BufferedWriter(new FileWriter(keyFile)));
        for(long x : keys) {
            writer.println(x);
        }
        writer.close();
    }

}
