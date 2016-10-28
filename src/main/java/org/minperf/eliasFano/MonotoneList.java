package org.minperf.eliasFano;

import org.minperf.BitBuffer;
import org.minperf.generator.HybridGenerator;

/**
 * A list of monotone increasing values.
 */
public abstract class MonotoneList {

    public abstract int get(int i);

    public abstract long getPair(int i);

    public static MonotoneList generate(int[] data, BitBuffer buffer) {
        return HybridGenerator.ELIAS_FANO_LIST ?
                EliasFanoMonotoneList.generate(data, buffer) :
                MultiStageMonotoneList.generate(data, buffer);
    }

    public static MonotoneList load(BitBuffer buffer) {
        return HybridGenerator.ELIAS_FANO_LIST ?
                EliasFanoMonotoneList.load(buffer) :
                MultiStageMonotoneList.load(buffer);
    }

}
