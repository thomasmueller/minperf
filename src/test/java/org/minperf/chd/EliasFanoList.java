package org.minperf.chd;

import org.minperf.BitBuffer;
import org.minperf.monotoneList.EliasFanoMonotoneList;

public class EliasFanoList {

    private final int offset;
    private final BitBuffer buff;
    private final int bitsStart;
    private final EliasFanoMonotoneList borders;

    EliasFanoList(int offset, BitBuffer buff, int bitsStart, EliasFanoMonotoneList borders) {
        this.buff = buff;
        this.bitsStart = bitsStart;
        this.borders = borders;
        this.offset = offset;
    }

    public static EliasFanoList generate(int[] list, BitBuffer buffer) {
        int offset = Integer.MAX_VALUE;
        for(int x : list) {
            offset = Math.min(offset, x);
        }
        buffer.writeEliasDelta(offset + 1);
        int bitsCount = 0;
        int[] data = new int[1 + list.length];
        int last = 0;
        BitBuffer bits = new BitBuffer(32 * list.length);
        for(int i=0; i<list.length; i++) {
            int x = list[i] - offset + 1;
            int highest = 31 - Integer.numberOfLeadingZeros(x);
            last += highest;
            data[i + 1] = last;
            x &= (1 << highest) - 1;
            bits.writeNumber(x, highest);
        }
        bitsCount = last;
        buffer.writeEliasDelta(bitsCount + 1);
        int bitsStart = buffer.position();
        buffer.write(bits);
        EliasFanoMonotoneList borders = EliasFanoMonotoneList.generate(data, buffer);
        return new EliasFanoList(offset, buffer, bitsStart, borders);
    }

    public static EliasFanoList load(BitBuffer buffer) {
        int offset = (int) (buffer.readEliasDelta() - 1);
        int bitsCount = (int) (buffer.readEliasDelta() - 1);
        int bitsStart = buffer.position();
        buffer.seek(bitsStart + bitsCount);
        EliasFanoMonotoneList borders = EliasFanoMonotoneList.load(buffer);
        return new EliasFanoList(offset, buffer, bitsStart, borders);
    }

    public int get(int i) {
        long pair = borders.getPair(i);
        int from = (int) (pair >>> 32);
        int to = (int) pair;
        int highest = 1 << (to - from);
        int x = (int) buff.readNumber(bitsStart + from, to - from);
        return (highest | x) + offset - 1;
    }

}
