package org.minperf.eliasFano;

import java.util.BitSet;

import org.minperf.BitBuffer;
import org.minperf.rank.SimpleRankSelect;

/**
 * A list of monotone increasing values. Each entry needs 2 + log(gap) bits,
 * where gap is the average gap between entries, plus the overhead for the
 * "select" structure. A lookup needs one "select" lookup, and is otherwise
 * constant.
 */
public class EliasFanoMonotoneList {

    private final BitBuffer buffer;
    private final int start;
    private final int lowBitCount;
    private final SimpleRankSelect select;

    private EliasFanoMonotoneList(BitBuffer buffer, int start, int lowBitCount, SimpleRankSelect select) {
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
                throw new AssertionError();
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
        SimpleRankSelect select = SimpleRankSelect.generate(set, buffer);
        return new EliasFanoMonotoneList(buffer, start, lowBitCount, select);
    }

    public static EliasFanoMonotoneList load(BitBuffer buffer) {
        int len = (int) (buffer.readEliasDelta() - 1);
        int lowBitCount = (int) (buffer.readEliasDelta() - 1);
        int start = buffer.position();
        buffer.seek(start + len * lowBitCount);
        SimpleRankSelect select = SimpleRankSelect.load(buffer);
        return new EliasFanoMonotoneList(buffer, start, lowBitCount, select);
    }

    public int get(int i) {
        int low = (int) buffer.readNumber(start + i * lowBitCount, lowBitCount);
        int high = (int) select.select(i) - i;
        return (high << lowBitCount) + low;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " lowBitCount " + lowBitCount + " select " + select;
    }

}
