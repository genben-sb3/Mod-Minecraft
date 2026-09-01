package com.boyben.holobuild.command;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.BiFunction;

import javax.annotation.Nullable;

import com.boyben.holobuild.HoloBuild;
import com.boyben.holobuild.build.BuildOutcome;
import com.boyben.holobuild.build.Builder;
import com.boyben.holobuild.component.BlueprintSettings;
import com.boyben.holobuild.component.SaveOptions;
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
import com.boyben.holobuild.schematic.format.StructureFilter;
import com.boyben.holobuild.schematic.format.StructureNbt;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
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
                .then(Commands.literal("options")
                        .executes(context -> showOptions(context.getSource()))
                        .then(optionToggle("air", SaveOptions::withAir))
                        .then(optionToggle("contents", SaveOptions::withContents))
                        .then(optionToggle("entities", SaveOptions::withEntities)))
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

        // Not for the result, but for the refusal: rolling a building is a
        // player action, and this throws for the console or a command block.
        source.getPlayerOrException();
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
        SaveOptions options = saveOptionsOf(player);

        StructureTemplate template = new StructureTemplate();
        template.setAuthor(player.getGameProfile().getName());
        // Read everything first, including entities if they are wanted, then
        // let StructureFilter take out what the player switched off. Vanilla
        // can only leave out a single block type, which is not enough here.
        template.fillFromWorld(level, start, size, options.entities(), null);

        CompoundTag tag = template.save(new CompoundTag());
        int droppedAir = options.air() ? 0 : StructureFilter.stripAir(tag);
        if (!options.contents()) {
            StructureFilter.stripBlockEntities(tag);
        }

        // Marking nothing but air is a mistake worth naming: the file would
        // save fine and then build nothing at all.
        if (StructureFilter.isEmpty(tag)) {
            fail(source, Component.translatable("message.holobuild.selection_all_air"));
            return 0;
        }

        try {
            SchematicLoader.ensureFolderExists();
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
        if (droppedAir > 0) {
            // Say it, so nobody wonders why the file is smaller than the selection.
            source.sendSuccess(() -> Component.translatable("message.holobuild.saved_without_air",
                    droppedAir).withStyle(ChatFormatting.GRAY), false);
        }
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

    /**
     * One on/off switch under /holobuild options.
     *
     * The change itself is passed in rather than looked up from the name, so
     * there is no second list of option names that could drift out of step with
     * the first - the command tree is the only place they are written down.
     */
    private static LiteralArgumentBuilder<CommandSourceStack> optionToggle(
            String name, BiFunction<SaveOptions, Boolean, SaveOptions> change) {

        return Commands.literal(name)
                .then(Commands.argument("on", BoolArgumentType.bool())
                        .executes(context -> setOption(context.getSource(), change,
                                BoolArgumentType.getBool(context, "on"))));
    }

    private static int setOption(CommandSourceStack source,
                                 BiFunction<SaveOptions, Boolean, SaveOptions> change,
                                 boolean on) throws CommandSyntaxException {

        ServerPlayer player = source.getPlayerOrException();
        ItemStack wand = selectionWandOf(player);
        if (wand == null) {
            // The settings live on the wand, so without one there is nothing
            // to change - saying so beats silently altering nothing.
            fail(source, Component.translatable("message.holobuild.no_wand"));
            return 0;
        }

        SaveOptions options = wand.getOrDefault(ModComponents.SAVE_OPTIONS.get(), SaveOptions.DEFAULT);
        wand.set(ModComponents.SAVE_OPTIONS.get(), change.apply(options, on));
        return showOptions(source);
    }

    /** Lists what the held wand will keep the next time it saves. */
    private static int showOptions(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        if (selectionWandOf(player) == null) {
            fail(source, Component.translatable("message.holobuild.no_wand"));
            return 0;
        }

        SaveOptions options = saveOptionsOf(player);
        source.sendSuccess(() -> Component.translatable("message.holobuild.options_header")
                .withStyle(ChatFormatting.AQUA), false);
        optionLine(source, "message.holobuild.option_air", options.air());
        optionLine(source, "message.holobuild.option_contents", options.contents());
        optionLine(source, "message.holobuild.option_entities", options.entities());
        return 1;
    }

    private static void optionLine(CommandSourceStack source, String key, boolean on) {
        source.sendSuccess(() -> Component.translatable(key)
                .append(Component.literal(": "))
                .append(Component.translatable(on
                                ? "message.holobuild.option_on"
                                : "message.holobuild.option_off")
                        .withStyle(on ? ChatFormatting.GREEN : ChatFormatting.GRAY)), false);
    }

    /** @return the selection on a Selection Wand in either hand, or null */
    private static Selection selectionOf(ServerPlayer player) {
        ItemStack wand = selectionWandOf(player);
        return wand == null ? null : wand.getOrDefault(ModComponents.SELECTION.get(), Selection.EMPTY);
    }

    /** @return what the held wand keeps when saving, or the defaults if none is held */
    private static SaveOptions saveOptionsOf(ServerPlayer player) {
        ItemStack wand = selectionWandOf(player);
        return wand == null
                ? SaveOptions.DEFAULT
                : wand.getOrDefault(ModComponents.SAVE_OPTIONS.get(), SaveOptions.DEFAULT);
    }

    /** @return a Selection Wand from either hand, or null */
    private static ItemStack selectionWandOf(ServerPlayer player) {
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = player.getItemInHand(hand);
            if (stack.getItem() instanceof SelectionWandItem) {
                return stack;
            }
        }
        return null;
    }

    private static void fail(CommandSourceStack source, Component message) {
        source.sendFailure(message.copy().withStyle(ChatFormatting.RED));
    }
}
