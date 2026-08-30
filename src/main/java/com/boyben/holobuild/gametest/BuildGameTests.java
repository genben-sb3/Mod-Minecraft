package com.boyben.holobuild.gametest;

import java.util.List;

import com.boyben.holobuild.HoloBuild;
import com.boyben.holobuild.component.BlueprintSettings;
import com.boyben.holobuild.schematic.LoadedSchematic;
import com.boyben.holobuild.schematic.Placement;
import com.boyben.holobuild.schematic.SchematicLoader;
import com.boyben.holobuild.undo.UndoManager;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Tests that run inside a real Minecraft server.
 *
 * The unit tests cover the parts that are pure arithmetic; these cover the
 * parts that are not - loading a real schematic, working out where its blocks
 * land, putting them in a real world and taking them out again. They run
 * unattended with {@code gradlew runGameTestServer}, which starts a server,
 * runs everything and exits.
 */
@GameTestHolder(HoloBuild.MODID)
// Without this the class name is glued in front of the template name, and
// "buildgametests.holobuild:test_platform" is not a valid resource path.
@PrefixGameTestTemplate(false)
public class BuildGameTests {

    /** Just the name: @GameTestHolder already supplies the namespace. */
    private static final String ROOM = "test_platform";

    /** A building from the shipped library, so the test needs no files on disk. */
    private static final String SCHEMATIC = "cottage";

    /**
     * For the rotation test. The cottage is 7x7 on the ground, so turning it
     * leaves the bounding box identical and the test would prove nothing.
     * The bridge is 5 by 15 - a quarter turn has to swap those.
     */
    private static final String OBLONG = "bridge";

    /**
     * The whole chain: read a schematic, work out where it goes, place it.
     *
     * If loading, transforming or placing is broken, this fails - and it is the
     * single most important thing the mod does.
     */
    @GameTest(template = ROOM, timeoutTicks = 200)
    public static void placesEveryBlock(GameTestHelper helper) throws Exception {
        ServerLevel level = helper.getLevel();
        LoadedSchematic schematic = SchematicLoader.loadByName(SCHEMATIC, level);
        if (schematic == null) {
            helper.fail("Eingebautes Bauwerk '" + SCHEMATIC + "' nicht gefunden");
            return;
        }

        BlockPos origin = helper.absolutePos(new BlockPos(1, 1, 1));
        BlueprintSettings settings = new BlueprintSettings(SCHEMATIC, 0, false, 0);
        StructurePlaceSettings place = Placement.settings(schematic.size(), settings);

        List<StructureTemplate.StructureBlockInfo> blocks =
                Placement.transformed(schematic, place, origin);
        for (StructureTemplate.StructureBlockInfo info : blocks) {
            level.setBlock(info.pos(), info.state(), Block.UPDATE_CLIENTS);
        }

        // Every solid block of the schematic has to be where it was put.
        int checked = 0;
        for (StructureTemplate.StructureBlockInfo info : blocks) {
            if (info.state().isAir()) {
                continue;
            }
            BlockState actual = level.getBlockState(info.pos());
            if (actual.getBlock() != info.state().getBlock()) {
                helper.fail("Block bei " + info.pos() + " ist " + actual.getBlock()
                        + " statt " + info.state().getBlock());
                return;
            }
            checked++;
        }
        if (checked == 0) {
            helper.fail("Es wurde kein einziger fester Block gesetzt");
            return;
        }
        helper.succeed();
    }

    /**
     * Undo has to put the world back exactly as it was.
     *
     * This is the promise that makes the wand safe to try things with, so it is
     * worth checking that it really holds.
     */
    @GameTest(template = ROOM, timeoutTicks = 200)
    public static void undoRestoresEverything(GameTestHelper helper) throws Exception {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = helper.makeMockServerPlayerInLevel();

        LoadedSchematic schematic = SchematicLoader.loadByName(SCHEMATIC, level);
        if (schematic == null) {
            helper.fail("Eingebautes Bauwerk '" + SCHEMATIC + "' nicht gefunden");
            return;
        }

        BlockPos origin = helper.absolutePos(new BlockPos(1, 1, 1));
        BlueprintSettings settings = new BlueprintSettings(SCHEMATIC, 0, false, 0);
        StructurePlaceSettings place = Placement.settings(schematic.size(), settings);
        BoundingBox box = Placement.box(schematic, place, origin);

        // The marker has to sit where the build really puts a block, otherwise
        // it would survive by accident and prove nothing. The lowest corner is
        // part of the cottage floor, so that spot is certain to be overwritten.
        BlockPos marker = new BlockPos(box.minX(), box.minY(), box.minZ());
        level.setBlock(marker, Blocks.GOLD_BLOCK.defaultBlockState(), Block.UPDATE_CLIENTS);

        if (!UndoManager.record(player, level, box)) {
            helper.fail("Undo konnte den Bereich nicht aufzeichnen");
            return;
        }

        for (StructureTemplate.StructureBlockInfo info : Placement.transformed(schematic, place, origin)) {
            level.setBlock(info.pos(), info.state(), Block.UPDATE_CLIENTS);
        }
        if (level.getBlockState(marker).is(Blocks.GOLD_BLOCK)) {
            helper.fail("Der Bau hat den Markierungsblock gar nicht ueberschrieben - "
                    + "der Test wuerde nichts pruefen");
            return;
        }

        int restored = UndoManager.undo(player, level);
        if (restored <= 0) {
            helper.fail("Undo hat nichts zurueckgesetzt");
            return;
        }
        if (!level.getBlockState(marker).is(Blocks.GOLD_BLOCK)) {
            helper.fail("Nach dem Undo fehlt der Markierungsblock bei " + marker);
            return;
        }
        helper.succeed();
    }

    /**
     * Turning has to move blocks and keep the footprint in place.
     *
     * The rotation happens around the middle, so a quarter turn must land the
     * building somewhere different without wandering off sideways.
     */
    @GameTest(template = ROOM, timeoutTicks = 200)
    public static void rotationMovesBlocks(GameTestHelper helper) throws Exception {
        ServerLevel level = helper.getLevel();
        LoadedSchematic schematic = SchematicLoader.loadByName(OBLONG, level);
        if (schematic == null) {
            helper.fail("Eingebautes Bauwerk '" + OBLONG + "' nicht gefunden");
            return;
        }
        if (schematic.size().getX() == schematic.size().getZ()) {
            helper.fail("'" + OBLONG + "' ist quadratisch - dann kann dieser Test nichts zeigen");
            return;
        }

        BlockPos origin = helper.absolutePos(new BlockPos(1, 1, 1));

        BoundingBox straight = boxFor(schematic, origin, 0);
        BoundingBox turned = boxFor(schematic, origin, 1);

        // A quarter turn has to swap width and depth.
        if (straight.getXSpan() != turned.getZSpan() || straight.getZSpan() != turned.getXSpan()) {
            helper.fail("Nach der Vierteldrehung passen die Seiten nicht: "
                    + straight.getXSpan() + "x" + straight.getZSpan()
                    + " wurde zu " + turned.getXSpan() + "x" + turned.getZSpan());
            return;
        }
        // The height must not change, and neither must the volume.
        if (straight.getYSpan() != turned.getYSpan()) {
            helper.fail("Die Drehung hat die Hoehe veraendert");
            return;
        }
        helper.succeed();
    }

    private static BoundingBox boxFor(LoadedSchematic schematic, BlockPos origin, int rotation) {
        BlueprintSettings settings = new BlueprintSettings(schematic.name(), rotation, false, 0);
        return Placement.box(schematic, Placement.settings(schematic.size(), settings), origin);
    }

    /**
     * A name that could reach outside the folder must not resolve to anything.
     *
     * The unit tests already check the spelling rules; this checks that the
     * loader itself refuses such a name when it is actually asked to load it.
     */
    @GameTest(template = ROOM, timeoutTicks = 100)
    public static void refusesEscapingNames(GameTestHelper helper) throws Exception {
        ServerLevel level = helper.getLevel();

        for (String hostile : new String[] {"../secrets", "..\\windows", "/etc/passwd", "a//b"}) {
            if (SchematicLoader.loadByName(hostile, level) != null) {
                helper.fail("Der Loader hat '" + hostile + "' angenommen");
                return;
            }
        }
        helper.succeed();
    }
}
