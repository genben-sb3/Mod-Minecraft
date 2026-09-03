package com.boyben.holobuild.client;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.joml.Vector3f;

import com.boyben.holobuild.HoloBuild;
import com.boyben.holobuild.edit.EditorCamera;
import com.boyben.holobuild.network.EditSchematicPayload;
import com.boyben.holobuild.schematic.LoadedSchematic;
import com.boyben.holobuild.schematic.SchematicCache;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Turn a building around and click blocks out of it.
 *
 * The picture and the clicking share one matrix from {@link EditorCamera}, so
 * they cannot drift apart: whatever is drawn is what a click lands on.
 *
 * <p>Nothing is written until Save. Removing a block only adds its position to
 * a list here; the server loads the real file and takes those spots out itself,
 * which is why a client can only ever remove from a building and never invent
 * one.
 */
public class EditorScreen extends Screen {

    /** Beyond this the editor is not offered - every block is drawn every frame. */
    public static final int MAX_EDITABLE_BLOCKS = PreviewRenderer.MAX_PREVIEW_BLOCKS;

    private static final int VIEW_SIZE = 220;
    private static final int VIEW_TOP = 40;

    /** Full brightness, so a building reads the same here as in daylight. */
    private static final int LIGHT = 0x00F000F0;

    private final Screen parent;
    private final String sourceName;
    private final LoadedSchematic schematic;

    private final EditorCamera camera = new EditorCamera();

    /**
     * Which blocks have been clicked away, in the order it happened.
     *
     * A LinkedHashSet so the same block clicked twice counts once, and so the
     * last one can still be taken back - clicking a block away by accident and
     * being unable to undo it would make the whole editor frightening to use.
     */
    private final Set<BlockPos> removed = new LinkedHashSet<>();

    private EditBox nameBox;
    private Button saveButton;

    /** The block the mouse is over, recomputed each frame. */
    private int hovered = -1;

    public EditorScreen(Screen parent, String sourceName, LoadedSchematic schematic) {
        super(Component.translatable("screen.holobuild.editor_title"));
        this.parent = parent;
        this.sourceName = sourceName;
        this.schematic = schematic;
    }

    /**
     * Opens the editor if the building is small enough to draw.
     *
     * @return null with a reason already shown, or the screen
     */
    public static EditorScreen tryOpen(Screen parent, String name) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            return null;
        }
        try {
            LoadedSchematic schematic = SchematicCache.get(name, minecraft.level);
            if (schematic == null) {
                return null;
            }
            if (schematic.blockCount() > MAX_EDITABLE_BLOCKS) {
                minecraft.player.displayClientMessage(
                        Component.translatable("message.holobuild.editor_too_big",
                                MAX_EDITABLE_BLOCKS).withStyle(ChatFormatting.YELLOW), false);
                return null;
            }
            return new EditorScreen(parent, name, schematic);
        } catch (Exception e) {
            HoloBuild.LOGGER.warn("Editor fuer '{}' nicht moeglich: {}", name, e.toString());
            return null;
        }
    }

    @Override
    protected void init() {
        int left = (width - VIEW_SIZE) / 2;
        int bottom = VIEW_TOP + VIEW_SIZE + 8;

        nameBox = new EditBox(font, left, bottom, VIEW_SIZE, 18,
                Component.translatable("screen.holobuild.editor_name"));
        // Start from the old name with a suffix, so Save never overwrites what
        // was opened unless somebody deliberately types the old name back in.
        nameBox.setValue(sourceName + "_edit");
        nameBox.setMaxLength(64);
        addRenderableWidget(nameBox);

        addRenderableWidget(Button.builder(Component.translatable("screen.holobuild.editor_undo"),
                        b -> undoLast())
                .bounds(left, bottom + 22, 70, 20)
                .build());
        addRenderableWidget(Button.builder(Component.translatable("screen.holobuild.editor_reset"),
                        b -> resetView())
                .bounds(left + 74, bottom + 22, 70, 20)
                .build());
        saveButton = Button.builder(Component.translatable("screen.holobuild.editor_save"),
                        b -> save())
                .bounds(left + 148, bottom + 22, 72, 20)
                .build();
        addRenderableWidget(saveButton);

        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(left + VIEW_SIZE / 2 - 50, bottom + 46, 100, 20)
                .build());
    }

    private void undoLast() {
        if (removed.isEmpty()) {
            return;
        }
        BlockPos last = null;
        for (BlockPos pos : removed) {
            last = pos;
        }
        removed.remove(last);
        UiSounds.favourite(false);
    }

    private void resetView() {
        camera.reset();
    }

    private void save() {
        String target = nameBox.getValue().trim();
        if (target.isEmpty() || removed.isEmpty()) {
            return;
        }
        PacketDistributor.sendToServer(new EditSchematicPayload(
                sourceName, target, List.copyOf(removed)));
        onClose();
    }

    /** Where the model is drawn, and how big one block is on screen. */
    private int centreX() {
        return width / 2;
    }

    private int centreY() {
        return VIEW_TOP + VIEW_SIZE / 2;
    }

    private float unitScale() {
        Vec3i size = schematic.size();
        int longest = Math.max(1, Math.max(size.getX(), Math.max(size.getY(), size.getZ())));
        return VIEW_SIZE / (float) longest * 0.55f;
    }

    private org.joml.Matrix4f transform() {
        Vec3i size = schematic.size();
        return camera.transform(centreX(), centreY(), unitScale(),
                size.getX(), size.getY(), size.getZ());
    }

    /** Block centres of everything still there, in the same order as {@link #standing()}. */
    private List<Vector3f> centres(List<StructureTemplate.StructureBlockInfo> standing) {
        List<Vector3f> points = new ArrayList<>(standing.size());
        for (StructureTemplate.StructureBlockInfo info : standing) {
            BlockPos pos = info.pos();
            points.add(new Vector3f(pos.getX() + 0.5f, pos.getY() + 0.5f, pos.getZ() + 0.5f));
        }
        return points;
    }

    /** Everything not clicked away yet, air left out. */
    private List<StructureTemplate.StructureBlockInfo> standing() {
        List<StructureTemplate.StructureBlockInfo> left = new ArrayList<>();
        for (StructureTemplate.StructureBlockInfo info : schematic.blocks()) {
            if (!info.state().isAir() && !removed.contains(info.pos())) {
                left.add(info);
            }
        }
        return left;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        List<StructureTemplate.StructureBlockInfo> standing = standing();
        hovered = EditorCamera.pick(transform(), centres(standing), mouseX, mouseY, hitRadius());

        drawModel(graphics, standing);

        graphics.drawCenteredString(font, title, width / 2, 16, 0xFFFFFF);
        graphics.drawCenteredString(font, Component.translatable("screen.holobuild.editor_count",
                        standing.size(), removed.size()),
                width / 2, VIEW_TOP + VIEW_SIZE - 10, 0xAAAAAA);

        // Name what is under the cursor. Clicking a block away without knowing
        // what it was is how a wall goes missing unnoticed.
        if (hovered >= 0) {
            BlockState state = standing.get(hovered).state();
            graphics.drawCenteredString(font,
                    new ItemStack(state.getBlock()).getHoverName().copy()
                            .withStyle(ChatFormatting.YELLOW),
                    width / 2, VIEW_TOP + 4, 0xFFFF55);
        } else {
            graphics.drawCenteredString(font,
                    Component.translatable("screen.holobuild.editor_hint"),
                    width / 2, VIEW_TOP + 4, 0x808080);
        }
    }

    /** Half a block on screen: close enough to feel aimed, not so far it grabs neighbours. */
    private float hitRadius() {
        return unitScale() * camera.zoom() * 0.5f;
    }

    private void drawModel(GuiGraphics graphics, List<StructureTemplate.StructureBlockInfo> standing) {
        if (minecraft == null || standing.isEmpty()) {
            return;
        }
        int left = (width - VIEW_SIZE) / 2;
        graphics.enableScissor(left, VIEW_TOP, left + VIEW_SIZE, VIEW_TOP + VIEW_SIZE);

        PoseStack pose = graphics.pose();
        pose.pushPose();
        // The very same matrix the picking used a moment ago.
        pose.mulPose(transform());

        BlockRenderDispatcher dispatcher = minecraft.getBlockRenderer();
        MultiBufferSource.BufferSource buffers = graphics.bufferSource();
        Lighting.setupFor3DItems();

        for (int i = 0; i < standing.size(); i++) {
            StructureTemplate.StructureBlockInfo info = standing.get(i);
            BlockPos pos = info.pos();

            pose.pushPose();
            pose.translate(pos.getX(), pos.getY(), pos.getZ());
            // The one under the cursor is drawn a touch larger, which reads as
            // "this one" without needing a colour that some blocks already are.
            if (i == hovered) {
                pose.translate(0.5f, 0.5f, 0.5f);
                pose.scale(1.15f, 1.15f, 1.15f);
                pose.translate(-0.5f, -0.5f, -0.5f);
            }
            dispatcher.renderSingleBlock(info.state(), pose, buffers,
                    LIGHT, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, null);
            pose.popPose();
        }

        graphics.flush();
        pose.popPose();
        Lighting.setupForFlatItems();
        graphics.disableScissor();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (button == 0 && hovered >= 0) {
            List<StructureTemplate.StructureBlockInfo> standing = standing();
            if (hovered < standing.size()) {
                removed.add(standing.get(hovered).pos());
                UiSounds.favourite(true);
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button,
                                double dragX, double dragY) {
        if (button == 1) {
            // Right-drag turns the model. Left is for clicking blocks away, so
            // turning and removing can never be confused with each other.
            camera.turn((float) dragX, (float) -dragY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        camera.zoomBy(scrollY > 0 ? 1.1f : 0.9f);
        return true;
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
