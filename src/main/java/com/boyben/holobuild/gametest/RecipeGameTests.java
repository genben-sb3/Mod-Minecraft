package com.boyben.holobuild.gametest;

import java.util.List;
import java.util.Optional;

import com.boyben.holobuild.HoloBuild;
import com.boyben.holobuild.registry.ModItems;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Tests that the two wands can really be crafted.
 *
 * A recipe lives in a JSON file, and a mistake in one of those is completely
 * silent: a misspelled item, a pattern that does not line up with its key, a
 * result id pointing at nothing - the recipe simply never loads, and the only
 * sign is that the crafting table does nothing. Nobody finds that by reading.
 *
 * These tests lay the ingredients out in a real 3x3 grid and ask the game what
 * would come out, which is the same question the crafting table asks.
 */
@GameTestHolder(HoloBuild.MODID)
@PrefixGameTestTemplate(false)
public class RecipeGameTests {

    private static final String ROOM = "test_platform";

    @GameTest(template = ROOM, timeoutTicks = 100)
    public static void blueprintWandCanBeCrafted(GameTestHelper helper) {
        // Amethyst top right, diamond in the middle, stick bottom left.
        check(helper, ModItems.BLUEPRINT_WAND.get(),
                Items.AIR, Items.AIR, Items.AMETHYST_SHARD,
                Items.AIR, Items.DIAMOND, Items.AIR,
                Items.STICK, Items.AIR, Items.AIR);
    }

    @GameTest(template = ROOM, timeoutTicks = 100)
    public static void selectionWandCanBeCrafted(GameTestHelper helper) {
        // The cheaper twin: iron in the middle instead of a diamond.
        check(helper, ModItems.SELECTION_WAND.get(),
                Items.AIR, Items.AIR, Items.AMETHYST_SHARD,
                Items.AIR, Items.IRON_INGOT, Items.AIR,
                Items.STICK, Items.AIR, Items.AIR);
    }

    /**
     * The wands must not be crafted by accident.
     *
     * Without this the pair above would still pass if the recipe had been
     * written so loosely that almost anything made a wand - and a recipe that
     * fires on a stray diamond in a crafting grid would be a nuisance rather
     * than a feature.
     */
    @GameTest(template = ROOM, timeoutTicks = 100)
    public static void aLooseHandfulMakesNoWand(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        // Same three ingredients, all in the top row: right items, wrong shape.
        CraftingInput input = grid(
                Items.AMETHYST_SHARD, Items.DIAMOND, Items.STICK,
                Items.AIR, Items.AIR, Items.AIR,
                Items.AIR, Items.AIR, Items.AIR);

        Optional<RecipeHolder<CraftingRecipe>> found =
                level.getServer().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level);

        if (found.isPresent()) {
            ItemStack result = found.get().value().assemble(input, level.registryAccess());
            if (result.is(ModItems.BLUEPRINT_WAND.get()) || result.is(ModItems.SELECTION_WAND.get())) {
                helper.fail("Die Zutaten in einer Reihe ergeben einen Stab - "
                        + "das Muster wird gar nicht geprueft");
                return;
            }
        }
        helper.succeed();
    }

    /** Lays the nine slots out and checks what the game would craft from them. */
    private static void check(GameTestHelper helper, Item expected, Item... slots) {
        ServerLevel level = helper.getLevel();
        CraftingInput input = grid(slots);

        Optional<RecipeHolder<CraftingRecipe>> found =
                level.getServer().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level);

        if (found.isEmpty()) {
            helper.fail("Kein Rezept passt auf dieses Muster - die JSON-Datei fuer "
                    + expected + " wurde nicht geladen oder passt nicht");
            return;
        }

        ItemStack result = found.get().value().assemble(input, level.registryAccess());
        if (!result.is(expected)) {
            helper.fail("Erwartet " + expected + ", herausgekommen ist " + result.getItem());
            return;
        }
        if (result.getCount() != 1) {
            helper.fail("Ein Stab pro Rezept, nicht " + result.getCount());
            return;
        }
        helper.succeed();
    }

    private static CraftingInput grid(Item... slots) {
        return CraftingInput.of(3, 3, List.of(
                new ItemStack(slots[0]), new ItemStack(slots[1]), new ItemStack(slots[2]),
                new ItemStack(slots[3]), new ItemStack(slots[4]), new ItemStack(slots[5]),
                new ItemStack(slots[6]), new ItemStack(slots[7]), new ItemStack(slots[8])));
    }
}
