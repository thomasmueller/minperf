package org.minperf.cuckoo;

import java.util.Map;
import java.util.Map.Entry;

import org.minperf.Settings;
import org.minperf.universal.UniversalHash;

/**
 * A simple cuckoo hash map.
 *
 * @param <K> the key type
 * @param <V> the value type
 */
public class CuckooHashMap<K, V> {

    private final int size;
    private final UniversalHash<K> hash;
    private int hashIndex;
    private int maxLoop;
    private K[] keys;
    private V[] values;

    @SuppressWarnings("unchecked")
    public CuckooHashMap(Map<K, V> map, UniversalHash<K> hash) {
        this.hash = hash;
        size = map.size();
        double e = 0.05;
        int r = (int) (size * (1 + e) + 1);
        maxLoop = (int) ((3 * Math.log(r) / Math.log(1 + e)) + 1);
        for (hashIndex = 0;; hashIndex += 2) {
            keys = (K[]) new Object[r * 2];
            values = (V[]) new Object[r * 2];
            if (tryAddAll(map)) {
                return;
            }
        }
    }

    private int index(K key, int id) {
        int offset;
        int x;
        if (id == 0) {
            offset = 0;
            x = hashIndex;
        } else {
            offset = keys.length / 2;
            x = hashIndex + 1;
        }
        return offset + Settings.scaleLong(hash.universalHash(key, x), keys.length / 2);
    }

    public V get(K key) {
        for (int i = 0; i < 2; i++) {
            int h = index(key, i);
            K k = keys[h];
            if (k == null) {
                return null;
            }
            if (k.equals(key)) {
                return values[h];
            }
        }
        return null;
    }

    private boolean tryAddAll(Map<K, V> map) {
        for (Entry<K, V> e : map.entrySet()) {
            if (!tryAdd(e.getKey(), e.getValue())) {
                return false;
            }
        }
        return true;
    }

    private boolean tryAdd(K key, V value) {
        for (int count = 0; count < 2 * maxLoop; count++) {
            int h = index(key, count & 1);
            K oldKey = keys[h];
            V oldValue = values[h];
            keys[h] = key;
            values[h] = value;
            if (oldKey == null) {
                return true;
            }
            key = oldKey;
            value = oldValue;
        }
        return false;
    }

}
