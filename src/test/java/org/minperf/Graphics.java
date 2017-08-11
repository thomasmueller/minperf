package org.minperf;

import java.util.HashMap;
import java.util.HashSet;

import org.minperf.generator.Generator;
import org.minperf.monotoneList.MonotoneList;
import org.minperf.universal.LongHash;
import org.minperf.universal.UniversalHash;

/**
 * A tool to generate Tikz graphics and a textual description of a hash function.
 */
public class Graphics {

    public static void main(String... args) {
        generateSplitTrees();
        // generateSampleTikz();
    }

    public static void generateSplitTrees() {
        int leafSize = 6;
        int columns = 6;
        System.out.print("\\begin{tabular}{");
        for (int i = 0; i < columns; i++) {
            System.out.print("|c");
        }
        System.out.println("|}");
        HashMap<Integer, Double> cache = new HashMap<>();
        for (int size = 0; size < 36; size++) {
            if (size % columns == 0) {
                System.out.println("\\hline");
            } else {
                System.out.println("&");
            }
            System.out.println(generateSplitTree(leafSize, size));
            if (size % columns == columns - 1) {
                System.out.println("\\\\");
                for (int i = 0; i < columns; i++) {
                    if (i > 0) {
                        System.out.print(" & ");
                    }
                    int s = (size - columns + 1 + i);
                    System.out.print(s + " entries");
                }
                System.out.println("\\\\");
                int averageBucketSize = 1024;
                Settings settings = new Settings(leafSize, averageBucketSize);
                for (int i = 0; i < columns; i++) {
                    if (i > 0) {
                        System.out.print(" & ");
                    }
                    int s = (size - columns + 1 + i);
                    double bits = SpaceEstimator.getExpectedBucketSpace(settings, s, 0, cache);
                    if (s > 0) {
                        bits /= s;
                    }
                    System.out.printf("%2.2f bits/key", bits);
                }
                System.out.println("\\\\");
            }
        }
    }

    public static String generateSplitTree(int leafSize, int size) {
        if (size < 1) {
            return "";
        }
        int averageBucketSize = 1024;
        Settings settings = new Settings(leafSize, averageBucketSize);
        StringBuilder buff = new StringBuilder();
        buff.append("% size " + size + "\n");
        buff.append("\\begin{tikzpicture}\n");
        buff.append("\\node {} child[grow cyclic, rotate=-90, sibling angle=30, " + getSizeTikz(size)
                    + ", level distance=7mm] {\n");
        buff.append(generateSampleTikz(settings, size) + " }; \n");
        buff.append("\\end{tikzpicture}");
        return buff.toString();
    }

    public static void generateSampleTikz() {
        StringBuilder bits = new StringBuilder();
        System.out.println("4.4 Data Format");
        int leafSize = 6;
        int averageBucketSize = 32;
        int size = 70;
        HashSet<Long> set = RandomizedTest.createSet(size, 6);
        UniversalHash<Long> hash = new LongHash();
        Settings settings = new Settings(leafSize, averageBucketSize);
        boolean eliasFano = true;
        BitBuffer buff = RecSplitBuilder.newInstance(hash).
                eliasFanoMonotoneLists(eliasFano).
                leafSize(leafSize).averageBucketSize(averageBucketSize).generate(set);
        buff.seek(0);
        System.out.println("\\begin{tikzpicture}");
        System.out.println("\\node {}");
        System.out.println("child[line width=1ex, level distance=6mm] {");
        System.out.println("child[edge from parent=draw, line width=.1ex, sibling distance=30mm, level distance=0mm] {");
        bits.append(" & Header \\\\\n");
        long size2 = buff.readEliasDelta() - 1;
        appendLastBits(bits, buff, buff.position());
        bits.append(" & size: " + size2 + " (Elias Delta code, plus 1)\\\\\n");
        boolean alternativeHashOption = buff.readBit() != 0;
        appendLastBits(bits, buff, 1);
        bits.append(" & alternativeHash: " + alternativeHashOption + " (0 false, 1 true)\\\\\n");
        int bucketCount = (size + (averageBucketSize - 1)) / averageBucketSize;
        int start = buff.position();
        int minOffsetDiff = (int) (buff.readEliasDelta() - 1);
        MonotoneList offsetList = MonotoneList.load(buff, eliasFano);
        appendLastBits(bits, buff, buff.position() - start);
        bits.append(" & offset list (an EliasFano monotone list)\\\\\n");
        start = buff.position();
        int minStartDiff = (int) (buff.readEliasDelta() - 1);
        MonotoneList startList = MonotoneList.load(buff, eliasFano);
        appendLastBits(bits, buff, buff.position() - start);
        bits.append(" & start list (an EliasFano monotone list)\\\\\n");
        int startBuckets = buff.position();
        for (int x = 0; x < bucketCount; x++) {
            int offset = 0;
            long offsetPair = offsetList.getPair(x);
            int o = (int) (offsetPair >>> 32) + x * minOffsetDiff;
            offset += o;
            int offsetNext = ((int) offsetPair) + (x + 1) * minOffsetDiff;
            int bucketSize = offsetNext - o;
            int startPos = startBuckets +
                    Generator.getMinBitCount(offset) +
                    startList.get(x) + x * minStartDiff;
            System.out.println("  child["+getSizeTikz(bucketSize)+"] {child[level distance=8mm]{node {$b_" + x + "$}");
            System.out.println("    child[grow cyclic, rotate=-90, sibling angle=30, "+
            getSizeTikz(bucketSize)+"] {");
            buff.seek(startPos);
            bits.append("\\hline\n");
            bits.append(" & Bucket " + x + " \\\\\n");
            generateBitDescription(settings, buff, bits, bucketSize);
            String t = generateSampleTikz(settings, bucketSize);
            System.out.println("    " + t);
            System.out.println("  }}}");
        }
        System.out.println(";}};");
        System.out.println("\\end{tikzpicture}");
        System.out.println(bits.toString());
    }

    private static String generateSampleTikz(Settings settings, int size) {
        String t = generateSampleTikzWithX(settings, size);
        for (int i = 0;; i++) {
            if (t.indexOf("$x$") < 0) {
                break;
            }
            t = t.replaceFirst("\\$x\\$", "\\$_{" + (char) ('a' + i) + "}\\$");
        }
        return t;
    }

    private static String generateSampleTikzWithX(Settings settings, int size) {
        if (size == 0) {
            return "node {}";
        } else if (size == 1) {
            return "node {} child[sibling angle=5, level distance=5mm, " +
                    getSizeTikz(1) + "]";
        }
        if (size <= settings.getLeafSize()) {
            StringBuilder buff = new StringBuilder();
            buff.append("node {$x$} child[sibling angle=5, level distance=5mm, "+
                    getSizeTikz(1)+"] foreach \\x in {");
            for (int i = 0; i < size; i++) {
                if (i > 0) {
                    buff.append(", ");
                }
                buff.append(i);
            }
            buff.append("} ");
            return buff.toString();
        }
        int split = settings.getSplit(size);
        int firstPart, otherPart;
        if (split < 0) {
            firstPart = -split;
            otherPart = size - firstPart;
            split = 2;
        } else {
            firstPart = size / split;
            otherPart = firstPart;
        }
        int childSize = firstPart;
        StringBuilder buff = new StringBuilder();
        buff.append("node {$x$} ");
        int angle = 30;
        if (size / split <= settings.getLeafSize()) {
            angle = 15;
        }
        for (int i = 0; i < split; i++) {
            buff.append("child[sibling angle=" + angle + ", "+getSizeTikz(size)+"] { ");
            buff.append(generateSampleTikzWithX(settings, childSize));
            buff.append("} ");
            childSize = otherPart;
        }
        return buff.toString();
    }

    private static String getSizeTikz(int size) {
        if (size == 1) {
            return "line width=0.02em";
        }
        return "line width="+(size/35.)+"ex";
    }

    private static void generateBitDescription(Settings settings, BitBuffer in, StringBuilder bits, int size) {
        if (size <= 1) {
            return;
        }
        if (size <= settings.getLeafSize()) {
            int shift = settings.getGolombRiceShift(size);
            long x = in.readGolombRice(shift);
            int count = BitBuffer.getGolombRiceSize(shift, x);
            appendLastBits(bits, in, count);
            bits.append(" & index: " + x + " (leaf of size " + size + ", k=" + shift +") \\\\\n");
            return;
        }
        int shift = settings.getGolombRiceShift(size);
        long x = in.readGolombRice(shift);
        int count = BitBuffer.getGolombRiceSize(shift, x);
        appendLastBits(bits, in, count);
        bits.append(" & index: " + x + " (inner node of size " + size +
                ", k=" + shift +") \\\\\n");

        int split = settings.getSplit(size);
        int firstPart, otherPart;
        if (split < 0) {
            firstPart = -split;
            otherPart = size - firstPart;
            split = 2;
        } else {
            firstPart = size / split;
            otherPart = firstPart;
        }
        int childSize = firstPart;
        for (int i = 0; i < split; i++) {
            generateBitDescription(settings, in, bits, childSize);
            childSize = otherPart;
        }
    }

    private static void appendLastBits(StringBuilder bits, BitBuffer in,
            int count) {
        in.seek(in.position() - count);
        int i = 0;
        if (count > 15) {
            for (; i < 5; i++) {
                long x = in.readBit();
                bits.append((char) ('0' + x));
            }
            bits.append("...");
            for (; i < count - 5; i++) {
                in.readBit();
            }
        }
        for (; i < count; i++) {
            long x = in.readBit();
            bits.append((char) ('0' + x));
        }
    }

}
