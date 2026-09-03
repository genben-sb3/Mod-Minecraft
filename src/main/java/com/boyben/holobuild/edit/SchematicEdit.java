package com.boyben.holobuild.edit;

import java.util.ArrayList;
import java.util.List;

import com.boyben.holobuild.schematic.LoadedSchematic;
import com.boyben.holobuild.schematic.format.RawSchematic;

import net.minecraft.core.Vec3i;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * A working copy of a schematic that can be changed and written back out.
 *
 * This is the part underneath the editor. The picking and drawing come later;
 * what has to be right first is the model, because every mistake in here is
 * silent - a schematic that quietly lost a wall or kept the chest contents of a
 * block that is no longer a chest still loads, still builds, and only looks
 * wrong long after it was saved.
 *
 * <p>The size is deliberately left alone by every operation. Rotation pivots on
 * half the size, so a copy that shrank to fit its remaining blocks would turn
 * around a different point than the original - the same trap the .litematic
 * reader fell into once already.
 */
public final class SchematicEdit {

    private final Vec3i size;
    private final List<StructureTemplate.StructureBlockInfo> blocks;

    private SchematicEdit(Vec3i size, List<StructureTemplate.StructureBlockInfo> blocks) {
        this.size = size;
        this.blocks = blocks;
    }

    /** Takes a copy, so the cached schematic everyone else is using stays untouched. */
    public static SchematicEdit of(LoadedSchematic schematic) {
        return new SchematicEdit(schematic.size(), new ArrayList<>(schematic.blocks()));
    }

    public int blockCount() {
        return blocks.size();
    }

    public Vec3i size() {
        return size;
    }

    /**
     * Takes out every block of one kind, whatever state it is in.
     *
     * Matching the block rather than the exact state is what makes this useful:
     * a wall of stairs facing four ways is still a wall of stairs, and having to
     * name each facing separately would make the command useless.
     *
     * @return how many blocks went
     */
    public int erase(Block kind) {
        int before = blocks.size();
        blocks.removeIf(info -> info.state().is(kind));
        return before - blocks.size();
    }

    /**
     * Swaps every block of one kind for another.
     *
     * The replacement keeps its own state and drops whatever the old block was
     * carrying: a chest turned into stone must not keep its contents, or the
     * file would hold items inside a block that cannot have any.
     *
     * @return how many blocks changed
     */
    public int replace(Block from, BlockState to) {
        int changed = 0;
        for (int i = 0; i < blocks.size(); i++) {
            StructureTemplate.StructureBlockInfo info = blocks.get(i);
            if (!info.state().is(from)) {
                continue;
            }
            blocks.set(i, new StructureTemplate.StructureBlockInfo(info.pos(), to, null));
            changed++;
        }
        return changed;
    }

    /** How many blocks of this kind are in here. */
    public int count(Block kind) {
        return (int) blocks.stream().filter(info -> info.state().is(kind)).count();
    }

    /** @return the edited schematic, ready for {@code StructureNbt.toCompound} */
    public RawSchematic toRaw() {
        return new RawSchematic(size, List.copyOf(blocks));
    }
}
