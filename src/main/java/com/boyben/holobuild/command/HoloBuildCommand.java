package com.boyben.holobuild.command;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import javax.annotation.Nullable;

import com.boyben.holobuild.HoloBuild;
import com.boyben.holobuild.build.BuildOutcome;
import com.boyben.holobuild.build.Builder;
import com.boyben.holobuild.component.BlueprintSettings;
import com.boyben.holobuild.component.Selection;
import com.boyben.holobuild.generator.ModulePack;
import com.boyben.holobuild.generator.StackGenerator;
import com.boyben.holobuild.item.SelectionWandItem;
import com.boyben.holobuild.registry.ModComponents;
import com.boyben.holobuild.schematic.LoadedSchematic;
import com.boyben.holobuild.schematic.Placement;
import com.boyben.holobuild.schematic.SchematicCache;
import com.boyben.holobuild.schematic.SchematicLoader;
import com.boyben.holobuild.schematic.format.RawSchematic;
import com.boyben.holobuild.schematic.format.StructureNbt;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * The /holobuild commands: build, save, list and random.
 *
 * All of them need operator rights, the same bar vanilla sets for /setblock and
 * /fill. That is deliberately the only gate - no creative-mode check, so they
 * work in survival too, which is what the wand does not do.
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
                // Same bar vanilla sets for /setblock and /fill: anything that
                // rewrites the world needs operator rights. That is also why
                // the commands do not ask for creative mode - whoever may run
                // them may build, in survival just as much.
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("build")
                        .then(Commands.argument("name", StringArgumentType.string())
                                .executes(context -> build(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "name"),
                                        null, 0))
                                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                        .executes(context -> build(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "name"),
                                                BlockPosArgument.getLoadedBlockPos(context, "pos"), 0))
                                        .then(Commands.argument("rotation", IntegerArgumentType.integer(0, 3))
                                                .executes(context -> build(
                                                        context.getSource(),
                                                        StringArgumentType.getString(context, "name"),
                                                        BlockPosArgument.getLoadedBlockPos(context, "pos"),
                                                        IntegerArgumentType.getInteger(context, "rotation")))))))
                .then(Commands.literal("save")
                        .then(Commands.argument("name", StringArgumentType.string())
                                .executes(context -> save(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "name")))))
                .then(Commands.literal("list")
                        .executes(context -> list(context.getSource())))
                .then(Commands.literal("random")
                        .then(Commands.argument("set", StringArgumentType.string())
                                .then(Commands.argument("name", StringArgumentType.string())
                                        .executes(context -> random(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "set"),
                                                StringArgumentType.getString(context, "name"),
                                                RANDOM_FLOORS))
                                        .then(Commands.argument("floors", IntegerArgumentType.integer(
                                                        StackGenerator.MIN_FLOORS, StackGenerator.MAX_FLOORS))
                                                .executes(context -> random(
                                                        context.getSource(),
                                                        StringArgumentType.getString(context, "set"),
                                                        StringArgumentType.getString(context, "name"),
                                                        IntegerArgumentType.getInteger(context, "floors"))))))));
    }

    /** Passed as the floor count when the player did not name one. */
    private static final int RANDOM_FLOORS = -1;

    /**
     * Builds a schematic straight from the chat, no wand needed.
     *
     * Works with every schematic there is - the player's own files, the built-in
     * library, and anything the module generator rolled.
     *
     * @param at       where the corner goes, or null for the player's feet
     * @param rotation quarter turns clockwise, 0 to 3
     */
    private static int build(CommandSourceStack source, String name,
                             @Nullable BlockPos at, int rotation) throws CommandSyntaxException {

        ServerPlayer player = source.getPlayerOrException();
        ServerLevel level = source.getLevel();

        if (!SchematicLoader.isSafeName(name)) {
            fail(source, Component.translatable("message.holobuild.bad_name", name));
            return 0;
        }

        LoadedSchematic schematic;
        try {
            schematic = SchematicCache.get(name, level);
        } catch (IOException | RuntimeException e) {
            HoloBuild.LOGGER.error("Schematic konnte nicht geladen werden: {}", name, e);
            fail(source, Component.translatable("message.holobuild.load_failed", name));
            return 0;
        }
        if (schematic == null) {
            fail(source, Component.translatable("message.holobuild.missing_file", name));
            return 0;
        }

        BlockPos origin = at != null ? at : player.blockPosition();
        BlueprintSettings settings = new BlueprintSettings(name, rotation, false, 0);

        BuildOutcome outcome = Builder.build(player, level, schematic, origin, settings);
        if (!outcome.ok()) {
            source.sendFailure(outcome.message());
            return 0;
        }

        source.sendSuccess(outcome::message, false);
        Component warning = outcome.warning();
        if (warning != null) {
            Component finalWarning = warning;
            source.sendSuccess(() -> finalWarning, false);
        }
        return 1;
    }

    /**
     * Assembles a new building out of a set of modules and saves it.
     *
     * The result is an ordinary schematic file, so it can be previewed, rotated
     * and built like any other - and edited afterwards if a floor came out odd.
     */
    private static int random(CommandSourceStack source, String set, String name, int floors)
            throws CommandSyntaxException {

        ServerPlayer player = source.getPlayerOrException();
        ServerLevel level = source.getLevel();

        Path target = SchematicLoader.pathForNewFile(name);
        if (target == null) {
            fail(source, Component.translatable("message.holobuild.bad_name", name));
            return 0;
        }

        ModulePack pack;
        try {
            pack = ModulePack.find(set, level);
        } catch (ModulePack.MissingPieceException e) {
            fail(source, Component.translatable("message.holobuild.missing_module", set, e.getMessage()));
            return 0;
        } catch (IOException | RuntimeException e) {
            HoloBuild.LOGGER.error("Baukasten '{}' konnte nicht gelesen werden", set, e);
            fail(source, Component.translatable("message.holobuild.load_failed", set));
            return 0;
        }

        RandomSource random = level.getRandom();
        int chosen = floors == RANDOM_FLOORS ? 2 + random.nextInt(5) : floors;
        RawSchematic built = StackGenerator.stack(pack, chosen, random);

        try {
            SchematicLoader.ensureFolderExists();
            NbtIo.writeCompressed(StructureNbt.toCompound(built), target);
        } catch (IOException e) {
            HoloBuild.LOGGER.error("Erzeugtes Bauwerk konnte nicht gespeichert werden: {}", target, e);
            fail(source, Component.translatable("message.holobuild.save_failed", name));
            return 0;
        }
        SchematicCache.clear();

        Vec3i size = built.size();
        int floorCount = chosen;
        source.sendSuccess(() -> Component.translatable("message.holobuild.generated",
                name, floorCount, size.getX() + "x" + size.getY() + "x" + size.getZ())
                .withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    private static int save(CommandSourceStack source, String name) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel level = source.getLevel();

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
