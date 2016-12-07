package org.minperf.monotoneList;


/**
 * A monotone list that uses a fenwick tree.
 */
public class FenwickTreeMonotoneList extends MonotoneList {

    private final int[] array;

    FenwickTreeMonotoneList(int[] array) {
        this.array = array;
    }

    public static FenwickTreeMonotoneList generate(int[] data) {
        int[] array = new int[data.length];
        int last = 0;
        for (int i = 0; i < data.length; i++) {
            int x = data[i];
            add(array, i, x - last);
            last = x;
        }
        for (int i = 0; i < data.length; i++) {
            if (sum(array, i) != data[i]) {
                throw new AssertionError();
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

    private static int sum(int[] array, int index) {
        int sum = 0;
        index++;
        while (index > 0) {
            sum += array[index - 1];
            index -= index & -index;
        }
        return sum;
    }

    @Override
    public int get(int i) {
        return sum(array, i);
    }

    @Override
    public long getPair(int i) {
        return (((long) get(i)) << 32) | get(i + 1);
    }

}
