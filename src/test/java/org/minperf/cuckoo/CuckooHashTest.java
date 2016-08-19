package org.minperf.cuckoo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map.Entry;

import org.junit.Test;
import org.minperf.PerformanceTest;
import org.minperf.universal.StringHash;
import org.minperf.universal.UniversalHash;

/**
 * Tests for the cuckoo hash maps.
 */
public class CuckooHashTest {

    public static void main(String... args) {
        new CuckooHashTest().test();
        new CuckooHashTest().testLongKey();
        testPerf();
    }

    private static void testPerf() {
        HashSet<Long> set = PerformanceTest.createSet(100000, 1);
        CuckooLongKeyHashSet ch = new CuckooLongKeyHashSet(set);

        ArrayList<Long> list = new ArrayList<Long>(set);
        long time = System.currentTimeMillis();
        int sum = 0;
        for (int j = 0; j < 100; j++) {
            for (long x : list) {
                sum += ch.contains(x) ? 1 : 0;
            }
        }
        time = System.currentTimeMillis() - time;
        System.out.println("cuckoo " + time + " dummy " + sum);
        time = System.currentTimeMillis();
        sum = 0;
        for (int j = 0; j < 100; j++) {
            for (long x : list) {
                sum += set.contains(x) ? 1 : 0;
            }
        }
        time = System.currentTimeMillis() - time;
        System.out.println("hashSet " + time + " dummy " + sum);

    }

    @Test
    public void test() {
        for (int size = 10; size < 1000000; size *= 2) {
            test(size);
        }
    }

    static void test(int size) {
        HashMap<String, String> map = new HashMap<String, String>();
        for (int i = 0; i < size; i++) {
            map.put("k" + i, "v" + i);
        }
        UniversalHash<String> hash = new StringHash();
        CuckooHashMap<String, String> ch = new CuckooHashMap<String, String>(
                map, hash);
        for (Entry<String, String> e : map.entrySet()) {
            String v = ch.get(e.getKey());
            assertEquals(v, e.getValue());
        }
        for (int i = 1; i < 10; i++) {
            assertTrue(ch.get("wrong-" + i) == null);
        }
    }

    @Test
    public void testLongKey() {
        for (int size = 10; size < 1000000; size *= 2) {
            testLongKey(size);
        }
    }

    static void testLongKey(int size) {
        HashSet<Long> set = PerformanceTest.createSet(size, 1);
        CuckooLongKeyHashSet ch = new CuckooLongKeyHashSet(set);
        for (long x : set) {
            assertTrue(ch.getIndex(x) >= 0);
            assertTrue(ch.contains(x));
            if (!set.contains(x + 1)) {
                assertTrue(ch.getIndex(x + 1) < 0);
                assertFalse(ch.contains(x + 1));
            }
        }
    }

}
