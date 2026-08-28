package com.boyben.holobuild.schematic.format;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.boyben.holobuild.HoloBuild;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * Reads .schem files - the Sponge Schematic format used by WorldEdit and most
 * schematic websites.
 *
 * Versions 1, 2 and 3 all land here. The layout is a palette mapping block
 * state text like "minecraft:oak_stairs[facing=north]" to a number, plus one
 * long byte array holding those numbers, one per block position, packed as
 * varints. Version 3 moved everything one level down into "Schematic".
 */
public final class SpongeSchematicReader {
    private SpongeSchematicReader() {}

    /** Guards against a corrupt or hostile file claiming absurd dimensions. */
    private static final long MAX_VOLUME = 20_000_000L;

    public static RawSchematic read(CompoundTag root, HolderLookup<net.minecraft.world.level.block.Block> lookup)
            throws IOException {

        // Version 3 wraps everything in a "Schematic" compound.
        CompoundTag tag = root.contains("Schematic", Tag.TAG_COMPOUND)
                ? root.getCompound("Schematic")
                : root;

        int width = tag.getShort("Width") & 0xFFFF;
        int height = tag.getShort("Height") & 0xFFFF;
        int length = tag.getShort("Length") & 0xFFFF;
        if (width == 0 || height == 0 || length == 0) {
            throw new IOException("Schematic hat keine Groesse (Width/Height/Length fehlen oder sind 0)");
        }
        long volume = (long) width * height * length;
        if (volume > MAX_VOLUME) {
            throw new IOException("Schematic ist zu gross: " + volume + " Bloecke");
        }

        // Version 3 keeps palette and data inside a "Blocks" compound.
        CompoundTag blocksTag = tag.contains("Blocks", Tag.TAG_COMPOUND) ? tag.getCompound("Blocks") : tag;
        CompoundTag paletteTag = blocksTag.getCompound("Palette");
        byte[] data = blocksTag.contains("Data", Tag.TAG_BYTE_ARRAY)
                ? blocksTag.getByteArray("Data")
                : blocksTag.getByteArray("BlockData");

        if (paletteTag.isEmpty() || data.length == 0) {
            throw new IOException("Schematic enthaelt keine Bloecke (Palette oder Data fehlt)");
        }

        BlockState[] palette = readPalette(paletteTag, lookup);
        List<StructureTemplate.StructureBlockInfo> blocks =
                readBlocks(data, palette, width, height, length);

        return new RawSchematic(new Vec3i(width, height, length), blocks);
    }

    /**
     * The palette maps text to an index, so the array is built the other way
     * round: index to block state.
     */
    private static BlockState[] readPalette(CompoundTag paletteTag,
                                            HolderLookup<net.minecraft.world.level.block.Block> lookup) {
        int max = 0;
        for (String key : paletteTag.getAllKeys()) {
            max = Math.max(max, paletteTag.getInt(key));
        }

        BlockState[] palette = new BlockState[max + 1];
        for (String key : paletteTag.getAllKeys()) {
            int index = paletteTag.getInt(key);
            palette[index] = parseState(key, lookup);
        }
        // Gaps in the numbering would leave nulls behind.
        for (int i = 0; i < palette.length; i++) {
            if (palette[i] == null) {
                palette[i] = Blocks.AIR.defaultBlockState();
            }
        }
        return palette;
    }

    /**
     * Turns "minecraft:oak_stairs[facing=north]" into a real block state.
     * Unknown blocks - from another mod, or a newer Minecraft - become air
     * rather than sinking the whole file.
     */
    private static BlockState parseState(String text,
                                         HolderLookup<net.minecraft.world.level.block.Block> lookup) {
        try {
            return BlockStateParser.parseForBlock(lookup, text, false).blockState();
        } catch (CommandSyntaxException e) {
            HoloBuild.LOGGER.warn("Unbekannter Block in .schem uebersprungen: {}", text);
            return Blocks.AIR.defaultBlockState();
        }
    }

    /**
     * Walks the packed data. Positions run x fastest, then z, then y - so the
     * n-th entry sits at x = n % width, z = (n / width) % length, y = n / (width * length).
     */
    private static List<StructureTemplate.StructureBlockInfo> readBlocks(
            byte[] data, BlockState[] palette, int width, int height, int length) throws IOException {

        List<StructureTemplate.StructureBlockInfo> blocks = new ArrayList<>();
        VarIntStream stream = new VarIntStream(data);
        int position = 0;
        int total = width * height * length;

        while (stream.hasNext() && position < total) {
            int value = stream.next();

            if (value >= 0 && value < palette.length) {
                BlockState state = palette[value];
                if (!state.isAir()) {
                    int x = position % width;
                    int z = (position / width) % length;
                    int y = position / (width * length);
                    blocks.add(new StructureTemplate.StructureBlockInfo(
                            new BlockPos(x, y, z), state, null));
                }
            }
            position++;
        }

        return blocks;
    }
}
