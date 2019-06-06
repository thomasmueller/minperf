package org.minperf.tools;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

import org.minperf.BitBuffer;
import org.minperf.RecSplitBuilder;
import org.minperf.generator.Generator;
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
                    parallelism(1).
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
//            System.out.println("split_evals: " + Generator.num_split_evals);
//            System.out.println("split_count: " + Generator.num_split_count);
//            System.out.println("evals/split: " + (double) Generator.num_split_evals / Generator.num_split_count);
//            System.out.println("bij_evals: " + Arrays.toString(Generator.num_bij_evals));
//            System.out.println("bij_counts: " + Arrays.toString(Generator.num_bij_counts));
//            for(int i=0; i<20; i++) {
//                if (Generator.num_bij_counts[i] > 0)
//                System.out.println("evals/bij: " + i + " " + (double) Generator.num_bij_evals[i] / Generator.num_bij_counts[i]);
//            }
            out.close();
        }
    }

}
