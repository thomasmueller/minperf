package org.minperf;

import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collections;

import org.minperf.utils.Text;

/**
 * Process a text file.
 */
public class TextFileTest {

    private int leafSize = 8;
    private int loadFactor = 14;

    private String textFile;
    private String hashFile;
    private String indexFile;
    private String outputFile;

    public static void main(String... args) throws IOException {
        new TextFileTest().execute(args);
    }

    private void execute(String... args) throws IOException {
        int threadCount = Runtime.getRuntime().availableProcessors();
        int repeat = 1;
        if (args.length == 0) {
            printUsage(threadCount);
        }
        for (int i = 0; i < args.length; i++) {
            if ("-leafSize".equals(args[i])) {
                leafSize = Integer.parseInt(args[++i]);
            } else if ("-loadFactor".equals(args[i])) {
                loadFactor = Integer.parseInt(args[++i]);
            } else if ("-textFile".equals(args[i])) {
                textFile = args[++i];
            } else if ("-hashFile".equals(args[i])) {
                hashFile = args[++i];
            } else if ("-indexFile".equals(args[i])) {
                indexFile = args[++i];
                outputFile = args[++i];
            } else if ("-threadCount".equals(args[i])) {
                threadCount = Integer.parseInt(args[++i]);
            } else if ("-repeat".equals(args[i])) {
                repeat = Integer.parseInt(args[++i]);
            } else {
                printUsage(threadCount);
            }
        }
        System.out.println("Settings: leafSize=" + leafSize + ", loadFactor=" + loadFactor);
        if (textFile != null) {
            System.out.println("Generating MPHF from text file: " + textFile);
            if (hashFile == null) {
                System.out.println("hashFile option not set, so hash not stored");
            }
            for (int i = 0; i < repeat; i++) {
                generateFromTextFile(threadCount);
            }
        }
        if (indexFile != null) {
            System.out.println("Listing indexes for index file: " + indexFile);
            if (hashFile == null) {
                throw new IllegalArgumentException("hashFile option not set");
            }
            for (int i = 0; i < repeat; i++) {
                generateIndexes(threadCount);
            }
        }
    }

    private void generateIndexes(int threadCount) throws IOException {
        byte[] desc = readFile(hashFile);
        final RecSplitEvaluator<Text> eval = RecSplitBuilder.
                newInstance(new Text.UniversalTextHash()).
                leafSize(leafSize).
                loadFactor(loadFactor).
                buildEvaluator(new BitBuffer(desc));
        final ArrayList<Text> list = readTextFile(indexFile);
        final int[] indices = new int[list.size()];
        System.out.println("Calculating indices");
        long time = System.nanoTime();
        Thread[] threads = new Thread[threadCount];
        for (int i = 0; i < threadCount; i++) {
            final int start = (int) ((long) i * list.size() / threadCount);
            final int end = (int) ((long) (i + 1) * list.size() / threadCount) - 1;
            Thread thread = new Thread(new Runnable() {
                @Override
                public void run() {
                    for (int i = start; i <= end; i++) {
                        indices[i] = eval.evaluate(list.get(i));
                    }
                }
            });
            thread.start();
            threads[i] = thread;
        }
        for (Thread t : threads) {
            try {
                t.join();
            } catch (InterruptedException e) {
                // ignore
            }
        }
        time = System.nanoTime() - time;
        System.out.println("Calculated in " + time / 1000000 / 1000 +
                " seconds, at " + time / list.size() + " ns/key, " +
                "using " + threadCount + " thread(s)");
        System.out.println("Writing index file: " + outputFile);
        Writer writer = new BufferedWriter(new FileWriter(outputFile));
        for (int x : indices) {
            writer.write(x + "\n");
        }
        writer.close();
        System.out.println("Done");
    }

    private static byte[] readFile(String fileName) throws IOException {
        RandomAccessFile f = new RandomAccessFile(fileName, "r");
        byte[] data = new byte[(int) f.length()];
        f.readFully(data);
        f.close();
        return data;
    }

    private static void writeFile(String fileName, byte[] data) throws IOException {
        FileOutputStream out = new FileOutputStream(fileName);
        out.write(data);
        out.close();
    }

    private static ArrayList<Text> makeListUnique(ArrayList<Text> list) {
        long time = System.currentTimeMillis();
        Text.FastComparator comp = new Text.FastComparator();
        Collections.sort(list, comp);
        time = System.currentTimeMillis() - time;
        System.out.println("Sorted list (to check entries are unique) in " +
                time / 1000 + " seconds");
        if (comp.equalCount() == 0) {
            System.out.println("List is unique");
            return list;
        }
        time = System.currentTimeMillis();
        ArrayList<Text> list2 = new ArrayList<Text>(list.size());
        Text previous = null;
        for (Text t : list) {
            if (previous != null && previous.equals(t)) {
                continue;
            }
            list2.add(t);
            previous = t;
        }
        time = System.currentTimeMillis() - time;
        System.out.println("Made unique in " + time / 1000 + " seconds");
        return list2;
    }

    private static ArrayList<Text> readTextFile(String fileName) throws IOException {
        ArrayList<Text> list = new ArrayList<Text>();
        byte[] data = readFile(fileName);
        int end = Text.indexOf(data, 0, '\n');
        Text t = new Text(data, 0, end);
        System.out.println("Splitting into lines");
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
                System.out.print(100L * start / data.length + "% ");
                time = now;
            }
        }
        System.out.println();
        System.out.println("Lines: " + list.size());
        return list;
    }

    private void generateFromTextFile(int threadCount) throws IOException {
        ArrayList<Text> list = readTextFile(textFile);
        list = makeListUnique(list);
        // this is much slower and uses a lot of memory
        // HashSet<Text> set = new HashSet<Text>(list);
        System.out.println("Unique entries: " + list.size());
        System.out.println("Generating hash function...");
        long nanoSeconds = System.nanoTime();
        byte[] desc = RecSplitBuilder.
                newInstance(new Text.UniversalTextHash()).
                parallelism(threadCount).
                leafSize(leafSize).
                loadFactor(loadFactor).
                generate(list).toByteArray();
        nanoSeconds = System.nanoTime() - nanoSeconds;
        int seconds = (int) (nanoSeconds / 1000 / 1000000);
        System.out.println("Generated in " + seconds + " seconds, at " +
                nanoSeconds / list.size() + " ns/key, using " +
                threadCount + " thread(s)");
        System.out.println("Bytes: " + desc.length);
        int bits = desc.length * 8;
        System.out.println(((double) bits / list.size()) + " bits/key");
        if (hashFile != null) {
            System.out.println("Storing to hash file: " + hashFile);
            writeFile(hashFile, desc);
        }
        System.out.println("Done");
    }

    void printUsage(int threadCount) {
        System.out.println("Usage: java " + getClass().getName() + " [options]");
        System.out.println("Options:");
        System.out.println("-leafSize <integer>  " +
                "leafSize parameter, default " + leafSize);
        System.out.println("-loadFactor <integer>  " +
                "loadFactor parameter, default " + loadFactor);
        System.out.println("-textFile <fileName>  " +
                "read from the file, store the hash function in hashFile");
        System.out.println("-hashFile <fileName>  " +
                "file with the minimal perfect hash function");
        System.out.println("-indexFile <fileName> <outputFile>  " +
                "for each line, calculate the hash value (hashFile is used as input)");
        System.out.println("-threadCount <integer>  " +
                "use that many threads (default: " + threadCount + ")");
        System.out.println();
        System.out.println("Example (index one million entries, and evaluate):");
        System.out.println("seq 1 1000000 > seq.txt");
        System.out.println("java -cp bin org.minperf.TextFileTest " +
                "-textFile seq.txt -hashFile seq.bin " +
                "-threadCount 1 -repeat 3");
        System.out.println("java -cp bin org.minperf.TextFileTest " +
                "-hashFile seq.bin -indexFile seq.txt seq.index.txt " +
                "-threadCount 1 -repeat 3");
    }

}
