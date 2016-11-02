package org.minperf.monotoneList;

import org.minperf.BitBuffer;

/**
 * A list of monotone increasing values.
 */
public abstract class MonotoneList {

    public static final boolean ELIAS_FANO_LIST = false;

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

}
