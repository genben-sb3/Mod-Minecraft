package com.boyben.holobuild.schematic.format;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/**
 * Tests for the save filters.
 *
 * These run without a world, a server or a block registry, because the filters
 * work on the finished tag and recognise air by the plain text in the palette.
 * That is the whole reason the filtering was put after the read instead of
 * inside it - this file is what that decision bought.
 */
class StructureFilterTest {

    /** Builds a structure tag by hand, the way fillFromWorld would leave it. */
    private static CompoundTag structure(String... paletteNames) {
        CompoundTag root = new CompoundTag();
        ListTag palette = new ListTag();
        for (String name : paletteNames) {
            CompoundTag entry = new CompoundTag();
            entry.putString("Name", name);
            palette.add(entry);
        }
        root.put("palette", palette);
        root.put("blocks", new ListTag());
        root.put("entities", new ListTag());
        return root;
    }

    /** Adds one block pointing at a palette entry. */
    private static void block(CompoundTag structure, int x, int y, int z, int state) {
        CompoundTag entry = new CompoundTag();
        ListTag pos = new ListTag();
        pos.add(IntTag.valueOf(x));
        pos.add(IntTag.valueOf(y));
        pos.add(IntTag.valueOf(z));
        entry.put("pos", pos);
        entry.putInt("state", state);
        structure.getList("blocks", Tag.TAG_COMPOUND).add(entry);
    }

    private static ListTag blocksOf(CompoundTag structure) {
        return structure.getList("blocks", Tag.TAG_COMPOUND);
    }

    private static ListTag paletteOf(CompoundTag structure) {
        return structure.getList("palette", Tag.TAG_COMPOUND);
    }

    @Test
    @DisplayName("Air goes, stone stays, and the survivors still point at the right block")
    void stripAirKeepsIndicesCorrect() {
        // Palette on purpose with air in the MIDDLE: if the remaining entries
        // were not renumbered, index 2 would afterwards point at the wrong
        // block - and the test would catch it, because dirt would turn to stone.
        CompoundTag structure = structure("minecraft:stone", "minecraft:air", "minecraft:dirt");
        block(structure, 0, 0, 0, 0);   // stone
        block(structure, 1, 0, 0, 1);   // air  - goes
        block(structure, 2, 0, 0, 2);   // dirt
        block(structure, 3, 0, 0, 1);   // air  - goes

        int dropped = StructureFilter.stripAir(structure);

        assertEquals(2, dropped, "Two air blocks should have been dropped");
        assertEquals(2, blocksOf(structure).size(), "Only stone and dirt should be left");
        assertEquals(2, paletteOf(structure).size(), "Air should be gone from the palette too");

        // The heart of it: dirt must still be dirt.
        int dirtState = blocksOf(structure).getCompound(1).getInt("state");
        assertEquals("minecraft:dirt",
                paletteOf(structure).getCompound(dirtState).getString("Name"),
                "After renumbering, the second block must still be dirt");

        int stoneState = blocksOf(structure).getCompound(0).getInt("state");
        assertEquals("minecraft:stone",
                paletteOf(structure).getCompound(stoneState).getString("Name"));
    }

    @Test
    @DisplayName("Cave air and void air count as air as well")
    void stripAirCatchesAllThreeKinds() {
        // This is not pedantry: a cellar is full of cave_air, so a rule that
        // only knew plain air would still cut a hole exactly where people build.
        CompoundTag structure = structure(
                "minecraft:air", "minecraft:cave_air", "minecraft:void_air", "minecraft:stone");
        block(structure, 0, 0, 0, 0);
        block(structure, 1, 0, 0, 1);
        block(structure, 2, 0, 0, 2);
        block(structure, 3, 0, 0, 3);

        assertEquals(3, StructureFilter.stripAir(structure));
        assertEquals(1, blocksOf(structure).size());
        assertEquals("minecraft:stone", paletteOf(structure).getCompound(0).getString("Name"));
    }

    @Test
    @DisplayName("A selection of nothing but air ends up empty, not half-broken")
    void stripAirCanEmptyTheWholeThing() {
        CompoundTag structure = structure("minecraft:air");
        block(structure, 0, 0, 0, 0);
        block(structure, 0, 1, 0, 0);

        assertEquals(2, StructureFilter.stripAir(structure));
        assertTrue(StructureFilter.isEmpty(structure),
                "Nothing but air must be recognisable as empty, so saving can refuse");
    }

    @Test
    @DisplayName("A block pointing outside the palette is dropped instead of crashing")
    void stripAirSurvivesABrokenFile() {
        CompoundTag structure = structure("minecraft:stone");
        block(structure, 0, 0, 0, 0);
        block(structure, 1, 0, 0, 7);   // nonsense - no such palette entry

        StructureFilter.stripAir(structure);

        assertEquals(1, blocksOf(structure).size(),
                "The good block stays, the impossible one goes");
    }

    @Test
    @DisplayName("Removing contents empties the chests but leaves the chests")
    void stripBlockEntitiesKeepsTheBlocks() {
        CompoundTag structure = structure("minecraft:chest", "minecraft:stone");
        block(structure, 0, 0, 0, 0);
        block(structure, 1, 0, 0, 1);

        CompoundTag contents = new CompoundTag();
        contents.putString("id", "minecraft:chest");
        blocksOf(structure).getCompound(0).put("nbt", contents);

        assertEquals(1, StructureFilter.stripBlockEntities(structure));
        assertEquals(2, blocksOf(structure).size(), "Both blocks must still be there");
        assertFalse(blocksOf(structure).getCompound(0).contains("nbt"),
                "The chest should have lost its contents");
    }

    @Test
    @DisplayName("Keeping contents means the tag is left exactly as it was")
    void stripBlockEntitiesDoesNothingWhenThereIsNothingToStrip() {
        CompoundTag structure = structure("minecraft:stone");
        block(structure, 0, 0, 0, 0);

        assertEquals(0, StructureFilter.stripBlockEntities(structure));
        assertFalse(blocksOf(structure).getCompound(0).contains("nbt"));
    }

    @Test
    @DisplayName("Entities are cleared out")
    void stripEntitiesEmptiesTheList() {
        CompoundTag structure = structure("minecraft:stone");
        ListTag entities = structure.getList("entities", Tag.TAG_COMPOUND);
        entities.add(new CompoundTag());
        entities.add(new CompoundTag());

        assertEquals(2, StructureFilter.stripEntities(structure));
        assertTrue(structure.getList("entities", Tag.TAG_COMPOUND).isEmpty());
    }

    @Test
    @DisplayName("A structure with blocks is not empty")
    void isEmptyOnlyWhenThereAreNoBlocks() {
        CompoundTag structure = structure("minecraft:stone");
        assertTrue(StructureFilter.isEmpty(structure));
        block(structure, 0, 0, 0, 0);
        assertFalse(StructureFilter.isEmpty(structure));
    }
}
