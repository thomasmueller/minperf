package org.minperf.utils;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * Tests the text class.
 */
public class TextTest {

    @Test
    public void test() {
        Text text = new Text("hello world".getBytes(), 0, 11);
        assertEquals("hello world", text.toString());
        assertEquals("hello", text.subSequence(0, 5).toString());
        assertEquals("world", text.subSequence(6, 6 + 5).toString());
        assertEquals('h', text.charAt(0));
    }

}
