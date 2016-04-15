package org.minperf;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

/**
 * A simple bit buffer.
 */
public class BitBuffer {
    
    private final int[] data;
    private int pos;
    private long currentRead;
    
    public BitBuffer(byte[] data) {
        this.data = new int[(data.length + 3) / 4];
        if (this.data.length != data.length * 4) {
            data = Arrays.copyOf(data, this.data.length * 4);
        }
        ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).asIntBuffer().get(this.data);
    }

    /**
     * Create a buffer that shared the byte data, but uses a separate position
     * (initially 0).
     * 
     * @param buffer the buffer
     */
    BitBuffer(BitBuffer buffer) {
        this.data = buffer.data;
   }

    public void write(BitBuffer bits) {
        int count = bits.pos;
        bits.pos = 0;
        for (int i = 0; i < count; i++) {
            writeBit(bits.readBit());
        }
    }

    public int position() {
        return pos;
    }

    public void seek(int pos) {
        currentRead = 0;
        this.pos = pos;
    }

    public long readNumber(int bitCount) {
        long x = 0;
        while (bitCount-- > 0) {
            x = (x << 1) | readBit();
        }
        return x;
    } 
    
    /**
     * Fold a signed number into an unsigned number. Negative numbers are odd,
     * and positive numbers are even. For example, -5 is converted to 11, and 5
     * to 10.
     * 
     * @param x a signed number
     * @return an unsigned number
     */
    public static long foldSigned(long x) {
        return x > 0 ? x * 2 - 1 : -x * 2;
//        return (Math.abs(x) << 1) | (x >>> 63);
    }

    /**
     * Unfold an unsigned number into a signed number.
     * 
     * @param x an unsigned number
     * @return a signed number
     */
    public static long unfoldSigned(long x) {
        return ((x & 1) == 1) ? (x + 1) / 2 : -(x / 2);
//        return (x >>> 1) * (1 | -(x & 1));
    }

    private static int index(int pos) {
        return pos >>> 5;
    }
    
    private static int bitMask(int pos) {
        return 1 << (pos & 31);
    }
    
    public void writeBit(long x) {
        currentRead = 0;
        if (x == 1) {
            data[index(pos)] |= bitMask(pos);
        }
        pos++;
    }
    
    private long readNextBytes() {
        int index = index(pos);
        currentRead = (
                (1L << 32) | (data[index] & 0xffffffffL))
                >> (pos & 31);
        return currentRead;
    }

    public long readBit() {
        // return (data[index(pos)] >>> (pos++ & 7)) & 1;
        long x = currentRead;
        if (x <= 1) {
            x = readNextBytes();
        }
        currentRead >>= 1;
        pos++;
        return x & 1;
    }
    
    public void writeGolombRice(int shift, long value) {
        long q = value >>> shift;
        for (int i = 0; i < q; i++) {
            writeBit(1);
        }
        writeBit(0);
        for (int i = shift - 1; i >= 0; i--) {
            writeBit((value >>> i) & 1);
        }
    }
   
    public long readGolombRice(int shift) {
        int q = 0;
        while (readBit() == 1) {
            q++;
        }
        long x = ((long) q) << shift;
        for (int i = shift - 1; i >= 0; i--) {
            x |= readBit() << i;
        }
        return x;
    }
    
    public void writeEliasDelta(long value) {
        int q = 64 - Long.numberOfLeadingZeros(value);
        int qq = 31 - Integer.numberOfLeadingZeros(q);
        for (int i = 0; i < qq; i++) {
            writeBit(0);
        }
        for (int i = qq; i >= 0; i--) {
            writeBit((q >>> i) & 1);
        }
        for (int i = q - 2; i >= 0; i--) {
            writeBit((value >>> i) & 1);
        }
    }
    
    public long readEliasDelta() {
        int qq = 0;
        while (readBit() == 0) {
            qq++;
        }   
        long q = 1;
        for (int i = qq; i > 0; i--) {
            q = (q << 1) | readBit();
        }
        long x = 1;
        for (long i = q - 2; i >= 0; i--) {
            x = (x << 1) | readBit();
        }
        return x;
    }
    
    /**
     * Write a number of bits. The most significant bit is written first.
     * 
     * @param x the number
     * @param bitCount the number of bits
     */
    public void writeNumber(long x, int bitCount) {
        while (bitCount-- > 0) {
            writeBit((int) ((x >>> bitCount) & 1));
        }
    }
    
    public byte[] toByteArray() {
        byte[] d = new byte[data.length * 4];
        ByteBuffer.wrap(d).order(ByteOrder.LITTLE_ENDIAN).asIntBuffer()
                .put(data, 0, (pos + 31) / 32);
        if ((pos + 7) / 8 == d.length) {
            return d;
        }
        return Arrays.copyOf(d, (pos + 7) / 8);
    }

    public static int getGolombRiceSize(int shift, long value) {
        return (int) ((value >>> shift) + 1 + shift);
    }
    
}