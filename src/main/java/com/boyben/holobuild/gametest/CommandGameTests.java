package com.boyben.holobuild.gametest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import com.boyben.holobuild.HoloBuild;
import com.boyben.holobuild.component.SaveOptions;
import com.boyben.holobuild.component.Selection;
import com.boyben.holobuild.registry.ModComponents;
import com.boyben.holobuild.registry.ModItems;
import com.boyben.holobuild.schematic.LoadedSchematic;
import com.boyben.holobuild.schematic.SchematicCache;
import com.boyben.holobuild.schematic.SchematicLoader;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Tests for the /holobuild commands.
 *
 * The pieces behind them are covered elsewhere; what these check is the wiring
 * - that the arguments arrive where they should, and that a command with a
 * hostile name does nothing rather than something.
 */
@GameTestHolder(HoloBuild.MODID)
@PrefixGameTestTemplate(false)
public class CommandGameTests {

    private static final String ROOM = "test_platform";

    /** Operator rights, and no chat spam in the test log. */
    private static CommandSourceStack sourceFor(ServerPlayer player) {
        return player.createCommandSourceStack().withPermission(2).withSuppressedOutput();
    }

    private static void run(ServerLevel level, ServerPlayer player, String command) {
        level.getServer().getCommands().performPrefixedCommand(sourceFor(player), command);
    }

    /**
     * /holobuild build has to put the building where the coordinates say.
     *
     * The command goes through the same Builder the wand uses, so this mostly
     * proves that name and position survive the trip through the parser.
     */
    @GameTest(template = ROOM, timeoutTicks = 400)
    public static void buildCommandPlacesAtGivenPosition(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        BlockPos target = helper.absolutePos(new BlockPos(1, 1, 1));

        run(level, player, "holobuild build cottage "
                + target.getX() + " " + target.getY() + " " + target.getZ());

        // The cottage has a cobblestone floor, so its corner must fill up.
        helper.succeedWhen(() -> {
            if (level.getBlockState(target).isAir()) {
                throw new GameTestAssertException("Am angegebenen Punkt " + target
                        + " steht immer noch nichts");
            }
        });
    }

    /**
     * A name pointing outside the folder must not reach the file it points at.
     *
     * A real schematic is placed one level above the schematics folder first.
     * Without that the test would prove nothing: asking for "../secrets" when
     * no such file exists fails for the boring reason, and would stay green
     * even with the name check removed. With the file really sitting there,
     * only the check stands between the command and reading it.
     */
    @GameTest(template = ROOM, timeoutTicks = 200)
    public static void buildCommandCannotReachOutsideTheFolder(GameTestHelper helper) throws Exception {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        BlockPos target = helper.absolutePos(new BlockPos(1, 2, 1));

        Path folder = SchematicLoader.getSchematicFolder();
        Path outside = folder.getParent().resolve("gametest_outside.nbt");

        // Bait: a genuine, loadable structure just outside the folder. If the
        // guard failed, this is what would get built - and then the test sees it.
        Files.createDirectories(outside.getParent());
        StructureTemplate bait = new StructureTemplate();
        bait.fillFromWorld(level, helper.absolutePos(new BlockPos(0, 0, 0)),
                new Vec3i(2, 2, 2), false, null);
        NbtIo.writeCompressed(bait.save(new CompoundTag()), outside);

        SchematicCache.clear();
        run(level, player, "holobuild build \"../gametest_outside\" "
                + target.getX() + " " + target.getY() + " " + target.getZ());

        // Checking straight away would prove nothing: building is spread over
        // ticks, so even a successful build has not placed anything yet. Wait
        // long enough that a build would have finished, then look.
        helper.runAfterDelay(TICKS_FOR_A_SMALL_BUILD, () -> {
            try {
                if (!level.getBlockState(target).isAir()) {
                    helper.fail("Der Befehl hat eine Datei ausserhalb des Ordners gebaut - "
                            + "die Namenspruefung greift nicht");
                    return;
                }
                helper.succeed();
            } finally {
                try {
                    Files.deleteIfExists(outside);
                } catch (Exception ignored) {
                    // Leftover bait in the run folder is untidy, not harmful.
                }
                SchematicCache.clear();
            }
        });
    }

    /**
     * Long enough for an eight-block build to be finished and settled.
     *
     * Used by the tests that check nothing happened - they have to wait out the
     * time in which something could have happened.
     */
    private static final int TICKS_FOR_A_SMALL_BUILD = 40;

    /**
     * Saving must leave the air out, so a building settles into the ground
     * instead of cutting a box out of it.
     *
     * The pair below matters more than either half alone. This one shows the
     * air is dropped; the next shows it comes back when the option is on. With
     * only the first, a save that had quietly stopped reading the option at all
     * would still look right.
     */
    @GameTest(template = ROOM, timeoutTicks = 300)
    public static void saveCommandLeavesAirOut(GameTestHelper helper) throws Exception {
        int kept = saveAndCountBlocks(helper, "gametest_no_air", SaveOptions.DEFAULT);
        if (kept != 3) {
            helper.fail("Erwartet: 3 Steinbloecke ohne die Luft, gespeichert wurden " + kept);
            return;
        }
        helper.succeed();
    }

    /** ... and with air switched on, the empty spot is part of the file again. */
    @GameTest(template = ROOM, timeoutTicks = 300)
    public static void saveCommandKeepsAirWhenAsked(GameTestHelper helper) throws Exception {
        int kept = saveAndCountBlocks(helper, "gametest_with_air",
                SaveOptions.DEFAULT.withAir(true));
        if (kept != 4) {
            helper.fail("Mit air=true gehoert die Luft dazu: erwartet 4, gespeichert wurden " + kept);
            return;
        }
        helper.succeed();
    }

    /**
     * /holobuild erase has to write a real file with the block gone.
     *
     * The editing itself is covered elsewhere; what this proves is the trip
     * through the command and back off the disk - a copy that never reached the
     * folder, or reached it unreadable, would look identical from inside.
     */
    @GameTest(template = ROOM, timeoutTicks = 200)
    public static void eraseCommandWritesAnEditedFile(GameTestHelper helper) throws Exception {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = helper.makeMockServerPlayerInLevel();

        String name = "gametest_erased";
        Path file = SchematicLoader.getSchematicFolder().resolve(name + ".nbt");
        try {
            SchematicCache.clear();
            LoadedSchematic before = SchematicLoader.loadByName("cottage", level);
            if (before == null) {
                helper.fail("Eingebautes Bauwerk 'cottage' nicht gefunden");
                return;
            }
            int cobbleBefore = countOf(before, Blocks.COBBLESTONE);
            if (cobbleBefore == 0) {
                helper.fail("Die Huette hat gar kein Bruchstein - dann prueft der Test nichts");
                return;
            }

            run(level, player, "holobuild erase cottage minecraft:cobblestone " + name);

            SchematicCache.clear();
            LoadedSchematic after = SchematicLoader.loadByName(name, level);
            if (after == null) {
                helper.fail("Der Befehl hat keine lesbare Datei hinterlassen: " + file);
                return;
            }
            if (countOf(after, Blocks.COBBLESTONE) != 0) {
                helper.fail("Es ist noch Bruchstein drin");
                return;
            }
            if (after.blocks().size() != before.blocks().size() - cobbleBefore) {
                helper.fail("Es sind andere Bloecke mitgegangen: erwartet "
                        + (before.blocks().size() - cobbleBefore)
                        + ", uebrig " + after.blocks().size());
                return;
            }
            if (!after.size().equals(before.size())) {
                helper.fail("Die Groesse hat sich geaendert: " + before.size()
                        + " zu " + after.size());
                return;
            }
            helper.succeed();

        } finally {
            Files.deleteIfExists(file);
            SchematicCache.clear();
        }
    }

    private static int countOf(LoadedSchematic schematic, net.minecraft.world.level.block.Block kind) {
        int found = 0;
        for (StructureTemplate.StructureBlockInfo info : schematic.blocks()) {
            if (info.state().is(kind)) {
                found++;
            }
        }
        return found;
    }

    /**
     * Marks a 2x2x1 area holding three stone blocks and one gap, saves it
     * through the real command, and reads the file back.
     *
     * @return how many blocks the saved file actually contains
     */
    private static int saveAndCountBlocks(GameTestHelper helper, String name, SaveOptions options)
            throws Exception {

        ServerLevel level = helper.getLevel();
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Path file = SchematicLoader.getSchematicFolder().resolve(name + ".nbt");

        try {
            BlockPos corner = helper.absolutePos(new BlockPos(1, 1, 1));
            BlockPos opposite = corner.offset(1, 1, 0);

            BlockState stone = Blocks.STONE.defaultBlockState();
            level.setBlock(corner, stone, Block.UPDATE_CLIENTS);
            level.setBlock(corner.east(), stone, Block.UPDATE_CLIENTS);
            level.setBlock(corner.above(), stone, Block.UPDATE_CLIENTS);
            // The deliberate gap - this is the block the test is about.
            level.setBlock(corner.above().east(), Blocks.AIR.defaultBlockState(),
                    Block.UPDATE_CLIENTS);

            ItemStack wand = new ItemStack(ModItems.SELECTION_WAND.get());
            wand.set(ModComponents.SELECTION.get(),
                    new Selection(Optional.of(corner), Optional.of(opposite)));
            wand.set(ModComponents.SAVE_OPTIONS.get(), options);
            player.setItemInHand(InteractionHand.MAIN_HAND, wand);

            run(level, player, "holobuild save " + name);

            SchematicCache.clear();
            LoadedSchematic saved = SchematicLoader.loadByName(name, level);
            if (saved == null) {
                throw new IllegalStateException("Der Befehl hat keine lesbare Datei hinterlassen");
            }
            return saved.blocks().size();

        } finally {
            Files.deleteIfExists(file);
            SchematicCache.clear();
        }
    }

    /**
     * /holobuild random has to leave a usable file behind.
     *
     * The generator itself is tested elsewhere; this checks that the command
     * writes the result where the picker will find it.
     */
    @GameTest(template = ROOM, timeoutTicks = 300)
    public static void randomCommandWritesAFile(GameTestHelper helper) throws Exception {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = helper.makeMockServerPlayerInLevel();

        String name = "gametest_rolled";
        Path file = SchematicLoader.getSchematicFolder().resolve(name + ".nbt");
        try {
            run(level, player, "holobuild random tower " + name + " 2");

            if (!Files.isRegularFile(file)) {
                helper.fail("Der Befehl hat keine Datei angelegt: " + file);
                return;
            }
            SchematicCache.clear();
            if (SchematicLoader.loadByName(name, level) == null) {
                helper.fail("Die erzeugte Datei laesst sich nicht laden");
                return;
            }
            helper.succeed();

        } finally {
            Files.deleteIfExists(file);
            SchematicCache.clear();
        }
    }
}
