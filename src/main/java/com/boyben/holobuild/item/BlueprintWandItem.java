package com.boyben.holobuild.item;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import com.boyben.holobuild.HoloBuild;
import com.boyben.holobuild.component.BlueprintSettings;
import com.boyben.holobuild.registry.ModComponents;
import com.boyben.holobuild.schematic.LoadedSchematic;
import com.boyben.holobuild.schematic.SchematicCache;
import com.boyben.holobuild.schematic.SchematicLoader;
import com.boyben.holobuild.undo.UndoManager;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;

/**
 * Right-click a block to place the selected schematic against the face you clicked.
 * Shift + right-click undoes the last build.
 *
 * Creative mode only. Which schematic is selected, and how it is turned, lives
 * in a {@link BlueprintSettings} data component on the stack itself.
 */
public class BlueprintWandItem extends Item {

    public BlueprintWandItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }

        if (!player.isCreative()) {
            if (!level.isClientSide) {
                tell(player, Component.translatable("message.holobuild.creative_only"), ChatFormatting.RED);
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

        // Nothing chosen yet? Fall back to the first file in the folder, and
        // remember it so the wand keeps using the same one.
        if (!settings.hasSchematic()) {
            List<Path> files = SchematicLoader.listSchematics();
            if (files.isEmpty()) {
                tell(player, Component.translatable("message.holobuild.no_schematics",
                        SchematicLoader.getSchematicFolder().toString()), ChatFormatting.RED);
                return InteractionResult.FAIL;
            }
            settings = settings.withSchematic(SchematicLoader.displayName(files.get(0)));
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

        BlockPos target = placementOrigin(context, settings);
        StructurePlaceSettings placeSettings = placeSettingsFor(schematic, settings);

        // Save the old state first - once the blocks are overwritten it is too late.
        BoundingBox box = schematic.template().getBoundingBox(placeSettings, target);
        boolean undoable = UndoManager.record(player, level, box);

        boolean placed = schematic.template().placeInWorld(
                level, target, target, placeSettings, level.getRandom(), Block.UPDATE_CLIENTS);
        if (!placed) {
            tell(player, Component.translatable("message.holobuild.place_failed"), ChatFormatting.RED);
            return InteractionResult.FAIL;
        }

        Vec3i size = schematic.size();
        tell(player, Component.translatable("message.holobuild.built",
                schematic.name(), schematic.blockCount(),
                size.getX() + "x" + size.getY() + "x" + size.getZ()), ChatFormatting.GREEN);
        if (!undoable) {
            tell(player, Component.translatable("message.holobuild.too_big_to_undo", UndoManager.MAX_BLOCKS),
                    ChatFormatting.YELLOW);
        }
        return InteractionResult.CONSUME;
    }

    /** Where the structure's corner goes: on the clicked face, shifted by the chosen offset. */
    private static BlockPos placementOrigin(UseOnContext context, BlueprintSettings settings) {
        return context.getClickedPos()
                .relative(context.getClickedFace())
                .above(settings.yOffset());
    }

    private static StructurePlaceSettings placeSettingsFor(LoadedSchematic schematic, BlueprintSettings settings) {
        Vec3i size = schematic.size();
        return new StructurePlaceSettings()
                .setRotation(settings.toRotation())
                .setMirror(settings.toMirror())
                // Turn around the middle, otherwise rotating swings the whole
                // building off to the side instead of spinning it in place.
                .setRotationPivot(new BlockPos(size.getX() / 2, 0, size.getZ() / 2))
                .setIgnoreEntities(false);
    }

    private static void tell(Player player, Component message, ChatFormatting colour) {
        player.displayClientMessage(message.copy().withStyle(colour), false);
    }
}
