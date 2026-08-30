package com.boyben.holobuild.schematic.format;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * Reads .litematic files, the format Litematica itself writes.
 *
 * Trickier than .schem in three ways:
 * <ul>
 *   <li>block indices are bit-packed into a long array, and an index may
 *       straddle the boundary between two longs</li>
 *   <li>a file can hold several regions, each with its own palette</li>
 *   <li>a region size may be negative, meaning it extends the other way</li>
 * </ul>
 */
public final class LitematicaReader {
    private LitematicaReader() {}

    /** Guards against a corrupt or hostile file claiming absurd dimensions. */
    private static final long MAX_VOLUME = 20_000_000L;

    public static RawSchematic read(CompoundTag root, HolderGetter<Block> lookup) throws IOException {
        CompoundTag regions = root.getCompound("Regions");
        if (regions.isEmpty()) {
            throw new IOException("Litematic enthaelt keine Regionen");
        }

        List<StructureTemplate.StructureBlockInfo> all = new ArrayList<>();

        // The size comes from what the regions declare, not from where blocks
        // happen to sit. Trimming to the occupied blocks would shrink a
        // schematic with air around its edges - and since rotation turns around
        // size/2, a trimmed size means it spins around the wrong point. It would
        // also disagree with the .schem reader, which uses the declared size.
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;

        for (String name : regions.getAllKeys()) {
            CompoundTag region = regions.getCompound(name);
            BoundingBox bounds = regionBounds(region);
            if (bounds != null) {
                minX = Math.min(minX, bounds.minX());
                minY = Math.min(minY, bounds.minY());
                minZ = Math.min(minZ, bounds.minZ());
                maxX = Math.max(maxX, bounds.maxX());
                maxY = Math.max(maxY, bounds.maxY());
                maxZ = Math.max(maxZ, bounds.maxZ());
            }
            all.addAll(readRegion(region, lookup, name));
        }

        if (all.isEmpty()) {
            throw new IOException("Litematic enthaelt keine Bloecke");
        }
        if (minX > maxX) {
            throw new IOException("Litematic hat keine brauchbaren Regionsgrenzen");
        }

        // Regions sit at arbitrary coordinates; shift everything so the lowest
        // corner becomes 0,0,0 like a normal structure.
        List<StructureTemplate.StructureBlockInfo> shifted = new ArrayList<>(all.size());
        for (StructureTemplate.StructureBlockInfo info : all) {
            BlockPos pos = info.pos();
            shifted.add(new StructureTemplate.StructureBlockInfo(
                    new BlockPos(pos.getX() - minX, pos.getY() - minY, pos.getZ() - minZ),
                    info.state(), info.nbt()));
        }

        Vec3i size = new Vec3i(maxX - minX + 1, maxY - minY + 1, maxZ - minZ + 1);
        return new RawSchematic(size, shifted);
    }

    private static List<StructureTemplate.StructureBlockInfo> readRegion(
            CompoundTag region, HolderGetter<Block> lookup, String regionName) throws IOException {

        Vec3i position = readVec(region.getCompound("Position"));
        Vec3i rawSize = readVec(region.getCompound("Size"));

        int width = Math.abs(rawSize.getX());
        int height = Math.abs(rawSize.getY());
        int length = Math.abs(rawSize.getZ());
        if (width == 0 || height == 0 || length == 0) {
            return List.of();
        }
        long volume = (long) width * height * length;
        if (volume > MAX_VOLUME) {
            throw new IOException("Region '" + regionName + "' ist zu gross: " + volume + " Bloecke");
        }

        // A negative size means the region grows towards smaller coordinates,
        // so the stated position is the far corner, not the near one.
        int originX = rawSize.getX() < 0 ? position.getX() + rawSize.getX() + 1 : position.getX();
        int originY = rawSize.getY() < 0 ? position.getY() + rawSize.getY() + 1 : position.getY();
        int originZ = rawSize.getZ() < 0 ? position.getZ() + rawSize.getZ() + 1 : position.getZ();

        ListTag paletteTag = region.getList("BlockStatePalette", Tag.TAG_COMPOUND);
        if (paletteTag.isEmpty()) {
            return List.of();
        }
        BlockState[] palette = new BlockState[paletteTag.size()];
        for (int i = 0; i < paletteTag.size(); i++) {
            // Litematica writes {Name, Properties}, the same shape vanilla uses.
            palette[i] = NbtUtils.readBlockState(lookup, paletteTag.getCompound(i));
        }

        long[] packed = region.getLongArray("BlockStates");
        if (packed.length == 0) {
            return List.of();
        }

        Map<BlockPos, CompoundTag> blockEntities = readBlockEntities(region);

        int bits = BitArray.bitsPerEntry(palette.length);

        List<StructureTemplate.StructureBlockInfo> blocks = new ArrayList<>();
        int index = 0;
        for (int y = 0; y < height; y++) {
            for (int z = 0; z < length; z++) {
                for (int x = 0; x < width; x++) {
                    int paletteIndex = BitArray.get(packed, index++, bits);
                    if (paletteIndex < 0 || paletteIndex >= palette.length) {
                        continue;
                    }
                    BlockState state = palette[paletteIndex];
                    if (state.isAir()) {
                        continue;
                    }
                    BlockPos local = new BlockPos(x, y, z);
                    blocks.add(new StructureTemplate.StructureBlockInfo(
                            new BlockPos(originX + x, originY + y, originZ + z),
                            state,
                            blockEntities.get(local)));
                }
            }
        }
        return blocks;
    }

    /**
     * The area a region claims, from its stated position and size.
     *
     * A negative size means it grows towards smaller coordinates, so the stated
     * position is the far corner - the same rule {@link #readRegion} follows
     * when placing the blocks.
     *
     * @return the box, or null if the region has no extent at all
     */
    private static BoundingBox regionBounds(CompoundTag region) {
        Vec3i position = readVec(region.getCompound("Position"));
        Vec3i size = readVec(region.getCompound("Size"));

        if (size.getX() == 0 || size.getY() == 0 || size.getZ() == 0) {
            return null;
        }

        int minX = size.getX() < 0 ? position.getX() + size.getX() + 1 : position.getX();
        int minY = size.getY() < 0 ? position.getY() + size.getY() + 1 : position.getY();
        int minZ = size.getZ() < 0 ? position.getZ() + size.getZ() + 1 : position.getZ();

        return new BoundingBox(minX, minY, minZ,
                minX + Math.abs(size.getX()) - 1,
                minY + Math.abs(size.getY()) - 1,
                minZ + Math.abs(size.getZ()) - 1);
    }

    /** Chest contents and the like, keyed by their position inside the region. */
    private static Map<BlockPos, CompoundTag> readBlockEntities(CompoundTag region) {
        Map<BlockPos, CompoundTag> result = new HashMap<>();
        ListTag list = region.getList("TileEntities", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            result.put(new BlockPos(entry.getInt("x"), entry.getInt("y"), entry.getInt("z")), entry);
        }
        return result;
    }

    private static Vec3i readVec(CompoundTag tag) {
        return new Vec3i(tag.getInt("x"), tag.getInt("y"), tag.getInt("z"));
    }
}
