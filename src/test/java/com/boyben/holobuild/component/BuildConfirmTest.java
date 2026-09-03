package com.boyben.holobuild.component;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for the "click again to build anyway" offer.
 *
 * Every case here would go unnoticed in play until it bit somebody: an offer
 * that never expires means a click a week later silently empties an inventory;
 * one that carries over to a different building means the warning was about the
 * cottage and the lighthouse got built.
 */
class BuildConfirmTest {

    private static final long WARNED_AT = 1_000L;

    private static BuildConfirm warnedAboutCottage() {
        return new BuildConfirm("cottage", WARNED_AT);
    }

    @Test
    @DisplayName("Right after the warning, and all through the window, the build may go ahead")
    void allowsInsideTheWindow() {
        BuildConfirm confirm = warnedAboutCottage();
        assertTrue(confirm.allows("cottage", WARNED_AT), "Straight away");
        assertTrue(confirm.allows("cottage", WARNED_AT + 100), "Halfway through");
        assertTrue(confirm.allows("cottage", WARNED_AT + BuildConfirm.WINDOW_TICKS),
                "The last tick of the window still counts");
    }

    @Test
    @DisplayName("One tick past the window the offer is gone")
    void expiresAfterTheWindow() {
        assertFalse(warnedAboutCottage().allows("cottage", WARNED_AT + BuildConfirm.WINDOW_TICKS + 1));
        assertFalse(warnedAboutCottage().allows("cottage", WARNED_AT + 100_000),
                "Ein Klick viel spaeter ist eine neue Entscheidung, keine alte");
    }

    @Test
    @DisplayName("A warning about one building says nothing about another")
    void doesNotCarryOverToAnotherBuilding() {
        BuildConfirm confirm = warnedAboutCottage();
        assertFalse(confirm.allows("lighthouse", WARNED_AT + 10),
                "Die Warnung galt der Huette, nicht dem Leuchtturm");
        assertFalse(confirm.allows("", WARNED_AT + 10));
    }

    @Test
    @DisplayName("No warning at all allows nothing")
    void emptyConfirmAllowsNothing() {
        assertFalse(BuildConfirm.NONE.allows("cottage", 0));
        assertFalse(BuildConfirm.NONE.allows("cottage", 5_000));
        assertFalse(BuildConfirm.NONE.allows("", 0),
                "Auch ein leerer Name darf nicht zufaellig auf den leeren Eintrag passen");
    }

    @Test
    @DisplayName("A clock that went backwards does not hand out a permanent pass")
    void ignoresTimeGoingBackwards() {
        // Happens when the wand is carried into another world, or a save from
        // before the current one is loaded. A negative wait must not read as
        // "well within the window".
        assertFalse(warnedAboutCottage().allows("cottage", WARNED_AT - 1));
        assertFalse(warnedAboutCottage().allows("cottage", 0));
    }
}
