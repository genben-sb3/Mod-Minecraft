package com.boyben.holobuild.schematic.format;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Random;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for the bit packing used by .litematic files.
 *
 * This is the one piece of HoloBuild that is pure arithmetic and can be checked
 * without starting Minecraft - and it is also the piece where a mistake is
 * hardest to spot, because a wrong bit offset does not crash, it just quietly
 * shuffles a building into nonsense.
 */
class BitArrayTest {

    @Test
    @DisplayName("Bit width: never below two, otherwise just enough for the palette")
    void bitsPerEntry() {
        assertEquals(2, BitArray.bitsPerEntry(0));
        assertEquals(2, BitArray.bitsPerEntry(1));
        assertEquals(2, BitArray.bitsPerEntry(2));
        assertEquals(2, BitArray.bitsPerEntry(4));
        assertEquals(3, BitArray.bitsPerEntry(5));
        assertEquals(3, BitArray.bitsPerEntry(8));
        assertEquals(4, BitArray.bitsPerEntry(9));
        assertEquals(4, BitArray.bitsPerEntry(16));
        assertEquals(5, BitArray.bitsPerEntry(17));
        assertEquals(8, BitArray.bitsPerEntry(256));
        assertEquals(9, BitArray.bitsPerEntry(257));
    }

    @Test
    @DisplayName("Entries that fit inside one long come back unchanged")
    void readsWithinOneLong() {
        // 2 bits each, so 32 fit in a single long.
        int[] values = {0, 1, 2, 3, 3, 2, 1, 0};
        long[] packed = BitArray.pack(values, 2);

        for (int i = 0; i < values.length; i++) {
            assertEquals(values[i], BitArray.get(packed, i, 2), "Eintrag " + i);
        }
    }

    @Test
    @DisplayName("Entries straddling the boundary between two longs come back unchanged")
    void readsAcrossLongBoundary() {
        // 5 bits does not divide 64, so entry 12 starts at bit 60 and spills
        // over into the second long. This is exactly where a padded-long
        // implementation would silently return the wrong value.
        int bits = 5;
        int[] values = new int[40];
        for (int i = 0; i < values.length; i++) {
            values[i] = i % 32;
        }
        long[] packed = BitArray.pack(values, bits);

        for (int i = 0; i < values.length; i++) {
            assertEquals(values[i], BitArray.get(packed, i, bits), "Eintrag " + i);
        }
    }

    @Test
    @DisplayName("Round trip survives every bit width from 2 to 16")
    void roundTripAcrossWidths() {
        Random random = new Random(1234);

        for (int bits = 2; bits <= 16; bits++) {
            int max = (1 << bits) - 1;
            int[] values = new int[200];
            for (int i = 0; i < values.length; i++) {
                values[i] = random.nextInt(max + 1);
            }
            long[] packed = BitArray.pack(values, bits);

            for (int i = 0; i < values.length; i++) {
                assertEquals(values[i], BitArray.get(packed, i, bits),
                        "bits=" + bits + ", Eintrag " + i);
            }
        }
    }

    @Test
    @DisplayName("Known bit pattern decodes correctly, worked out by hand")
    void decodesKnownPattern() {
        // Three 3-bit values 1, 2, 3 packed low bits first:
        //   value 1 at bits 0-2  -> 001        =   1
        //   value 2 at bits 3-5  -> 010 << 3   =  16
        //   value 3 at bits 6-8  -> 011 << 6   = 192
        // which adds up to 209.
        long[] packed = {209L};

        assertEquals(1, BitArray.get(packed, 0, 3));
        assertEquals(2, BitArray.get(packed, 1, 3));
        assertEquals(3, BitArray.get(packed, 2, 3));
    }

    @Test
    @DisplayName("Value split across two longs decodes correctly, worked out by hand")
    void decodesValueSplitByHand() {
        // With 5 bits, entry 12 starts at bit 60: four of its bits sit at the
        // top of the first long, the fifth is the lowest bit of the second.
        // Storing 31 (11111) there means 1111 at bits 60-63 and a lone 1 after.
        // If the code assumed padded longs it would read 15 here instead of 31.
        long[] packed = {0xF000000000000000L, 1L};

        assertEquals(31, BitArray.get(packed, 12, 5));
    }

    @Test
    @DisplayName("pack produces exactly the hand-computed longs")
    void packMatchesKnownPattern() {
        // Same numbers as decodesKnownPattern, from the other direction - this
        // is what stops pack and get sharing a mistake and still agreeing.
        long[] packed = BitArray.pack(new int[] {1, 2, 3}, 3);

        assertEquals(1, packed.length);
        assertEquals(209L, packed[0]);
    }

    @Test
    @DisplayName("Reading past the end reports out of range instead of throwing")
    void readingPastTheEnd() {
        long[] packed = BitArray.pack(new int[] {1, 2, 3}, 4);

        assertEquals(BitArray.OUT_OF_RANGE, BitArray.get(packed, 1000, 4));
        assertEquals(BitArray.OUT_OF_RANGE, BitArray.get(packed, -1, 4));
    }

    @Test
    @DisplayName("Highest value for a width is not clipped")
    void keepsTopValue() {
        for (int bits = 2; bits <= 16; bits++) {
            int max = (1 << bits) - 1;
            long[] packed = BitArray.pack(new int[] {max, 0, max}, bits);

            assertEquals(max, BitArray.get(packed, 0, bits), "bits=" + bits);
            assertEquals(0, BitArray.get(packed, 1, bits), "bits=" + bits);
            assertEquals(max, BitArray.get(packed, 2, bits), "bits=" + bits);
        }
    }
}
