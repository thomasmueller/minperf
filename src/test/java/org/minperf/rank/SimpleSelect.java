package org.minperf.rank;

import java.util.ArrayList;
import java.util.BitSet;

import org.minperf.BitBuffer;

/**
 * A simple select data structure implementation for a list of bits.
 * <p>
 * Select(x) gets the position of the xth 1 bit (positions starting at 0).
 * <p>
 * The select operation is fast and space usage is quite low, but there is no
 * strict guarantee that space usage is O(n) and the select operation is
 * constant time, in the mathematical sense.
 */
public class SimpleSelect {

    public static final byte[] SELECT_BIT_IN_BYTE;
    public static final byte[] SELECT_BIT_IN_BYTE_REVERSE;

    static {
        byte[] data = new byte[256 * 8];
        byte[] reverse = new byte[256 * 8];
        for (int n = 0; n < 8; n++) {
            for (int i = 0; i < 256; i++) {
                data[i + (n << 8)] = (byte) selectBitSlow(i, n);
                reverse[i + (n << 8)] = (byte) selectBitSlowReverse((long) i << (64 - 8), n);
            }
        }
        SELECT_BIT_IN_BYTE = data;
        SELECT_BIT_IN_BYTE_REVERSE = reverse;
    }

    // must be a power of 2
    private static final int BITS_PER_BLOCK = 32;
    private static final int BITS_PER_BLOCK_SHIFT =
            31 - Integer.numberOfLeadingZeros(BITS_PER_BLOCK);

    private final BitBuffer buffer;
    private final int blockCount;
    private final long blockCountScale;
    private final int size;
    private final int cardinality;
    private final int bitCount;
    private final int added;
    private final int offsetPos;
    private final int dataPos;

    private SimpleSelect(BitBuffer buffer) {
        this.buffer = buffer;
        this.size = (int) (buffer.readEliasDelta() - 1);
        this.cardinality = (int) (buffer.readEliasDelta() - 1);
        this.blockCount = (cardinality + BITS_PER_BLOCK - 1) / BITS_PER_BLOCK;
        this.blockCountScale = getScaleFactor(size, blockCount);
        this.added = (int) (buffer.readEliasDelta() - 1);
        this.bitCount = (int) (buffer.readEliasDelta() - 1);
        this.offsetPos = buffer.position();
        this.dataPos = offsetPos + bitCount * blockCount;
        buffer.seek(dataPos + size);
    }

    /**
     * Generate a rank/select object, and store it into the provided buffer.
     *
     * @param set the bit set
     * @param buffer the buffer
     * @return the generated object
     */
    public static SimpleSelect generate(BitSet set, BitBuffer buffer) {
        int start = buffer.position();
        int size = set.length() + 1;
        buffer.writeEliasDelta(size + 1);
        int cardinality = set.cardinality();
        buffer.writeEliasDelta(cardinality + 1);
        int blockCount = (cardinality + BITS_PER_BLOCK - 1) / BITS_PER_BLOCK;
        ArrayList<Integer> list = new ArrayList<Integer>();
        int pos = set.nextSetBit(0);
        for (int i = 0; i < blockCount; i++) {
            list.add(pos);
            for (int j = 0; j < BITS_PER_BLOCK; j++) {
                pos = set.nextSetBit(pos + 1);
            }
        }
        long blockCountScale = getScaleFactor(size, blockCount);
        int minDiff = Integer.MAX_VALUE;
        for (int i = 0; i < list.size(); i++) {
            // int expected = (int) ((long) size * i / blockCount);
            int expected = (int) ((i * blockCountScale) >>> 32);
            int got = list.get(i);
            int diff = got - expected;
            list.set(i, diff);
            minDiff = Math.min(minDiff, diff);
        }
        int max = Integer.MIN_VALUE;
        for (int i = 0; i < list.size(); i++) {
            int x = list.get(i) - minDiff;
            max = Math.max(max, x);
            list.set(i, x);
        }
        buffer.writeEliasDelta(-minDiff + 1);
        int bitCount = 32 - Integer.numberOfLeadingZeros(max);
        buffer.writeEliasDelta(bitCount + 1);
        for (int i = 0; i < list.size(); i++) {
            buffer.writeNumber(list.get(i), bitCount);
        }
        for (int i = 0; i < size; i++) {
            buffer.writeBit(set.get(i) ? 1 : 0);
        }
        buffer.seek(start);
        return new SimpleSelect(buffer);
    }

    private static long getScaleFactor(int multiply, int divide) {
        return divide == 0 ? 0 : ((long) multiply << 32) / divide + 1;
    }

    /**
     * Generate a rank/select object from the provided buffer.
     *
     * @param buffer the buffer
     * @return the loaded object
     */
    public static SimpleSelect load(BitBuffer buffer) {
        return new SimpleSelect(buffer);
    }

    /**
     * Get the position of the xth 1 bit.
     *
     * @param x the value (starting with 0)
     * @return the position (0 is the first bit), or -1 if x is too large
     */
    public long select(long x) {
        int block = (int) (x >>> BITS_PER_BLOCK_SHIFT);
        int expected = (int) ((block * blockCountScale) >>> 32);
        long read = buffer.readNumber(offsetPos + block * bitCount, bitCount);
        long result = expected + read - added;
        int remaining = (int) (x - ((long) block << BITS_PER_BLOCK_SHIFT));
        while (true) {
            int data = (int) buffer.readNumber((int) result + dataPos, 32);
            int bitCount = Integer.bitCount(data);
            if (remaining < bitCount) {
                return result + selectBitReverse(data, remaining);
            }
            result += 32;
            remaining -= bitCount;
        }
    }

    public static int selectBitSlowReverse(long x, int n) {
        return selectBitSlow(Long.reverse(x), n);
    }

    public static int selectBitSlow(long x, int n) {
        n++;
        for (int i = 0; i < 64; i++) {
            if ((x & 1) == 1) {
                n--;
                if (n == 0) {
                    return i;
                }
            }
            x >>>= 1;
        }
        return -1;
    }

    public static int selectBitLongReverse(long x, int n) {
        // int bitCount = Long.bitCount(x & 0xffffffff00000000L);
        int bitCount = Long.bitCount(x >>> 32);
        int more = (bitCount - n - 1) >> 31;
        int result = more & 32;
        n -= bitCount & more;
        // bitCount = Long.bitCount((x << result) & 0xffff000000000000L);
        bitCount = Long.bitCount((x << result) >>> 48);
        more = (bitCount - n - 1) >> 31;
        result += more & 16;
        n -= bitCount & more;
        // bitCount = Long.bitCount((x << result) & 0xff00000000000000L);
        bitCount = Long.bitCount((x << result) >>> 56);
        more = (bitCount - n - 1) >> 31;
        result += more & 8;
        n -= bitCount & more;
        return SELECT_BIT_IN_BYTE_REVERSE[(int) ((x << result) >>> 56) | (n << 8)] + result;
    }

    public static int selectBitLong(long x, int n) {
        int bitCount = Long.bitCount(x & 0xffffffffL);
        int more = (bitCount - n - 1) >> 31;
        int result = more & 32;
        n -= bitCount & more;
        bitCount = Long.bitCount((x >>> result) & 0xffff);
        more = (bitCount - n - 1) >> 31;
        result += more & 16;
        n -= bitCount & more;
        bitCount = Long.bitCount((x >>> result) & 0xff);
        more = (bitCount - n - 1) >> 31;
        result += more & 8;
        n -= bitCount & more;
        return SELECT_BIT_IN_BYTE[(int) ((x >>> result) & 0xff) | (n << 8)] + result;
    }

    public static int selectBitReverse(int x, int n) {
        // int bitCount = Integer.bitCount(x & 0xffff0000);
        int bitCount = Integer.bitCount(x >>> 16);
        int more = (bitCount - n - 1) >> 31;
        int result = more & 16;
        n -= bitCount & more;
        // bitCount = Integer.bitCount((x << result) & 0xff000000);
        bitCount = Integer.bitCount((x << result) >>> 24);
        more = (bitCount - n - 1) >> 31;
        result += more & 8;
        n -= bitCount & more;
        return SELECT_BIT_IN_BYTE_REVERSE[((x << result) >>> 24) | (n << 8)] + result;
    }

    public static int selectBit(int x, int n) {
        int bitCount = Integer.bitCount(x & 0xffff);
        int more = (bitCount - n - 1) >> 31;
        int result = more & 16;
        n -= bitCount & more;
        bitCount = Integer.bitCount((x >>> result) & 0xff);
        more = (bitCount - n - 1) >> 31;
        result += more & 8;
        n -= bitCount & more;
        return SELECT_BIT_IN_BYTE[((x >>> result) & 0xff) | (n << 8)] + result;
    }

}
