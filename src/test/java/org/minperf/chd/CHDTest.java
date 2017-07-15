package org.minperf.chd;

import java.util.BitSet;
import java.util.Set;

import org.junit.Assert;
import org.minperf.BitBuffer;
import org.minperf.RandomizedTest;
import org.minperf.universal.LongHash;
import org.minperf.universal.UniversalHash;

public class CHDTest<T> {

    public static void main(String... args) {
        for (int size = 10; size <= 100000; size *= 10) {
            testSize(size);
        }
    }

    private static void testSize(int size) {
        Set<Long> set = RandomizedTest.createSet(size, 1);
        test(set, Long.class);
    }

    @SuppressWarnings("unchecked")
    private static <T> void test(Set<T> set, Class<T> clazz) {
        int size = set.size();
        BitBuffer buff = new BitBuffer(1000 + size * 1000);
        UniversalHash<T> hash = null;
        if (clazz == Long.class) {
            hash = (UniversalHash<T>) new LongHash();
        }
        CHD<T> chd = new CHD<T>(hash, buff);
        chd.generate(set);
        long totalBits = buff.position();
        System.out.println("size " + size + " bits/key " + (double) totalBits / size);
        buff.seek(0);
        chd = new CHD<T>(hash, buff);
        chd.load();
        verify(chd, set);
    }

    private static <T> void verify(CHD<T> eval, Set<T> set) {
        BitSet known = new BitSet();
        for (T x : set) {
            int index = eval.evaluate(x);
            if (index > set.size() || index < 0) {
                Assert.fail("wrong entry: " + x + " " + index);
            }
            if (known.get(index)) {
                eval.evaluate(x);
                Assert.fail("duplicate entry: " + x + " " + index);
            }
            known.set(index);
        }
    }

}
