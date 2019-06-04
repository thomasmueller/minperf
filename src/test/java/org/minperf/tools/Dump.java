package org.minperf.tools;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;

import org.minperf.BitBuffer;
import org.minperf.RecSplitBuilder;
import org.minperf.universal.LongHash;

/**
 * Generate a MPHF and store it in a file.
 */
public class Dump {

    public static void main(String... args) throws IOException {
        if (args.length != 4) {
            System.out.println("Usage: java " + Dump.class.getName() + 
                    " <n> <bucket size> <leaf size> <file name>");
            System.out.println("    n: number of keys");
            System.out.println("    bucket size: average bucket size");
            System.out.println("    leaf size: size of a leaf");
            System.out.println("    file name: target file name");
            return;
        }
        int n = Integer.parseInt(args[0]);
        int avgBucketSize = Integer.parseInt(args[1]);
        int leafSize = Integer.parseInt(args[2]);
        String fileName = args[3];
        LongHash hash = new LongHash();
        ArrayList<Long> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            list.add((long) i);
        }
        for (int test = 0; test < 5; test++) {
            System.out.println("test #" + test);
            long start = System.nanoTime();
            BitBuffer buff = RecSplitBuilder.newInstance(hash).
                    leafSize(leafSize).
                    averageBucketSize(avgBucketSize).
                    eliasFanoMonotoneLists(true).
                    generate(list);
            long time = System.nanoTime() - start;
            long nsPerKey = time / n;
            System.out.println(nsPerKey + " ns/key");
            FileOutputStream out = new FileOutputStream(fileName);
            byte[] data = buff.toByteArray();
            System.out.println(data.length * 8. / n + " bits/key");
            out.write(data);
            out.close();
        }
    }

}
