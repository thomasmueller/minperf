package org.minperf.tools;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.BitSet;

import org.minperf.BitBuffer;
import org.minperf.RecSplitBuilder;
import org.minperf.RecSplitEvaluator;
import org.minperf.universal.LongHash;

/**
 * Read a MPHF from a file and evaluate it.
 */
public class Load {

    public static void main(String... args) throws IOException {
        if (args.length != 4) {
            System.out.println("Usage: java " + Load.class.getName() + 
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
        RandomAccessFile f = new RandomAccessFile(fileName, "r");
        byte[] data = new byte[(int) f.length()];
        f.readFully(data);
        f.close();
        RecSplitEvaluator<Long> eval = RecSplitBuilder.newInstance(hash).
                leafSize(leafSize).
                averageBucketSize(avgBucketSize).
                eliasFanoMonotoneLists(true).
                buildEvaluator(new BitBuffer(data));
        for (int test = 0; test < 5; test++) {
            System.out.println("test #" + test);
            long start = System.nanoTime();
            BitSet set = new BitSet();
            for (int i = 0; i < n; i++) {
                int x = eval.evaluate((long) i);
                if (set.get(x)) {
                    throw new AssertionError("duplicate key: " + i);
                }
                set.set(x);
            }
            long time = System.nanoTime() - start;
            long nsPerKey = time / n;
            System.out.println(nsPerKey + " ns/key");
        }
    }

}
