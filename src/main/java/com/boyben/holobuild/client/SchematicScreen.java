package com.boyben.holobuild.client;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import com.boyben.holobuild.component.BlueprintSettings;
import com.boyben.holobuild.item.BlueprintWandItem;
import com.boyben.holobuild.network.AdjustBlueprintPayload;
import com.boyben.holobuild.network.SelectSchematicPayload;
import com.boyben.holobuild.registry.ModComponents;
import com.boyben.holobuild.schematic.SchematicLoader;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
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

    /** Where the rotate/mirror/height row starts. */
    private static final int CONTROLS_TOP = LIST_TOP + VISIBLE_ROWS * ROW_HEIGHT + 14;

    /**
     * Lighter than the usual screen backdrop, so the hologram stays visible
     * behind the menu - turning it and watching it turn is the whole point of
     * having these buttons here rather than only on the keyboard.
     */
    private static final int BACKDROP = 0x66000000;

    /** One entry: the name shown to the player and how big the file is. */
    private record Entry(String name, long sizeBytes, boolean builtIn) {}

    private final List<Entry> all = new ArrayList<>();
    private List<Entry> filtered = new ArrayList<>();
    private final List<Button> rowButtons = new ArrayList<>();

    private Button rotateButton;
    private Button mirrorButton;
    private Button heightButton;

    private EditBox searchBox;
    private int scroll = 0;

    public SchematicScreen() {
        super(Component.translatable("screen.holobuild.title"));
    }

    @Override
    protected void init() {
        // Files may have changed since the picker was last open - a saved build,
        // a freshly rolled tower - so start over. Within one visit the cache
        // still means each entry is only read once, however much you scroll.
        SchematicInfo.clear();
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

        addControls(left);

        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(left + PANEL_WIDTH / 2 - 50, CONTROLS_TOP + 46, 100, 20)
                .build());

        applyFilter("");
    }

    /**
     * Rotate, mirror and height, right where the schematic is chosen.
     *
     * Every press goes to the server, because the wand lives there - the
     * changed setting comes back on its own and the labels pick it up in
     * {@link #render}, so they always show what is really set.
     */
    private void addControls(int left) {
        int half = PANEL_WIDTH / 2;

        rotateButton = Button.builder(Component.empty(),
                        b -> adjust(new AdjustBlueprintPayload(1, false, 0)))
                .bounds(left, CONTROLS_TOP, half - 2, 20)
                .build();
        mirrorButton = Button.builder(Component.empty(),
                        b -> adjust(new AdjustBlueprintPayload(0, true, 0)))
                .bounds(left + half + 2, CONTROLS_TOP, half - 2, 20)
                .build();

        Button down = Button.builder(Component.literal("▼"),
                        b -> adjust(new AdjustBlueprintPayload(0, false, -1)))
                .bounds(left, CONTROLS_TOP + 22, 40, 20)
                .build();
        heightButton = Button.builder(Component.empty(), b -> resetHeight())
                .bounds(left + 42, CONTROLS_TOP + 22, PANEL_WIDTH - 84, 20)
                .build();
        heightButton.setTooltip(Tooltip.create(
                Component.translatable("screen.holobuild.height_reset")));
        Button up = Button.builder(Component.literal("▲"),
                        b -> adjust(new AdjustBlueprintPayload(0, false, 1)))
                .bounds(left + PANEL_WIDTH - 40, CONTROLS_TOP + 22, 40, 20)
                .build();

        addRenderableWidget(rotateButton);
        addRenderableWidget(mirrorButton);
        addRenderableWidget(down);
        addRenderableWidget(heightButton);
        addRenderableWidget(up);
    }

    private void adjust(AdjustBlueprintPayload payload) {
        PacketDistributor.sendToServer(payload);
    }

    /** Back to the height the schematic was saved at. */
    private void resetHeight() {
        int offset = currentSettings().yOffset();
        if (offset != 0) {
            adjust(new AdjustBlueprintPayload(0, false, -offset));
        }
    }

    /** The settings on the wand in hand, or empty if none is held. */
    private BlueprintSettings currentSettings() {
        if (minecraft == null || minecraft.player == null) {
            return BlueprintSettings.EMPTY;
        }
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = minecraft.player.getItemInHand(hand);
            if (stack.getItem() instanceof BlueprintWandItem) {
                return stack.getOrDefault(ModComponents.BLUEPRINT.get(), BlueprintSettings.EMPTY);
            }
        }
        return BlueprintSettings.EMPTY;
    }

    private void loadEntries() {
        all.clear();
        for (String name : SchematicLoader.allNames()) {
            Path file = SchematicLoader.findByName(name);
            long size = 0;
            if (file != null) {
                try {
                    size = Files.size(file);
                } catch (IOException e) {
                    size = 0;
                }
            }
            all.add(new Entry(name, size, file == null));
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
            // Read on demand: only the eight rows on screen get looked up, and
            // each one only once for the whole session.
            name.setTooltip(tooltipFor(entry));

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
        // Built-in buildings have no file, so show where they come from instead
        // of a size - otherwise every one of them would read "0 B".
        if (entry.builtIn()) {
            return Component.literal(entry.name() + "  ")
                    .append(Component.translatable("screen.holobuild.built_in")
                            .withStyle(ChatFormatting.DARK_AQUA));
        }
        String size = entry.sizeBytes() < 1024
                ? entry.sizeBytes() + " B"
                : (entry.sizeBytes() / 1024) + " KB";
        return Component.literal(entry.name() + "  §7(" + size + ")");
    }

    /**
     * What the entry contains: size, block count and the main material.
     *
     * Reading the file is what makes this possible, so it happens only for rows
     * actually on screen, and the result is remembered for the session.
     */
    private Tooltip tooltipFor(Entry entry) {
        SchematicInfo info = minecraft == null || minecraft.level == null
                ? null
                : SchematicInfo.of(entry.name(), minecraft.level);

        if (info == null) {
            return Tooltip.create(Component.translatable("screen.holobuild.info_unreadable")
                    .withStyle(ChatFormatting.RED));
        }

        MutableComponent text = Component.literal(entry.name())
                .withStyle(ChatFormatting.WHITE)
                .append(Component.literal("\n"))
                .append(Component.translatable("screen.holobuild.info_size", info.dimensions())
                        .withStyle(ChatFormatting.GRAY))
                .append(Component.literal("\n"))
                .append(Component.translatable("screen.holobuild.info_blocks", info.blocks(), info.distinct())
                        .withStyle(ChatFormatting.GRAY));

        if (!info.topBlock().isEmpty()) {
            text.append(Component.literal("\n"))
                    .append(Component.translatable("screen.holobuild.info_main",
                            info.topBlock(), info.topCount()).withStyle(ChatFormatting.DARK_AQUA));
        }
        if (entry.builtIn()) {
            text.append(Component.literal("\n"))
                    .append(Component.translatable("screen.holobuild.built_in")
                            .withStyle(ChatFormatting.DARK_GRAY));
        }
        return Tooltip.create(text);
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

    /**
     * A thin veil instead of the usual heavy backdrop, so the hologram behind
     * the menu stays readable while its buttons are being pressed.
     */
    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, BACKDROP);
    }

    /**
     * Labels follow whatever is really on the wand.
     *
     * A press travels to the server and the answer comes back a tick later, so
     * reading the values every frame is what keeps the buttons honest rather
     * than showing what we hope happened.
     */
    private void updateControlLabels() {
        BlueprintSettings settings = currentSettings();

        rotateButton.setMessage(Component.translatable(
                "screen.holobuild.rotate_button", settings.rotation() * 90));
        mirrorButton.setMessage(Component.translatable(settings.mirrored()
                ? "screen.holobuild.mirror_button_on"
                : "screen.holobuild.mirror_button_off"));

        int offset = settings.yOffset();
        heightButton.setMessage(Component.translatable("screen.holobuild.height_button",
                offset > 0 ? "+" + offset : String.valueOf(offset)));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        updateControlLabels();
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
