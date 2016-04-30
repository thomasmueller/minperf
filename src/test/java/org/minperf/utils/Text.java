package org.minperf.utils;

import java.util.Comparator;

import org.minperf.universal.StringHash;
import org.minperf.universal.UniversalHash;

/**
 * A text. It is similar to a String, but needs less memory.
 */
public class Text {

    /**
     * The byte data (may be shared, so must not be modified).
     */
    final byte[] data;

    /**
     * The offset (start location).
     */
    private final int offset;

    /**
     * The length.
     */
    private final int len;

    public Text(byte[] data, int offset, int len) {
        this.data = data;
        this.offset = offset;
        this.len = len;
    }

    public static int indexOf(byte[] data, int index, int character) {
        while (data[index] != character) {
            index++;
        }
        return index;
    }

    /**
     * The hash code (using a universal hash function).
     *
     * @param index the hash function index
     * @return the hash code
     */
    public long hashCode(long index) {
        return StringHash.getSipHash24(data, offset, offset + len, index, 0);
    }

    @Override
    public int hashCode() {
        return (int) hashCode(0);
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        } else if (!(other instanceof Text)) {
            return false;
        }
        Text o = (Text) other;
        if (o.len != len) {
            return false;
        }
        for (int i = 0; i < len; i++) {
            if (data[offset + i] != o.data[o.offset + i]) {
                return false;
            }
        }
        return true;
    }

    public int compareFast(Text o) {
        int comp = Integer.compare(len, o.len);
        if (comp != 0) {
            return comp;
        }
        for (int i = 0; i < len; i++) {
            int b = data[offset + i] & 0xff;
            int b2 = o.data[o.offset + i] & 0xff;
            if (b != b2) {
                return b > b2 ? 1 : -1;
            }
        }
        return 0;
    }

    @Override
    public String toString() {
        return new String(data, offset, len);
    }

    public int length() {
        return len;
    }

    /**
     * The universal hash function for text.
     */
    public static class UniversalTextHash implements UniversalHash<Text> {

        @Override
        public long universalHash(Text o, long index) {
            return o.hashCode(index);
        }

    }

    /**
     * Compare two texts. For improved speed, sorting is a bit unusual: sorting
     * is first done by size, and only then by data. This is a bit faster then
     * always sorting by data.
     */
    public static class FastComparator implements Comparator<Text> {

        private int equalCount;

        @Override
        public int compare(Text o1, Text o2) {
            int comp = o1.compareFast(o2);
            if (comp == 0) {
                equalCount++;
            }
            return comp;
        }

        public int equalCount() {
            return equalCount;
        }

    }

}