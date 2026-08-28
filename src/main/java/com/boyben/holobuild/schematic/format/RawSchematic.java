package com.boyben.holobuild.schematic.format;

import java.util.List;

import net.minecraft.core.Vec3i;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * A schematic straight after reading, before it becomes a StructureTemplate.
 *
 * Every file format HoloBuild understands is turned into this shape first, so
 * only one piece of code has to know how to hand it over to Minecraft.
 *
 * @param size   width, height, length
 * @param blocks positions relative to the corner, untransformed
 */
public record RawSchematic(Vec3i size, List<StructureTemplate.StructureBlockInfo> blocks) {
}
