package com.boyben.holobuild.client;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
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

    private static final int PANEL_WIDTH = 240;
    private static final int ROW_HEIGHT = 20;
    private static final int VISIBLE_ROWS = 8;

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
        int top = 40;

        searchBox = new EditBox(font, left, top, PANEL_WIDTH, 18,
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
                .bounds(left + PANEL_WIDTH / 2 - 50, top + 24 + VISIBLE_ROWS * ROW_HEIGHT + 8, 100, 20)
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
        int top = 40 + 24;

        for (int row = 0; row < VISIBLE_ROWS; row++) {
            int index = scroll + row;
            if (index >= filtered.size()) {
                break;
            }
            Entry entry = filtered.get(index);
            Button button = Button.builder(label(entry), b -> choose(entry))
                    .bounds(left, top + row * ROW_HEIGHT, PANEL_WIDTH, ROW_HEIGHT - 2)
                    .build();
            rowButtons.add(button);
            addRenderableWidget(button);
        }
    }

    private Component label(Entry entry) {
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
        int listBottom = 40 + 24 + VISIBLE_ROWS * ROW_HEIGHT;

        if (all.isEmpty()) {
            // Tell people where to put files rather than showing a blank panel.
            graphics.drawCenteredString(font,
                    Component.translatable("screen.holobuild.empty"),
                    width / 2, 40 + 30, 0xFF5555);
            graphics.drawCenteredString(font,
                    Component.literal(SchematicLoader.getSchematicFolder().toString()),
                    width / 2, 40 + 44, 0xAAAAAA);
        } else if (filtered.isEmpty()) {
            graphics.drawCenteredString(font,
                    Component.translatable("screen.holobuild.no_match"),
                    width / 2, 40 + 30, 0xFFAA00);
        } else {
            graphics.drawString(font,
                    Component.translatable("screen.holobuild.count",
                            filtered.size(), all.size()),
                    left, listBottom - 4, 0xAAAAAA, false);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
