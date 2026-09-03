package com.boyben.holobuild.gametest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.boyben.holobuild.HoloBuild;
import com.boyben.holobuild.edit.EditIo;
import com.boyben.holobuild.edit.SchematicEdit;
import com.boyben.holobuild.network.EditSchematicPayload;
import com.boyben.holobuild.schematic.LoadedSchematic;
import com.boyben.holobuild.schematic.SchematicCache;
import com.boyben.holobuild.schematic.SchematicLoader;
import com.boyben.holobuild.schematic.format.RawSchematic;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Tests for editing a schematic: taking blocks out and swapping them.
 *
 * Everything here fails quietly if it fails. A copy that lost the wrong blocks,
 * or kept the contents of a chest that is no longer a chest, still loads and
 * still builds - it only looks wrong much later, by which time the original may
 * be gone. That is why the model is tested before any editor is drawn on top
 * of it.
 */
@GameTestHolder(HoloBuild.MODID)
@PrefixGameTestTemplate(false)
public class EditGameTests {

    private static final String ROOM = "test_platform";

    /**
     * Builds a schematic by hand: a chest with contents, and two stones.
     *
     * The template only supplies a size; every block is given here, so the test
     * knows exactly what should come out the other end.
     */
    private static LoadedSchematic handmade(ServerLevel level, GameTestHelper helper) {
        StructureTemplate template = new StructureTemplate();
        template.fillFromWorld(level, helper.absolutePos(new BlockPos(0, 0, 0)),
                new Vec3i(3, 1, 1), false, null);

        CompoundTag chestData = new CompoundTag();
        chestData.putString("id", "minecraft:chest");

        // Deliberately a list that CAN be changed. With an immutable one the
        // test below could never catch a missing copy - the edit would throw
        // instead, which passes for a failure but proves something else.
        List<StructureTemplate.StructureBlockInfo> blocks = new ArrayList<>(List.of(
                new StructureTemplate.StructureBlockInfo(
                        new BlockPos(0, 0, 0), Blocks.CHEST.defaultBlockState(), chestData),
                new StructureTemplate.StructureBlockInfo(
                        new BlockPos(1, 0, 0), Blocks.STONE.defaultBlockState(), null),
                new StructureTemplate.StructureBlockInfo(
                        new BlockPos(2, 0, 0), Blocks.STONE.defaultBlockState(), null)));

        return new LoadedSchematic("gametest_handmade", template, blocks);
    }

    @GameTest(template = ROOM, timeoutTicks = 100)
    public static void eraseTakesOutOnlyThatKind(GameTestHelper helper) {
        SchematicEdit edit = SchematicEdit.of(handmade(helper.getLevel(), helper));

        int removed = edit.erase(Blocks.STONE);

        if (removed != 2) {
            helper.fail("Zwei Steine sollten weg sein, entfernt wurden " + removed);
            return;
        }
        if (edit.blockCount() != 1 || edit.count(Blocks.CHEST) != 1) {
            helper.fail("Die Kiste haette bleiben muessen, uebrig sind "
                    + edit.blockCount() + " Bloecke");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = ROOM, timeoutTicks = 100)
    public static void replaceKeepsEveryBlockInPlace(GameTestHelper helper) {
        SchematicEdit edit = SchematicEdit.of(handmade(helper.getLevel(), helper));
        int before = edit.blockCount();

        int changed = edit.replace(Blocks.STONE, Blocks.OAK_PLANKS.defaultBlockState());

        if (changed != 2) {
            helper.fail("Zwei Steine sollten getauscht werden, getauscht wurden " + changed);
            return;
        }
        if (edit.blockCount() != before) {
            helper.fail("Beim Tauschen darf sich die Anzahl nicht aendern: "
                    + before + " vorher, " + edit.blockCount() + " nachher");
            return;
        }
        if (edit.count(Blocks.STONE) != 0 || edit.count(Blocks.OAK_PLANKS) != 2) {
            helper.fail("Nach dem Tausch: " + edit.count(Blocks.STONE) + " Stein, "
                    + edit.count(Blocks.OAK_PLANKS) + " Bretter");
            return;
        }
        helper.succeed();
    }

    /**
     * A chest turned into stone must not keep its contents.
     *
     * Nothing would complain: the file saves, loads and builds. The items would
     * simply sit inside a block that cannot hold any, waiting to turn up as a
     * mystery years later.
     */
    @GameTest(template = ROOM, timeoutTicks = 100)
    public static void replaceDropsWhatTheOldBlockCarried(GameTestHelper helper) {
        SchematicEdit edit = SchematicEdit.of(handmade(helper.getLevel(), helper));

        edit.replace(Blocks.CHEST, Blocks.STONE.defaultBlockState());

        for (StructureTemplate.StructureBlockInfo info : edit.toRaw().blocks()) {
            if (info.pos().equals(new BlockPos(0, 0, 0)) && info.nbt() != null) {
                helper.fail("Der ehemalige Kistenplatz traegt immer noch Daten mit sich");
                return;
            }
        }
        helper.succeed();
    }

    /**
     * The size must survive every edit untouched.
     *
     * Rotation pivots on half the size. A copy that shrank to fit its remaining
     * blocks would turn around a different point than the original - which is
     * exactly the bug the .litematic reader had, found by a test rather than by
     * anyone looking at it.
     */
    @GameTest(template = ROOM, timeoutTicks = 100)
    public static void editingLeavesTheSizeAlone(GameTestHelper helper) {
        LoadedSchematic original = handmade(helper.getLevel(), helper);
        Vec3i before = original.size();

        SchematicEdit edit = SchematicEdit.of(original);
        edit.erase(Blocks.STONE);
        RawSchematic result = edit.toRaw();

        if (!result.size().equals(before)) {
            helper.fail("Die Groesse hat sich beim Bearbeiten geaendert: "
                    + before + " zu " + result.size());
            return;
        }
        helper.succeed();
    }

    @GameTest(template = ROOM, timeoutTicks = 100)
    public static void removeAtTakesOnlyTheNamedSpots(GameTestHelper helper) {
        SchematicEdit edit = SchematicEdit.of(handmade(helper.getLevel(), helper));

        int gone = edit.removeAt(Set.of(new BlockPos(1, 0, 0)));

        if (gone != 1 || edit.blockCount() != 2) {
            helper.fail("Genau ein Block sollte weg sein, entfernt " + gone
                    + ", uebrig " + edit.blockCount());
            return;
        }
        if (edit.count(Blocks.CHEST) != 1 || edit.count(Blocks.STONE) != 1) {
            helper.fail("Es ist der falsche Block gegangen");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = ROOM, timeoutTicks = 100)
    public static void editRefusesWhenItHitsNothing(GameTestHelper helper) {
        SchematicEdit edit = SchematicEdit.of(handmade(helper.getLevel(), helper));

        // A spot with no block in it - a client sending stale positions, or
        // simply clicking a place that was already empty.
        int result = EditSchematicPayload.apply(edit, Set.of(new BlockPos(9, 9, 9)));

        if (result != EditSchematicPayload.NOTHING_MATCHED) {
            helper.fail("Ein Treffer ins Leere muss abgelehnt werden, kam zurueck: " + result);
            return;
        }
        helper.succeed();
    }

    @GameTest(template = ROOM, timeoutTicks = 100)
    public static void editRefusesToEmptyTheBuilding(GameTestHelper helper) {
        SchematicEdit edit = SchematicEdit.of(handmade(helper.getLevel(), helper));

        int result = EditSchematicPayload.apply(edit, Set.of(
                new BlockPos(0, 0, 0), new BlockPos(1, 0, 0), new BlockPos(2, 0, 0)));

        if (result != EditSchematicPayload.WOULD_BE_EMPTY) {
            helper.fail("Ein Bauwerk ohne Bloecke muss abgelehnt werden, kam zurueck: " + result);
            return;
        }
        helper.succeed();
    }

    /**
     * A name off the network must not reach a file outside the folder.
     *
     * The editor is opened by a message from a client, and the source name in
     * that message becomes a file path. A real, loadable schematic is placed
     * one level above the folder first - without it the test would pass for the
     * boring reason that no such file exists.
     *
     * <p>What this proves is the outcome, not any one check. Two things stand in
     * the way - the name rule in EditIo and the path rule in findByName - and
     * taking either one out on its own leaves this test green, because the other
     * still holds. That was measured, not assumed: with both removed it fails.
     * Which is the whole point of having two.
     */
    @GameTest(template = ROOM, timeoutTicks = 100)
    public static void editingCannotReachOutsideTheFolder(GameTestHelper helper) throws Exception {
        ServerLevel level = helper.getLevel();
        Path outside = SchematicLoader.getSchematicFolder().getParent()
                .resolve("gametest_edit_bait.nbt");

        try {
            Files.createDirectories(outside.getParent());
            StructureTemplate bait = new StructureTemplate();
            bait.fillFromWorld(level, helper.absolutePos(new BlockPos(0, 0, 0)),
                    new Vec3i(2, 2, 2), false, null);
            NbtIo.writeCompressed(bait.save(new CompoundTag()), outside);
            SchematicCache.clear();

            if (EditIo.open("../gametest_edit_bait", level) != null) {
                helper.fail("Der Editor hat eine Datei ausserhalb des Ordners geoeffnet");
                return;
            }
            helper.succeed();

        } finally {
            Files.deleteIfExists(outside);
            SchematicCache.clear();
        }
    }

    /** Editing a copy must not disturb the schematic everyone else is sharing. */
    @GameTest(template = ROOM, timeoutTicks = 100)
    public static void editingDoesNotTouchTheOriginal(GameTestHelper helper) {
        LoadedSchematic original = handmade(helper.getLevel(), helper);
        int before = original.blocks().size();

        SchematicEdit.of(original).erase(Blocks.STONE);

        if (original.blocks().size() != before) {
            helper.fail("Das Original hat Bloecke verloren: " + before
                    + " vorher, " + original.blocks().size() + " nachher");
            return;
        }
        helper.succeed();
    }
}
