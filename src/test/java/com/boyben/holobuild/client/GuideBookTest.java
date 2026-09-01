package com.boyben.holobuild.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for the guide's table of contents.
 *
 * That every line has a translation is already covered by
 * {@link com.boyben.holobuild.LanguageFilesTest}, which searches the source for
 * keys. What is left is the shape of the book itself: an empty page, a heading
 * used twice, or a page number that walks off the end would all show up in the
 * game as a blank screen rather than as an error.
 */
class GuideBookTest {

    @Test
    @DisplayName("There are pages, and none of them is empty")
    void everyPageHasContent() {
        assertFalse(GuideBook.PAGES.isEmpty(), "Eine Anleitung ohne Seiten hilft niemandem");

        for (GuideBook.Page page : GuideBook.PAGES) {
            assertFalse(page.titleKey().isBlank(), "Seite ohne Ueberschrift");
            assertFalse(page.lineKeys().isEmpty(),
                    "Seite '" + page.titleKey() + "' hat keine einzige Zeile");
        }
    }

    @Test
    @DisplayName("No text key is used twice")
    void noKeyAppearsTwice() {
        Set<String> seen = new HashSet<>();
        for (GuideBook.Page page : GuideBook.PAGES) {
            assertTrue(seen.add(page.titleKey()),
                    "Ueberschrift doppelt benutzt: " + page.titleKey());
            for (String line : page.lineKeys()) {
                assertTrue(seen.add(line), "Zeile doppelt benutzt: " + line);
            }
        }
    }

    @Test
    @DisplayName("Page numbers stay inside the book, however far you page")
    void clampKeepsThePageInRange() {
        int last = GuideBook.pageCount() - 1;

        assertEquals(0, GuideBook.clamp(0));
        assertEquals(last, GuideBook.clamp(last));

        // Turning past either end must stop, not wrap and not run off.
        assertEquals(0, GuideBook.clamp(-1), "Zurueck von der ersten Seite bleibt auf der ersten");
        assertEquals(last, GuideBook.clamp(last + 1), "Weiter von der letzten bleibt auf der letzten");
        assertEquals(0, GuideBook.clamp(Integer.MIN_VALUE));
        assertEquals(last, GuideBook.clamp(Integer.MAX_VALUE));
    }

    @Test
    @DisplayName("Every page keeps to the height the screen can draw")
    void pagesFitOnScreen() {
        // The screen starts the text at y=66 and gives each line 12 pixels; the
        // buttons sit at the bottom. Ten lines is what fits without running into
        // them on the smallest window Minecraft allows.
        for (GuideBook.Page page : GuideBook.PAGES) {
            assertTrue(page.lineKeys().size() <= 10,
                    "Seite '" + page.titleKey() + "' hat " + page.lineKeys().size()
                            + " Zeilen - mehr als 10 laufen unten in die Knoepfe");
        }
    }
}
