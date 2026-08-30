package com.boyben.holobuild.gametest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.boyben.holobuild.HoloBuild;
import com.boyben.holobuild.build.BuildAnimator;
import com.boyben.holobuild.component.BlueprintSettings;
import com.boyben.holobuild.schematic.LoadedSchematic;
import com.boyben.holobuild.schematic.Placement;
import com.boyben.holobuild.schematic.SchematicCache;
import com.boyben.holobuild.schematic.SchematicLoader;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Tests for the animated build and for saving a selection back to disk.
 *
 * Both take more than one tick, which is why they belong here rather than in a
 * unit test: the animator hands out its blocks a few per tick and only settles
 * them at the end.
 */
@GameTestHolder(HoloBuild.MODID)
@PrefixGameTestTemplate(false)
public class AnimatorGameTests {

    private static final String ROOM = "test_platform";

    /**
     * The animated build has to finish, and finish completely.
     *
     * It spreads the work over ticks, defers anything with nothing to stand on,
     * and only afterwards lets the blocks settle. Plenty of places to lose a
     * block quietly, which is exactly why this waits for every single one.
     */
    @GameTest(template = ROOM, timeoutTicks = 400)
    public static void animatedBuildPlacesEveryBlock(GameTestHelper helper) throws Exception {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = helper.makeMockServerPlayerInLevel();

        LoadedSchematic schematic = SchematicLoader.loadByName("cottage", level);
        if (schematic == null) {
            helper.fail("Eingebautes Bauwerk 'cottage' nicht gefunden");
            return;
        }

        BlockPos origin = helper.absolutePos(new BlockPos(1, 1, 1));
        BlueprintSettings settings = new BlueprintSettings("cottage", 0, false, 0);
        StructurePlaceSettings place = Placement.settings(schematic.size(), settings);
        List<StructureTemplate.StructureBlockInfo> blocks =
                Placement.transformed(schematic, place, origin);

        BuildAnimator.start(player, level, "cottage", blocks, false);

        // Runs every tick until nothing is missing any more.
        helper.succeedWhen(() -> {
            for (StructureTemplate.StructureBlockInfo info : blocks) {
                if (info.state().isAir()) {
                    continue;
                }
                if (!level.getBlockState(info.pos()).is(info.state().getBlock())) {
                    throw new GameTestAssertException("Es fehlt noch " + info.state().getBlock()
                            + " bei " + info.pos());
                }
            }
        });
    }

    /**
     * A door has to end up whole, even when the blocks arrive in a bad order.
     *
     * Note what this does and does not show. Turning off {@link
     * com.boyben.holobuild.build.PlacementOrder} entirely leaves this test
     * green, because two separate safeguards protect doors: the sorting, and
     * the check in the animator that puts a block aside when it says it cannot
     * stand yet. The second one alone is enough here.
     *
     * So this is a test of the outcome, not of one mechanism - it says the door
     * arrives whole, without claiming which safeguard delivered it. That is
     * still worth having: if both were broken, this is what would notice.
     */
    @GameTest(template = ROOM, timeoutTicks = 400)
    public static void animatedBuildKeepsDoorsWhole(GameTestHelper helper) throws Exception {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = helper.makeMockServerPlayerInLevel();

        BlockPos base = helper.absolutePos(new BlockPos(2, 1, 2));
        BlockState lower = Blocks.OAK_DOOR.defaultBlockState()
                .setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.LOWER);
        BlockState upper = Blocks.OAK_DOOR.defaultBlockState()
                .setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER);

        // Deliberately upper first: if the order were simply taken as given,
        // the top half would be placed with nothing under it and vanish.
        List<StructureTemplate.StructureBlockInfo> blocks = List.of(
                new StructureTemplate.StructureBlockInfo(base.above(), upper, null),
                new StructureTemplate.StructureBlockInfo(base, lower, null),
                new StructureTemplate.StructureBlockInfo(base.below(), Blocks.STONE.defaultBlockState(), null));

        BuildAnimator.start(player, level, "door test", blocks, false);

        helper.succeedWhen(() -> {
            BlockState atBase = level.getBlockState(base);
            BlockState atTop = level.getBlockState(base.above());
            if (!atBase.is(Blocks.OAK_DOOR)) {
                throw new GameTestAssertException("Unten steht keine Tuer, sondern " + atBase.getBlock());
            }
            if (!atTop.is(Blocks.OAK_DOOR)) {
                throw new GameTestAssertException("Die obere Tuerhaelfte fehlt");
            }
        });
    }

    /**
     * Saving a piece of world and reading it back has to give the same blocks.
     *
     * This is the path behind /holobuild save, and the promise that a saved
     * building is really the one that was standing there.
     */
    @GameTest(template = ROOM, timeoutTicks = 300)
    public static void saveAndReloadMatches(GameTestHelper helper) throws Exception {
        ServerLevel level = helper.getLevel();
        Path file = SchematicLoader.getSchematicFolder().resolve("gametest_roundtrip.nbt");

        try {
            // A small pattern with three different blocks, so a mix-up shows.
            BlockPos corner = helper.absolutePos(new BlockPos(1, 1, 1));
            level.setBlock(corner, Blocks.GOLD_BLOCK.defaultBlockState(), Block.UPDATE_CLIENTS);
            level.setBlock(corner.east(), Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
            level.setBlock(corner.above(), Blocks.OAK_PLANKS.defaultBlockState(), Block.UPDATE_CLIENTS);

            StructureTemplate saved = new StructureTemplate();
            saved.fillFromWorld(level, corner, new Vec3i(2, 2, 2), false, null);

            Files.createDirectories(file.getParent());
            CompoundTag tag = saved.save(new CompoundTag());
            NbtIo.writeCompressed(tag, file);

            SchematicCache.clear();
            LoadedSchematic reloaded = SchematicLoader.loadByName("gametest_roundtrip", level);
            if (reloaded == null) {
                helper.fail("Die gespeicherte Datei liess sich nicht wieder laden");
                return;
            }

            // The three blocks have to come back at the same relative spots.
            expect(helper, reloaded, new BlockPos(0, 0, 0), Blocks.GOLD_BLOCK);
            expect(helper, reloaded, new BlockPos(1, 0, 0), Blocks.STONE);
            expect(helper, reloaded, new BlockPos(0, 1, 0), Blocks.OAK_PLANKS);
            helper.succeed();

        } finally {
            Files.deleteIfExists(file);
            SchematicCache.clear();
        }
    }

    private static void expect(GameTestHelper helper, LoadedSchematic schematic,
                               BlockPos relative, Block expected) {
        for (StructureTemplate.StructureBlockInfo info : schematic.blocks()) {
            if (info.pos().equals(relative)) {
                if (!info.state().is(expected)) {
                    helper.fail("Bei " + relative + " steht " + info.state().getBlock()
                            + " statt " + expected);
                }
                return;
            }
        }
        helper.fail("Bei " + relative + " fehlt " + expected + " nach dem Wiederladen");
    }
}
