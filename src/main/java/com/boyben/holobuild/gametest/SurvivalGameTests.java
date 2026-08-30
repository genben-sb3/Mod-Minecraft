package com.boyben.holobuild.gametest;

import java.util.HashMap;
import java.util.Map;

import com.boyben.holobuild.HoloBuild;
import com.boyben.holobuild.generator.ModulePack;
import com.boyben.holobuild.generator.StackGenerator;
import com.boyben.holobuild.schematic.LoadedSchematic;
import com.boyben.holobuild.schematic.SchematicLoader;
import com.boyben.holobuild.schematic.format.RawSchematic;
import com.boyben.holobuild.survival.BlockCost;
import com.boyben.holobuild.survival.InventoryDrain;
import com.boyben.holobuild.survival.MaterialList;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Tests for the survival side and the module generator.
 *
 * These need a running game because they deal in real block states and a real
 * inventory, but they never place anything - they check that the numbers add up
 * before any of it reaches a world.
 */
@GameTestHolder(HoloBuild.MODID)
@PrefixGameTestTemplate(false)
public class SurvivalGameTests {

    private static final String ROOM = "test_platform";

    /**
     * A door is two blocks and one item.
     *
     * Charging for both halves would make every shopping list ask for twice the
     * doors - the kind of mistake that is only obvious once someone has walked
     * to a village to buy them.
     */
    @GameTest(template = ROOM, timeoutTicks = 100)
    public static void doorCostsOneItem(GameTestHelper helper) {
        BlockState lower = Blocks.OAK_DOOR.defaultBlockState()
                .setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.LOWER);
        BlockState upper = Blocks.OAK_DOOR.defaultBlockState()
                .setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER);

        int total = BlockCost.itemsFor(lower) + BlockCost.itemsFor(upper);
        if (total != 1) {
            helper.fail("Eine Tuer sollte einen Gegenstand kosten, kostet aber " + total);
            return;
        }

        // The other way round: a double slab is one block but two slabs.
        BlockState doubleSlab = Blocks.OAK_SLAB.defaultBlockState()
                .setValue(BlockStateProperties.SLAB_TYPE, SlabType.DOUBLE);
        if (BlockCost.itemsFor(doubleSlab) != 2) {
            helper.fail("Eine Doppelstufe sollte zwei Stufen kosten, kostet aber "
                    + BlockCost.itemsFor(doubleSlab));
            return;
        }

        // And water costs nothing at all.
        if (!BlockCost.free(Blocks.WATER.defaultBlockState())) {
            helper.fail("Wasser sollte nichts kosten");
            return;
        }
        helper.succeed();
    }

    /**
     * The shopping list has to promise exactly what the build takes.
     *
     * Both go through BlockCost, and this is the test that proves they really
     * do: a list asking for two doors while the build removes one would send
     * someone off gathering the wrong amount.
     */
    @GameTest(template = ROOM, timeoutTicks = 200)
    public static void listMatchesWhatBuildingTakes(GameTestHelper helper) throws Exception {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = helper.makeMockServerPlayerInLevel();

        LoadedSchematic schematic = SchematicLoader.loadByName("cottage", level);
        if (schematic == null) {
            helper.fail("Eingebautes Bauwerk 'cottage' nicht gefunden");
            return;
        }

        // What the list promises.
        Map<Item, Integer> promised = new HashMap<>();
        for (MaterialList.Entry entry : MaterialList.of(schematic, player).entries()) {
            promised.put(entry.item(), entry.needed());
        }

        // What the build would actually take, block by block.
        Map<Item, Integer> taken = new HashMap<>();
        for (StructureTemplate.StructureBlockInfo info : schematic.blocks()) {
            BlockState state = info.state();
            if (BlockCost.free(state)) {
                continue;
            }
            taken.merge(BlockCost.itemFor(state), BlockCost.itemsFor(state), Integer::sum);
        }

        if (promised.isEmpty()) {
            helper.fail("Die Materialliste ist leer - dann prueft dieser Test nichts");
            return;
        }
        if (!promised.equals(taken)) {
            helper.fail("Liste und Verbrauch weichen ab: Liste " + promised + ", Verbrauch " + taken);
            return;
        }
        helper.succeed();
    }

    /**
     * Paying is all or nothing.
     *
     * Taking part of the cost for a block that is then not placed would quietly
     * eat someone's materials - the one outcome nobody forgives.
     */
    @GameTest(template = ROOM, timeoutTicks = 100)
    public static void payingIsAllOrNothing(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.getInventory().clearContent();
        player.getInventory().add(new ItemStack(Items.OAK_PLANKS, 5));

        // More than there is: nothing may be touched.
        if (InventoryDrain.take(player, Items.OAK_PLANKS, 8)) {
            helper.fail("Es wurden 8 Bretter abgezogen, obwohl nur 5 da waren");
            return;
        }
        int left = countOf(player, Items.OAK_PLANKS);
        if (left != 5) {
            helper.fail("Nach dem fehlgeschlagenen Abzug sind noch " + left + " statt 5 Bretter da");
            return;
        }

        // Exactly what is there: taken, and the stack ends up empty.
        if (!InventoryDrain.take(player, Items.OAK_PLANKS, 5)) {
            helper.fail("Fuenf Bretter konnten nicht abgezogen werden, obwohl fuenf da waren");
            return;
        }
        if (countOf(player, Items.OAK_PLANKS) != 0) {
            helper.fail("Nach dem Abzug sind noch Bretter uebrig");
            return;
        }
        helper.succeed();
    }

    /**
     * Stacking modules has to add up.
     *
     * Base plus floors plus roof, and the footprint stays what the pieces
     * share - if the height came out wrong the generator would be silently
     * dropping or repeating a floor.
     */
    @GameTest(template = ROOM, timeoutTicks = 200)
    public static void generatorStacksToTheRightHeight(GameTestHelper helper) throws Exception {
        ServerLevel level = helper.getLevel();

        ModulePack pack;
        try {
            pack = ModulePack.find("tower", level);
        } catch (ModulePack.MissingPieceException e) {
            helper.fail("Baukasten 'tower' unvollstaendig: " + e.getMessage());
            return;
        }

        int floors = 3;
        RandomSource random = RandomSource.create(1234);
        RawSchematic built = StackGenerator.stack(pack, floors, random);

        int expected = pack.base().size().getY()
                + floors * pack.middles().get(0).size().getY()
                + pack.top().size().getY();

        if (built.size().getY() != expected) {
            helper.fail("Turm mit " + floors + " Etagen ist " + built.size().getY()
                    + " hoch, erwartet waren " + expected);
            return;
        }
        if (built.size().getX() != pack.footprint().getX()
                || built.size().getZ() != pack.footprint().getZ()) {
            helper.fail("Die Grundflaeche stimmt nicht mehr: " + built.size());
            return;
        }
        if (built.blocks().isEmpty()) {
            helper.fail("Der gewuerfelte Turm enthaelt keine Bloecke");
            return;
        }
        helper.succeed();
    }

    private static int countOf(ServerPlayer player, Item item) {
        int found = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(item)) {
                found += stack.getCount();
            }
        }
        return found;
    }
}
