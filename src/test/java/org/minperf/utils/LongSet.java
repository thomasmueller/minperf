package org.minperf.utils;

import java.util.AbstractSet;
import java.util.Iterator;
import java.util.Spliterator;
import java.util.function.Consumer;

/**
 * A map of longs. Only add an iteration are not supported. This implementation
 * doesn't use much memory.
 */
public class LongSet extends AbstractSet<Long> {

    LargeLongArray data;
    boolean containsZero;
    private long size;

    public LongSet(int capacity) {
        // 80% fill rate
        long len = capacity * 12L / 10;
        data = new LargeLongArray(len);
    }

    @Override
    public int size() {
        return (int) size;
    }

    @Override
    public boolean isEmpty() {
        return size == 0;
    }

    @Override
    public boolean add(Long e) {
        if (e == 0) {
            if (containsZero) {
                return false;
            }
            containsZero = true;
            size++;
            return true;
        }
        long x = e;
        if (size * 10 > data.size() * 11) {
            // more than 90% full
            throw new UnsupportedOperationException();
        }
        long index = (x & 0xffffffffffffL) % data.size();
        while (true) {
            if (data.get(index) == x) {
                return false;
            }
            if (data.get(index) == 0) {
                data.set(index, x);
                size++;
                return true;
            }
            index++;
            if (index >= data.size()) {
                index = 0;
            }
        }
    }

    @Override
    public Iterator<Long> iterator() {
        return new Iterator<Long>() {

            private long index;

            {
                index = -1;
                if (!containsZero) {
                    fetchNext();
                }
            }

            private void fetchNext() {
                while (true) {
                    index++;
                    if (index >= data.size()) {
                        return;
                    }
                    if (data.get(index) != 0) {
                        return;
                    }
                }
            }

            @Override
            public boolean hasNext() {
                return index < data.size();
            }

            @Override
            public Long next() {
                if (!hasNext()) {
                    throw new UnsupportedOperationException();
                }
                if (index == -1) {
                    fetchNext();
                    return 0L;
                }
                long result = data.get(index);
                fetchNext();
                return result;
            }

            @Override
            public void remove() {
                throw new UnsupportedOperationException();
            }

            @Override
            public void forEachRemaining(Consumer<? super Long> action) {
                throw new UnsupportedOperationException();
            }

        };
    }

    @Override
    public Spliterator<Long> spliterator() {
        throw new UnsupportedOperationException();
    }

    /**
     * A large long array.
     */
    static class LargeLongArray {
        private static final int CHUNK_SHIFT = 20;
        private static final int CHUNK_SIZE = 1 << CHUNK_SHIFT;
        private static final int CHUNK_MASK = CHUNK_SIZE - 1;
        private final long[][] data;
        private final long size;

        LargeLongArray(long size) {
            this.size = size;
            int chunkCount = (int) ((size + CHUNK_SIZE - 1) >>> CHUNK_SHIFT);
            data = new long[chunkCount][];
            long remaining = size;
            for (int i = 0; i < chunkCount - 1; i++) {
                data[i] = new long[CHUNK_SIZE];
                remaining -= CHUNK_SIZE;
            }
            data[chunkCount - 1] = new long[(int) remaining];
        }

        long get(long i) {
            return data[(int) (i >>> CHUNK_SHIFT)][(int) (i & CHUNK_MASK)];
        }

        void set(long i, long x) {
            data[(int) (i >>> CHUNK_SHIFT)][(int) (i & CHUNK_MASK)] = x;
        }

        long size() {
            return size;
        }

    }

}
