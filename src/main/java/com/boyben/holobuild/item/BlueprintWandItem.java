package com.boyben.holobuild.item;

import java.io.IOException;
import java.util.List;

import com.boyben.holobuild.Config;
import com.boyben.holobuild.HoloBuild;
import com.boyben.holobuild.build.BuildAnimator;
import com.boyben.holobuild.build.BuildOutcome;
import com.boyben.holobuild.build.Builder;
import com.boyben.holobuild.component.BlueprintSettings;
import com.boyben.holobuild.registry.ModComponents;
import com.boyben.holobuild.schematic.LoadedSchematic;
import com.boyben.holobuild.schematic.Placement;
import com.boyben.holobuild.schematic.SchematicCache;
import com.boyben.holobuild.schematic.SchematicLoader;
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

/**
 * Right-click a block to place the selected schematic against the face you clicked.
 * Shift + right-click undoes the last build.
 *
 * Previewing and turning work in any mode. Building is creative-only unless
 * survivalBuilding is switched on, and then every block is paid for out of
 * the inventory - the wand never conjures blocks in survival.
 *
 * Which schematic is selected, and how it is turned, lives in a
 * {@link BlueprintSettings} data component on the stack itself.
 */
public class BlueprintWandItem extends Item {

    public BlueprintWandItem(Properties properties) {
        super(properties);
    }

    /**
     * The controls, and which blueprint is loaded right now.
     *
     * Rotating and mirroring are on keys the player never pressed before, so
     * they are listed even though they are not needed to get started - someone
     * holding the wand should be able to find out what it can do without
     * leaving the game.
     */
    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                List<Component> lines, TooltipFlag flag) {
        WandTooltip.control(lines, "tooltip.holobuild.key_open", "tooltip.holobuild.blueprint.choose");
        WandTooltip.control(lines, "tooltip.holobuild.rmb", "tooltip.holobuild.blueprint.build");
        WandTooltip.control(lines, "tooltip.holobuild.shift_rmb", "tooltip.holobuild.blueprint.undo");
        WandTooltip.control(lines, "tooltip.holobuild.keys_turn", "tooltip.holobuild.blueprint.turn");
        WandTooltip.blank(lines);

        BlueprintSettings settings = stack.getOrDefault(
                ModComponents.BLUEPRINT.get(), BlueprintSettings.EMPTY);
        if (settings.hasSchematic()) {
            WandTooltip.state(lines, Component.translatable("tooltip.holobuild.blueprint.chosen",
                    settings.schematic()), ChatFormatting.GREEN);
        } else {
            WandTooltip.state(lines, Component.translatable("tooltip.holobuild.blueprint.none"),
                    ChatFormatting.YELLOW);
        }
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }

        // In survival the wand always previews and turns. Whether it may also
        // build - paying with real blocks - is a setting, off by default:
        // putting up a whole building at once changes how a world plays.
        if (!player.isCreative() && !Config.survivalBuilding()) {
            if (!level.isClientSide) {
                tell(player, Component.translatable("message.holobuild.survival_hint"), ChatFormatting.YELLOW);
            }
            return InteractionResult.FAIL;
        }

        // Placing and restoring blocks is the server's job. Doing it on the
        // client too would look right for a moment and then vanish.
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!(player instanceof ServerPlayer serverPlayer) || !(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.PASS;
        }

        return player.isShiftKeyDown()
                ? undo(serverPlayer, serverLevel)
                : place(context, serverPlayer, serverLevel);
    }

    private InteractionResult undo(ServerPlayer player, ServerLevel level) {
        // A build still running is stopped first - undoing underneath a build
        // that keeps placing blocks would just fight itself.
        if (BuildAnimator.cancel(player.getUUID())) {
            tell(player, Component.translatable("message.holobuild.build_cancelled"), ChatFormatting.YELLOW);
        }

        int restored = UndoManager.undo(player, level);
        if (restored < 0) {
            tell(player, Component.translatable("message.holobuild.nothing_to_undo"), ChatFormatting.YELLOW);
            return InteractionResult.FAIL;
        }
        tell(player, Component.translatable("message.holobuild.undone", restored), ChatFormatting.AQUA);
        return InteractionResult.CONSUME;
    }

    private InteractionResult place(UseOnContext context, ServerPlayer player, ServerLevel level) {
        ItemStack stack = context.getItemInHand();
        BlueprintSettings settings = stack.getOrDefault(ModComponents.BLUEPRINT.get(), BlueprintSettings.EMPTY);

        // Nothing chosen yet? Fall back to the first thing available - a file of
        // the player's own, or otherwise a built-in building - and remember it
        // so the wand keeps using the same one.
        if (!settings.hasSchematic()) {
            List<String> available = SchematicLoader.allNames();
            if (available.isEmpty()) {
                tell(player, Component.translatable("message.holobuild.no_schematics",
                        SchematicLoader.getSchematicFolder().toString()), ChatFormatting.RED);
                return InteractionResult.FAIL;
            }
            settings = settings.withSchematic(available.get(0));
            stack.set(ModComponents.BLUEPRINT.get(), settings);
        }

        LoadedSchematic schematic;
        try {
            schematic = SchematicCache.get(settings.schematic(), level);
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

        BlockPos target = Placement.origin(context.getClickedPos(), context.getClickedFace(), settings);
        BuildOutcome outcome = Builder.build(player, level, schematic, target, settings);

        player.displayClientMessage(outcome.message(), false);
        Component warning = outcome.warning();
        if (warning != null) {
            player.displayClientMessage(warning, false);
        }

        return outcome.ok() ? InteractionResult.CONSUME : InteractionResult.FAIL;
    }

    private static void tell(Player player, Component message, ChatFormatting colour) {
        player.displayClientMessage(message.copy().withStyle(colour), false);
    }
}
