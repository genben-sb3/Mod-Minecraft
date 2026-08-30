package com.boyben.holobuild.schematic;

/**
 * Decides whether a name may be turned into a file path.
 *
 * Names arrive from the network and from commands, so this is the gate that
 * keeps a modified client from reaching files outside the schematics folder.
 * A forward slash is allowed - schematics live in folders - which is exactly
 * what makes the rest of the rules matter.
 *
 * Deliberately free of Minecraft classes, like {@link com.boyben.holobuild.schematic.format.BitArray},
 * so the rules can be tested against hostile input without starting the game.
 * Security code that is never exercised is security code nobody trusts.
 */
public final class SchematicName {
    private SchematicName() {}

    /** Longer than any sensible name; stops a huge string arriving over the network. */
    public static final int MAX_LENGTH = 128;

    public static boolean isSafe(String name) {
        if (name == null || name.isEmpty() || name.length() > MAX_LENGTH) {
            return false;
        }

        // Anything that could climb out of the folder or address another disk.
        if (name.contains("..") || name.contains("//")
                || name.startsWith("/") || name.endsWith("/")) {
            return false;
        }

        for (int i = 0; i < name.length(); i++) {
            if (!isAllowed(name.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Letters, digits, and the handful of punctuation marks a file name needs.
     * Backslashes are refused on purpose: on Windows they separate folders too,
     * and allowing both would mean two spellings of the same escape.
     */
    private static boolean isAllowed(char c) {
        return (c >= 'a' && c <= 'z')
                || (c >= 'A' && c <= 'Z')
                || (c >= '0' && c <= '9')
                || c == '_' || c == '-' || c == ' ' || c == '.' || c == '/';
    }
}
