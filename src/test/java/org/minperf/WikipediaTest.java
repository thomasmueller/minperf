package org.minperf;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.HashSet;

import org.minperf.universal.StringHash;
import org.minperf.universal.UniversalHash;

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
        Text t = new Text(data, 0);
        long time = System.currentTimeMillis();
        while (true) {
            list.add(t);
            int end = t.end;
            if (end >= data.length - 1) {
                break;
            }
            t = new Text(data, end + 1);
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
        Text t = new Text(data, 0);
        long time = System.currentTimeMillis();
        while (true) {
            set.add(t);
            int end = t.end;
            if (end >= data.length - 1) {
                break;
            }
            t = new Text(data, end + 1);
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
        UniversalHash<Text> hash = new UniversalHash<Text>() {

            @Override
            public int universalHash(Text o, long index) {
                return o.hashCode(index);
            }

        };
        long time = System.currentTimeMillis();
        byte[] desc = RecSplitBuilder.newInstance(hash).
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
    }

    private static void test(HashSet<Text> set, int leafSize, int loadFactor) {
        UniversalHash<Text> hash = new UniversalHash<Text>() {

            @Override
            public int universalHash(Text o, long index) {
                return o.hashCode(index);
            }

        };
        long time = System.currentTimeMillis();
        byte[] desc = RecSplitBuilder.newInstance(hash).
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

    /**
     * A text.
     */
    static class Text {

        /**
         * The byte data (may be shared, so must not be modified).
         */
        final byte[] data;

        /**
         * The start and end location.
         */
        final int start, end;

        Text(byte[] data, int start) {
            this.data = data;
            this.start = start;
            int end = start;
            while (data[end] != '\n') {
                end++;
            }
            this.end = end;
        }

        /**
         * The hash code (using a universal hash function).
         *
         * @param index the hash function index
         * @return the hash code
         */
        public int hashCode(long index) {
            return StringHash.getSipHash24(data, start, end, index, 0);
        }

        @Override
        public int hashCode() {
            return hashCode(0);
        }

        @Override
        public boolean equals(Object other) {
            if (other == this) {
                return true;
            } else if (!(other instanceof Text)) {
                return false;
            }
            Text o = (Text) other;
            int s2 = o.start;
            int e2 = o.end;
            if (e2 - s2 != end - start) {
                return false;
            }
            for (int s1 = start; s1 < end; s1++, s2++) {
                if (data[s1] != o.data[s2]) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public String toString() {
            return new String(data, start, end - start);
        }
    }
}
