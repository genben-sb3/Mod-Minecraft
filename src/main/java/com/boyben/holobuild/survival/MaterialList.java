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
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * What it takes to build a schematic by hand.
 *
 * Counting blocks is not the same as counting items, and the difference is
 * where a shopping list becomes useful or misleading:
 * <ul>
 *   <li>A door is two blocks but one item, so the upper half is not counted.
 *       Same for beds and tall flowers.</li>
 *   <li>A double slab is one block but two items.</li>
 *   <li>Water, lava and air need no item at all.</li>
 * </ul>
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
            int count = itemsFor(state);
            if (count == 0) {
                continue;
            }
            Item item = state.getBlock().asItem();
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

    /** @return how many items this one block costs, 0 if it costs none */
    private static int itemsFor(BlockState state) {
        if (state.isAir()) {
            return 0;
        }

        // The top half of a door, a bed or a tall flower comes along with the
        // bottom one - counting both would ask for twice as many.
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
