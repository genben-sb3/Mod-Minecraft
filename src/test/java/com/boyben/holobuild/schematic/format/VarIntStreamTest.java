package com.boyben.holobuild.schematic.format;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.Random;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for the varint decoding used by .schem files.
 *
 * Same reasoning as {@link BitArrayTest}: a wrong shift does not crash, it just
 * quietly returns the wrong block for every position after it.
 */
class VarIntStreamTest {

    @Test
    @DisplayName("Single-byte numbers, worked out by hand")
    void decodesSmallNumbers() throws IOException {
        // Values below 128 fit in one byte and are stored as-is.
        VarIntStream stream = new VarIntStream(new byte[] {0, 1, 42, 127});

        assertEquals(0, stream.next());
        assertEquals(1, stream.next());
        assertEquals(42, stream.next());
        assertEquals(127, stream.next());
        assertFalse(stream.hasNext());
    }

    @Test
    @DisplayName("Multi-byte numbers, worked out by hand")
    void decodesLargeNumbers() throws IOException {
        // 128 is the first value needing two bytes: low seven bits 0 with the
        // continuation flag set, then 1.
        //   128 -> 0x80 0x01
        //   300 -> 0xAC 0x02   (300 = 0b100101100: low 7 = 0101100, high = 10)
        VarIntStream stream = new VarIntStream(
                new byte[] {(byte) 0x80, 0x01, (byte) 0xAC, 0x02});

        assertEquals(128, stream.next());
        assertEquals(300, stream.next());
        assertFalse(stream.hasNext());
    }

    @Test
    @DisplayName("encode produces exactly the hand-computed bytes")
    void encodesKnownBytes() {
        // From the other direction, so encode and next cannot share a mistake
        // and still agree with each other.
        assertArrayEquals(new byte[] {127}, VarIntStream.encode(127));
        assertArrayEquals(new byte[] {(byte) 0x80, 0x01}, VarIntStream.encode(128));
        assertArrayEquals(new byte[] {(byte) 0xAC, 0x02}, VarIntStream.encode(300));
    }

    @Test
    @DisplayName("Round trip survives a wide spread of values")
    void roundTrip() throws IOException {
        Random random = new Random(4321);
        int[] values = new int[500];
        for (int i = 0; i < values.length; i++) {
            // Mix of tiny, medium and large - palettes can be any of these.
            values[i] = switch (i % 3) {
                case 0 -> random.nextInt(128);
                case 1 -> random.nextInt(16384);
                default -> random.nextInt(Integer.MAX_VALUE);
            };
        }

        VarIntStream stream = new VarIntStream(VarIntStream.encode(values));
        for (int i = 0; i < values.length; i++) {
            assertEquals(values[i], stream.next(), "Eintrag " + i);
        }
        assertFalse(stream.hasNext());
    }

    @Test
    @DisplayName("Boundary values where the byte count changes")
    void boundaryValues() throws IOException {
        int[] values = {127, 128, 16383, 16384, 2097151, 2097152};

        VarIntStream stream = new VarIntStream(VarIntStream.encode(values));
        for (int value : values) {
            assertEquals(value, stream.next());
        }
    }

    @Test
    @DisplayName("A number cut off at the end is reported, not silently accepted")
    void truncatedNumberFails() {
        // 0x80 says "another byte follows" but the data stops here.
        VarIntStream stream = new VarIntStream(new byte[] {(byte) 0x80});

        assertTrue(stream.hasNext());
        assertThrows(IOException.class, stream::next);
    }

    @Test
    @DisplayName("An endlessly continued number is rejected instead of looping")
    void overlongNumberFails() {
        byte[] endless = new byte[10];
        for (int i = 0; i < endless.length; i++) {
            endless[i] = (byte) 0x80;      // every byte says "more follows"
        }
        VarIntStream stream = new VarIntStream(endless);

        assertThrows(IOException.class, stream::next);
    }
}
