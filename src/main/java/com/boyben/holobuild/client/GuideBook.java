package com.boyben.holobuild.client;

import java.util.List;

/**
 * What the in-game guide says, page by page.
 *
 * Kept apart from the screen and free of Minecraft classes so the book can be
 * checked without a game: that every page has a heading and some lines, and
 * that no key appears twice.
 *
 * <p>Every key is written out in full rather than built from a number and a
 * loop. That is on purpose: {@code LanguageFilesTest} finds translation keys by
 * searching the source for them, so a key assembled as {@code "page" + i} would
 * slip past it and only turn up in the game as raw text in the middle of the
 * screen.
 */
public final class GuideBook {
    private GuideBook() {}

    /** One page: a heading and the lines under it. */
    public record Page(String titleKey, List<String> lineKeys) {}

    public static final List<Page> PAGES = List.of(
            new Page("guide.holobuild.start.title", List.of(
                    "guide.holobuild.start.1",
                    "guide.holobuild.start.2",
                    "guide.holobuild.start.3",
                    "guide.holobuild.start.4",
                    "guide.holobuild.start.5")),

            new Page("guide.holobuild.craft.title", List.of(
                    "guide.holobuild.craft.1",
                    "guide.holobuild.craft.2",
                    "guide.holobuild.craft.3",
                    "guide.holobuild.craft.4",
                    "guide.holobuild.craft.5",
                    "guide.holobuild.craft.6",
                    "guide.holobuild.craft.7")),

            new Page("guide.holobuild.wand.title", List.of(
                    "guide.holobuild.wand.1",
                    "guide.holobuild.wand.2",
                    "guide.holobuild.wand.3",
                    "guide.holobuild.wand.4",
                    "guide.holobuild.wand.5",
                    "guide.holobuild.wand.6",
                    "guide.holobuild.wand.7")),

            new Page("guide.holobuild.selection.title", List.of(
                    "guide.holobuild.selection.1",
                    "guide.holobuild.selection.2",
                    "guide.holobuild.selection.3",
                    "guide.holobuild.selection.4",
                    "guide.holobuild.selection.5")),

            new Page("guide.holobuild.site.title", List.of(
                    "guide.holobuild.site.1",
                    "guide.holobuild.site.2",
                    "guide.holobuild.site.3",
                    "guide.holobuild.site.4",
                    "guide.holobuild.site.5",
                    "guide.holobuild.site.6",
                    "guide.holobuild.site.7")),

            new Page("guide.holobuild.saving.title", List.of(
                    "guide.holobuild.saving.1",
                    "guide.holobuild.saving.2",
                    "guide.holobuild.saving.3",
                    "guide.holobuild.saving.4",
                    "guide.holobuild.saving.5")),

            new Page("guide.holobuild.commands.title", List.of(
                    "guide.holobuild.commands.1",
                    "guide.holobuild.commands.2",
                    "guide.holobuild.commands.3",
                    "guide.holobuild.commands.4",
                    "guide.holobuild.commands.5",
                    "guide.holobuild.commands.6")),

            new Page("guide.holobuild.edit.title", List.of(
                    "guide.holobuild.edit.1",
                    "guide.holobuild.edit.2",
                    "guide.holobuild.edit.3",
                    "guide.holobuild.edit.4",
                    "guide.holobuild.edit.5",
                    "guide.holobuild.edit.6")),

            new Page("guide.holobuild.survival.title", List.of(
                    "guide.holobuild.survival.1",
                    "guide.holobuild.survival.2",
                    "guide.holobuild.survival.3",
                    "guide.holobuild.survival.4",
                    "guide.holobuild.survival.5",
                    "guide.holobuild.survival.6")),

            new Page("guide.holobuild.files.title", List.of(
                    "guide.holobuild.files.1",
                    "guide.holobuild.files.2",
                    "guide.holobuild.files.3",
                    "guide.holobuild.files.4")));

    public static int pageCount() {
        return PAGES.size();
    }

    /** Keeps a page number inside the book, however it was arrived at. */
    public static int clamp(int page) {
        return Math.max(0, Math.min(page, PAGES.size() - 1));
    }
}
