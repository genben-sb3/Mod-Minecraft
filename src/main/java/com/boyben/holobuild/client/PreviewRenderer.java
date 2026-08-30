package com.boyben.holobuild.client;

import java.util.List;

import com.boyben.holobuild.schematic.LoadedSchematic;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.neoforged.neoforge.client.model.data.ModelData;


/**
 * Draws a small turning model of a schematic inside a menu.
 *
 * Block models are made for the world, not for menus, so three things have to
 * be arranged by hand: the vertical axis points the other way in a GUI, the
 * lighting has to be set up for solid shapes, and the whole thing has to be
 * clipped so a tall building does not spill over the panel.
 */
public final class PreviewRenderer {
    private PreviewRenderer() {}

    /**
     * Above this the preview is skipped. Every block is drawn every frame, so a
     * large build would cost more framerate than the picture is worth.
     */
    public static final int MAX_PREVIEW_BLOCKS = 4_000;

    /** Tilt, so the model is seen slightly from above rather than head-on. */
    private static final float TILT_DEGREES = 22.0f;

    /** A full turn takes this long, slow enough to look at. */
    private static final float SECONDS_PER_TURN = 12.0f;

    public static boolean tooLarge(LoadedSchematic schematic) {
        return schematic.blockCount() > MAX_PREVIEW_BLOCKS;
    }

    /**
     * @param centreX centre of the area to draw in
     * @param centreY centre of the area to draw in
     * @param boxSize width and height available
     */
    public static void render(GuiGraphics graphics, LoadedSchematic schematic,
                              int centreX, int centreY, int boxSize) {

        List<StructureTemplate.StructureBlockInfo> blocks = schematic.blocks();
        if (blocks.isEmpty()) {
            return;
        }

        Vec3i size = schematic.size();
        int longest = Math.max(size.getX(), Math.max(size.getY(), size.getZ()));
        if (longest <= 0) {
            return;
        }

        // Leave a margin, and account for the model turning: a long building
        // is wider seen from the corner than from the front.
        float scale = boxSize / (float) longest * 0.55f;
        float spin = turnAngle();

        graphics.enableScissor(centreX - boxSize / 2, centreY - boxSize / 2,
                centreX + boxSize / 2, centreY + boxSize / 2);

        PoseStack pose = graphics.pose();
        pose.pushPose();

        // Move to the middle of the panel, then far enough forward that the
        // model sits in front of the background but behind any tooltip.
        pose.translate(centreX, centreY, 200.0f);

        // Y is upside down in a GUI compared to the world, so the vertical
        // scale is negated rather than the model being flipped later.
        pose.scale(scale, -scale, scale);

        pose.mulPose(Axis.XP.rotationDegrees(TILT_DEGREES));
        pose.mulPose(Axis.YP.rotationDegrees(spin));

        // Turn around the middle of the building, not around its corner.
        pose.translate(-size.getX() / 2.0f, -size.getY() / 2.0f, -size.getZ() / 2.0f);

        Minecraft mc = Minecraft.getInstance();
        BlockRenderDispatcher dispatcher = mc.getBlockRenderer();
        MultiBufferSource.BufferSource buffers = graphics.bufferSource();

        Lighting.setupFor3DItems();

        for (StructureTemplate.StructureBlockInfo info : blocks) {
            BlockState state = info.state();
            if (state.isAir()) {
                continue;
            }
            BlockPos pos = info.pos();

            pose.pushPose();
            pose.translate(pos.getX(), pos.getY(), pos.getZ());
            dispatcher.renderSingleBlock(state, pose, buffers,
                    LIGHT, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, null);
            pose.popPose();
        }

        graphics.flush();
        pose.popPose();

        // Back to flat lighting, or every widget drawn afterwards comes out
        // shaded like a block.
        Lighting.setupForFlatItems();
        graphics.disableScissor();
    }

    /** Full brightness: a preview should read the same in a cave and at noon. */
    private static final int LIGHT = 0x00F000F0;

    /** Where in the turn we are, from the wall clock so it keeps going smoothly. */
    private static float turnAngle() {
        float seconds = (System.currentTimeMillis() % (long) (SECONDS_PER_TURN * 1000)) / 1000.0f;
        return seconds / SECONDS_PER_TURN * 360.0f;
    }
}
