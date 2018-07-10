/*
 * Copyright 2004-2018 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (http://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.minperf.bloom;

import org.minperf.BitBuffer;

/**
 * A binary arithmetic stream.
 */
public class BinaryArithmeticBuffer {

    /**
     * The maximum probability.
     */
    public static final int MAX_PROBABILITY = (1 << 12) - 1;

    /**
     * The low marker.
     */
    protected int low;

    /**
     * The high marker.
     */
    protected int high = 0xffffffff;

    /**
     * A binary arithmetic input stream.
     */
    public static class In extends BinaryArithmeticBuffer {

        private final BitBuffer in;
        private int data;

        public In(BitBuffer in) {
            this.in = in;
            data = (int) in.readNumber(32);
        }

        /**
         * Read a bit.
         *
         * @param probability the probability that the value is true
         * @return the value
         */
        public boolean readBit(int probability) {
            int split = low + probability * ((high - low) >>> 12);
            boolean value;
            // compare unsigned
            if (data + Integer.MIN_VALUE > split + Integer.MIN_VALUE) {
                low = split + 1;
                value = false;
            } else {
                high = split;
                value = true;
            }
            while (low >>> 24 == high >>> 24) {
                data = (data << 8) | (int) in.readNumber(8);
                low <<= 8;
                high = (high << 8) | 0xff;
            }
            return value;
        }

    }

    /**
     * A binary arithmetic output stream.
     */
    public static class Out extends BinaryArithmeticBuffer {

        private final BitBuffer out;

        public Out(BitBuffer out) {
            this.out = out;
        }

        /**
         * Write a bit.
         *
         * @param value the value
         * @param probability the probability that the value is true
         */
        public void writeBit(boolean value, int probability) {
            int split = low + probability * ((high - low) >>> 12);
            if (value) {
                high = split;
            } else {
                low = split + 1;
            }
            while (low >>> 24 == high >>> 24) {
                out.writeNumber(high >>> 24, 8);
                low <<= 8;
                high = (high << 8) | 0xff;
            }
        }

        /**
         * Flush the stream.
         */
        public void flush() {
            out.writeNumber(high >>> 24, 8);
            out.writeNumber(high >>> 16, 8);
            out.writeNumber(high >>> 8, 8);
            out.writeNumber(high, 8);
        }

    }

}
