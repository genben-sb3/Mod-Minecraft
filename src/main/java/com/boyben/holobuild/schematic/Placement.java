package com.boyben.holobuild.schematic;

import com.boyben.holobuild.component.BlueprintSettings;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;

/**
 * Works out where a schematic ends up in the world.
 *
 * Both the hologram on the client and the actual placing on the server go
 * through here. If they each did their own maths the preview would slowly drift
 * away from where blocks really land.
 */
public final class Placement {
    private Placement() {}

    /** Corner the structure starts at: against the clicked face, shifted by the chosen offset. */
    public static BlockPos origin(BlockPos clicked, Direction face, BlueprintSettings settings) {
        return clicked.relative(face).above(settings.yOffset());
    }

    public static StructurePlaceSettings settings(Vec3i size, BlueprintSettings blueprint) {
        return new StructurePlaceSettings()
                .setRotation(blueprint.toRotation())
                .setMirror(blueprint.toMirror())
                // Turn around the middle, otherwise rotating swings the whole
                // building off to the side instead of spinning it in place.
                .setRotationPivot(new BlockPos(size.getX() / 2, 0, size.getZ() / 2))
                .setIgnoreEntities(false);
    }

    public static BoundingBox box(LoadedSchematic schematic, StructurePlaceSettings settings, BlockPos origin) {
        return schematic.template().getBoundingBox(settings, origin);
    }
}
