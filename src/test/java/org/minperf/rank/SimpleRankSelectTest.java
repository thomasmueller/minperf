package org.minperf.rank;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.BitSet;
import java.util.Random;

import org.junit.Test;
import org.minperf.BitBuffer;

/**
 * Test the simple rank/select implementation.
 */
public class SimpleRankSelectTest {

    public static void main(String... args) {
        new SimpleRankSelectTest().test();
    }

    @Test
    public void test() {
        int maxOverhead = 0;
        for (int i = 1; i < 1000; i++) {
            BitBuffer buffer = new BitBuffer(8000);
            BitSet set = new BitSet(i + 1);
            set.set(0, i);
            SimpleRankSelect rank = SimpleRankSelect.generate(set, buffer);
            maxOverhead = Math.max(maxOverhead, rank.getOverhead());
            int read = rank.getReadBits();
            double readFactor = read / (Math.log(i) / Math.log(2));
            if (i > 10 && readFactor > 4) {
                fail();
            }
        }
        assertTrue(maxOverhead < 32);
        for (int size = 0; size < 2000; size++) {
            test(size);
        }
        for (int size = 64; size < 1024 * 1024; size *= 2) {
            test(size);
        }
    }

    private static void test(int size) {
        testFull(size);
        testRandom(size);
        testEmpty(size);
    }

    private static void testEmpty(int size) {
        BitSet set = new BitSet();
        set.set(0, size, false);
        BitBuffer buffer = new BitBuffer(100 + size * 2);
        SimpleRankSelect rank = SimpleRankSelect.generate(set, buffer);
        rank = reopen(rank, buffer);
        assertEquals(0, rank.rank(0));
        for (int j = 0; j < size; j++) {
            assertEquals(0L, rank.rank(j));
        }
        assertEquals(-1, rank.select(1));
    }

    private static void testRandom(int size) {
        BitSet set = new BitSet();
        Random r = new Random(size);
        for (int i = 0; i < size / 10; i++) {
            while (true) {
                int x = r.nextInt(size);
                if (!set.get(x)) {
                    set.set(x);
                    break;
                }
            }
        }
        BitBuffer buffer = new BitBuffer(100 + size * 2);
        SimpleRankSelect rank = SimpleRankSelect.generate(set, buffer);
        rank = reopen(rank, buffer);
        assertEquals(0, rank.rank(0));
        int x = 0;
        for (int j = 0; j < size; j++) {
            assertEquals(x, rank.rank(j));
            if (set.get(j)) {
                assertEquals(j, rank.select(x));
                x++;
            }
        }
    }

    private static void testFull(int size) {
        BitSet set = new BitSet();
        set.set(0, size, true);
        BitBuffer buffer = new BitBuffer(100 + size * 2);
        SimpleRankSelect rank = SimpleRankSelect.generate(set, buffer);
        rank = reopen(rank, buffer);
        assertEquals(0, rank.rank(0));
        for (int j = 1; j < size; j++) {
            assertEquals(j, rank.rank(j));
            assertEquals(j, rank.select(j));
        }
    }

    private static SimpleRankSelect reopen(SimpleRankSelect rank, BitBuffer buffer) {
        int size = rank.getSize();
        buffer.seek(0);
        SimpleRankSelect result = SimpleRankSelect.load(buffer);
        assertEquals(size, rank.getSize());
        return result;
    }

}
