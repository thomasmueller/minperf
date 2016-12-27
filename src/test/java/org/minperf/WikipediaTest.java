package org.minperf;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.HashSet;

import org.junit.Assert;
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
//            FunctionInfo info = RandomizedTest.test(leafSize, loadFactor, list.size(), false);
//            System.out.println("random data: " + info.bitsPerKey + " bits/key");
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

      int[] pairs = new int[] {
              5, 20,
              6, 20,
              7, 16,
              7, 20,
              7, 24,
              8, 10,
              8, 18,
              8, 20,
              8, 24,
              9, 12,
              9, 14,
              9, 16,
              9, 18,
              9, 20,
              9, 24,
              9, 28,
              9, 32,
              9, 64,
              10, 32,
              10, 64,
              11, 32,
              11, 64,
              12, 64,
              13, 128,
              13, 192,
              14, 192,
              14, 256,
              15, 256,
              15, 512,
              15, 1024,
              16, 1024,          };

        for (int i = 0; i < pairs.length; i += 2) {
            int leafSize = pairs[i], loadFactor = pairs[i + 1];
            test(list, leafSize, loadFactor);
        }


        // 1 thread
//        5, 20, 2.2934250358455595, 1257, 334,
//        6, 20, 2.2301333899505726, 1177, 338,
//        7, 16, 2.2056644551315054, 1459, 316,
//        7, 20, 2.1689277935290385, 1625, 325,
//        7, 24, 2.097395632808568, 1537, 322,
//        8, 10, 2.3722683340699433, 1990, 314,
//        8, 18, 2.127560134412306, 2131, 310,
//        8, 20, 2.146903893975849, 2270, 310,
//        8, 24, 2.0783823125303242, 2244, 315,
//        9, 12, 2.228793858569091, 3609, 302,
//        9, 14, 2.157263044381975, 3573, 304,
//        9, 16, 2.131986997469965, 3813, 307,
//        9, 18, 2.088727997602901, 3888, 305,
//        9, 20, 2.085344159281969, 4008, 304,
//        9, 24, 2.0510659982789283, 4119, 312,
//        9, 28, 2.006607237192625, 4283, 311,
//        9, 32, 1.9651353563942207, 4473, 314,
//        9, 64, 1.8612538090483068, 5012, 335,
//        10, 32, 1.9459898065405232, 8674, 310,
//        10, 64, 1.7997964108894018, 14669, 317,
//        11, 32, 1.921768633858763, 19027, 302,
//        11, 64, 1.7817167768394646, 26900, 318,
//        12, 64, 1.7734789550806909, 54813, 310,
//        13, 128, 1.6908858307695058, 179575, 338,
//        13, 192, 1.6529698752547937, 199952, 362,
//        14, 192, 1.6437121891329978, 402364, 372,
//        14, 256, 1.6302684001174041, 404552, 401,

        // 8 threads
//        5, 20, 2.2934250358455595, 741, 334,
//        6, 20, 2.2301333899505726, 515, 334,
//        7, 16, 2.2056644551315054, 571, 325,
//        7, 20, 2.1689277935290385, 591, 316,
//        7, 24, 2.097395632808568, 651, 315,
//        8, 10, 2.3722683340699433, 652, 309,
//        8, 18, 2.127560134412306, 668, 306,
//        8, 20, 2.146903893975849, 661, 309,
//        8, 24, 2.0783823125303242, 687, 309,
//        9, 12, 2.228793858569091, 949, 302,
//        9, 14, 2.157263044381975, 957, 304,
//        9, 16, 2.131986997469965, 971, 300,
//        9, 18, 2.088727997602901, 983, 301,
//        9, 20, 2.085344159281969, 999, 304,
//        9, 24, 2.0510659982789283, 1015, 303,
//        9, 28, 2.006607237192625, 1029, 304,
//        9, 32, 1.9651353563942207, 1099, 310,
//        9, 64, 1.8612538090483068, 1150, 339,
//        10, 32, 1.9459898065405232, 1935, 314,


//
//        test(list, 5, 10);
//        for (int leafSize = 5; leafSize < 10; leafSize++) {
//            test(list, leafSize, 10);
//            test(list, leafSize, 12);
//            test(list, leafSize, 14);
//            test(list, leafSize, 16);
//            test(list, leafSize, 18);
//        }
//
//        test(list, 5, 256);
//        test(list, 6, 512);
//        test(list, 8, 1024);
//        test(list, 10, 2048);
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

        test(set, 8, 14);
        test(set, 8, 10);
        test(set, 8, 16);
        test(set, 8, 12);
    }

    private static void test(Collection<Text> set, int leafSize, int loadFactor) {
        long size = set.size();
        long time = System.nanoTime();
        byte[] desc = RecSplitBuilder.
                newInstance(new Text.UniversalTextHash()).
         //       parallelism(1).
                leafSize(leafSize).
                loadFactor(loadFactor).
                generate(set).toByteArray();
        time = System.nanoTime() - time;
        long generateNanos = time / size;
        // System.out.println("generate nanos: " + generateNanos);
        int bits = desc.length * 8;
        double bitsPerKey = (double) bits / set.size();
        RecSplitEvaluator<Text> eval = RecSplitBuilder
                .newInstance(new Text.UniversalTextHash()).leafSize(leafSize)
                .loadFactor(loadFactor).buildEvaluator(new BitBuffer(desc));
        long evaluateNanos = test(set, eval);
        System.out.println("  " + leafSize + ", " +
                loadFactor + ", " + bitsPerKey + ", " +
                generateNanos + ", " + evaluateNanos + ",");
//        System.out.println("evaluate");
//        System.out.println("        % leafSize " + leafSize + " loadFactor " + loadFactor);
//        System.out.println("        (" + bitsPerKey + ", " + evaluateNanos + ")");
//        System.out.println("generate");
//        System.out.println("        % leafSize " + leafSize + " loadFactor " + loadFactor);
//        System.out.println("        (" + bitsPerKey + ", " + generateNanos + ")");
    }

    private static <T> long test(Collection<T> set, RecSplitEvaluator<T> eval) {
        BitSet known = new BitSet();
        int size = set.size();
        // verify
        for (T x : set) {
            int index = eval.evaluate(x);
            if (index > set.size() || index < 0) {
                Assert.fail("wrong entry: " + x + " " + index);
            }
            if (known.get(index)) {
                Assert.fail("duplicate entry: " + x + " " + index);
            }
            known.set(index);
        }
        // the the CPU cool
        try {
            Thread.sleep(10 * 1000);
        } catch (InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        // measure
        // Profiler prof = new Profiler().startCollecting();
        long best = Long.MAX_VALUE;
        ArrayList<T> list = new ArrayList<T>(set);
        for (int i = 0; i < 10; i++) {
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            long evalNanos = System.nanoTime();
            for (T x : list) {
                int index = eval.evaluate(x);
                if (index > list.size() || index < 0) {
                    Assert.fail("wrong entry: " + x + " " + index);
                }
            }
            evalNanos = System.nanoTime() - evalNanos;
            long evaluateNanos = evalNanos / size;
            // System.out.println("evaluate: " + evaluateNanos);
            best = Math.min(best, evaluateNanos);
        }
        // System.out.println(prof.getTop(5));
        return best;
        // System.out.println(prof.getTop(5));
    }

}
