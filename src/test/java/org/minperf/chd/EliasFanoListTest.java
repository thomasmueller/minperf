package org.minperf.chd;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.minperf.BitBuffer;

public class EliasFanoListTest {
    @Test
    public void test() {
        int[] list = new int[10];
        for (int i = 0; i < list.length; i++) {
            list[i] = i;
        }
        BitBuffer buffer = new BitBuffer(1000);
        EliasFanoList elist = EliasFanoList.generate(list, buffer);
        for (int i = 0; i < list.length; i++) {
            assertEquals("" + i, list[i], elist.get(i));
        }
        int len = buffer.position();
        buffer.seek(0);
        elist = EliasFanoList.load(buffer);
        assertEquals(len, buffer.position());
        for (int i = 0; i < list.length; i++) {
            assertEquals("" + i, list[i], elist.get(i));
        }
    }
}
