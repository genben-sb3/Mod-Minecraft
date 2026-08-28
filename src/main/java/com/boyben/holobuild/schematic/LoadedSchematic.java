package com.boyben.holobuild.schematic;

import net.minecraft.core.Vec3i;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * One schematic that has been read from disk and is ready to place.
 *
 * @param name       file name without the extension, shown to the player
 * @param template   the loaded structure, ready for placeInWorld
 * @param blockCount how many blocks it contains
 */
public record LoadedSchematic(String name, StructureTemplate template, int blockCount) {
    public Vec3i size() {
        return template.getSize();
    }
}
