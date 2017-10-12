package org.minperf.c;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.LineNumberReader;
import java.io.RandomAccessFile;
import java.util.HashSet;

import org.minperf.BitBuffer;
import org.minperf.RecSplitBuilder;
import org.minperf.RecSplitEvaluator;
import org.minperf.Settings;
import org.minperf.universal.StringHash;

/**
 * A generator for the C version.
 */
public class HashGenerator {

    public static void main(String... args) throws IOException {

        String settingsFileName = "settings.bin";
        String hashFileName = "hash.bin";
        String keyFileName = "keys.txt";
        String directory = ".";

        int leafSize = 8;
        int averageBucketSize = 18;
        for(int i=0; i<args.length; i++) {
            if ("-dir".equals(args[i])) {
                directory = args[++i];
            } else if ("-leafSize".equals(args[i])) {
                leafSize = Integer.parseInt(args[++i]);
            } else if ("-averageBucketSize".equals(args[i])) {
                averageBucketSize = Integer.parseInt(args[++i]);
            }
        }
        System.out.println("Directory: " + directory);

        /*
        seq 100 > ~/temp/keys.txt
        seq -f "%.20g" 1 1000000 > ~/temp/hash/keys.txt
        */
        String fileName = directory + "/" + keyFileName;
        HashSet<String> set = new HashSet<String>();
        System.out.println("Reading keys from " + fileName);
        LineNumberReader reader = new LineNumberReader(new BufferedReader(
                new FileReader(fileName)));
        while(true) {
            String line = reader.readLine();
            if(line == null) {
                break;
            }
            set.add(line);
        }
        reader.close();
        System.out.println("Generating MPHF with leafSize " + leafSize +
                ", averageBucketSize " + averageBucketSize + ", size " + set.size());

        BitBuffer buff = RecSplitBuilder.newInstance(new StringHash()).
                leafSize(leafSize).averageBucketSize(averageBucketSize).
                eliasFanoMonotoneLists(false).generate(set);
        System.out.println("Storing MPHF with " +
                buff.position() / (double) set.size() + " bits/key");
        storeBuffer(directory + "/" + hashFileName, buff);

        Settings s = new Settings(leafSize, averageBucketSize);
        storeSettings(s, directory + "/" + settingsFileName);

        buff.seek(0);
        RecSplitEvaluator<String> evaluator =
                RecSplitBuilder.newInstance(new StringHash()).
                leafSize(leafSize).averageBucketSize(averageBucketSize).
                eliasFanoMonotoneLists(false).buildEvaluator(buff);

        long sum = 0;
        reader = new LineNumberReader(new BufferedReader(
                new FileReader(directory + "/" + keyFileName)));
        while(true) {
            String line = reader.readLine();
            if(line == null) {
                break;
            }
            sum += evaluator.evaluate(line);
        }
        System.out.println("Sum of indexes: " + sum);
    }

    private static void storeSettings(Settings s, String fileName) throws IOException {
        BitBuffer buff = new BitBuffer(100000);
        int len = 4 * 1024;
        buff.writeEliasDelta(s.getLeafSize() + 1);
        buff.writeEliasDelta(s.getAverageBucketSize() + 1);
        buff.writeEliasDelta(len + 1);
        for (int i = 0; i < len; i++) {
            buff.writeEliasDelta(BitBuffer.foldSigned(s.getSplit(i)) + 1);
            buff.writeEliasDelta(s.getGolombRiceShift(i) + 1);
        }
        System.out.println("Storing settings to " + fileName);
        storeBuffer(fileName, buff);
    }

    private static void storeBuffer(String fileName, BitBuffer buff) throws IOException {
        RandomAccessFile f = new RandomAccessFile(fileName, "rw");
        f.write(buff.toByteArray());
        System.out.println("(file length: " + f.length() + " bytes)");
        f.close();
    }

}
