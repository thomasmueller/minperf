package org.minperf.eliasFano;

import java.util.BitSet;

import org.minperf.BitBuffer;
import org.minperf.select.VerySimpleSelect;

/**
 * A list of monotone increasing values. Each entry needs 2 + log(gap) bits,
 * where gap is the average gap between entries, plus the overhead for the
 * "select" structure. A lookup needs one "select" call, and is otherwise
 * constant.
 */
public class EliasFanoMonotoneList {

    private final BitBuffer buffer;
    private final int start;
    private final int lowBitCount;
    private final VerySimpleSelect select;

    private EliasFanoMonotoneList(BitBuffer buffer, int start, int lowBitCount, VerySimpleSelect select) {
        this.buffer = buffer;
        this.start = start;
        this.lowBitCount = lowBitCount;
        this.select = select;
    }

    public static EliasFanoMonotoneList generate(int[] data, BitBuffer buffer) {
        int len = data.length;
        // verify it is monotone
        for (int i = 1; i < len; i++) {
            if (data[i - 1] > data[i]) {
                throw new IllegalArgumentException();
            }
        }
        buffer.writeEliasDelta(len + 1);
        int max = data[len - 1];
        int lowBitCount = 32 - Integer.numberOfLeadingZeros(Integer.highestOneBit(max / len));
        buffer.writeEliasDelta(lowBitCount + 1);
        int start = buffer.position();
        BitSet set = new BitSet();
        for (int i = 0; i < len; i++) {
            int x = i + (data[i] >>> lowBitCount);
            set.set(x);
        }
        int mask = (1 << lowBitCount) - 1;
        for (int i = 0; i < len; i++) {
            buffer.writeNumber(data[i] & mask, lowBitCount);
        }
        VerySimpleSelect select = VerySimpleSelect.generate(set, buffer);
        return new EliasFanoMonotoneList(buffer, start, lowBitCount, select);
    }

    public static EliasFanoMonotoneList load(BitBuffer buffer) {
        int len = (int) (buffer.readEliasDelta() - 1);
        int lowBitCount = (int) (buffer.readEliasDelta() - 1);
        int start = buffer.position();
        buffer.seek(start + len * lowBitCount);
        VerySimpleSelect select = VerySimpleSelect.load(buffer);
        return new EliasFanoMonotoneList(buffer, start, lowBitCount, select);
    }

    public int get(int i) {
        int low = (int) buffer.readNumber(start + i * lowBitCount, lowBitCount);
        int high = (int) select.select(i) - i;
        return (high << lowBitCount) + low;
    }

    public long getPair(int i) {
        long lowPair = buffer.readNumber(start + i * lowBitCount, lowBitCount + lowBitCount);
        int low1 = (int) (lowPair >>> lowBitCount);
        int low2 = (int) (lowPair - ((long) low1 << lowBitCount));
        long highPair = select.selectPair(i);
        int high1 = (int) (highPair >>> 32) - i;
        int high2 = (int) highPair - i - 1;
        int result1 = (high1 << lowBitCount) + low1;
        int result2 = (high2 << lowBitCount) + low2;
        return ((long) result1 << 32) | result2;
    }

}
