package com.boyben.holobuild.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for working out which list row the mouse is on.
 *
 * All of this is arithmetic that fails quietly. A row off by one previews the
 * building above the one you are pointing at, and it looks for all the world
 * like the list is simply wrong - nothing crashes, nothing is logged.
 */
class ListLayoutTest {

    /** The real numbers from the screen: 8 rows of 20, 2 of them dead space. */
    private static final ListLayout LAYOUT = new ListLayout(100, 240, 64, 20, 2, 8);

    /** Somewhere comfortably inside the panel horizontally. */
    private static final int INSIDE_X = 150;

    @Test
    @DisplayName("The top row starts exactly at the top of the list, not one below")
    void firstRowStartsAtListTop() {
        assertEquals(0, LAYOUT.rowAt(INSIDE_X, 64), "The very first pixel belongs to row 0");
        assertEquals(ListLayout.NONE, LAYOUT.rowAt(INSIDE_X, 63), "One pixel above is outside");
    }

    @Test
    @DisplayName("Each row covers its 18 pixels, and the 2-pixel gap belongs to nobody")
    void gapBetweenRowsIsDeadSpace() {
        // Row 0 runs from 64 to 81; 82 and 83 are the gap; row 1 starts at 84.
        assertEquals(0, LAYOUT.rowAt(INSIDE_X, 81), "Last pixel of row 0");
        assertEquals(ListLayout.NONE, LAYOUT.rowAt(INSIDE_X, 82), "First gap pixel");
        assertEquals(ListLayout.NONE, LAYOUT.rowAt(INSIDE_X, 83), "Second gap pixel");
        assertEquals(1, LAYOUT.rowAt(INSIDE_X, 84), "Row 1 starts right after the gap");
    }

    @Test
    @DisplayName("The list ends after the eighth row")
    void nothingBelowTheLastRow() {
        assertEquals(7, LAYOUT.rowAt(INSIDE_X, 64 + 7 * 20), "Row 7 is the last one");
        assertEquals(ListLayout.NONE, LAYOUT.rowAt(INSIDE_X, 64 + 8 * 20),
                "Where a ninth row would be, there is nothing");
    }

    @Test
    @DisplayName("Left and right of the panel there is no row")
    void horizontalEdges() {
        assertEquals(0, LAYOUT.rowAt(100, 70), "The left edge itself counts");
        assertEquals(ListLayout.NONE, LAYOUT.rowAt(99, 70), "One pixel left of it does not");
        assertEquals(0, LAYOUT.rowAt(339, 70), "The last pixel of the panel counts");
        assertEquals(ListLayout.NONE, LAYOUT.rowAt(340, 70), "One past the right edge does not");
    }

    @Test
    @DisplayName("Scrolling shifts which entry a row stands for")
    void scrollOffsetsTheIndex() {
        // Same spot on screen, different entry underneath.
        assertEquals(0, LAYOUT.indexAt(INSIDE_X, 70, 0, 40));
        assertEquals(12, LAYOUT.indexAt(INSIDE_X, 70, 12, 40));
        assertEquals(15, LAYOUT.indexAt(INSIDE_X, 64 + 3 * 20, 12, 40),
                "Fourth row on screen with 12 scrolled past is entry 15");
    }

    @Test
    @DisplayName("Rows below the last entry point at nothing")
    void emptyRowsOnTheLastPage() {
        // Three entries, so rows 0-2 are filled and rows 3-7 are blank.
        assertEquals(2, LAYOUT.indexAt(INSIDE_X, 64 + 2 * 20, 0, 3));
        assertEquals(ListLayout.NONE, LAYOUT.indexAt(INSIDE_X, 64 + 3 * 20, 0, 3),
                "The first empty row must not point at entry 3");
        assertEquals(ListLayout.NONE, LAYOUT.indexAt(INSIDE_X, 64 + 7 * 20, 0, 3));
    }

    @Test
    @DisplayName("An empty list has nothing under the mouse anywhere")
    void emptyList() {
        for (int row = 0; row < 8; row++) {
            assertEquals(ListLayout.NONE, LAYOUT.indexAt(INSIDE_X, 64 + row * 20, 0, 0));
        }
    }
}
