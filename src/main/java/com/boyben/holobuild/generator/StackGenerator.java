package com.boyben.holobuild.generator;

import java.util.ArrayList;
import java.util.List;

import com.boyben.holobuild.schematic.LoadedSchematic;
import com.boyben.holobuild.schematic.format.RawSchematic;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * Builds a new tower by stacking pieces from a {@link ModulePack}.
 *
 * The same idea Minecraft's own villages use: a handful of interchangeable
 * parts, shuffled into something that looks designed. Base at the bottom,
 * a random pick of middle floors, roof on top.
 */
public final class StackGenerator {
    private StackGenerator() {}

    public static final int MIN_FLOORS = 1;
    public static final int MAX_FLOORS = 12;

    /**
     * @param floors how many middle floors to stack between base and roof
     * @return the finished building, ready to be written out as a schematic
     */
    public static RawSchematic stack(ModulePack pack, int floors, RandomSource random) {
        int clamped = Math.max(MIN_FLOORS, Math.min(MAX_FLOORS, floors));

        List<StructureTemplate.StructureBlockInfo> blocks = new ArrayList<>();
        int y = 0;

        y = append(blocks, pack.base(), y);
        for (int i = 0; i < clamped; i++) {
            // Drawn fresh each floor, so a tower can repeat a shape or mix them.
            LoadedSchematic middle = pack.middles().get(random.nextInt(pack.middles().size()));
            y = append(blocks, middle, y);
        }
        y = append(blocks, pack.top(), y);

        Vec3i footprint = pack.footprint();
        return new RawSchematic(new Vec3i(footprint.getX(), y, footprint.getZ()), blocks);
    }

    /**
     * Copies one piece in at the given height.
     *
     * @return the height the next piece starts at
     */
    private static int append(List<StructureTemplate.StructureBlockInfo> blocks,
                              LoadedSchematic piece, int baseY) {
        for (StructureTemplate.StructureBlockInfo info : piece.blocks()) {
            BlockPos pos = info.pos();
            blocks.add(new StructureTemplate.StructureBlockInfo(
                    new BlockPos(pos.getX(), pos.getY() + baseY, pos.getZ()),
                    info.state(), info.nbt()));
        }
        return baseY + piece.size().getY();
    }
}
