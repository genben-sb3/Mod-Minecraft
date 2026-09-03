package com.boyben.holobuild.client;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import com.boyben.holobuild.HoloBuild;
import com.boyben.holobuild.component.BlueprintSettings;
import com.boyben.holobuild.item.BlueprintWandItem;
import com.boyben.holobuild.network.AdjustBlueprintPayload;
import com.boyben.holobuild.network.SelectSchematicPayload;
import com.boyben.holobuild.registry.ModComponents;
import com.boyben.holobuild.schematic.LoadedSchematic;
import com.boyben.holobuild.schematic.SchematicCache;
import com.boyben.holobuild.schematic.SchematicLoader;
import com.boyben.holobuild.survival.MaterialList;

import net.minecraft.ChatFormatting;
import net.minecraft.Util;
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
    /** Dead space at the bottom of each row, so the buttons do not touch. */
    private static final int ROW_GAP = 2;
    private static final int VISIBLE_ROWS = 8;
    private static final int LIST_TOP = 64;

    private static final String STAR_ON = "★";     // filled star
    private static final String STAR_OFF = "☆";    // hollow star

    /** Where the rotate/mirror/height row starts. */
    private static final int CONTROLS_TOP = LIST_TOP + VISIBLE_ROWS * ROW_HEIGHT + 14;

    /** Side length of the turning model panel, sitting right of the list. */
    private static final int PREVIEW_SIZE = 130;
    private static final int PREVIEW_GAP = 8;

    /** List and preview together, so the pair stays centred on screen. */
    private static final int TOTAL_WIDTH = PANEL_WIDTH + PREVIEW_GAP + PREVIEW_SIZE;

    /**
     * Lighter than the usual screen backdrop, so the hologram stays visible
     * behind the menu - turning it and watching it turn is the whole point of
     * having these buttons here rather than only on the keyboard.
     */
    private static final int BACKDROP = 0x66000000;

    /** Surface and edge of the panels the list and the preview sit on. */
    private static final int PANEL_FILL = 0x50101018;
    private static final int PANEL_EDGE = 0x40FFFFFF;

    /** Draws one panel: a dark surface with a thin light edge. */
    private static void panel(GuiGraphics graphics, int x1, int y1, int x2, int y2) {
        graphics.fill(x1, y1, x2, y2, PANEL_FILL);
        graphics.renderOutline(x1, y1, x2 - x1, y2 - y1, PANEL_EDGE);
    }

    /** One entry: the name shown to the player and how big the file is. */
    private record Entry(String name, long sizeBytes, long modified, boolean builtIn) {}

    /** How the list is ordered. Favourites stay on top whichever is chosen. */
    private enum SortMode {
        NAME("screen.holobuild.sort_name"),
        SIZE("screen.holobuild.sort_size"),
        NEWEST("screen.holobuild.sort_newest");

        final String key;

        SortMode(String key) {
            this.key = key;
        }

        SortMode next() {
            return values()[(ordinal() + 1) % values().length];
        }
    }

    /** Kept across visits, so the chosen order sticks for the session. */
    private static SortMode sortMode = SortMode.NAME;

    private final List<Entry> all = new ArrayList<>();
    private List<Entry> filtered = new ArrayList<>();
    private final List<Button> rowButtons = new ArrayList<>();

    private Button sortButton;
    private Button rotateButton;
    private Button mirrorButton;
    private Button heightButton;

    private EditBox searchBox;
    private int scroll = 0;

    /**
     * How long the mouse has to rest on an entry before it is previewed.
     *
     * Long enough that running the mouse down the list costs nothing, short
     * enough that stopping on a name feels like it answered straight away.
     */
    private static final long HOVER_DELAY_MS = 200;

    /** The entry the mouse is currently on, and since when. */
    private String hoverName = "";
    private long hoverSince;

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

        int left = (width - TOTAL_WIDTH) / 2;

        int sortWidth = 76;
        searchBox = new EditBox(font, left, 40, PANEL_WIDTH - sortWidth - 4, 18,
                Component.translatable("screen.holobuild.search"));
        searchBox.setHint(Component.translatable("screen.holobuild.search")
                .withStyle(ChatFormatting.DARK_GRAY));
        searchBox.setResponder(text -> {
            scroll = 0;
            applyFilter(text);
        });
        addRenderableWidget(searchBox);
        setInitialFocus(searchBox);

        sortButton = Button.builder(Component.translatable(sortMode.key), b -> cycleSort())
                .bounds(left + PANEL_WIDTH - sortWidth, 40, sortWidth, 18)
                .build();
        sortButton.setTooltip(Tooltip.create(Component.translatable("screen.holobuild.sort_hint")));
        addRenderableWidget(sortButton);

        addControls(left);

        // Materials, guide and done share the bottom row in equal thirds.
        int third = (PANEL_WIDTH - 2 * 4) / 3;
        int row = CONTROLS_TOP + 46;
        addRenderableWidget(Button.builder(Component.translatable("screen.holobuild.materials_button"),
                        b -> openMaterials())
                .bounds(left, row, third, 20)
                .build());
        Button guide = Button.builder(Component.translatable("screen.holobuild.guide_button"),
                        b -> openGuide())
                .bounds(left + third + 4, row, third, 20)
                .build();
        guide.setTooltip(Tooltip.create(Component.translatable("screen.holobuild.guide_hint")));
        addRenderableWidget(guide);
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(left + 2 * (third + 4), row, third, 20)
                .build());

        applyFilter("");
    }

    /** Opens the manual, and comes back here when it is closed. */
    private void openGuide() {
        if (minecraft != null) {
            minecraft.setScreen(new GuideScreen(this));
        }
    }

    /**
     * Shows what the chosen schematic would cost to build by hand.
     *
     * Reading the inventory happens on the client, where it already is - no
     * need to ask the server what the player is carrying.
     */
    private void openMaterials() {
        if (minecraft == null || minecraft.player == null || minecraft.level == null) {
            return;
        }
        String name = currentSettings().schematic();
        if (name.isEmpty()) {
            return;
        }
        try {
            LoadedSchematic schematic = SchematicLoader.loadByName(name, minecraft.level);
            if (schematic == null) {
                return;
            }
            minecraft.setScreen(new MaterialScreen(this, schematic,
                    MaterialList.of(schematic, minecraft.player)));
        } catch (Exception e) {
            HoloBuild.LOGGER.warn("Materialliste fuer '{}' nicht moeglich: {}", name, e.toString());
        }
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

    /** Steps through the orders and re-sorts straight away. */
    private void cycleSort() {
        sortMode = sortMode.next();
        sortButton.setMessage(Component.translatable(sortMode.key));
        scroll = 0;
        applyFilter(searchBox == null ? "" : searchBox.getValue());
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
            long modified = 0;
            if (file != null) {
                try {
                    size = Files.size(file);
                    modified = Files.getLastModifiedTime(file).toMillis();
                } catch (IOException e) {
                    size = 0;
                }
            }
            all.add(new Entry(name, size, modified, file == null));
        }
    }

    private void applyFilter(String query) {
        String needle = query.trim().toLowerCase(Locale.ROOT);
        filtered = all.stream()
                // The search covers the folder too, so typing "haeuser" brings
                // up everything inside that folder.
                .filter(e -> needle.isEmpty() || e.name().toLowerCase(Locale.ROOT).contains(needle))
                // Starred entries float to the top whatever the chosen order.
                .sorted(Comparator
                        .comparingInt((Entry e) -> Favourites.isFavourite(e.name()) ? 0 : 1)
                        .thenComparing(orderWithin()))
                .toList();
        clampScroll();
        rebuildRows();
    }

    /** How entries are ranked inside the starred and unstarred groups. */
    private Comparator<Entry> orderWithin() {
        return switch (sortMode) {
            case NAME -> Comparator.comparing(e -> e.name().toLowerCase(Locale.ROOT));
            // Biggest and newest first - that is what people are looking for
            // when they pick those orders.
            case SIZE -> Comparator.comparingLong(Entry::sizeBytes).reversed()
                    .thenComparing(e -> e.name().toLowerCase(Locale.ROOT));
            case NEWEST -> Comparator.comparingLong(Entry::modified).reversed()
                    .thenComparing(e -> e.name().toLowerCase(Locale.ROOT));
        };
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

        int left = (width - TOTAL_WIDTH) / 2;

        for (int row = 0; row < VISIBLE_ROWS; row++) {
            int index = scroll + row;
            if (index >= filtered.size()) {
                break;
            }
            Entry entry = filtered.get(index);
            int y = LIST_TOP + row * ROW_HEIGHT;

            Button star = Button.builder(starLabel(entry.name()), b -> toggleStar(entry))
                    .bounds(left, y, STAR_WIDTH, ROW_HEIGHT - ROW_GAP)
                    .build();
            Button name = Button.builder(nameLabel(entry), b -> choose(entry))
                    .bounds(left + STAR_WIDTH, y, NAME_WIDTH, ROW_HEIGHT - ROW_GAP)
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
        // Higher going on, lower coming off - the pitch says which way it went
        // without having to look at the star.
        UiSounds.favourite(Favourites.isFavourite(entry.name()));
        // Re-sort, so the entry moves to or from the top straight away.
        applyFilter(searchBox == null ? "" : searchBox.getValue());
    }

    private Component starLabel(String name) {
        boolean starred = Favourites.isFavourite(name);
        return Component.literal(starred ? STAR_ON : STAR_OFF)
                .withStyle(starred ? ChatFormatting.GOLD : ChatFormatting.DARK_GRAY);
    }

    private Component nameLabel(Entry entry) {
        // Mark the one the wand is actually holding. Choosing closes the menu,
        // so without this there is nothing anywhere that says which building is
        // loaded - reopening the list showed eight identical-looking rows.
        boolean chosen = entry.name().equals(currentSettings().schematic());
        MutableComponent label = Component.literal((chosen ? "▶ " : "") + entry.name())
                .withStyle(chosen ? ChatFormatting.GOLD : ChatFormatting.WHITE);

        // Built-in buildings have no file, so show where they come from instead
        // of a size - otherwise every one of them would read "0 B".
        if (entry.builtIn()) {
            return label.append(Component.literal("  "))
                    .append(Component.translatable("screen.holobuild.built_in")
                            .withStyle(ChatFormatting.DARK_AQUA));
        }
        String size = entry.sizeBytes() < 1024
                ? entry.sizeBytes() + " B"
                : (entry.sizeBytes() / 1024) + " KB";
        return label.append(Component.literal("  (" + size + ")")
                .withStyle(ChatFormatting.GRAY));
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
        // The menu closes straight away, so the sound is the only thing that
        // confirms the click landed on the row you meant.
        UiSounds.chosen();
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

        // Panels behind the widgets rather than in front, so the buttons keep
        // their own look and only gain a surface to sit on. Drawn here because
        // this runs before the widgets do.
        int left = (width - TOTAL_WIDTH) / 2;
        int listBottom = LIST_TOP + VISIBLE_ROWS * ROW_HEIGHT;
        panel(graphics, left - 4, LIST_TOP - 4, left + PANEL_WIDTH + 4, listBottom + 2);

        int previewLeft = left + PANEL_WIDTH + PREVIEW_GAP;
        panel(graphics, previewLeft - 4, LIST_TOP - 4,
                previewLeft + PREVIEW_SIZE + 4, LIST_TOP + PREVIEW_SIZE + 4);
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

    /**
     * Which list entry the mouse is resting on.
     *
     * Worked out from the geometry rather than asked of the row buttons,
     * because a Button only knows it is hovered while it is drawing itself, and
     * the preview is drawn afterwards. The arithmetic itself lives in
     * {@link ListLayout}, where it can be tested.
     *
     * @return the entry under the mouse, or null if the mouse is elsewhere
     */
    private Entry hoveredEntry(int mouseX, int mouseY) {
        ListLayout layout = new ListLayout((width - TOTAL_WIDTH) / 2, PANEL_WIDTH,
                LIST_TOP, ROW_HEIGHT, ROW_GAP, VISIBLE_ROWS);
        int index = layout.indexAt(mouseX, mouseY, scroll, filtered.size());
        return index == ListLayout.NONE ? null : filtered.get(index);
    }

    /**
     * Decides what the preview should show: the entry under the mouse, or the
     * chosen one when the mouse is elsewhere.
     *
     * The short wait matters. Without it, sweeping the mouse down a long list
     * would read every file it passed over, and most of those pictures nobody
     * would even see. Resting on an entry is what says "show me this one".
     *
     * @return the name to preview, empty for nothing
     */
    private String previewName() {
        return hoverSettled() ? hoverName : currentSettings().schematic();
    }

    private boolean hoverSettled() {
        return !hoverName.isEmpty() && Util.getMillis() - hoverSince >= HOVER_DELAY_MS;
    }

    /** Notices when the mouse moves to a different entry and restarts the clock. */
    private void trackHover(int mouseX, int mouseY) {
        Entry under = hoveredEntry(mouseX, mouseY);
        String name = under == null ? "" : under.name();
        if (!name.equals(hoverName)) {
            hoverName = name;
            hoverSince = Util.getMillis();
        }
    }

    /**
     * The turning model of whatever is being previewed.
     *
     * Drawn after the widgets so it sits on top of the panel, and skipped for
     * large builds - every block is drawn every frame, so a cathedral would
     * cost more framerate than the picture is worth.
     */
    private void renderPreview(GuiGraphics graphics, int left) {
        int boxLeft = left + PANEL_WIDTH + PREVIEW_GAP;
        int centreX = boxLeft + PREVIEW_SIZE / 2;
        int centreY = LIST_TOP + PREVIEW_SIZE / 2;

        graphics.fill(boxLeft, LIST_TOP, boxLeft + PREVIEW_SIZE, LIST_TOP + PREVIEW_SIZE, 0x55000000);

        if (minecraft == null || minecraft.level == null) {
            return;
        }
        String name = previewName();
        if (name.isEmpty()) {
            graphics.drawCenteredString(font, Component.translatable("screen.holobuild.preview_none"),
                    centreX, centreY - 4, 0x888888);
            return;
        }

        // Name the building under the picture, and say plainly when it is only
        // being looked at rather than chosen - otherwise the preview quietly
        // disagreeing with the hologram behind it is baffling.
        boolean peeking = hoverSettled() && !name.equals(currentSettings().schematic());
        graphics.drawCenteredString(font, Component.literal(name),
                centreX, LIST_TOP + PREVIEW_SIZE + 4, peeking ? 0xFFD24A : 0xAAAAAA);
        if (peeking) {
            graphics.drawCenteredString(font,
                    Component.translatable("screen.holobuild.preview_peek"),
                    centreX, LIST_TOP + PREVIEW_SIZE + 15, 0x888888);
        }

        LoadedSchematic schematic;
        try {
            schematic = SchematicCache.get(name, minecraft.level);
        } catch (Exception e) {
            return;
        }
        if (schematic == null) {
            return;
        }

        if (PreviewRenderer.tooLarge(schematic)) {
            graphics.drawCenteredString(font, Component.translatable("screen.holobuild.preview_too_big"),
                    centreX, centreY - 4, 0x888888);
            return;
        }
        PreviewRenderer.render(graphics, schematic, centreX, centreY, PREVIEW_SIZE);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        updateControlLabels();
        trackHover(mouseX, mouseY);
        super.render(graphics, mouseX, mouseY, partialTick);

        renderPreview(graphics, (width - TOTAL_WIDTH) / 2);

        graphics.drawCenteredString(font, title, width / 2, 20, 0xFFFFFF);

        int left = (width - TOTAL_WIDTH) / 2;
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
