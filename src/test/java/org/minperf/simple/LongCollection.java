package org.minperf.simple;

import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;

import org.minperf.BitBuffer;
import org.minperf.RecSplitBuilder;
import org.minperf.universal.LongHash;

public class LongCollection implements Collection<Long> {

    public static void main(String... args) {
        long[] data = new long[63_000_000];
        // Random r = new Random(1);
        for (int i = 0; i < data.length; i++) {
            data[i] = i * 2; // r.nextLong();
        }
        Arrays.sort(data);
        for (int i = 1; i < data.length; i++) {
            if (data[i - 1] == data[i]) {
                System.out.println("duplicate!");
            }
        }
        LongCollection collection = new LongCollection(data);
        System.out.println("generating...");
        RecSplitBuilder<Long> builder = RecSplitBuilder.newInstance(new LongHash());
        builder.leafSize(7);
        builder.averageBucketSize(1000);
        builder.maxChunkSize(1_000_000);
        long time = System.currentTimeMillis();
        BitBuffer buff = builder.generate(collection);
        System.out.println((double) buff.position() / data.length + " bits/key");
        time = System.currentTimeMillis() - time;
        System.out.println(time + " ms");
    }

    private final long[] data;

    LongCollection(long[] data) {
        this.data = data;
    }

    @Override
    public int size() {
        return data.length;
    }

    @Override
    public boolean isEmpty() {
        return data.length == 0;
    }

    @Override
    public boolean contains(Object o) {
        if (o == null) {
            return false;
        } else if (!(o instanceof Long)) {
            return false;
        }
        long x = (Long) o;
        for(long y : data) {
            if (y == x) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Iterator<Long> iterator() {
        return new Iterator<Long>() {

            int pos;

            @Override
            public boolean hasNext() {
                return pos < data.length;
            }

            @Override
            public Long next() {
                return data[pos++];
            }

        };
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
    public boolean add(Long e) {
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
    public boolean retainAll(Collection<?> c) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void clear() {
        throw new UnsupportedOperationException();
    }

}
