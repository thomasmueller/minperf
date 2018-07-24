package org.minperf.bloom.utils;

public class BitArray {

    private final byte[] data;
    private final long size;
    private final int arraySize;
    private final int bitsPerEntry;
    private final int mask;

    public BitArray(long size, int bitsPerEntry) {
        this.size = size;
        this.bitsPerEntry = bitsPerEntry;
        this.mask = (1 << bitsPerEntry) - 1;
        int bytes;
        if (bitsPerEntry >= 1 && bitsPerEntry <= 15) {
            // two additional bytes, as we always read 3 bytes
            bytes = (int) ((size * bitsPerEntry + 3 * 8 - 1) / 8);
        } else {
            throw new IllegalArgumentException("Supported bits per entry: 1-15");
        }
        this.arraySize = bytes;
        data = new byte[arraySize];
    }

    public int get2(long index) {
        long bitPos = index * bitsPerEntry;
        int firstBytePos = (int) (bitPos >>> 3);
        int word = ((data[firstBytePos] & 0xff) << 8) |
                (data[firstBytePos + 1] & 0xff);
        return (word >>> (0x10 - bitsPerEntry - (bitPos & 7))) & mask;
    }

    public int get3(long index) {
        long bitPos = index * bitsPerEntry;
        int firstBytePos = (int) (bitPos >>> 3);
        int word = ((data[firstBytePos] & 0xff) << 16) |
                ((data[firstBytePos + 1] & 0xff) << 8) |
                ((data[firstBytePos + 2] & 0xff));
        return (word >>> (24 - bitsPerEntry - (bitPos & 7))) & mask;
    }

    public void put2(long pos, int value) {
        long bitPos = pos * bitsPerEntry;
        int firstBytePos = (int) (bitPos >>> 3);
        int word = ((data[firstBytePos] & 0xff) << 8) |
                (data[firstBytePos + 1] & 0xff);
        word &= ~(mask << (0x10 - bitsPerEntry - (bitPos & 7)));
        word |= ((value & mask) << (0x10 - bitsPerEntry - (bitPos & 7)));
        data[firstBytePos] = (byte) (word >>> 8);
        data[firstBytePos + 1] = (byte) word;
    }

    public void put3(long pos, int value) {
        long bitPos = pos * bitsPerEntry;
        int firstBytePos = (int) (bitPos >>> 3);
        int word = ((data[firstBytePos] & 0xff) << 16) |
                ((data[firstBytePos + 1] & 0xff) << 8) |
                ((data[firstBytePos + 2] & 0xff));
        word &= ~(mask << (24 - bitsPerEntry - (bitPos & 7)));
        word |= ((value & mask) << (24 - bitsPerEntry - (bitPos & 7)));
        data[firstBytePos] = (byte) (word >>> 16);
        data[firstBytePos + 1] = (byte) (word >>> 8);
        data[firstBytePos + 2] = (byte) word;
    }

    public long getSize() {
        return size;
    }

}
