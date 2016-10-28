package org.minperf.eliasFano;

import org.minperf.BitBuffer;

/**
 * A monotone list that uses a fenwick tree.
 */
public class FenwickTreeMonotoneList {

    private final int[] array;

    FenwickTreeMonotoneList(int[] array) {
        this.array = array;
    }

    public static FenwickTreeMonotoneList generate(int[] data, BitBuffer buffer) {
        int[] array = new int[data.length];
        int last = 0;
        for (int i = 0; i < data.length; i++) {
            int x = data[i];
            add(array, i, x - last);
            last = x;
        }
        for (int i = 0; i < data.length; i++) {
            if (sum(array, i) != data[i]) {
                System.out.println("??? " + i + " " + data[i] + " " +
                        sum(array, i) + " " + sum(array, i - 1));
            }
        }
        return new FenwickTreeMonotoneList(array);
    }

    private static void add(int[] array, int index, int value) {
        int len = array.length;
        index++;
        while (index <= len) {
            array[index - 1] += value;
            index += index & -index;
        }
    }

    private static int get(int[] array, int index) {
        return sum(array, index) - sum(array, index - 1);
    }

    private static int sum(int[] array, int index) {
        int sum = 0;
        index++;
        while (index > 0) {
            sum += array[index - 1];
            index -= index & -index;
        }
        return sum;
    }

    public int get(int i) {
        return sum(array, i);
    }

}
