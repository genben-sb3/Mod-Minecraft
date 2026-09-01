package com.boyben.holobuild.schematic.format;

import java.util.Set;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/**
 * Throws away parts of a saved structure the player did not ask to keep.
 *
 * Reading the world is left to vanilla's {@code fillFromWorld}, which knows how
 * to pull a block entity apart properly. What it cannot do is leave things out,
 * so the sorting happens afterwards, on the finished tag.
 *
 * Doing it on the tag has a second benefit: none of this needs a world, a
 * server, or even a block registry - air is recognised by its name, which is
 * plain text in the palette. That makes every rule here testable in a plain
 * unit test rather than only in a running game.
 *
 * The methods change the tag they are given and return it, because a save can
 * hold half a million blocks and copying that twice to be tidy would be a poor
 * trade. They are only ever used on a tag that was just created.
 */
public final class StructureFilter {
    private StructureFilter() {}

    private static final String PALETTE = "palette";
    private static final String BLOCKS = "blocks";
    private static final String ENTITIES = "entities";
    private static final String STATE = "state";
    private static final String NAME = "Name";
    private static final String NBT = "nbt";

    /**
     * Every block that counts as empty space.
     *
     * cave_air and void_air matter more than they look: a cellar is full of
     * cave_air, so a rule that only knew plain air would still punch a hole
     * through the ground exactly where players build most.
     */
    private static final Set<String> AIR = Set.of(
            "minecraft:air", "minecraft:cave_air", "minecraft:void_air");

    /**
     * Drops all air, so a building settles into the ground instead of taking a
     * box out of it.
     *
     * The palette is rebuilt rather than merely left alone, so the remaining
     * blocks keep pointing at the right entries.
     *
     * @return how many blocks were dropped
     */
    public static int stripAir(CompoundTag structure) {
        ListTag palette = structure.getList(PALETTE, Tag.TAG_COMPOUND);
        ListTag blocks = structure.getList(BLOCKS, Tag.TAG_COMPOUND);
        if (palette.isEmpty()) {
            return 0;
        }

        // -1 marks an entry that is going away; everything else gets its new
        // index in the shortened palette.
        int[] moved = new int[palette.size()];
        ListTag keptPalette = new ListTag();
        for (int i = 0; i < palette.size(); i++) {
            if (AIR.contains(palette.getCompound(i).getString(NAME))) {
                moved[i] = -1;
            } else {
                moved[i] = keptPalette.size();
                keptPalette.add(palette.getCompound(i));
            }
        }

        ListTag keptBlocks = new ListTag();
        for (int i = 0; i < blocks.size(); i++) {
            CompoundTag entry = blocks.getCompound(i);
            int state = entry.getInt(STATE);
            // A state outside the palette should not happen, but a broken file
            // must not take the save down with it - treat it as air and drop it.
            if (state < 0 || state >= moved.length || moved[state] < 0) {
                continue;
            }
            entry.putInt(STATE, moved[state]);
            keptBlocks.add(entry);
        }

        int dropped = blocks.size() - keptBlocks.size();
        structure.put(PALETTE, keptPalette);
        structure.put(BLOCKS, keptBlocks);
        return dropped;
    }

    /**
     * Removes chest contents, sign text, shulker boxes and everything else a
     * block entity carries. The blocks themselves stay.
     *
     * @return how many blocks lost their contents
     */
    public static int stripBlockEntities(CompoundTag structure) {
        ListTag blocks = structure.getList(BLOCKS, Tag.TAG_COMPOUND);
        int stripped = 0;
        for (int i = 0; i < blocks.size(); i++) {
            CompoundTag entry = blocks.getCompound(i);
            if (entry.contains(NBT)) {
                entry.remove(NBT);
                stripped++;
            }
        }
        return stripped;
    }

    /** Removes item frames, armour stands, paintings - and any cow standing in the way. */
    public static int stripEntities(CompoundTag structure) {
        int removed = structure.getList(ENTITIES, Tag.TAG_COMPOUND).size();
        structure.put(ENTITIES, new ListTag());
        return removed;
    }

    /** @return true if the structure has no blocks left to build. */
    public static boolean isEmpty(CompoundTag structure) {
        return structure.getList(BLOCKS, Tag.TAG_COMPOUND).isEmpty();
    }
}
