package com.boyben.holobuild.survival;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.boyben.holobuild.schematic.LoadedSchematic;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * What it takes to build a schematic by hand.
 *
 * What each block costs is decided by {@link BlockCost}, the same rule the
 * survival build uses when it takes items out of the inventory. A list that
 * said two doors while the build took one would be worse than no list at all.
 */
public record MaterialList(List<Entry> entries, int totalItems) {

    /**
     * @param item   what to bring
     * @param needed how many
     * @param have   how many the player is carrying
     */
    public record Entry(Item item, int needed, int have) {
        public int missing() {
            return Math.max(0, needed - have);
        }

        public boolean enough() {
            return have >= needed;
        }
    }

    /** Works out the list, and checks it against what the player carries. */
    public static MaterialList of(LoadedSchematic schematic, Player player) {
        Map<Item, Integer> needed = new HashMap<>();

        for (StructureTemplate.StructureBlockInfo info : schematic.blocks()) {
            BlockState state = info.state();
            int count = BlockCost.itemsFor(state);
            if (count == 0) {
                continue;
            }
            Item item = BlockCost.itemFor(state);
            if (item == Items.AIR) {
                continue;      // water, lava, structure void: nothing to carry
            }
            needed.merge(item, count, Integer::sum);
        }

        List<Entry> entries = new ArrayList<>(needed.size());
        int total = 0;
        for (Map.Entry<Item, Integer> entry : needed.entrySet()) {
            int have = countInInventory(player, entry.getKey());
            entries.add(new Entry(entry.getKey(), entry.getValue(), have));
            total += entry.getValue();
        }

        // Missing ones first - that is the part worth acting on - then by how
        // many are needed, so the big jobs stand out.
        entries.sort(Comparator
                .comparingInt((Entry e) -> e.enough() ? 1 : 0)
                .thenComparing(Comparator.comparingInt(Entry::needed).reversed()));

        return new MaterialList(List.copyOf(entries), total);
    }

    private static int countInInventory(Player player, Item item) {
        int found = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(item)) {
                found += stack.getCount();
            }
        }
        return found;
    }

    public boolean complete() {
        return entries.stream().allMatch(Entry::enough);
    }

    public int missingTotal() {
        return entries.stream().mapToInt(Entry::missing).sum();
    }
}
