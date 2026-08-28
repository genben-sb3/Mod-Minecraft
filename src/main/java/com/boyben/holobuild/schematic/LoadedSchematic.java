package com.boyben.holobuild.schematic;

import java.util.List;

import net.minecraft.core.Vec3i;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * One schematic read from disk and ready to place.
 *
 * We keep our own block list next to the template: StructureTemplate does not
 * hand its blocks out (filterBlocks only ever returns a single block type), but
 * the hologram needs every block, and so will the animated build later on.
 *
 * @param name     file name without the extension, shown to the player
 * @param template the loaded structure, used for the real placing
 * @param blocks   every block in template coordinates, untransformed
 */
public record LoadedSchematic(String name, StructureTemplate template,
                              List<StructureTemplate.StructureBlockInfo> blocks) {

    public Vec3i size() {
        return template.getSize();
    }

    public int blockCount() {
        return blocks.size();
    }
}
