package org.minperf.bdz;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;

import org.minperf.BitBuffer;
import org.minperf.Settings;
import org.minperf.rank.VerySimpleRank;
import org.minperf.universal.UniversalHash;

/**
 * A simple implementation of the BDZ algorithm as documented in
 * "Simple and Space-Efficient Minimal Perfect Hash Functions" (F. C. Botelho,
 * R. Pagh, N. Ziviani).
 *
 * This implementation around 3.66 bits/key, which is much more than really
 * needed, mainly because no compression is used.
 *
 * @param <T> the type
 */
public class BDZ<T> {

    // needs 3.66 bits/key
    private static final int HASHES = 3;
    private static final int FACTOR_TIMES_100 = 123;
    private static final int BITS_PER_ENTRY = 2;

    // needs 3.78 bits/key
    // private static final int HASHES = 4;
    // private static final int FACTOR_TIMES_100 = 132;
    // private static final int BITS_PER_ENTRY = 2;

    // needs 4.25 bits/key
    // private static final int HASHES = 2;
    // private static final int FACTOR_TIMES_100 = 240;
    // private static final int BITS_PER_ENTRY = 1;

    private final UniversalHash<T> hash;
    private final BitBuffer data;
    private final int hashIndex;
    private final int arrayLength;
    private final int size;
    private final int startPos;
    private final VerySimpleRank rank;

    private BDZ(UniversalHash<T> hash, BitBuffer data) {
        this.hash = hash;
        this.data = data;
        this.size = (int) data.readEliasDelta() - 1;
        this.arrayLength = getArrayLength(size);
        this.hashIndex = (int) data.readEliasDelta() - 1;
        this.rank = VerySimpleRank.load(data);
        this.startPos = data.position();
        data.seek(startPos + size * BITS_PER_ENTRY);
    }

    public int evaluate(T x) {
        int sum = 0;
        for (int hi = 0; hi < HASHES; hi++) {
            int h = getHash(x, hash, hashIndex, hi, arrayLength);
            if (rank.get(h)) {
                int pos = (int) rank.rank(h);
                sum += data.readNumber(startPos + pos * BITS_PER_ENTRY, BITS_PER_ENTRY);
            }
        }
        int h = getHash(x, hash, hashIndex, sum % HASHES, arrayLength);
        int pos = (int) rank.rank(h);
        return pos;
    }

    public static <T> BDZ<T> load(UniversalHash<T> hash, BitBuffer data) {
        return new BDZ<T>(hash, data);
    }

    @SuppressWarnings("unchecked")
    public static <T> BitBuffer generate(UniversalHash<T> hash, Collection<T> set) {
        int size = set.size();
        int arrayLength = getArrayLength(size);
        BitBuffer data = new BitBuffer(100 + arrayLength * (BITS_PER_ENTRY  + 2));
        data.writeEliasDelta(size + 1);

        ArrayList<T> list = new ArrayList<T>(set);
        int m = arrayLength;
        ArrayList<T> order = new ArrayList<T>();
        HashSet<T> done = new HashSet<T>();
        T[] at;
        int hashIndex = 0;
        while (true) {
            order.clear();
            done.clear();
            at = (T[]) new Object[m];
            ArrayList<HashSet<T>> list2 = new ArrayList<HashSet<T>>();
            for (int i = 0; i < m; i++) {
                list2.add(new HashSet<T>());
            }
            for (int i = 0; i < size; i++) {
                T x = list.get(i);
                for (int hi = 0; hi < HASHES; hi++) {
                    int h = getHash(x, hash, hashIndex, hi, arrayLength);
                    HashSet<T> l = list2.get(h);
                    l.add(x);
                }
            }
            LinkedList<Integer> alone = new LinkedList<Integer>();
            for (int i = 0; i < arrayLength; i++) {
                if (list2.get(i).size() == 1) {
                    alone.add(i);
                }
            }
            while (!alone.isEmpty()) {
                int i = alone.removeFirst();
                HashSet<T> l = list2.get(i);
                if (l.isEmpty()) {
                    continue;
                }
                T x = l.iterator().next();
                if (done.contains(x)) {
                    continue;
                }
                order.add(x);
                done.add(x);
                boolean found = false;
                for (int hi = 0; hi < HASHES; hi++) {
                    int h = getHash(x, hash, hashIndex, hi, arrayLength);
                    l = list2.get(h);
                    l.remove(x);
                    if (l.isEmpty()) {
                        if (!found) {
                            at[h] = x;
                            found = true;
                        }
                    } else if (l.size() == 1) {
                        alone.add(h);
                    }
                }
            }
            if (order.size() == size) {
                break;
            }
            hashIndex++;
        }
        data.writeEliasDelta(hashIndex + 1);

        BitSet visited = new BitSet();
        BitSet used = new BitSet();
        int[] g = new int[m];
        for (int i = order.size() - 1; i >= 0; i--) {
            T x = order.get(i);
            int sum = 0;
            int change = 0;
            int target = 0;
            for (int hi = 0; hi < HASHES; hi++) {
                int h = getHash(x, hash, hashIndex, hi, arrayLength);
                if (visited.get(h)) {
                    sum += g[h];
                } else {
                    visited.set(h);
                    if (at[h] == x) {
                        used.set(h);
                        change = h;
                        target = hi;
                    }
                }
            }
            int result = (HASHES + target - (sum % HASHES)) % HASHES;
            g[change] = result;
        }
        VerySimpleRank.generate(used, data);
        for (int i = 0; i < m; i++) {
            if (used.get(i)) {
                data.writeNumber(g[i], BITS_PER_ENTRY);
            } else if (g[i] != 0) {
                throw new AssertionError();
            }
        }
        return data;
    }

    public int getSize() {
        return size;
    }

    private static int getArrayLength(int size) {
        return HASHES + FACTOR_TIMES_100 * size / 100;
    }

    private static <T> int getHash(T x, UniversalHash<T> hash,
            int hashIndex, int index, int arrayLength) {
        long r = hash.universalHash(x, hashIndex + index);
        r = Settings.reduce((int) r, arrayLength / HASHES);
        r = r + index * arrayLength / HASHES;
        return (int) r;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " size " + size + " hashIndex " + hashIndex;
    }

}
