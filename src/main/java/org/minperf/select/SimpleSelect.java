/*
 * Sux4J: Succinct data structures for Java
 *
 * Copyright (C) 2008-2016 Sebastiano Vigna
 *
 *  This library is free software; you can redistribute it and/or modify it
 *  under the terms of the GNU Lesser General Public License as published by the Free
 *  Software Foundation; either version 3 of the License, or (at your option)
 *  any later version.
 *
 *  This library is distributed in the hope that it will be useful, but
 *  WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 *  or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License
 *  for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, see <http://www.gnu.org/licenses/>.
 */
package org.minperf.select;

import java.util.BitSet;

import org.minperf.BitBuffer;

/**
 * A select implementation with guaranteed O(1) query time. This is a copy of
 * the (very good) implementation in Sux4J it.unimi.dsi.sux4j.bits.SimpleSelect
 * by Sebastiano Vigna (see copyright), with a custom serialization format.
 */
public class SimpleSelect extends Select {

    private static final long ONES_STEP_4 = 0x1111111111111111L;
    private static final long ONES_STEP_8 = 0x0101010101010101L;
    private static final long MSBS_STEP_8 = 0x80L * ONES_STEP_8;

    private static final byte[] SELECT_IN_BYTE = {
            -1, 0, 1, 0, 2, 0, 1, 0, 3,
            0, 1, 0, 2, 0, 1, 0, 4, 0, 1, 0, 2, 0, 1, 0, 3, 0, 1, 0, 2, 0, 1,
            0, 5, 0, 1, 0, 2, 0, 1, 0, 3, 0, 1, 0, 2, 0, 1, 0, 4, 0, 1, 0, 2,
            0, 1, 0, 3, 0, 1, 0, 2, 0, 1, 0, 6, 0, 1, 0, 2, 0, 1, 0, 3, 0, 1,
            0, 2, 0, 1, 0, 4, 0, 1, 0, 2, 0, 1, 0, 3, 0, 1, 0, 2, 0, 1, 0, 5,
            0, 1, 0, 2, 0, 1, 0, 3, 0, 1, 0, 2, 0, 1, 0, 4, 0, 1, 0, 2, 0, 1,
            0, 3, 0, 1, 0, 2, 0, 1, 0, 7, 0, 1, 0, 2, 0, 1, 0, 3, 0, 1, 0, 2,
            0, 1, 0, 4, 0, 1, 0, 2, 0, 1, 0, 3, 0, 1, 0, 2, 0, 1, 0, 5, 0, 1,
            0, 2, 0, 1, 0, 3, 0, 1, 0, 2, 0, 1, 0, 4, 0, 1, 0, 2, 0, 1, 0, 3,
            0, 1, 0, 2, 0, 1, 0, 6, 0, 1, 0, 2, 0, 1, 0, 3, 0, 1, 0, 2, 0, 1,
            0, 4, 0, 1, 0, 2, 0, 1, 0, 3, 0, 1, 0, 2, 0, 1, 0, 5, 0, 1, 0, 2,
            0, 1, 0, 3, 0, 1, 0, 2, 0, 1, 0, 4, 0, 1, 0, 2, 0, 1, 0, 3, 0, 1,
            0, 2, 0, 1, 0, -1, -1, -1, 1, -1, 2, 2, 1, -1, 3, 3, 1, 3, 2, 2, 1,
            -1, 4, 4, 1, 4, 2, 2, 1, 4, 3, 3, 1, 3, 2, 2, 1, -1, 5, 5, 1, 5, 2,
            2, 1, 5, 3, 3, 1, 3, 2, 2, 1, 5, 4, 4, 1, 4, 2, 2, 1, 4, 3, 3, 1,
            3, 2, 2, 1, -1, 6, 6, 1, 6, 2, 2, 1, 6, 3, 3, 1, 3, 2, 2, 1, 6, 4,
            4, 1, 4, 2, 2, 1, 4, 3, 3, 1, 3, 2, 2, 1, 6, 5, 5, 1, 5, 2, 2, 1,
            5, 3, 3, 1, 3, 2, 2, 1, 5, 4, 4, 1, 4, 2, 2, 1, 4, 3, 3, 1, 3, 2,
            2, 1, -1, 7, 7, 1, 7, 2, 2, 1, 7, 3, 3, 1, 3, 2, 2, 1, 7, 4, 4, 1,
            4, 2, 2, 1, 4, 3, 3, 1, 3, 2, 2, 1, 7, 5, 5, 1, 5, 2, 2, 1, 5, 3,
            3, 1, 3, 2, 2, 1, 5, 4, 4, 1, 4, 2, 2, 1, 4, 3, 3, 1, 3, 2, 2, 1,
            7, 6, 6, 1, 6, 2, 2, 1, 6, 3, 3, 1, 3, 2, 2, 1, 6, 4, 4, 1, 4, 2,
            2, 1, 4, 3, 3, 1, 3, 2, 2, 1, 6, 5, 5, 1, 5, 2, 2, 1, 5, 3, 3, 1,
            3, 2, 2, 1, 5, 4, 4, 1, 4, 2, 2, 1, 4, 3, 3, 1, 3, 2, 2, 1, -1, -1,
            -1, -1, -1, -1, -1, 2, -1, -1, -1, 3, -1, 3, 3, 2, -1, -1, -1, 4,
            -1, 4, 4, 2, -1, 4, 4, 3, 4, 3, 3, 2, -1, -1, -1, 5, -1, 5, 5, 2,
            -1, 5, 5, 3, 5, 3, 3, 2, -1, 5, 5, 4, 5, 4, 4, 2, 5, 4, 4, 3, 4, 3,
            3, 2, -1, -1, -1, 6, -1, 6, 6, 2, -1, 6, 6, 3, 6, 3, 3, 2, -1, 6,
            6, 4, 6, 4, 4, 2, 6, 4, 4, 3, 4, 3, 3, 2, -1, 6, 6, 5, 6, 5, 5, 2,
            6, 5, 5, 3, 5, 3, 3, 2, 6, 5, 5, 4, 5, 4, 4, 2, 5, 4, 4, 3, 4, 3,
            3, 2, -1, -1, -1, 7, -1, 7, 7, 2, -1, 7, 7, 3, 7, 3, 3, 2, -1, 7,
            7, 4, 7, 4, 4, 2, 7, 4, 4, 3, 4, 3, 3, 2, -1, 7, 7, 5, 7, 5, 5, 2,
            7, 5, 5, 3, 5, 3, 3, 2, 7, 5, 5, 4, 5, 4, 4, 2, 5, 4, 4, 3, 4, 3,
            3, 2, -1, 7, 7, 6, 7, 6, 6, 2, 7, 6, 6, 3, 6, 3, 3, 2, 7, 6, 6, 4,
            6, 4, 4, 2, 6, 4, 4, 3, 4, 3, 3, 2, 7, 6, 6, 5, 6, 5, 5, 2, 6, 5,
            5, 3, 5, 3, 3, 2, 6, 5, 5, 4, 5, 4, 4, 2, 5, 4, 4, 3, 4, 3, 3, 2,
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, 3, -1,
            -1, -1, -1, -1, -1, -1, 4, -1, -1, -1, 4, -1, 4, 4, 3, -1, -1, -1,
            -1, -1, -1, -1, 5, -1, -1, -1, 5, -1, 5, 5, 3, -1, -1, -1, 5, -1,
            5, 5, 4, -1, 5, 5, 4, 5, 4, 4, 3, -1, -1, -1, -1, -1, -1, -1, 6,
            -1, -1, -1, 6, -1, 6, 6, 3, -1, -1, -1, 6, -1, 6, 6, 4, -1, 6, 6,
            4, 6, 4, 4, 3, -1, -1, -1, 6, -1, 6, 6, 5, -1, 6, 6, 5, 6, 5, 5, 3,
            -1, 6, 6, 5, 6, 5, 5, 4, 6, 5, 5, 4, 5, 4, 4, 3, -1, -1, -1, -1,
            -1, -1, -1, 7, -1, -1, -1, 7, -1, 7, 7, 3, -1, -1, -1, 7, -1, 7, 7,
            4, -1, 7, 7, 4, 7, 4, 4, 3, -1, -1, -1, 7, -1, 7, 7, 5, -1, 7, 7,
            5, 7, 5, 5, 3, -1, 7, 7, 5, 7, 5, 5, 4, 7, 5, 5, 4, 5, 4, 4, 3, -1,
            -1, -1, 7, -1, 7, 7, 6, -1, 7, 7, 6, 7, 6, 6, 3, -1, 7, 7, 6, 7, 6,
            6, 4, 7, 6, 6, 4, 6, 4, 4, 3, -1, 7, 7, 6, 7, 6, 6, 5, 7, 6, 6, 5,
            6, 5, 5, 3, 7, 6, 6, 5, 6, 5, 5, 4, 6, 5, 5, 4, 5, 4, 4, 3, -1, -1,
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, 4, -1, -1, -1, -1,
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, 5, -1, -1, -1, -1, -1,
            -1, -1, 5, -1, -1, -1, 5, -1, 5, 5, 4, -1, -1, -1, -1, -1, -1, -1,
            -1, -1, -1, -1, -1, -1, -1, -1, 6, -1, -1, -1, -1, -1, -1, -1, 6,
            -1, -1, -1, 6, -1, 6, 6, 4, -1, -1, -1, -1, -1, -1, -1, 6, -1, -1,
            -1, 6, -1, 6, 6, 5, -1, -1, -1, 6, -1, 6, 6, 5, -1, 6, 6, 5, 6, 5,
            5, 4, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            7, -1, -1, -1, -1, -1, -1, -1, 7, -1, -1, -1, 7, -1, 7, 7, 4, -1,
            -1, -1, -1, -1, -1, -1, 7, -1, -1, -1, 7, -1, 7, 7, 5, -1, -1, -1,
            7, -1, 7, 7, 5, -1, 7, 7, 5, 7, 5, 5, 4, -1, -1, -1, -1, -1, -1,
            -1, 7, -1, -1, -1, 7, -1, 7, 7, 6, -1, -1, -1, 7, -1, 7, 7, 6, -1,
            7, 7, 6, 7, 6, 6, 4, -1, -1, -1, 7, -1, 7, 7, 6, -1, 7, 7, 6, 7, 6,
            6, 5, -1, 7, 7, 6, 7, 6, 6, 5, 7, 6, 6, 5, 6, 5, 5, 4, -1, -1, -1,
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            -1, -1, -1, -1, -1, -1, -1, -1, -1, 5, -1, -1, -1, -1, -1, -1, -1,
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            -1, -1, -1, -1, -1, -1, -1, 6, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            -1, -1, -1, -1, -1, -1, 6, -1, -1, -1, -1, -1, -1, -1, 6, -1, -1,
            -1, 6, -1, 6, 6, 5, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            -1, -1, 7, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            -1, 7, -1, -1, -1, -1, -1, -1, -1, 7, -1, -1, -1, 7, -1, 7, 7, 5,
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, 7, -1,
            -1, -1, -1, -1, -1, -1, 7, -1, -1, -1, 7, -1, 7, 7, 6, -1, -1, -1,
            -1, -1, -1, -1, 7, -1, -1, -1, 7, -1, 7, 7, 6, -1, -1, -1, 7, -1,
            7, 7, 6, -1, 7, 7, 6, 7, 6, 6, 5, -1, -1, -1, -1, -1, -1, -1, -1,
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            6, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, 7, -1, -1, -1,
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, 7, -1, -1, -1, -1, -1,
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, 7, -1, -1, -1, -1, -1, -1,
            -1, 7, -1, -1, -1, 7, -1, 7, 7, 6, -1, -1, -1, -1, -1, -1, -1, -1,
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            -1, -1, -1, -1, -1, -1, -1, -1, -1, 7
        };

    private static final boolean ASSERTS = true;

    private static final int MAX_ONES_PER_INVENTORY = 8192;
    private static final int MAX_LOG2_LONGWORDS_PER_SUBINVENTORY = 3;

    /**
     * The maximum size of span to qualify for a subinventory made of 16-bit
     * offsets.
     */
    private static final int MAX_SPAN = 1 << 16;

    // The number of ones
    private final long numOnes;
    // The number of words
    private final int numWords;
    // The cached result of the bits
    private transient long[] bits;

    /**
     * The first-level inventory containing information about one bit each
     * {@link #onesPerInventory}. If the entry is nonnegative, it is the rank of
     * the bit and subsequent information is recorded in {@link #subinventory16}
     * as offsets of one bit each {@link #onesPerSub16} (then, sequential search
     * is necessary). Otherwise, a negative value means that offsets are too
     * large and they have been recorded as 64-bit values. If
     * {@link #onesPerSub64} is 1, then offsets are directly stored into
     * {@link #subinventory}. Otherwise, the first {@link #subinventory} entry
     * is actually a pointer to {@link #exactSpill}, where the offsets can be
     * found.
     */
    private final long[] inventory;
    /** The logarithm of the number of ones per {@link #inventory} entry. */
    private final int log2OnesPerInventory;
    /** The number of ones per {@link #inventory} entry. */
    private final int onesPerInventory;
    /** The mask associated to the number of ones per {@link #inventory} entry. */
    private final int onesPerInventoryMask;
    /**
     * The second-level inventory (records the offset of each bit w.r.t. the
     * first-level inventory).
     */
    private final long[] subinventory;
    /**
     * The logarithm of the number of longwords used in the part of the
     * subinventory associated to an inventory entry.
     */
    private final int log2LongwordsPerSubinventory;
    /**
     * The logarithm of the number of ones for each {@link #subinventory}
     * longword.
     */
    private final int log2OnesPerSub64;
    /** The number of ones for each {@link #subinventory} longword. */
    private final int onesPerSub64;
    /**
     * The logarithm of the number of ones for each {@link #subinventory} short.
     */
    private final int log2OnesPerSub16;
    /** The number of ones for each {@link #subinventory} short. */
    private final int onesPerSub16;
    /**
     * The mask associated to number of ones for each {@link #subinventory}
     * short.
     */
    private final int onesPerSub16Mask;
    /** The list of exact spills. */
    private final long[] exactSpill;

    private SimpleSelect(BitBuffer buffer) {
        numOnes = buffer.readEliasDelta() - 1;
        numWords = (int) buffer.readEliasDelta() - 1;
        bits = new long[numWords];
        for (int i = 0; i < bits.length; i++) {
            bits[i] = buffer.readLong();
        }
        inventory = new long[(int) buffer.readEliasDelta() - 1];
        for (int i = 0; i < inventory.length; i++) {
            inventory[i] = buffer.readLong();
        }
        log2OnesPerInventory = (int) buffer.readEliasDelta() - 1;
        onesPerInventory = 1 << log2OnesPerInventory;
        onesPerInventoryMask = onesPerInventory - 1;
        subinventory = new long[(int) buffer.readEliasDelta() - 1];
        for (int i = 0; i < subinventory.length; i++) {
            subinventory[i] = buffer.readLong();
        }
        log2LongwordsPerSubinventory = Math.min(
                MAX_LOG2_LONGWORDS_PER_SUBINVENTORY,
                Math.max(0, log2OnesPerInventory - 2));
        log2OnesPerSub64 = Math.max(0, log2OnesPerInventory -
                log2LongwordsPerSubinventory);
        onesPerSub64 = 1 << log2OnesPerSub64;
        log2OnesPerSub16 = Math.max(0, log2OnesPerSub64 - 2);
        onesPerSub16 = 1 << log2OnesPerSub16;
        onesPerSub16Mask = onesPerSub16 - 1;
        exactSpill = new long[(int) buffer.readEliasDelta() - 1];
        for (int i = 0; i < exactSpill.length; i++) {
            exactSpill[i] = buffer.readLong();
        }
    }

    private SimpleSelect(BitSet bitSet) {
        long length = bitSet.length();
        numWords = (int) ((length + 63) / 64);
        this.bits = new long[numWords];
        for (int i = 0; i < length; i++) {
            bits[i / 64] |= bitSet.get(i) ? (1L << (i % 64)) : 0;
        }
        // We compute quickly the number of ones (possibly counting spurious
        // bits in the last word).
        long d = 0;
        for (int i = numWords; i-- != 0;) {
            d += Long.bitCount(bits[i]);
        }
        int x = length == 0 ? 1
                : (int) ((d * MAX_ONES_PER_INVENTORY + length - 1) / length);
        int mostSignificantBit = 63 - Long.numberOfLeadingZeros(x);
        log2OnesPerInventory = mostSignificantBit;
        onesPerInventory = 1 << log2OnesPerInventory;
        onesPerInventoryMask = onesPerInventory - 1;
        int inventorySize = (int) ((d + onesPerInventory - 1) / onesPerInventory);
        inventory = new long[inventorySize + 1];
        // First phase: we build an inventory for each one out of
        // onesPerInventory.
        d = 0;
        for (int i = 0; i < numWords; i++) {
            for (int j = 0; j < 64; j++) {
                if (i * 64L + j >= length) {
                    break;
                }
                if ((bits[i] & 1L << j) != 0) {
                    if ((d & onesPerInventoryMask) == 0) {
                        inventory[(int) (d >>> log2OnesPerInventory)] = i *
                                64L + j;
                    }
                    d++;
                }
            }
        }
        numOnes = d;
        inventory[inventorySize] = length;
        log2LongwordsPerSubinventory = Math.min(
                MAX_LOG2_LONGWORDS_PER_SUBINVENTORY,
                Math.max(0, log2OnesPerInventory - 2));
        log2OnesPerSub64 = Math.max(0, log2OnesPerInventory -
                log2LongwordsPerSubinventory);
        log2OnesPerSub16 = Math.max(0, log2OnesPerSub64 - 2);
        onesPerSub64 = 1 << log2OnesPerSub64;
        onesPerSub16 = 1 << log2OnesPerSub16;
        onesPerSub16Mask = onesPerSub16 - 1;
        if (onesPerInventory <= 1) {
            subinventory = exactSpill = new long[0];
            return;
        }
        d = 0;
        int ones;
        long diff16 = 0, start = 0, span = 0;
        int spilled = 0, inventoryIndex = 0;
        for (int i = 0; i < numWords; i++) {
            // We estimate the subinventory and exact spill size
            for (int j = 0; j < 64; j++) {
                if (i * 64L + j >= length) {
                    break;
                }
                if ((bits[i] & 1L << j) != 0) {
                    if ((d & onesPerInventoryMask) == 0) {
                        inventoryIndex = (int) (d >>> log2OnesPerInventory);
                        start = inventory[inventoryIndex];
                        span = inventory[inventoryIndex + 1] - start;
                        ones = (int) Math
                                .min(numOnes - d, onesPerInventory);

                        // We must always count (possibly unused) diff16's.
                        // And we cannot store less then 4 diff16.
                        diff16 += Math.max(4,
                                        (ones + onesPerSub16 - 1) >>> log2OnesPerSub16);
                        if (span >= MAX_SPAN && onesPerSub64 > 1) {
                            spilled += ones;
                        }
                    }
                    d++;
                }
            }
        }
        int subinventorySize = (int) ((diff16 + 3) / 4);
        int exactSpillSize = spilled;
        subinventory = new long[subinventorySize];
        exactSpill = new long[exactSpillSize];
        int offset = 0;
        spilled = 0;
        d = 0;
        for (int i = 0; i < numWords; i++) {
            for (int j = 0; j < 64; j++) {
                if (i * 64L + j >= length) {
                    break;
                }
                if ((bits[i] & 1L << j) != 0) {
                    if ((d & onesPerInventoryMask) == 0) {
                        inventoryIndex = (int) (d >>> log2OnesPerInventory);
                        start = inventory[inventoryIndex];
                        span = inventory[inventoryIndex + 1] - start;
                        offset = 0;
                    }
                    if (span < MAX_SPAN) {
                        if (ASSERTS) {
                            assert i * 64L + j - start <= MAX_SPAN;
                        }
                        if ((d & onesPerSub16Mask) == 0) {
                            setSubInventory16((inventoryIndex << log2LongwordsPerSubinventory + 2) +
                                    offset++, (int) (i * 64L + j - start));
                        }
                    } else {
                        if (onesPerSub64 == 1) {
                            subinventory[(inventoryIndex << log2LongwordsPerSubinventory) +
                                    offset++] = i * 64L + j;
                        } else {
                            if ((d & onesPerInventoryMask) == 0) {
                                inventory[inventoryIndex] |= 1L << 63;
                                subinventory[inventoryIndex << log2LongwordsPerSubinventory] = spilled;
                            }
                            exactSpill[spilled++] = i * 64L + j;
                        }
                    }
                    d++;
                }
            }
        }
    }

    public static SimpleSelect load(BitBuffer buffer) {
        return new SimpleSelect(buffer);
    }

    public static SimpleSelect generate(BitSet bitSet, BitBuffer buffer) {
        SimpleSelect s = new SimpleSelect(bitSet);
        buffer.writeEliasDelta(s.numOnes + 1);
        buffer.writeEliasDelta(s.numWords + 1);
        for (long x : s.bits) {
            buffer.writeNumber(x, 64);
        }
        buffer.writeEliasDelta(s.inventory.length + 1);
        for (long x : s.inventory) {
            buffer.writeNumber(x, 64);
        }
        buffer.writeEliasDelta(s.log2OnesPerInventory + 1);
        buffer.writeEliasDelta(s.subinventory.length + 1);
        for (long x : s.subinventory) {
            buffer.writeNumber(x, 64);
        }
        buffer.writeEliasDelta(s.exactSpill.length + 1);
        for (long x : s.exactSpill) {
            buffer.writeNumber(x, 64);
        }
        return s;
    }

    void setSubInventory16(int index, int x) {
        subinventory[index / 4] |= ((long) x) << (16 * (index % 4));
    }

    int getSubInventory16(int index) {
        return (short) (subinventory[index / 4] >>> (16 * (index % 4)));
    }

    @Override
    public long select(long rank) {
        if (rank >= numOnes) {
            return -1;
        }
        int inventoryIndex = (int) (rank >>> log2OnesPerInventory);
        long inventoryRank = inventory[inventoryIndex];
        int subrank = (int) (rank & onesPerInventoryMask);
        if (subrank == 0) {
            return inventoryRank & ~(1L << 63);
        }
        long start;
        int residual;
        if (inventoryRank >= 0) {
            start = inventoryRank +
                    getSubInventory16((inventoryIndex << log2LongwordsPerSubinventory + 2) +
                            (subrank >>> log2OnesPerSub16));
            residual = subrank & onesPerSub16Mask;
        } else {
            if (onesPerSub64 == 1) {
                return subinventory[(inventoryIndex << log2LongwordsPerSubinventory) +
                        subrank];
            }
            return exactSpill[(int) (subinventory[inventoryIndex << log2LongwordsPerSubinventory] + subrank)];
        }
        if (residual == 0) {
            return start;
        }
        long[] bits = this.bits;
        int wordIndex = (int) (start / 64);
        long word = bits[wordIndex] & (-1L << start);
        for (;;) {
            int bitCount = Long.bitCount(word);
            if (residual < bitCount) {
                break;
            }
            word = bits[++wordIndex];
            residual -= bitCount;
        }
        return wordIndex * 64L + selectInLong(word, residual);
    }

    public static int selectInLong(long x, int rank) {
        assert rank < Long.bitCount(x);
        // Phase 1: sums by byte
        long byteSums = x - ((x & 0xa * ONES_STEP_4) >>> 1);
        byteSums = (byteSums & 3 * ONES_STEP_4) +
                ((byteSums >>> 2) & 3 * ONES_STEP_4);
        byteSums = (byteSums + (byteSums >>> 4)) & 0x0f * ONES_STEP_8;
        byteSums *= ONES_STEP_8;
        // Phase 2: compare each byte sum with rank to obtain the relevant byte
        long rankStep8 = rank * ONES_STEP_8;
        int byteOffset = (int) (((((rankStep8 | MSBS_STEP_8) - byteSums) & MSBS_STEP_8) >>> 7) *
                ONES_STEP_8 >>> 53) &
                ~0x7;
        int byteRank = (int) (rank - (((byteSums << 8) >>> byteOffset) & 0xFF));
        return byteOffset +
                SELECT_IN_BYTE[(int) (x >>> byteOffset & 0xFF) | byteRank << 8];
    }

    @Override
    public long selectPair(long i) {
        long x = select(i);
        int curr = (int) (x / Long.SIZE);
        long window = bits[curr] & -1L << x;
        window &= window - 1;
        while (window == 0) {
            window = bits[++curr];
        }
        long y = curr * Long.SIZE + Long.numberOfTrailingZeros(window);
        return (x << 32) | y;
    }

}
