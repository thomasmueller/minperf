package org.minperf.utils;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.Spliterator;

/**
 * A persisted list of longs.
 */
public class LargeLongList extends AbstractList<Long> {

    static final int CHUNK_SHIFT = 27;
    static final int CHUNK_SIZE = 1 << CHUNK_SHIFT;
    private static final int CHUNK_MASK = CHUNK_SIZE - 1;

    private final int size;
    private ArrayList<LargeLongArray> list = new ArrayList<LargeLongArray>();

    LargeLongList(int size, ArrayList<LargeLongArray> list) {
        this.size = size;
        this.list = list;
    }

    @Override
    public Long get(int index) {
        return list.get(index >>> CHUNK_SHIFT).get(index & CHUNK_MASK);
    }

    @Override
    public int size() {
        return size;
    }

    @Override
    public void finalize() {
        dispose();
    }

    public void dispose() {
        for (LargeLongArray a : list) {
            a.dispose();
        }
    }

    public static LargeLongList create(Collection<Long> collection) {
        int size = collection.size();
        int chunks = (size + CHUNK_SIZE - 1) / CHUNK_SIZE;
        int remaining = size;
        Iterator<Long> iterator = collection.iterator();
        ArrayList<LargeLongArray> list = new ArrayList<LargeLongArray>();
        for (int i = 0; i < chunks; i++) {
            int len = Math.min(CHUNK_SIZE, remaining);
            list.add(LargeLongArray.create(iterator, len));
            remaining -= len;
        }
        return new LargeLongList(size, list);
    }

    @Override
    public Spliterator<Long> spliterator() {
        throw new UnsupportedOperationException();
    }

    /**
     * A large long array.
     */
    static class LargeLongArray {
        private File file;
        private FileChannel channel;
        private MappedByteBuffer map;
        private final int size;
        private boolean closed;

        public LargeLongArray(int size, File file, FileChannel channel,
                MappedByteBuffer map) {
            this.size = size;
            this.file = file;
            this.channel = channel;
            this.map = map;
        }

        static LargeLongArray create(Iterator<Long> iterator, int size) {
            try {
                File file = File.createTempFile("list", ".tmp");
                file.deleteOnExit();
                FileChannel channel = new RandomAccessFile(file, "rw").getChannel();
                MappedByteBuffer map = channel
                        .map(MapMode.READ_WRITE, 0, size * 8L);
                for (int i = 0; i < size; i++) {
                    long x = iterator.next();
                    map.putLong(x);
                }
                return new LargeLongArray(size, file, channel, map);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void finalize() {
            dispose();
        }

        public void dispose() {
            if (closed) {
                return;
            }
            closed = true;
            try {
                channel.close();
                file.delete();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        public Long get(int index) {
            if (index > size) {
                throw new IllegalArgumentException();
            }
            return map.getLong(index * 8);
        }

    }

}
