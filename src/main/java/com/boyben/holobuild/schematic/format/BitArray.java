package com.boyben.holobuild.schematic.format;

/**
 * Reads numbers that were packed tightly into a long array.
 *
 * Litematica stores one palette index per block position using only as many
 * bits as the palette needs, packed continuously - so a single index can start
 * in one long and finish in the next. Minecraft's own chunk storage has padded
 * each long since 1.16, which is why this cannot simply reuse vanilla code.
 *
 * Deliberately free of any Minecraft classes: this is pure arithmetic, and
 * keeping it separate means it can be tested on its own. Getting it wrong turns
 * a building into confetti, so it is worth testing.
 */
public final class BitArray {
    private BitArray() {}

    /** Marks a read that ran past the end of the array. */
    public static final int OUT_OF_RANGE = -1;

    /**
     * How many bits one entry takes: never fewer than two, otherwise just
     * enough to hold the largest index in a palette of this size.
     */
    public static int bitsPerEntry(int paletteSize) {
        if (paletteSize <= 1) {
            return 2;
        }
        return Math.max(2, 32 - Integer.numberOfLeadingZeros(paletteSize - 1));
    }

    /**
     * Reads the entry at {@code index}.
     *
     * @return the value, or {@link #OUT_OF_RANGE} if it lies past the array
     */
    public static int get(long[] packed, int index, int bits) {
        if (index < 0 || bits <= 0 || bits > 32) {
            return OUT_OF_RANGE;
        }
        long mask = (1L << bits) - 1L;

        long startBit = (long) index * bits;
        int startLong = (int) (startBit >> 6);
        int endLong = (int) (((long) (index + 1) * bits - 1L) >> 6);
        int offset = (int) (startBit & 63L);

        if (startLong >= packed.length) {
            return OUT_OF_RANGE;
        }
        if (startLong == endLong) {
            return (int) ((packed[startLong] >>> offset) & mask);
        }
        if (endLong >= packed.length) {
            return OUT_OF_RANGE;
        }
        // The entry straddles two longs: take the high bits of the first and
        // the low bits of the second.
        int spill = 64 - offset;
        return (int) (((packed[startLong] >>> offset) | (packed[endLong] << spill)) & mask);
    }

    /**
     * Packs values the same way, so a round trip can be checked.
     * Only used by tests today, but it is the other half of the definition.
     */
    public static long[] pack(int[] values, int bits) {
        long totalBits = (long) values.length * bits;
        int longs = (int) ((totalBits + 63) >> 6);
        long[] packed = new long[Math.max(longs, 1)];
        long mask = (1L << bits) - 1L;

        for (int index = 0; index < values.length; index++) {
            long value = values[index] & mask;
            long startBit = (long) index * bits;
            int startLong = (int) (startBit >> 6);
            int endLong = (int) (((long) (index + 1) * bits - 1L) >> 6);
            int offset = (int) (startBit & 63L);

            packed[startLong] |= value << offset;
            if (startLong != endLong) {
                packed[endLong] |= value >>> (64 - offset);
            }
        }
        return packed;
    }
}
