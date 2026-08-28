package com.boyben.holobuild.schematic.format;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * Writes a {@link RawSchematic} out in Minecraft's own structure format.
 *
 * This is what lets foreign formats work everywhere else without changing a
 * line: once a .schem looks like a structure block file, StructureTemplate
 * loads it and the rest of HoloBuild cannot tell the difference.
 */
public final class StructureNbt {
    private StructureNbt() {}

    public static CompoundTag toCompound(RawSchematic schematic) {
        CompoundTag root = new CompoundTag();

        Vec3i size = schematic.size();
        root.put("size", intList(size.getX(), size.getY(), size.getZ()));

        // Collect the distinct states first, so each one is written once and
        // the block list can just point at it by number.
        Map<BlockState, Integer> indices = new HashMap<>();
        List<BlockState> ordered = new ArrayList<>();
        for (StructureTemplate.StructureBlockInfo info : schematic.blocks()) {
            indices.computeIfAbsent(info.state(), state -> {
                ordered.add(state);
                return ordered.size() - 1;
            });
        }

        ListTag palette = new ListTag();
        for (BlockState state : ordered) {
            palette.add(NbtUtils.writeBlockState(state));
        }
        root.put("palette", palette);

        ListTag blocks = new ListTag();
        for (StructureTemplate.StructureBlockInfo info : schematic.blocks()) {
            BlockPos pos = info.pos();
            CompoundTag entry = new CompoundTag();
            entry.put("pos", intList(pos.getX(), pos.getY(), pos.getZ()));
            entry.putInt("state", indices.get(info.state()));
            if (info.nbt() != null) {
                entry.put("nbt", info.nbt());
            }
            blocks.add(entry);
        }
        root.put("blocks", blocks);

        root.put("entities", new ListTag());
        root.putInt("DataVersion", SharedConstants.getCurrentVersion().getDataVersion().getVersion());
        return root;
    }

    private static ListTag intList(int... values) {
        ListTag list = new ListTag();
        for (int value : values) {
            list.add(IntTag.valueOf(value));
        }
        return list;
    }
}
