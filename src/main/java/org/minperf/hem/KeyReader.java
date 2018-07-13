package org.minperf.hem;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.PrimitiveIterator;
import java.util.UUID;
import java.util.concurrent.Future;
import org.minperf.hash.Mix;
import org.minperf.hash.Murmur2;

public class KeyReader {

    private static final int MAX_KEY_SIZE = 1024;
    private static final UUID EMPTY = new UUID(0, 0);

    public static void main(String... args) {
        // seq -f "%.20g" 1 1000000 > ~/temp/hash/keys.txt
        // seq -f "%.20g" 1 100000000 > ~/temp/hash/keys.txt
        for(int test = 0; test < 10; test++) {
            Iterator<UUID> it = KeyReader.readSignaturesFromTextFile2(args[0]);
            long time = System.nanoTime();
            long count = 0;
            long dummy = 0;
            while(it.hasNext()) {
                UUID uuid = it.next();
                count++;
                dummy += uuid.getMostSignificantBits() + uuid.getLeastSignificantBits();
            }
            time = System.nanoTime() - time;
            System.out.println("time: " + time + " dummy "+ dummy + " count " + count + " " + time / count + " ns/key");
        }
    }

    static Iterator<UUID> readSignaturesFromTextFile(final String fileName) {
        return new Iterator<UUID>() {

            private static final int BUFFER_SIZE = 512 * 1024 * 1024;

            private final InputStream input;
            private final byte[] key = new byte[MAX_KEY_SIZE];
            private UUID current;

            {
                FileInputStream fileIn;
                try {
                    fileIn = new FileInputStream(fileName);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                input = new BufferedInputStream(fileIn, BUFFER_SIZE);
                fetchNext();
            }

            private void fetchNext() {
                int i = 0;
                for (;; i++) {
                    int x;
                    try {
                        x = input.read();
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                    if (x < 0) {
                        try {
                            input.close();
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                        if (i == 0) {
                            current = null;
                            return;
                        }
                        break;
                    }
                    if (x <= ' ') {
                        if (i == 0) {
                            i--;
                            continue;
                        }
                        break;
                    }
                    if (i >= MAX_KEY_SIZE) {
                        throw new RuntimeException("Key too long, max size " + MAX_KEY_SIZE);
                    }
                    key[i] = (byte) x;
                }
                current = EMPTY;
                // current = getSipHash24(key, 0, i);
            }

            @Override
            public boolean hasNext() {
                return current != null;
            }

            @Override
            public UUID next() {
                UUID result = current;
                if (result != null) {
                    fetchNext();
                }
                return result;
            }

        };
    }

    public static Iterator<UUID> readSignaturesFromTextFile2(final String fileName) {
        return new Iterator<UUID>() {

            private final RandomAccessFile f;
            private final FileChannel fc;
            private final MappedByteBuffer buff;
            private final byte[] key = new byte[MAX_KEY_SIZE];
            private UUID current;
            private int size;
            private int pos;

            {
                try {
                    f = new RandomAccessFile(fileName, "r");
                    fc = f.getChannel();
                    size = (int) fc.size();
                    buff = fc.map(MapMode.READ_ONLY, 0, size);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                fetchNext();
            }

            private void fetchNext() {
                int i=0;
                for (;; i++) {
                    if (pos >= size) {
                        try {
                            fc.close();
                            f.close();
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                        current = null;
                        return;
                    }
                    int x = buff.get();
                    pos++;
                    if (x <= ' ') {
                        if (i == 0) {
                            i--;
                            continue;
                        }
                        break;
                    }
                    key[i] = (byte) x;
                    // if (i >= MAX_KEY_SIZE) {
                    //     throw new RuntimeException("Key too long, max size " + MAX_KEY_SIZE);
                    // }
                }
                // current = EMPTY;
                // TODO find a fast 128 bit hash function
                current = new UUID(Murmur2.hash64(key, i, 0), 0);
                // current = getSipHash24(buff, start, i);
            }

            @Override
            public boolean hasNext() {
                return current != null;
            }

            @Override
            public UUID next() {
                UUID result = current;
                if (result != null) {
                    fetchNext();
                }
                return result;
            }

        };
    }

    static PrimitiveIterator.OfLong readSignaturesFromTextFile64(final String fileName) {
        return new PrimitiveIterator.OfLong() {

            private static final int MAX_BUFFER_SIZE = 512 * 1024 * 1024;

            private final RandomAccessFile f;
            private final FileChannel fc;
            private MappedByteBuffer buff;
            private long size;
            private long pos;

            {
                try {
                    f = new RandomAccessFile(fileName, "r");
                    fc = f.getChannel();
                    size = fc.size();
                    remap();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }

            void remap() {
                try {
                    long s = Math.min(size - pos, MAX_BUFFER_SIZE);
                    buff = fc.map(MapMode.READ_ONLY, pos, s);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }

            @Override
            public boolean hasNext() {
                return pos < size;
            }

            @Override
            public long nextLong() {
                if (!buff.hasRemaining()) {
                    remap();
                }
                pos += 8;
                // return buff.getLong();
                 return Mix.hash64(buff.getLong());
                 // return universalHash(buff.getLong(), 0);
            }

        };
    }

    public static Iterator<Long> readSignaturesFromTextFile64input(final String fileName) {
        return new Iterator<Long>() {

            private static final int BUFFER_SIZE = 8 * 1024;

            private final DataInputStream in;
            private long size;
            private long pos;

            {
                try {
                    size = new File(fileName).length();
                    in = new DataInputStream(
                            new BufferedInputStream(
                            new FileInputStream(fileName)
                            , BUFFER_SIZE
                            )
                            );
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }

            @Override
            public boolean hasNext() {
                return pos < size;
            }

            @Override
            public Long next() {
                try {
                    pos += 8;
                    return in.readLong();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }

        };
    }

    static Iterator<Long> readSignaturesFromTextFile64b(final String fileName) {
        return new Iterator<Long>() {

            private static final int BUFFER_SIZE = 64 * 1024;

            private final RandomAccessFile f;
            private final FileChannel fc;
            private final ByteBuffer buff;
            private int size;
            private int pos;

            {
                try {
                    f = new RandomAccessFile(fileName, "r");
                    fc = f.getChannel();
                    size = (int) fc.size();
                    buff = ByteBuffer.allocate(BUFFER_SIZE);
                    readBatch();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }

            void readBatch() {
                buff.clear();
                try {
                    // TODO may not read all bytes (and possibly not a multiple of 64)
                    fc.read(buff);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                buff.flip();
            }

            @Override
            public boolean hasNext() {
                return pos < size ;
            }

            @Override
            public Long next() {
                if (!buff.hasRemaining()) {
                    readBatch();
                }
                pos += 8;
                return buff.getLong();
                // return mix64(buff.getLong());
                // return universalHash(buff.getLong(), 0);
            }

        };
    }

    static Iterator<Long> readSignaturesFromTextFile64async(final String fileName) {
        return new Iterator<Long>() {

            private static final int BUFFER_SIZE = 64 * 1024;

            private final AsynchronousFileChannel fc;
            private final ByteBuffer[] buffers = new ByteBuffer[2];
            private Future<Integer> future;
            private int currentBuff = 1;
            private int size;
            private int pos;

            {
                try {
                    fc = AsynchronousFileChannel.open(Paths.get(fileName));
                    size = (int) fc.size();
                    buffers[0] = ByteBuffer.allocate(BUFFER_SIZE);
                    buffers[1] = ByteBuffer.allocate(BUFFER_SIZE);
                    readBatch();
                    readBatch();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }

            void readBatch() {
                if (future != null) {
                    try {
                        while (!future.isDone()) {
                            Thread.yield();
                        }
                        Integer x = future.get();
                        pos += x;
                        int readBuff = 1 ^ currentBuff;
                        ByteBuffer buff = buffers[readBuff];
                        buff.flip();
                        currentBuff ^= 1;
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
                int readBuff = 1 ^ currentBuff;
                ByteBuffer buff = buffers[readBuff];
                buff.clear();
                // TODO may not read all bytes (and possibly not a multiple of 64)
                future = fc.read(buff, pos);
            }

            @Override
            public boolean hasNext() {
                return pos < size ;
            }

            @Override
            public Long next() {
                ByteBuffer buff = buffers[currentBuff];
                if (!buff.hasRemaining()) {
                    readBatch();
                    buff = buffers[currentBuff];
                }
                return buff.getLong();
                // return mix64(buff.getLong());
                // return universalHash(buff.getLong(), 0);
            }

        };
    }

}
