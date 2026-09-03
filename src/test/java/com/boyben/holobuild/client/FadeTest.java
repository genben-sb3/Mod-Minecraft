package com.boyben.holobuild.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for the animation timing.
 *
 * Every case here is one that fails quietly rather than loudly: a progress
 * above 1 overflows a colour byte, a negative one turns a highlight inside out,
 * and a zero duration divides by zero on exactly the frame nobody is watching.
 */
class FadeTest {

    private static final long START = 10_000L;
    private static final long DURATION = 500L;

    @Test
    @DisplayName("Runs from 0 to 1 across the duration")
    void progressAcrossTheDuration() {
        assertEquals(0f, Fade.progress(START, START, DURATION), "At the start, nothing has happened");
        assertEquals(0.5f, Fade.progress(START, START + 250, DURATION), 0.001f);
        assertEquals(1f, Fade.progress(START, START + 500, DURATION), "Exactly at the end it is done");
    }

    @Test
    @DisplayName("Never leaves 0..1, however long ago it started")
    void progressStaysInRange() {
        assertEquals(1f, Fade.progress(START, START + 10_000, DURATION),
                "Long past the end it stays at 1, it does not keep counting");
        assertEquals(0f, Fade.progress(START, START - 5_000, DURATION),
                "A clock that jumped backwards must not give a negative fade");
    }

    @Test
    @DisplayName("A duration of zero counts as finished, not as a division by zero")
    void zeroDuration() {
        assertEquals(1f, Fade.progress(START, START, 0));
        assertEquals(1f, Fade.progress(START, START, -1));
    }

    @Test
    @DisplayName("Smoothing keeps the ends put and stays inside 0..1")
    void smoothKeepsItsEnds() {
        assertEquals(0f, Fade.smooth(0f), 0.0001f);
        assertEquals(1f, Fade.smooth(1f), 0.0001f);
        assertEquals(0.5f, Fade.smooth(0.5f), 0.0001f, "The middle stays in the middle");

        // Out-of-range input is clamped rather than sent somewhere absurd.
        assertEquals(0f, Fade.smooth(-3f), 0.0001f);
        assertEquals(1f, Fade.smooth(9f), 0.0001f);
    }

    @Test
    @DisplayName("Smoothing only ever moves forwards")
    void smoothNeverGoesBackwards() {
        float previous = -1f;
        for (int step = 0; step <= 100; step++) {
            float value = Fade.smooth(step / 100f);
            assertTrue(value >= previous,
                    "Bei t=" + (step / 100f) + " ging die Kurve zurueck: " + value + " nach " + previous);
            previous = value;
        }
    }

    @Test
    @DisplayName("The colour keeps its rgb and only its alpha changes")
    void colourFadesAlphaOnly() {
        int full = Fade.colour(0xFFD24A, 1f, 255);
        assertEquals(0xFFD24A, full & 0xFFFFFF, "Die Farbe selbst darf sich nicht aendern");
        assertEquals(255, (full >>> 24) & 0xFF, "Ganz vorne ist sie voll deckend");

        int half = Fade.colour(0xFFD24A, 0.5f, 200);
        assertEquals(100, (half >>> 24) & 0xFF, 1, "Halbe Zeit, halbe Deckkraft");
    }

    @Test
    @DisplayName("Alpha never reaches zero, because the font renderer skips such text")
    void colourNeverFullyTransparent() {
        // Minecraft treats a nearly invisible colour as "do not draw", so a fade
        // that truly started at 0 would pop into view instead of growing.
        int atStart = Fade.colour(0xFFFFFF, 0f, 255);
        int alpha = (atStart >>> 24) & 0xFF;

        // "Above zero" is not enough, and asking only for that made this test
        // useless: an alpha of 1 passes it and is still invisible, so the fade
        // would begin with a blank pause and then pop. The floor has to be high
        // enough that something is actually drawn.
        assertTrue(alpha >= 8,
                "Am Anfang muss der Text schwach, aber sichtbar sein - Alpha war " + alpha);
        assertTrue(alpha < 40, "Sichtbar ja, aber noch nicht fast fertig - Alpha war " + alpha);
    }

    @Test
    @DisplayName("Alpha cannot overflow into the colour")
    void colourClampsAlpha() {
        int over = Fade.colour(0x000000, 5f, 255);
        assertEquals(255, (over >>> 24) & 0xFF,
                "Ein t ueber 1 darf nicht in die Farbbytes ueberlaufen");
    }
}
