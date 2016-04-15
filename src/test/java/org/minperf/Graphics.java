package org.minperf;

import java.util.HashSet;

import org.minperf.universal.LongHash;
import org.minperf.universal.UniversalHash;

/**
 * A tool to generate Tikz graphics and a textual description of a hash function.
 */
public class Graphics {

    public static void generateSampleTikz() {
        StringBuilder bits = new StringBuilder();
        System.out.println("4.4 Data Format");
        int leafSize = 6;
        int loadFactor = 32;
        int size = 70;
        //int size = 97;
        HashSet<Long> set = RandomizedTest.createSet(size, 6);
        UniversalHash<Long> hash = new LongHash();
        Settings settings = new Settings(leafSize, loadFactor);
        BitBuffer buff = RecSplitBuilder.newInstance(hash).leafSize(leafSize).loadFactor(loadFactor).generate(set);
        buff.seek(0);
        System.out.println("\\begin{tikzpicture}");
        System.out.println("\\node {}");
        System.out.println("child[line width=1ex, level distance=6mm] {");
        System.out.println("child[edge from parent=draw, line width=.1ex, sibling distance=30mm, level distance=0mm] {");
        bits.append(" & Header \\\\\n");
        long size2 = buff.readEliasDelta() - 1;
        appendLastBits(bits, buff, buff.position());
        bits.append(" & size: " + size2 + " (Elias Delta code, plus 1)\\\\\n");
        bits.append("\\hline\n");
        bits.append(" & Bucket Header \\\\\n");
        int startDataSize = buff.position();
        long dataBits = settings.getEstimatedBits(size) + 
                BitBuffer.unfoldSigned(buff.readEliasDelta() - 1);
        appendLastBits(bits, buff, buff.position() - startDataSize);
        bits.append(" & data bits: " + dataBits + " (Elias Delta code of difference, plus 1)\\\\\n");
        int startBitsPerEntry = buff.position();
        int bitsPerEntry = (int) buff.readGolombRice(2);
        appendLastBits(bits, buff, buff.position() - startBitsPerEntry);
        bits.append(" & bits per offset array entry: " + bitsPerEntry + " (Rice code with k=2)\\\\\n");
        int bucketCount = (size + (loadFactor - 1)) /
                loadFactor;
        int tableStart = buff.position();
        int tableBits = (bitsPerEntry + bitsPerEntry) * (bucketCount - 1);
        int headerBits = tableStart + tableBits;
        int[] pSizeList = new int[bucketCount]; 
        int[] startList = new int[bucketCount];
        bits.append("\\hline\n");
        bits.append(" & Offset Array \\\\\n");
        for (int x = 0; x < bucketCount; x++) {
            int add, start, pSize;
            if (x == 0) {
                add = 0;
                start = 0;
            } else {
                int pos = tableStart + (bitsPerEntry + bitsPerEntry) * (x - 1);
                buff.seek(pos);
                int expectedAdd = (int) ((long) size * x / bucketCount);
                int expectedStart = (int) (dataBits * x / bucketCount);
                int add0 = (int) buff.readNumber(bitsPerEntry);
                bits.append(String.format("%"+bitsPerEntry+"s", Integer.toBinaryString(add0)).replace(' ',  '0'));
                bits.append(" & add offset[" + x + "]: " + add0 + "\\\\\n");
                add = (int) BitBuffer.unfoldSigned(add0) + expectedAdd;
                int start0 = (int) buff.readNumber(bitsPerEntry);
                bits.append(String.format("%"+bitsPerEntry+"s", Integer.toBinaryString(start0)).replace(' ',  '0'));
                bits.append(" & start offset[" + x + "]: " + start0 + "\\\\\n");
                start = (int) BitBuffer.unfoldSigned(start0) + expectedStart;
            }
            if (x < bucketCount - 1) {
                int expectedAdd = (int) ((long) size * (x + 1) / bucketCount);
                int nextAdd = (int) BitBuffer.unfoldSigned(buff.readNumber(bitsPerEntry)) + expectedAdd;
                pSize = nextAdd - add;
            } else {
                pSize = size - add;
            }
            pSizeList[x] = pSize;
            startList[x] = start;
        }
        for (int x = 0; x < bucketCount; x++) {
            int start = startList[x];
            int pSize = pSizeList[x];
            System.out.println("  child["+getSizeTikz(pSize)+"] {child[level distance=8mm]{node {$b_" + x + "$}");
            System.out.println("    child[grow cyclic, rotate=-90, sibling angle=30, "+
            getSizeTikz(pSize)+"] {");
            buff.seek(headerBits + start);
            bits.append("\\hline\n");
            bits.append(" & Bucket " + x + " \\\\\n");
            String t = generateSampleTikz(settings, buff, bits, pSize);
            for (int i = 0;; i++) {
                if (t.indexOf("$x$") < 0) {
                    break;
                }
                t = t.replaceFirst("\\$x\\$", "\\$_{" + i + "}\\$");
            }
            System.out.println("    " + t);
            System.out.println("  }}}");
        }
        System.out.println(";}};");
        System.out.println("\\end{tikzpicture}");
        System.out.println(bits.toString());
    }
    
    private static String getSizeTikz(int size) {
        if (size == 1) {
            return "line width=0.02em";
        }
        return "line width="+(size/35.)+"ex";
    }
    
    private static String generateSampleTikz(Settings settings, BitBuffer in, StringBuilder bits, int size) {
        if (size == 0) {
            return "node {$x$}";
        } else if (size == 1) {
            return "node {$x$} child[sibling angle=5, level distance=6mm, " +
                    getSizeTikz(1) + "]";
        }
        if (size <= settings.getLeafSize()) {
            int shift = settings.getGolombRiceShift(size);
            long x = in.readGolombRice(shift);
            int count = BitBuffer.getGolombRiceSize(shift, x);
            appendLastBits(bits, in, count);
            bits.append(" & index: " + x + " (leaf of size " + size + ", k=" + shift +") \\\\\n");
            StringBuilder buff = new StringBuilder();
            buff.append("node {$x$} child[sibling angle=5, level distance=6mm, "+
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
        StringBuilder buff = new StringBuilder();
        buff.append("node {$x$} ");
        int angle = 30;
        if (size / split <= settings.getLeafSize()) {
            angle = 15;
        }
        for (int i = 0; i < split; i++) {
            buff.append("child[sibling angle=" + angle + ", "+getSizeTikz(size)+"] { ");
            buff.append(generateSampleTikz(settings, in, bits, childSize));
            buff.append("} ");
            childSize = otherPart;
        }
        return buff.toString();
    }

    private static void appendLastBits(StringBuilder bits, BitBuffer in,
            int count) {
        in.seek(in.position() - count);
        for (int i = 0; i < count; i++) {
            long x = in.readBit();
            bits.append((char) ('0' + x));
        }
    }

}
