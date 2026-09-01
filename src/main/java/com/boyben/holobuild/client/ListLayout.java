package com.boyben.holobuild.client;

/**
 * Where the rows of the schematic list sit, and which entry a mouse position
 * lands on.
 *
 * Pulled out of the screen and kept free of Minecraft classes for one reason:
 * this is all off-by-one territory - the gap between two rows, the scroll
 * offset, the empty space below the last entry - and none of it announces a
 * mistake. It just quietly previews the wrong building, which is the sort of
 * bug that survives for months because it looks like the list is fine.
 *
 * A screen cannot be built in a test; this can.
 *
 * @param left        left edge of the list panel
 * @param panelWidth  how wide the clickable rows are
 * @param listTop     y of the first row
 * @param rowHeight   distance from one row to the next, gap included
 * @param rowGap      dead space at the bottom of each row
 * @param visibleRows how many rows fit on screen at once
 */
public record ListLayout(int left, int panelWidth, int listTop,
                         int rowHeight, int rowGap, int visibleRows) {

    /** Nothing is under the mouse. */
    public static final int NONE = -1;

    /**
     * @param scroll     index of the entry drawn in the top row
     * @param entryCount how many entries the filtered list holds
     * @return index into the filtered list, or {@link #NONE}
     */
    public int indexAt(int mouseX, int mouseY, int scroll, int entryCount) {
        int row = rowAt(mouseX, mouseY);
        if (row == NONE) {
            return NONE;
        }
        int index = scroll + row;
        // The last page is rarely full, so the bottom rows can be empty.
        return index < entryCount ? index : NONE;
    }

    /** @return which of the visible rows the mouse is on, or {@link #NONE} */
    public int rowAt(int mouseX, int mouseY) {
        if (mouseX < left || mouseX >= left + panelWidth || mouseY < listTop) {
            return NONE;
        }
        int intoList = mouseY - listTop;
        int row = intoList / rowHeight;
        if (row >= visibleRows) {
            return NONE;
        }
        // The gap at the bottom of a row belongs to neither it nor the next
        // one. Without this the preview would swap twice while the mouse
        // crosses a boundary, which reads as a flicker.
        if (intoList % rowHeight >= rowHeight - rowGap) {
            return NONE;
        }
        return row;
    }
}
