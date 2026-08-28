package com.boyben.holobuild.client;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import com.boyben.holobuild.network.SelectSchematicPayload;
import com.boyben.holobuild.schematic.SchematicLoader;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Pick a schematic from the folder.
 *
 * Built out of plain buttons rather than a scrolling list widget: fewer moving
 * parts, and the mouse wheel still works because the screen just shifts which
 * slice of the list it shows.
 */
public class SchematicScreen extends Screen {

    private static final int STAR_WIDTH = 20;
    private static final int NAME_WIDTH = 220;
    private static final int PANEL_WIDTH = STAR_WIDTH + NAME_WIDTH;
    private static final int ROW_HEIGHT = 20;
    private static final int VISIBLE_ROWS = 8;
    private static final int LIST_TOP = 64;

    private static final String STAR_ON = "★";     // filled star
    private static final String STAR_OFF = "☆";    // hollow star

    /** One entry: the name shown to the player and how big the file is. */
    private record Entry(String name, long sizeBytes) {}

    private final List<Entry> all = new ArrayList<>();
    private List<Entry> filtered = new ArrayList<>();
    private final List<Button> rowButtons = new ArrayList<>();

    private EditBox searchBox;
    private int scroll = 0;

    public SchematicScreen() {
        super(Component.translatable("screen.holobuild.title"));
    }

    @Override
    protected void init() {
        loadEntries();

        int left = (width - PANEL_WIDTH) / 2;

        searchBox = new EditBox(font, left, 40, PANEL_WIDTH, 18,
                Component.translatable("screen.holobuild.search"));
        searchBox.setHint(Component.translatable("screen.holobuild.search")
                .withStyle(ChatFormatting.DARK_GRAY));
        searchBox.setResponder(text -> {
            scroll = 0;
            applyFilter(text);
        });
        addRenderableWidget(searchBox);
        setInitialFocus(searchBox);

        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(left + PANEL_WIDTH / 2 - 50, LIST_TOP + VISIBLE_ROWS * ROW_HEIGHT + 12, 100, 20)
                .build());

        applyFilter("");
    }

    private void loadEntries() {
        all.clear();
        for (Path file : SchematicLoader.listSchematics()) {
            long size;
            try {
                size = Files.size(file);
            } catch (IOException e) {
                size = 0;
            }
            all.add(new Entry(SchematicLoader.displayName(file), size));
        }
    }

    private void applyFilter(String query) {
        String needle = query.trim().toLowerCase(Locale.ROOT);
        filtered = all.stream()
                .filter(e -> needle.isEmpty() || e.name().toLowerCase(Locale.ROOT).contains(needle))
                // Starred entries float to the top, alphabetical within each group.
                .sorted(Comparator
                        .comparing((Entry e) -> Favourites.isFavourite(e.name()) ? 0 : 1)
                        .thenComparing(e -> e.name().toLowerCase(Locale.ROOT)))
                .toList();
        clampScroll();
        rebuildRows();
    }

    private void clampScroll() {
        int maxScroll = Math.max(0, filtered.size() - VISIBLE_ROWS);
        scroll = Math.max(0, Math.min(scroll, maxScroll));
    }

    private void rebuildRows() {
        for (Button button : rowButtons) {
            removeWidget(button);
        }
        rowButtons.clear();

        int left = (width - PANEL_WIDTH) / 2;

        for (int row = 0; row < VISIBLE_ROWS; row++) {
            int index = scroll + row;
            if (index >= filtered.size()) {
                break;
            }
            Entry entry = filtered.get(index);
            int y = LIST_TOP + row * ROW_HEIGHT;

            Button star = Button.builder(starLabel(entry.name()), b -> toggleStar(entry))
                    .bounds(left, y, STAR_WIDTH, ROW_HEIGHT - 2)
                    .build();
            Button name = Button.builder(nameLabel(entry), b -> choose(entry))
                    .bounds(left + STAR_WIDTH, y, NAME_WIDTH, ROW_HEIGHT - 2)
                    .build();

            rowButtons.add(star);
            rowButtons.add(name);
            addRenderableWidget(star);
            addRenderableWidget(name);
        }
    }

    private void toggleStar(Entry entry) {
        Favourites.toggle(entry.name());
        // Re-sort, so the entry moves to or from the top straight away.
        applyFilter(searchBox == null ? "" : searchBox.getValue());
    }

    private Component starLabel(String name) {
        boolean starred = Favourites.isFavourite(name);
        return Component.literal(starred ? STAR_ON : STAR_OFF)
                .withStyle(starred ? ChatFormatting.GOLD : ChatFormatting.DARK_GRAY);
    }

    private Component nameLabel(Entry entry) {
        String size = entry.sizeBytes() < 1024
                ? entry.sizeBytes() + " B"
                : (entry.sizeBytes() / 1024) + " KB";
        return Component.literal(entry.name() + "  §7(" + size + ")");
    }

    private void choose(Entry entry) {
        PacketDistributor.sendToServer(new SelectSchematicPayload(entry.name()));
        onClose();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!filtered.isEmpty()) {
            scroll -= (int) Math.signum(scrollY);
            clampScroll();
            rebuildRows();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        graphics.drawCenteredString(font, title, width / 2, 20, 0xFFFFFF);

        int left = (width - PANEL_WIDTH) / 2;
        int listBottom = LIST_TOP + VISIBLE_ROWS * ROW_HEIGHT;

        if (all.isEmpty()) {
            // Tell people where to put files rather than showing a blank panel.
            graphics.drawCenteredString(font,
                    Component.translatable("screen.holobuild.empty"),
                    width / 2, LIST_TOP + 6, 0xFF5555);
            graphics.drawCenteredString(font,
                    Component.literal(SchematicLoader.getSchematicFolder().toString()),
                    width / 2, LIST_TOP + 20, 0xAAAAAA);
        } else if (filtered.isEmpty()) {
            graphics.drawCenteredString(font,
                    Component.translatable("screen.holobuild.no_match"),
                    width / 2, LIST_TOP + 6, 0xFFAA00);
        } else {
            graphics.drawString(font,
                    Component.translatable("screen.holobuild.count", filtered.size(), all.size()),
                    left, listBottom + 2, 0xAAAAAA, false);

            if (filtered.size() > VISIBLE_ROWS) {
                Component hint = Component.translatable("screen.holobuild.scroll_hint",
                        scroll + 1, Math.min(scroll + VISIBLE_ROWS, filtered.size()));
                graphics.drawString(font, hint,
                        left + PANEL_WIDTH - font.width(hint), listBottom + 2, 0xAAAAAA, false);
            }
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
