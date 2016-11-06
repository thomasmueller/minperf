package org.minperf.select;

//import it.unimi.dsi.bits.LongArrayBitVector;
//import it.unimi.dsi.sux4j.bits.SimpleSelect;
//
//import java.io.ByteArrayInputStream;
//import java.io.ByteArrayOutputStream;
//import java.io.IOException;
//import java.io.ObjectInputStream;
//import java.io.ObjectOutputStream;
//import java.util.BitSet;
//
//import org.minperf.BitBuffer;

/**
 * A select implementation with guaranteed O(1) query time. This is a wrapper
 * around the the (very good) implementation in Sux4J
 * it.unimi.dsi.sux4j.bits.SimpleSelect by Sebastiano Vigna.
 */
public class SimpleSelectWrapper {
//    extends Select {
//
//    private final SimpleSelect select;
//
//    private SimpleSelectWrapper(SimpleSelect select) {
//        this.select = select;
//    }
//
//    public static SimpleSelectWrapper generate(BitSet bitSet, BitBuffer buffer) {
//        int len = bitSet.length();
//        LongArrayBitVector bv = LongArrayBitVector.ofLength(len);
//        for (int i = 0; i < len; i++) {
//            if (bitSet.get(i)) {
//                bv.set(i);
//            }
//        }
//        SimpleSelect select = new SimpleSelect(bv);
//        ByteArrayOutputStream out = new ByteArrayOutputStream();
//        try {
//            ObjectOutputStream oo = new ObjectOutputStream(out);
//            oo.writeObject(select);
//        } catch (IOException e) {
//            throw new RuntimeException();
//        }
//        byte[] data = out.toByteArray();
//        buffer.writeEliasDelta(data.length + 1);
//        for (byte b : data) {
//            buffer.writeNumber(b & 255, 8);
//        }
//        return new SimpleSelectWrapper(select);
//    }
//
//    public static Select load(BitBuffer buffer) {
//        int len = (int) buffer.readEliasDelta() - 1;
//        byte[] data = new byte[len];
//        for (int i = 0; i < len; i++) {
//            data[i] = (byte) buffer.readNumber(8);
//        }
//        SimpleSelect select;
//        ByteArrayInputStream in = new ByteArrayInputStream(data);
//        try {
//            ObjectInputStream oi = new ObjectInputStream(in);
//            select = (SimpleSelect) oi.readObject();
//        } catch (Exception e) {
//            throw new RuntimeException();
//        }
//        return new SimpleSelectWrapper(select);
//    }
//
//    @Override
//    public long select(long x) {
//        return select.select(x);
//    }
//
//    @Override
//    public long selectPair(long x) {
//        long[] dest = new long[2];
//        select.select(x, dest);
//        return (dest[0] << 32) | dest[1];
//    }

}
