package com.boyben.holobuild.survival;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Takes the blocks a survival build uses out of the player's inventory.
 *
 * All or nothing per block: either the items are there and get removed, or
 * nothing is touched and the block is skipped. Half-charging for a block that
 * was never placed is the one outcome nobody would forgive.
 */
public final class InventoryDrain {
    private InventoryDrain() {}

    /**
     * Removes {@code count} of {@code item}, spread over as many stacks as needed.
     *
     * @return true if they were all there and have been removed
     */
    public static boolean take(Player player, Item item, int count) {
        if (count <= 0) {
            return true;
        }
        if (countOf(player, item) < count) {
            return false;      // check before touching anything
        }

        int left = count;
        for (ItemStack stack : player.getInventory().items) {
            if (left == 0) {
                break;
            }
            if (!stack.is(item)) {
                continue;
            }
            int taken = Math.min(left, stack.getCount());
            stack.shrink(taken);
            left -= taken;
        }

        player.getInventory().setChanged();
        return left == 0;
    }

    public static int countOf(Player player, Item item) {
        int found = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(item)) {
                found += stack.getCount();
            }
        }
        return found;
    }
}
