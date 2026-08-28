package com.boyben.holobuild.command;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.boyben.holobuild.HoloBuild;
import com.boyben.holobuild.component.Selection;
import com.boyben.holobuild.item.SelectionWandItem;
import com.boyben.holobuild.registry.ModComponents;
import com.boyben.holobuild.schematic.Placement;
import com.boyben.holobuild.schematic.SchematicCache;
import com.boyben.holobuild.schematic.SchematicLoader;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * /holobuild save &lt;name&gt; and /holobuild list.
 *
 * Saving reads the marked area straight out of the world using Minecraft's own
 * structure code, so the file it writes is a completely normal .nbt structure -
 * usable by the structure block and by other mods too.
 */
public final class HoloBuildCommand {
    private HoloBuildCommand() {}

    /** Beyond this, saving would stall the server and produce a huge file. */
    private static final long MAX_SAVE_VOLUME = 500_000L;

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("holobuild")
                .then(Commands.literal("save")
                        .then(Commands.argument("name", StringArgumentType.string())
                                .executes(context -> save(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "name")))))
                .then(Commands.literal("list")
                        .executes(context -> list(context.getSource()))));
    }

    private static int save(CommandSourceStack source, String name) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel level = source.getLevel();

        if (!player.isCreative()) {
            fail(source, Component.translatable("message.holobuild.creative_only"));
            return 0;
        }

        Path target = SchematicLoader.pathForNewFile(name);
        if (target == null) {
            fail(source, Component.translatable("message.holobuild.bad_name", name));
            return 0;
        }

        Selection selection = selectionOf(player);
        if (selection == null || !selection.isComplete()) {
            fail(source, Component.translatable("message.holobuild.no_selection"));
            return 0;
        }

        long volume = selection.volume();
        if (volume > MAX_SAVE_VOLUME) {
            fail(source, Component.translatable("message.holobuild.selection_too_big",
                    volume, MAX_SAVE_VOLUME));
            return 0;
        }

        BoundingBox box = selection.box();
        // Outside the build height there is nothing to read, so a selection
        // sticking out would quietly save a slab of empty air.
        if (!Placement.fitsInWorld(box, level)) {
            fail(source, Component.translatable("message.holobuild.selection_outside_world"));
            return 0;
        }

        BlockPos start = new BlockPos(box.minX(), box.minY(), box.minZ());
        Vec3i size = selection.size();

        StructureTemplate template = new StructureTemplate();
        template.setAuthor(player.getGameProfile().getName());
        // Entities stay out: a saved building should not come with the cows
        // that happened to stand in it.
        template.fillFromWorld(level, start, size, false, null);

        try {
            SchematicLoader.ensureFolderExists();
            CompoundTag tag = template.save(new CompoundTag());
            NbtIo.writeCompressed(tag, target);
        } catch (IOException e) {
            HoloBuild.LOGGER.error("Schematic konnte nicht gespeichert werden: {}", target, e);
            fail(source, Component.translatable("message.holobuild.save_failed", name));
            return 0;
        }

        // The file is new, so anything remembered about that name is stale.
        SchematicCache.clear();

        source.sendSuccess(() -> Component.translatable("message.holobuild.saved",
                name, size.getX() + "x" + size.getY() + "x" + size.getZ())
                .withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    private static int list(CommandSourceStack source) {
        List<String> names = SchematicLoader.allNames();
        if (names.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("message.holobuild.no_schematics",
                    SchematicLoader.getSchematicFolder().toString())
                    .withStyle(ChatFormatting.YELLOW), false);
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("message.holobuild.list_header", names.size())
                .withStyle(ChatFormatting.AQUA), false);
        for (String name : names) {
            // Mark the ones that come from inside the mod, so it is obvious
            // which files actually sit in the folder.
            boolean builtIn = SchematicLoader.isBuiltIn(name);
            Component line = Component.literal("  " + name)
                    .withStyle(ChatFormatting.GRAY);
            if (builtIn) {
                line = line.copy().append(Component.literal("  ")
                        .append(Component.translatable("screen.holobuild.built_in"))
                        .withStyle(ChatFormatting.DARK_AQUA));
            }
            Component finalLine = line;
            source.sendSuccess(() -> finalLine, false);
        }
        return names.size();
    }

    /** @return the selection on a Selection Wand in either hand, or null */
    private static Selection selectionOf(ServerPlayer player) {
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = player.getItemInHand(hand);
            if (stack.getItem() instanceof SelectionWandItem) {
                return stack.getOrDefault(ModComponents.SELECTION.get(), Selection.EMPTY);
            }
        }
        return null;
    }

    private static void fail(CommandSourceStack source, Component message) {
        source.sendFailure(message.copy().withStyle(ChatFormatting.RED));
    }
}
