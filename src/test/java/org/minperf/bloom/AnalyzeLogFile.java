package org.minperf.bloom;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.LineNumberReader;
import java.io.PrintWriter;
import java.io.RandomAccessFile;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;

import org.minperf.hem.Sort;

public class AnalyzeLogFile {

    final static String homeDir = System.getProperty("user.home");

    public static void main1(String... args) throws IOException {
        LineNumberReader r = new LineNumberReader(
                new BufferedReader(new FileReader(homeDir + "/results.txt")));
        PrintWriter w = new PrintWriter(new FileWriter(homeDir + "/results_log.txt"));
        int id = 0;
        while(true) {
            String line = r.readLine();
            if (line == null) {
                break;
            }
            w.println(line);
            if (line.startsWith("listing all")) {
                w.println("-> " + id);
                DataOutputStream out = new DataOutputStream(
                        new BufferedOutputStream(
                                new FileOutputStream(homeDir + "/data" + id + ".bin")));
                while(true) {
                    line = r.readLine();
                    if (line == null) {
                        break;
                    } else if (line.startsWith("end")) {
                        w.println(line);
                        break;
                    }
                    long x = Long.parseUnsignedLong(line);
                    out.writeLong(x);
                }
                out.close();
                id++;
            }
        }
        w.close();
        r.close();
    }

    public static void main2(String... args) throws IOException {
        for (int fi = 0; fi < 3; fi++) {
            FileChannel c = new RandomAccessFile(
                    homeDir + "/data" + fi + ".bin", "r").getChannel();
            MappedByteBuffer buff = c.map(MapMode.READ_ONLY, 0, 190_000_000 * 8);
            long[] data = new long[190_000_000];
            for (int i = 0; i < data.length; i++) {
                data[i] = buff.getLong();
            }
            System.out.println("sorting...");
            Sort.parallelSortUnsigned(data, 0, data.length);
            System.out.println(data[0]);
            System.out.println(data[1000000]);
            System.out.println(data[data.length / 2 - 1000000]);
            System.out.println(data[data.length / 2 + 1000000]);
            System.out.println(data[data.length - 1000000]);
            System.out.println(data[data.length - 1]);
            for (int i = 1; i < data.length; i++) {
                if (data[i - 1] == data[i]) {
                    System.out.println("duplicate: " + data[i]);
                }
            }
            System.out.println(fi + " done");
            c.close();
        }
    }

    public static void main(String... args) throws IOException {
        for (int fi = 0; fi < 3; fi++) {
            FileChannel c = new RandomAccessFile(homeDir + "/data" + fi + ".bin", "r").getChannel();
            MappedByteBuffer buff = c.map(MapMode.READ_ONLY, 0, 190_000_000 * 8);
            long[] data = new long[190_000_000];
            for (int i = 0; i < data.length; i++) {
                data[i] = buff.getLong();
            }
            c.close();
            System.out.println(data[0]);
            System.out.println(data[1000]);
            System.out.println("constructing...");
            XorFilter_8bit.construct(data);
            System.out.println("done");
            data = null;
        }
    }

}
