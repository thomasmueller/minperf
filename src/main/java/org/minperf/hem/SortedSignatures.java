package org.minperf.hem;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;
import java.util.PrimitiveIterator;

import org.minperf.BitBuffer;

public class SortedSignatures {

    private static final int MAP_SIZE = 512 * 1024 * 1024;

    private static final int BUFFER_SIZE = 4 * 1024 * 1024;
    private static final int OVERLAP = 1024;

    static class FileIterator {

        private final RandomAccessFile f;
        private final FileChannel fc;
        private MappedByteBuffer buff;
        private long offset;
        private final long size;

        FileIterator(String fileName) {
            try {
                f = new RandomAccessFile(fileName, "r");
                fc = f.getChannel();
                size = fc.size();
                remap();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        private long position() {
            return offset + buff.position();
        }

        void remap() {
            try {
                long pos = buff == null ? 0 : position();
                offset = pos / (MAP_SIZE / 2) * (MAP_SIZE / 2);
                long s = Math.min(size - offset, MAP_SIZE);
                buff = fc.map(MapMode.READ_ONLY, offset, s);
                buff.position((int) (pos - offset));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        void remapIfNeeded() {
            if (buff.position() > MAP_SIZE / 2) {
                remap();
            }
        }

        void close() {
            try {
                fc.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        PrimitiveIterator.OfLong iteratorVarLong(final int len) {
            return new PrimitiveIterator.OfLong() {
                private int i;
                private long x;
                @Override
                public boolean hasNext() {
                    return i < len;
                }
                @Override
                public long nextLong() {
                    remapIfNeeded();
                    x += readVarLong(buff);
                    return x;
                }
            };
        }

        PrimitiveIterator.OfLong iteratorGolombRice(final int len, final int shift) {
            return new PrimitiveIterator.OfLong() {
                private int i;
                private long last;
                private byte[] bytes = new byte[(BUFFER_SIZE + OVERLAP) / 8];
                private BitBuffer buffer;
                {
                    int readBytes = (int) Math.min(BUFFER_SIZE / 8, size);
                    remapIfNeeded();
                    buff.get(bytes, OVERLAP / 8, readBytes);
                    buffer = new BitBuffer(bytes);
                    buffer.seek(OVERLAP);
                }
                @Override
                public boolean hasNext() {
                    return i < len;
                }

                @Override
                public long nextLong() {
                    long x = buffer.readGolombRice(shift);
                    last += x;
                    int pos = buffer.position();
                    if (pos > BUFFER_SIZE) {
                        System.arraycopy(bytes, BUFFER_SIZE / 8, bytes, 0, OVERLAP / 8);
                        int readBytes = (int) Math.min(BUFFER_SIZE / 8, size - position());
                        remapIfNeeded();
                        buff.get(bytes, OVERLAP / 8, readBytes);
                        buffer = new BitBuffer(bytes);
                        buffer.seek(pos - BUFFER_SIZE);
                    }

                    return last;
                }
            };
        }

    }

    static class FileWriter {

        private final FileChannel fc;

        FileWriter(String fileName) {
            try {
                @SuppressWarnings("resource")
                RandomAccessFile f = new RandomAccessFile(fileName, "rw");
                fc = f.getChannel();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        void close() {
            try {
                fc.truncate(fc.position());
                fc.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        void writeDiffsGolombRice(long[] data, int shift) {
            // Sort.parallelSort(data);
            BitBuffer buff = new BitBuffer(BUFFER_SIZE + OVERLAP);
            long last = 0;
            for(long x : data) {
                long diff = x - last;
                last = x;
                buff.writeGolombRice(shift, diff);
                if (buff.position() > BUFFER_SIZE) {
                    writeBlock(buff);
                }
            }
            write(ByteBuffer.wrap(buff.toByteArray()));
        }

        void writeBlock(BitBuffer buff) {
            int pos = buff.position();
            write(ByteBuffer.wrap(buff.toByteArray(), 0, BUFFER_SIZE / 8));
            BitBuffer buff2 = new BitBuffer(OVERLAP);
            buff.seek(BUFFER_SIZE);
            while (buff.position() < pos) {
                buff2.writeBit(buff.readBit());
            }
            buff.clear();
            buff.seek(0);
            buff.write(buff2);
        }

        void writeDiffsVarLong(long[] data) {
            // Sort.parallelSort(data);
            ByteBuffer buff = ByteBuffer.allocate(BUFFER_SIZE);
            long last = 0;
            for(long x : data) {
                long diff = x - last;
                last = x;
                writeVarLong(buff, diff);
                if (buff.remaining() < 16) {
                    buff.flip();
                    write(buff);
                    buff.clear();
                }
            }
            buff.flip();
            write(buff);
        }

        void write(ByteBuffer data) {
            try {
                // TODO return value is currently not checked
                fc.write(data);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

    }

    static class FileWriterChannel {

        private final FileChannel fc;

        FileWriterChannel(String fileName) {
            try {
                new File(fileName).delete();
                @SuppressWarnings("resource")
                RandomAccessFile f = new RandomAccessFile(fileName, "rw");
                fc = f.getChannel();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        void close() {
            try {
                fc.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        void writeDiffsEliasDelta(long[] data) {
            // Sort.parallelSort(data);
            ByteBuffer buffer = ByteBuffer.allocate(4);
            buffer.putInt(data.length);
            buffer.flip();
            write(buffer);
            BitBuffer buff = new BitBuffer(BUFFER_SIZE);
            long last = 0;
            for(long x : data) {
                long diff = x - last;
                last = x;
                buff.writeEliasDelta(diff);
                writeBitBuffer(buff, false);
            }
            writeBitBuffer(buff, true);
        }

        void writeBitBuffer(BitBuffer buff, boolean always) {
            int remaining = 8 * BUFFER_SIZE - buff.position();
            if (always) {
                remaining = 0;
            }
            if (remaining < 256) {
                byte[] b = buff.toByteArray();
                ByteBuffer buffer = ByteBuffer.allocate(b.length + 1);
                buffer.put((byte) remaining);
                write(buffer);
                buff.seek(0);
            }
        }

        void writeDiffsGolombRice(long[] data, int shift) {
            // Sort.parallelSort(data);
            BitBuffer buff = new BitBuffer(BUFFER_SIZE);
            long last = 0;
            for(long x : data) {
                long diff = x - last;
                last = x;
                buff.writeGolombRice(shift, diff);
                writeBitBuffer(buff, false);
            }
            writeBitBuffer(buff, true);
        }

        void writeDiffs(long[] data) {
            // Sort.parallelSort(data);
            ByteBuffer buff = ByteBuffer.allocate(BUFFER_SIZE);
            long last = 0;
            for(long x : data) {
                long diff = x - last;
                last = x;
                writeVarLong(buff, diff);
                if (buff.remaining() < 16) {
                    buff.flip();
                    write(buff);
                    buff.clear();
                }
            }
            buff.flip();
            write(buff);
        }

        void write(ByteBuffer data) {
            try {
                fc.write(data);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

    }

    public static void writeVarLong(ByteBuffer buff, long x) {
        while ((x & ~0x7f) != 0) {
            buff.put((byte) (0x80 | (x & 0x7f)));
            x >>>= 7;
        }
        buff.put((byte) x);
    }

    public static long readVarLong(ByteBuffer buff) {
        long x = buff.get();
        if (x >= 0) {
            return x;
        }
        x &= 0x7f;
        for (int s = 7; s < 64; s += 7) {
            long b = buff.get();
            x |= (b & 0x7f) << s;
            if (b >= 0) {
                break;
            }
        }
        return x;
    }



}
