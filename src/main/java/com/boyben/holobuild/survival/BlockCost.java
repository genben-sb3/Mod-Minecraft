package com.boyben.holobuild.survival;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * What one block of a build costs in items.
 *
 * Counting blocks is not the same as counting items, and both the shopping
 * list and the survival build need the same answer - a list that says two
 * doors while the build takes one would be worse than no list at all.
 *
 * The rules read the block's own properties rather than a list of known
 * blocks, so doors and beds from other mods are handled too.
 */
public final class BlockCost {
    private BlockCost() {}

    /** @return how many items this block costs, 0 if it costs none */
    public static int itemsFor(BlockState state) {
        if (state.isAir()) {
            return 0;
        }

        // The top half of a door, a bed or a tall flower comes along with the
        // bottom one - charging for both would ask twice.
        if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
                && state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER) {
            return 0;
        }
        if (state.hasProperty(BlockStateProperties.BED_PART)
                && state.getValue(BlockStateProperties.BED_PART) == BedPart.HEAD) {
            return 0;
        }

        // A full block made of two slabs costs two slabs.
        if (state.getBlock() instanceof SlabBlock
                && state.hasProperty(BlockStateProperties.SLAB_TYPE)
                && state.getValue(BlockStateProperties.SLAB_TYPE) == SlabType.DOUBLE) {
            return 2;
        }

        return 1;
    }

    /**
     * The item to bring for this block.
     *
     * @return the item, or {@link Items#AIR} for things nobody carries -
     *         water, lava, fire, structure void
     */
    public static Item itemFor(BlockState state) {
        return state.getBlock().asItem();
    }

    /** True when this block costs nothing at all and can just be placed. */
    public static boolean free(BlockState state) {
        return itemsFor(state) == 0 || itemFor(state) == Items.AIR;
    }
}
