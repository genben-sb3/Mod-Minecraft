package com.boyben.holobuild.gametest;

import java.util.List;

import com.boyben.holobuild.HoloBuild;
import com.boyben.holobuild.item.SiteWandItem;
import com.boyben.holobuild.undo.UndoManager;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Tests for the Site Wand, which empties the space a building will go into.
 *
 * This is the only tool in HoloBuild that destroys without building anything
 * back, so the test that matters is not that it clears - that part is obvious
 * the moment you use it - but that undo brings the ground back. A clearing that
 * cannot be taken back would cost somebody their landscape.
 */
@GameTestHolder(HoloBuild.MODID)
@PrefixGameTestTemplate(false)
public class SiteWandGameTests {

    private static final String ROOM = "test_platform";

    /** Long enough for a handful of blocks to be cleared and settled. */
    private static final int TICKS_TO_FINISH = 40;

    @GameTest(template = ROOM, timeoutTicks = 400)
    public static void clearingEmptiesTheSpaceAndUndoBringsItBack(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = helper.makeMockServerPlayerInLevel();

        BlockPos corner = helper.absolutePos(new BlockPos(1, 1, 1));
        List<BlockPos> filled = List.of(
                corner, corner.east(), corner.above(), corner.above().east());
        for (BlockPos pos : filled) {
            level.setBlock(pos, Blocks.GOLD_BLOCK.defaultBlockState(), Block.UPDATE_CLIENTS);
        }

        BoundingBox box = BoundingBox.fromCorners(corner, corner.offset(1, 1, 0));
        SiteWandItem.clearSpace(player, level, box, "gametest_site");

        helper.runAfterDelay(TICKS_TO_FINISH, () -> {
            for (BlockPos pos : filled) {
                if (!level.getBlockState(pos).isAir()) {
                    helper.fail("Bei " + pos + " steht noch "
                            + level.getBlockState(pos).getBlock() + " - nicht freigeraeumt");
                    return;
                }
            }

            // The half that actually matters: getting the ground back.
            int restored = UndoManager.undo(player, level);
            if (restored <= 0) {
                helper.fail("Rueckgaengig hat nichts zurueckgeholt (" + restored + ")");
                return;
            }
            for (BlockPos pos : filled) {
                if (!level.getBlockState(pos).is(Blocks.GOLD_BLOCK)) {
                    helper.fail("Bei " + pos + " fehlt der Block nach dem Rueckgaengigmachen");
                    return;
                }
            }
            helper.succeed();
        });
    }

    /**
     * Clearing empty air must not pretend it did something.
     *
     * Without this, a wand that recorded an undo step for every click would fill
     * the undo history with nothing, and the real clearing five clicks ago would
     * drop off the end of it.
     */
    @GameTest(template = ROOM, timeoutTicks = 100)
    public static void clearingNothingDoesNothing(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = helper.makeMockServerPlayerInLevel();

        BlockPos corner = helper.absolutePos(new BlockPos(1, 3, 1));
        for (BlockPos pos : BlockPos.betweenClosed(corner, corner.offset(1, 1, 0))) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        }

        BoundingBox box = BoundingBox.fromCorners(corner, corner.offset(1, 1, 0));
        SiteWandItem.clearSpace(player, level, box, "gametest_site_empty");

        // Nothing was there, so there is nothing to take back either.
        if (UndoManager.undo(player, level) > 0) {
            helper.fail("Ein leerer Platz hat trotzdem einen Rueckgaengig-Schritt angelegt");
            return;
        }
        helper.succeed();
    }
}
