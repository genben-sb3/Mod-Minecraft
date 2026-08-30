package com.boyben.holobuild.schematic;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for the name check that guards the schematics folder.
 *
 * Every name here is one a modified client could send. The point is not that
 * the code looks right - it is that these specific attempts are refused.
 */
class SchematicNameTest {

    @Test
    @DisplayName("Ordinary names are accepted")
    void acceptsPlainNames() {
        assertTrue(SchematicName.isSafe("cottage"));
        assertTrue(SchematicName.isSafe("test_haus"));
        assertTrue(SchematicName.isSafe("Turm 2"));
        assertTrue(SchematicName.isSafe("mein-haus.v2"));
        assertTrue(SchematicName.isSafe("tower_mid_1"));
    }

    @Test
    @DisplayName("Folders are accepted, that is the point of allowing slashes")
    void acceptsFolders() {
        assertTrue(SchematicName.isSafe("haeuser/villa"));
        assertTrue(SchematicName.isSafe("redstone/tueren/kolbentuer"));
        assertTrue(SchematicName.isSafe("a/b/c/d/e"));
    }

    @Test
    @DisplayName("Climbing out with .. is refused, however it is dressed up")
    void refusesParentDirectory() {
        assertFalse(SchematicName.isSafe(".."));
        assertFalse(SchematicName.isSafe("../secrets"));
        assertFalse(SchematicName.isSafe("haeuser/../../secrets"));
        assertFalse(SchematicName.isSafe("ok/.."));
        assertFalse(SchematicName.isSafe("..hidden"));
    }

    @Test
    @DisplayName("Backslashes are refused - on Windows they separate folders too")
    void refusesBackslashes() {
        assertFalse(SchematicName.isSafe("haeuser\\villa"));
        assertFalse(SchematicName.isSafe("..\\..\\windows\\system32"));
        assertFalse(SchematicName.isSafe("a\\b"));
    }

    @Test
    @DisplayName("Absolute paths and drive letters are refused")
    void refusesAbsolutePaths() {
        assertFalse(SchematicName.isSafe("/etc/passwd"));
        assertFalse(SchematicName.isSafe("/haeuser/villa"));
        assertFalse(SchematicName.isSafe("C:/Windows/system.ini"));
        assertFalse(SchematicName.isSafe("C:\\Windows"));
    }

    @Test
    @DisplayName("Malformed slashes are refused")
    void refusesOddSlashes() {
        assertFalse(SchematicName.isSafe("haeuser//villa"));   // empty segment
        assertFalse(SchematicName.isSafe("haeuser/"));         // trailing
        assertFalse(SchematicName.isSafe("/"));
        assertFalse(SchematicName.isSafe("//"));
    }

    @Test
    @DisplayName("Empty, null and overlong names are refused")
    void refusesEmptyAndOverlong() {
        assertFalse(SchematicName.isSafe(null));
        assertFalse(SchematicName.isSafe(""));
        assertFalse(SchematicName.isSafe("x".repeat(SchematicName.MAX_LENGTH + 1)));
        assertTrue(SchematicName.isSafe("x".repeat(SchematicName.MAX_LENGTH)));
    }

    @Test
    @DisplayName("Characters with no business in a file name are refused")
    void refusesOddCharacters() {
        assertFalse(SchematicName.isSafe("haus\u0000.nbt"));   // null byte
        assertFalse(SchematicName.isSafe("haus\n"));
        assertFalse(SchematicName.isSafe("haus\r\nrm -rf"));
        assertFalse(SchematicName.isSafe("haus;rm"));
        assertFalse(SchematicName.isSafe("haus|pipe"));
        assertFalse(SchematicName.isSafe("haus*stern"));
        assertFalse(SchematicName.isSafe("haus?frage"));
        assertFalse(SchematicName.isSafe("~/haus"));
        assertFalse(SchematicName.isSafe("$HOME"));
    }
}
