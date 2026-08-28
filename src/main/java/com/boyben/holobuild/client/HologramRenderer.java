package com.boyben.holobuild.client;

import java.util.List;

import com.boyben.holobuild.HoloBuild;
import com.boyben.holobuild.component.BlueprintSettings;
import com.boyben.holobuild.component.Selection;
import com.boyben.holobuild.item.BlueprintWandItem;
import com.boyben.holobuild.item.SelectionWandItem;
import com.boyben.holobuild.registry.ModComponents;
import com.boyben.holobuild.schematic.LoadedSchematic;
import com.boyben.holobuild.schematic.Placement;
import com.boyben.holobuild.schematic.SchematicCache;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.model.data.ModelData;

/**
 * Draws the selected schematic as a see-through ghost before anything is placed.
 *
 * Client only. Position and orientation come from {@link Placement}, the exact
 * same code the server uses to place blocks, so what you see is what you get.
 */
@EventBusSubscriber(modid = HoloBuild.MODID, value = Dist.CLIENT)
public final class HologramRenderer {
    private HologramRenderer() {}

    /** Above this many blocks only the outline is drawn, to keep the framerate sane. */
    private static final int MAX_GHOST_BLOCKS = 30_000;

    /** Out of 255 - how solid the ghost blocks look. */
    private static final int GHOST_ALPHA = 115;

    private static final float OUTLINE_R = 0.30f;
    private static final float OUTLINE_G = 0.85f;
    private static final float OUTLINE_B = 1.00f;
    private static final float OUTLINE_A = 0.85f;

    /** Shown when the build would not fit between world floor and ceiling. */
    private static final float BLOCKED_R = 1.00f;
    private static final float BLOCKED_G = 0.25f;
    private static final float BLOCKED_B = 0.25f;

    /** Selection box is amber, so it never gets confused with the blueprint outline. */
    private static final float SELECT_R = 1.00f;
    private static final float SELECT_G = 0.78f;
    private static final float SELECT_B = 0.20f;

    /** Logged once instead of every frame - a broken hologram must not spam the log. */
    private static boolean warned = false;

    // Transforming every block each frame would be wasteful, so the result is
    // kept until something about the placement actually changes.
    private record CacheKey(String schematic, int rotation, boolean mirrored, BlockPos origin) {}

    private static CacheKey cachedKey;
    private static List<StructureTemplate.StructureBlockInfo> cachedBlocks;

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        try {
            render(event);
        } catch (Exception e) {
            // Never let a rendering slip take the whole game down.
            if (!warned) {
                warned = true;
                HoloBuild.LOGGER.error("Hologramm konnte nicht gezeichnet werden", e);
            }
        }
    }

    private static void render(RenderLevelStageEvent event) throws Exception {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || !player.isCreative()) {
            return;
        }

        renderSelection(event, player);

        ItemStack stack = wandInHand(player);
        if (stack == null) {
            return;
        }
        BlueprintSettings settings = stack.getOrDefault(ModComponents.BLUEPRINT.get(), BlueprintSettings.EMPTY);
        if (!settings.hasSchematic()) {
            return;
        }
        if (!(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) {
            return;
        }

        LoadedSchematic schematic = SchematicCache.get(settings.schematic(), mc.level);
        if (schematic == null) {
            return;
        }

        BlockPos origin = Placement.origin(hit.getBlockPos(), hit.getDirection(), settings);
        StructurePlaceSettings placeSettings = Placement.settings(schematic.size(), settings);
        BoundingBox box = Placement.box(schematic, placeSettings, origin);

        // Red outline when the build would poke through the world ceiling or
        // floor - those blocks would silently go missing, so warn before the click.
        boolean fits = Placement.fitsInWorld(box, mc.level);
        if (fits) {
            drawOutline(event, box);
        } else {
            drawOutline(event, box, BLOCKED_R, BLOCKED_G, BLOCKED_B, OUTLINE_A);
        }

        if (schematic.blockCount() <= MAX_GHOST_BLOCKS) {
            drawGhostBlocks(event, blocksFor(schematic, placeSettings, origin, settings));
        }
    }

    /** Reuses the transformed block list until the placement actually changes. */
    private static List<StructureTemplate.StructureBlockInfo> blocksFor(
            LoadedSchematic schematic, StructurePlaceSettings placeSettings,
            BlockPos origin, BlueprintSettings settings) {

        CacheKey key = new CacheKey(settings.schematic(), settings.rotation(), settings.mirrored(), origin);
        if (!key.equals(cachedKey)) {
            cachedKey = key;
            cachedBlocks = Placement.transformed(schematic, placeSettings, origin);
        }
        return cachedBlocks;
    }

    private static void drawGhostBlocks(RenderLevelStageEvent event,
                                        List<StructureTemplate.StructureBlockInfo> blocks) {
        Minecraft mc = Minecraft.getInstance();
        PoseStack pose = event.getPoseStack();
        Vec3 camera = event.getCamera().getPosition();

        BlockRenderDispatcher dispatcher = mc.getBlockRenderer();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer ghost = new GhostVertexConsumer(buffers.getBuffer(RenderType.translucent()), GHOST_ALPHA);

        for (StructureTemplate.StructureBlockInfo info : blocks) {
            BlockState state = info.state();
            if (state.isAir()) {
                continue;
            }
            BlockPos pos = info.pos();

            pose.pushPose();
            pose.translate(pos.getX() - camera.x, pos.getY() - camera.y, pos.getZ() - camera.z);

            BakedModel model = dispatcher.getBlockModel(state);
            // Full brightness so the preview reads clearly even in a dark cave.
            dispatcher.getModelRenderer().renderModel(
                    pose.last(), ghost, state, model,
                    1.0f, 1.0f, 1.0f,
                    LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
                    ModelData.EMPTY, null);

            pose.popPose();
        }

        buffers.endBatch(RenderType.translucent());
    }

    /**
     * Shows what the Selection Wand has marked so far: the full area once both
     * corners are set, or just the single corner while one is still missing.
     */
    private static void renderSelection(RenderLevelStageEvent event, LocalPlayer player) {
        ItemStack stack = itemInHand(player, SelectionWandItem.class);
        if (stack == null) {
            return;
        }
        Selection selection = stack.getOrDefault(ModComponents.SELECTION.get(), Selection.EMPTY);

        if (selection.isComplete()) {
            drawOutline(event, selection.box(), SELECT_R, SELECT_G, SELECT_B, OUTLINE_A);
        } else {
            selection.first().or(selection::second).ifPresent(corner ->
                    drawOutline(event, new BoundingBox(corner), SELECT_R, SELECT_G, SELECT_B, OUTLINE_A));
        }
    }

    private static ItemStack wandInHand(LocalPlayer player) {
        return itemInHand(player, BlueprintWandItem.class);
    }

    /** @return the held stack of that item type, main hand first, or null */
    private static ItemStack itemInHand(LocalPlayer player, Class<?> type) {
        ItemStack main = player.getMainHandItem();
        if (type.isInstance(main.getItem())) {
            return main;
        }
        ItemStack off = player.getOffhandItem();
        return type.isInstance(off.getItem()) ? off : null;
    }

    private static void drawOutline(RenderLevelStageEvent event, BoundingBox box) {
        drawOutline(event, box, OUTLINE_R, OUTLINE_G, OUTLINE_B, OUTLINE_A);
    }

    private static void drawOutline(RenderLevelStageEvent event, BoundingBox box,
                                    float red, float green, float blue, float alpha) {
        Minecraft mc = Minecraft.getInstance();
        PoseStack pose = event.getPoseStack();
        Vec3 camera = event.getCamera().getPosition();

        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());

        pose.pushPose();
        // The level renders relative to the camera, so shift the whole box back
        // by the camera position to land on real world coordinates.
        pose.translate(-camera.x, -camera.y, -camera.z);

        // BoundingBox max values are inclusive block positions; the box we want
        // to draw ends one block further along on each axis.
        AABB area = new AABB(
                box.minX(), box.minY(), box.minZ(),
                box.maxX() + 1.0, box.maxY() + 1.0, box.maxZ() + 1.0);
        LevelRenderer.renderLineBox(pose, lines, area, red, green, blue, alpha);

        pose.popPose();
        buffers.endBatch(RenderType.lines());
    }
}
