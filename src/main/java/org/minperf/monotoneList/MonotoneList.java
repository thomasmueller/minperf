package org.minperf.monotoneList;

import org.minperf.BitBuffer;

/**
 * A list of monotone increasing values.
 */
public abstract class MonotoneList {

    public static final boolean ELIAS_FANO_LIST = true;

    public abstract int get(int i);

    public abstract long getPair(int i);

    public static MonotoneList generate(int[] data, BitBuffer buffer) {
        return MonotoneList.ELIAS_FANO_LIST ?
                EliasFanoMonotoneList.generate(data, buffer) :
                MultiStageMonotoneList.generate(data, buffer);
    }

    public static MonotoneList load(BitBuffer buffer) {
        return MonotoneList.ELIAS_FANO_LIST ?
                EliasFanoMonotoneList.load(buffer) :
                MultiStageMonotoneList.load(buffer);
    }

    public String asString(int len) {
        StringBuilder buff = new StringBuilder();
        buff.append("[");
        for (int i = 0; i < len; i++) {
            if (i > 0) {
                buff.append(", ");
            }
            buff.append(get(i));
        }
        buff.append("]");
        return buff.toString();
    }

}
