package org.minperf.utils;

import java.util.Collection;
import java.util.Iterator;
import java.util.Spliterator;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * A map of longs. Only add an iteration are not supported. This implementation
 * doesn't use much memory.
 */
public class LongSet implements Collection<Long> {

    LargeLongArray data;
    boolean containsZero;
    private long size;

    LongSet(int capacity) {
        // 80% fill rate
        long len = (long) (capacity * 1.2);
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
    public void forEach(Consumer<? super Long> action) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean contains(Object o) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Object[] toArray() {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T> T[] toArray(T[] a) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean remove(Object o) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean addAll(Collection<? extends Long> c) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean removeIf(Predicate<? super Long> filter) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void clear() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Spliterator<Long> spliterator() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Stream<Long> stream() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Stream<Long> parallelStream() {
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
            data = new long[chunkCount][CHUNK_SIZE];
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
