package org.minperf;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.HashSet;

import org.minperf.utils.Text;

/**
 * Test a from from Wikipedia.
 */
public class WikipediaTest {

    /**
     * Run just this test.
     *
     * @param a ignored
     */
    public static void main(String... a) {
        try {
            if (!largeFileWithUniqueEntries(System.getProperty("user.home") + "/data/hash/mphf/" +
                    "enwiki-20160305-all-titles.unique.txt")) {
                largeFile(System.getProperty("user.home") + "/data/hash/mphf/" +
                        "enwiki-20160305-all-titles");
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static boolean largeFileWithUniqueEntries(String fileName) throws IOException {
        if (!new File(fileName).exists()) {
            System.out.println("not found: " + fileName);
            return false;
        }
        RandomAccessFile f = new RandomAccessFile(fileName, "r");
        byte[] data = new byte[(int) f.length()];
        f.readFully(data);
        ArrayList<Text> list = new ArrayList<Text>(30 * 1024 * 1024);
        int end = Text.indexOf(data, 0, '\n');
        Text t = new Text(data, 0, end);
        long time = System.currentTimeMillis();
        while (true) {
            list.add(t);
            if (end >= data.length - 1) {
                break;
            }
            int start = end + 1;
            end = Text.indexOf(data, start, '\n');
            t = new Text(data, start, end - start);
            long now = System.currentTimeMillis();
            if (now - time > 2000) {
                System.out.println("size: " + list.size());
                time = now;
            }
        }
        System.out.println("file: " + fileName);
        System.out.println("size: " + list.size());

        test(list, 5, 256);
        test(list, 6, 512);
        test(list, 8, 1024);
        test(list, 10, 2048);
        return true;
    }

    private static void largeFile(String fileName) throws IOException {
        if (!new File(fileName).exists()) {
            System.out.println("not found: " + fileName);
            return;
        }
        RandomAccessFile f = new RandomAccessFile(fileName, "r");
        byte[] data = new byte[(int) f.length()];
        f.readFully(data);
        HashSet<Text> set = new HashSet<Text>(40 * 1024 * 1024);
        int end = Text.indexOf(data, 0, '\n');
        Text t = new Text(data, 0, end);
        long time = System.currentTimeMillis();
        while (true) {
            set.add(t);
            if (end >= data.length - 1) {
                break;
            }
            int start = end + 1;
            end = Text.indexOf(data, start, '\n');
            t = new Text(data, start, end - start);
            long now = System.currentTimeMillis();
            if (now - time > 2000) {
                System.out.println("size: " + set.size());
                time = now;
            }
        }
        System.out.println("file: " + fileName);
        System.out.println("size: " + set.size());

        test(set, 5, 256);
        test(set, 6, 512);
        test(set, 8, 1024);
        test(set, 10, 2048);
    }

    private static void test(ArrayList<Text> list, int leafSize, int loadFactor) {
        long time = System.currentTimeMillis();
        byte[] desc = RecSplitBuilder.
                newInstance(new Text.UniversalTextHash()).
                leafSize(leafSize).
                loadFactor(loadFactor).
                generate(list).toByteArray();
        time = System.currentTimeMillis() - time;
        System.out.println("leafSize " + leafSize);
        System.out.println("loadFactor " + loadFactor);
        System.out.println("seconds: " + time / 1000.);
        System.out.println("len: " + desc.length);
        int bits = desc.length * 8;
        System.out.println(((double) bits / list.size()) + " bits/key");

        FunctionInfo info = RandomizedTest.test(leafSize, loadFactor, list.size(), false);
        System.out.println("random data: " + info.bitsPerKey + " bits/key");

    }

    private static void test(HashSet<Text> set, int leafSize, int loadFactor) {
        long time = System.currentTimeMillis();
        byte[] desc = RecSplitBuilder.
                newInstance(new Text.UniversalTextHash()).
                leafSize(leafSize).
                loadFactor(loadFactor).
                generate(set).toByteArray();
        time = System.currentTimeMillis() - time;
        System.out.println("leafSize " + leafSize);
        System.out.println("loadFactor " + loadFactor);
        System.out.println("seconds: " + time / 1000.);
        System.out.println("len: " + desc.length);
        int bits = desc.length * 8;
        System.out.println(((double) bits / set.size()) + " bits/key");
    }
}
