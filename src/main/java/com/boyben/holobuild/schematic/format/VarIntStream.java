package com.boyben.holobuild.schematic.format;

import java.io.IOException;

/**
 * Reads the varint stream that .schem files use for their block data.
 *
 * A varint stores a number in as few bytes as possible: seven bits of value per
 * byte, and the top bit says whether another byte follows. Small numbers take
 * one byte, which matters because a schematic has one number per block.
 *
 * Deliberately free of Minecraft classes, like {@link BitArray}, so the decoding
 * can be tested without starting the game. A wrong shift here does not crash -
 * it silently turns every block after it into the wrong one.
 */
public final class VarIntStream {

    /** Five 7-bit groups already cover the whole int range. */
    private static final int MAX_SHIFT = 28;

    private final byte[] data;
    private int cursor;

    public VarIntStream(byte[] data) {
        this.data = data;
    }

    public boolean hasNext() {
        return cursor < data.length;
    }

    public int position() {
        return cursor;
    }

    /**
     * Reads the next number.
     *
     * @throws IOException if the stream ends mid-number or the number is longer
     *                     than an int can hold
     */
    public int next() throws IOException {
        int value = 0;
        int shift = 0;

        while (true) {
            if (cursor >= data.length) {
                throw new IOException("Blockdaten brechen mitten in einer Zahl ab");
            }
            byte b = data[cursor++];
            value |= (b & 0x7F) << shift;
            if ((b & 0x80) == 0) {
                return value;
            }
            shift += 7;
            if (shift > MAX_SHIFT) {
                throw new IOException("Ungueltige Zahl in den Blockdaten (zu lang)");
            }
        }
    }

    /**
     * Encodes numbers the same way.
     * Used by the tests, and it documents the other half of the format.
     */
    public static byte[] encode(int... values) {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        for (int value : values) {
            int remaining = value;
            while (true) {
                int chunk = remaining & 0x7F;
                remaining >>>= 7;
                if (remaining != 0) {
                    out.write(chunk | 0x80);
                } else {
                    out.write(chunk);
                    break;
                }
            }
        }
        return out.toByteArray();
    }
}
