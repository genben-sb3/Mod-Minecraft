package com.boyben.holobuild.client;

import java.util.List;

import com.boyben.holobuild.schematic.LoadedSchematic;
import com.boyben.holobuild.survival.MaterialList;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * The shopping list for building something by hand.
 *
 * Shows every material, how many are needed and how many the player is
 * carrying. Missing ones come first and are marked red, because that is the
 * only part anyone acts on.
 */
public class MaterialScreen extends Screen {

    private static final int PANEL_WIDTH = 260;
    private static final int ROW_HEIGHT = 20;
    private static final int VISIBLE_ROWS = 9;
    private static final int LIST_TOP = 52;
    private static final int BACKDROP = 0x88000000;

    private final String schematicName;
    private final MaterialList materials;
    private final Screen parent;

    private int scroll = 0;

    public MaterialScreen(Screen parent, LoadedSchematic schematic, MaterialList materials) {
        super(Component.translatable("screen.holobuild.materials_title"));
        this.parent = parent;
        this.schematicName = schematic.name();
        this.materials = materials;
    }

    @Override
    protected void init() {
        int left = (width - PANEL_WIDTH) / 2;
        addRenderableWidget(Button.builder(Component.translatable("gui.back"), b -> onClose())
                .bounds(left + PANEL_WIDTH / 2 - 50, LIST_TOP + VISIBLE_ROWS * ROW_HEIGHT + 16, 100, 20)
                .build());
    }

    @Override
    public void onClose() {
        // Back to the picker rather than into the world - the list is something
        // you check on the way to choosing.
        if (minecraft != null) {
            minecraft.setScreen(parent);
        } else {
            super.onClose();
        }
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, BACKDROP);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int max = Math.max(0, materials.entries().size() - VISIBLE_ROWS);
        scroll = Math.max(0, Math.min(max, scroll - (int) Math.signum(scrollY)));
        return true;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        int left = (width - PANEL_WIDTH) / 2;

        graphics.drawCenteredString(font, title, width / 2, 16, 0xFFFFFF);
        graphics.drawCenteredString(font,
                Component.literal(schematicName).withStyle(ChatFormatting.GRAY),
                width / 2, 28, 0xAAAAAA);

        List<MaterialList.Entry> entries = materials.entries();
        if (entries.isEmpty()) {
            graphics.drawCenteredString(font,
                    Component.translatable("screen.holobuild.materials_none"),
                    width / 2, LIST_TOP + 10, 0xAAAAAA);
            return;
        }

        for (int row = 0; row < VISIBLE_ROWS; row++) {
            int index = scroll + row;
            if (index >= entries.size()) {
                break;
            }
            drawRow(graphics, entries.get(index), left, LIST_TOP + row * ROW_HEIGHT);
        }

        drawSummary(graphics, left, LIST_TOP + VISIBLE_ROWS * ROW_HEIGHT + 2);
    }

    private void drawRow(GuiGraphics graphics, MaterialList.Entry entry, int left, int y) {
        ItemStack icon = new ItemStack(entry.item());
        graphics.renderItem(icon, left, y);

        graphics.drawString(font, icon.getHoverName(), left + 22, y + 4, 0xFFFFFF, false);

        // Right-aligned so the numbers line up into a column that can be scanned.
        Component count = entry.enough()
                ? Component.translatable("screen.holobuild.materials_have", entry.needed())
                        .withStyle(ChatFormatting.GREEN)
                : Component.translatable("screen.holobuild.materials_missing",
                                entry.have(), entry.needed(), entry.missing())
                        .withStyle(ChatFormatting.RED);
        graphics.drawString(font, count, left + PANEL_WIDTH - font.width(count), y + 4, 0xFFFFFF, false);
    }

    private void drawSummary(GuiGraphics graphics, int left, int y) {
        Component summary = materials.complete()
                ? Component.translatable("screen.holobuild.materials_complete", materials.totalItems())
                        .withStyle(ChatFormatting.GREEN)
                : Component.translatable("screen.holobuild.materials_summary",
                                materials.totalItems(), materials.missingTotal())
                        .withStyle(ChatFormatting.YELLOW);
        graphics.drawString(font, summary, left, y, 0xFFFFFF, false);

        int total = materials.entries().size();
        if (total > VISIBLE_ROWS) {
            Component hint = Component.translatable("screen.holobuild.scroll_hint",
                    scroll + 1, Math.min(scroll + VISIBLE_ROWS, total));
            graphics.drawString(font, hint, left + PANEL_WIDTH - font.width(hint), y, 0xAAAAAA, false);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
