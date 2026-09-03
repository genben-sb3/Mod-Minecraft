package com.boyben.holobuild.item;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.boyben.holobuild.HoloBuild;
import com.boyben.holobuild.build.BuildAnimator;
import com.boyben.holobuild.component.BlueprintSettings;
import com.boyben.holobuild.registry.ModComponents;
import com.boyben.holobuild.schematic.LoadedSchematic;
import com.boyben.holobuild.schematic.Placement;
import com.boyben.holobuild.schematic.SchematicCache;
import com.boyben.holobuild.undo.UndoManager;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * Clears the space a building is about to go into.
 *
 * This exists because of a change made deliberately elsewhere: saved buildings
 * no longer carry the air between their blocks, so they settle into the ground
 * instead of punching a box out of it. That is what you want on flat ground and
 * exactly what you do not want on a hillside, where the hill then stands inside
 * the living room. Rather than take the air back, the clearing became its own
 * tool - so it is a decision the player makes, not one the file makes for them.
 *
 * <p>Which building to clear for is read from the Blueprint Wand the player is
 * carrying. Choosing stays in one place that way; this wand only ever asks.
 */
public class SiteWandItem extends Item {

    public SiteWandItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                List<Component> lines, TooltipFlag flag) {
        WandTooltip.control(lines, "tooltip.holobuild.rmb", "tooltip.holobuild.site.clear");
        WandTooltip.blank(lines);
        WandTooltip.state(lines, Component.translatable("tooltip.holobuild.site.needs_wand"),
                ChatFormatting.GRAY);
        WandTooltip.state(lines, Component.translatable("tooltip.holobuild.site.undo"),
                ChatFormatting.DARK_GRAY);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }

        // Creative only, on purpose. Emptying a hillside costs nothing here;
        // in survival that would be a free super-pickaxe, which is a much
        // bigger change to the game than a building tool should make.
        if (!player.isCreative()) {
            if (!level.isClientSide) {
                tell(player, Component.translatable("message.holobuild.site_creative_only"),
                        ChatFormatting.RED);
            }
            return InteractionResult.FAIL;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!(player instanceof ServerPlayer serverPlayer) || !(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.PASS;
        }

        BlueprintSettings settings = blueprintOf(player);
        if (!settings.hasSchematic()) {
            tell(player, Component.translatable("message.holobuild.site_no_blueprint"),
                    ChatFormatting.YELLOW);
            return InteractionResult.FAIL;
        }

        LoadedSchematic schematic;
        try {
            schematic = SchematicCache.get(settings.schematic(), serverLevel);
        } catch (IOException | RuntimeException e) {
            HoloBuild.LOGGER.error("Schematic konnte nicht geladen werden: {}", settings.schematic(), e);
            tell(player, Component.translatable("message.holobuild.load_failed", settings.schematic()),
                    ChatFormatting.RED);
            return InteractionResult.FAIL;
        }
        if (schematic == null) {
            tell(player, Component.translatable("message.holobuild.missing_file", settings.schematic()),
                    ChatFormatting.RED);
            return InteractionResult.FAIL;
        }

        BlockPos origin = Placement.origin(context.getClickedPos(), context.getClickedFace(), settings);
        StructurePlaceSettings placeSettings = Placement.settings(schematic.size(), settings);
        BoundingBox box = Placement.box(schematic, placeSettings, origin);

        // Same check the builder makes: outside the build height setBlock does
        // nothing at all, so the clearing would come out half done in silence.
        if (!Placement.fitsInWorld(box, serverLevel)) {
            tell(player, Component.translatable("message.holobuild.site_outside_world"),
                    ChatFormatting.RED);
            return InteractionResult.FAIL;
        }

        return clearSpace(serverPlayer, serverLevel, box, settings.schematic());
    }

    /**
     * Empties the box, through the same animator the building uses.
     *
     * Reusing it is not laziness: it already spreads the work over ticks, skips
     * chunks that are not loaded and counts what it could not do. A second,
     * simpler loop here would have to learn all of that again, and would learn
     * it by getting it wrong first.
     */
    public static InteractionResult clearSpace(ServerPlayer player, ServerLevel level,
                                              BoundingBox box, String name) {

        // Look before recording. Only reading the world so far, so nothing is
        // lost by doing this first - and it decides whether there is anything
        // worth an undo step at all.
        List<StructureTemplate.StructureBlockInfo> toClear = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(
                box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
            // Only what is actually in the way. Listing air as well would make
            // the job many times bigger and change nothing on screen.
            if (!level.getBlockState(pos).isAir()) {
                toClear.add(new StructureTemplate.StructureBlockInfo(
                        pos.immutable(), Blocks.AIR.defaultBlockState(), null));
            }
        }

        if (toClear.isEmpty()) {
            // No undo step for a click that changes nothing. The history holds
            // five steps; filling it with empty ones would quietly push the
            // clearing somebody actually wants back off the end of it.
            tell(player, Component.translatable("message.holobuild.site_already_clear"),
                    ChatFormatting.GRAY);
            return InteractionResult.FAIL;
        }

        // Now it is worth remembering, and it has to happen before the blocks go.
        boolean undoable = UndoManager.record(player, level, box);
        BuildAnimator.start(player, level, name, toClear, false);
        tell(player, Component.translatable("message.holobuild.site_clearing", toClear.size()),
                ChatFormatting.AQUA);
        if (!undoable) {
            tell(player, Component.translatable("message.holobuild.too_big_to_undo",
                    UndoManager.MAX_BLOCKS), ChatFormatting.YELLOW);
        }
        return InteractionResult.CONSUME;
    }

    /**
     * @return the blueprint chosen on a Blueprint Wand the player is carrying,
     *         or an empty one if there is none
     */
    public static BlueprintSettings blueprintOf(Player player) {
        for (ItemStack stack : player.getInventory().items) {
            BlueprintSettings settings = chosenOn(stack);
            if (settings != null) {
                return settings;
            }
        }
        for (ItemStack stack : player.getInventory().offhand) {
            BlueprintSettings settings = chosenOn(stack);
            if (settings != null) {
                return settings;
            }
        }
        return BlueprintSettings.EMPTY;
    }

    /** @return the settings if this stack is a wand with a building chosen, else null */
    private static BlueprintSettings chosenOn(ItemStack stack) {
        if (!(stack.getItem() instanceof BlueprintWandItem)) {
            return null;
        }
        BlueprintSettings settings = stack.getOrDefault(
                ModComponents.BLUEPRINT.get(), BlueprintSettings.EMPTY);
        return settings.hasSchematic() ? settings : null;
    }

    private static void tell(Player player, Component message, ChatFormatting colour) {
        player.displayClientMessage(message.copy().withStyle(colour), false);
    }
}
