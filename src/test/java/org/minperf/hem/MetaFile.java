package org.minperf.hem;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.Vector;
import java.util.concurrent.ConcurrentMap;

import org.junit.Assert;

public class MetaFile implements ConcurrentMap<String, String> {

    private final File file;
    private final TreeMap<String, String> cache = new TreeMap<String, String>();
    private FileChannel channel;
    private FileLock lock;

    public MetaFile(String fileName) {
        this.file = new File(fileName);
    }

    public static void main(String... args) {
        String fileName = args[0];
        MetaFile map = new MetaFile(fileName);
        MetaFile map2 = new MetaFile(fileName);
        map.clear();
        map.put("hello", "world");
        Assert.assertEquals("world", map2.get("hello"));
    }

    @Override
    public int size() {
        read();
        return cache.size();
    }

    @Override
    public boolean isEmpty() {
        read();
        return cache.isEmpty();
    }

    @Override
    public boolean containsKey(Object key) {
        read();
        return cache.containsKey(key);
    }

    @Override
    public boolean containsValue(Object value) {
        read();
        return cache.containsValue(value);
    }

    @Override
    public Collection<String> values() {
        read();
        return cache.values();
    }

    @Override
    public Set<String> keySet() {
        read();
        return cache.keySet();
    }

    @Override
    public Set<Entry<String, String>> entrySet() {
        read();
        return cache.entrySet();
    }

    @Override
    public String get(Object key) {
        read();
        return cache.get(key);
    }

    private void read() {
        try (FileInputStream in = new FileInputStream(file)) {
            FileChannel channel = in.getChannel();
            try (FileLock lock = channel.lock(0, Long.MAX_VALUE, true)) {
                Properties prop = new Properties();
                prop.load(in);
                for(Entry<Object, Object> e : prop.entrySet()) {
                    cache.put(e.getKey().toString(), e.getValue().toString());
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("resource")
    private void lockForWriting() {
        try {
            channel = new RandomAccessFile(file, "rw").getChannel();
            lock = channel.lock();
            InputStream in = Channels.newInputStream(channel);
            Properties prop = new Properties();
            prop.load(in);
            for(Entry<Object, Object> e : prop.entrySet()) {
                cache.put(e.getKey().toString(), e.getValue().toString());
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void writeAndUnlock() {
        try {
            channel.position(0);
            OutputStream out = Channels.newOutputStream(channel);
            Properties prop = new Properties() {
                private static final long serialVersionUID = 1L;
                @Override
                public synchronized Enumeration<Object> keys() {
                    Vector<String> v = new Vector<String>();
                    for (Object o : keySet()) {
                        v.add(o.toString());
                    }
                    Collections.sort(v);
                    return new Vector<Object>(v).elements();
                }
            };
            prop.putAll(cache);
            prop.store(out, null);
            out.flush();
            channel.truncate(channel.position());
            lock.release();
            channel.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void clear() {
        lockForWriting();
        try {
            cache.clear();
        } finally {
            writeAndUnlock();
        }
    }

    @Override
    public String remove(Object key) {
        lockForWriting();
        try {
            return cache.remove(key);
        } finally {
            writeAndUnlock();
        }
    }

    @Override
    public String put(String key, String value) {
        lockForWriting();
        try {
            return cache.put(key, value);
        } finally {
            writeAndUnlock();
        }
    }

    @Override
    public String putIfAbsent(String key, String value) {
        lockForWriting();
        try {
            return cache.putIfAbsent(key, value);
        } finally {
            writeAndUnlock();
        }
    }

    @Override
    public void putAll(Map<? extends String, ? extends String> map) {
        lockForWriting();
        try {
            cache.putAll(map);
        } finally {
            writeAndUnlock();
        }
    }

    @Override
    public boolean remove(Object key, Object value) {
        lockForWriting();
        try {
            return cache.remove(key, value);
        } finally {
            writeAndUnlock();
        }
    }

    @Override
    public boolean replace(String key, String oldValue, String newValue) {
        lockForWriting();
        try {
            return cache.replace(key, oldValue, newValue);
        } finally {
            writeAndUnlock();
        }
    }

    @Override
    public String replace(String key, String value) {
        lockForWriting();
        try {
            return cache.replace(key, value);
        } finally {
            writeAndUnlock();
        }
    }

}
