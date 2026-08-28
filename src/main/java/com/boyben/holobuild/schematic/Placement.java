package com.boyben.holobuild.schematic;

import java.util.ArrayList;
import java.util.List;

import com.boyben.holobuild.component.BlueprintSettings;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

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

    /**
     * Moves every block into world coordinates, turned and mirrored.
     *
     * Deliberately mirrors before rotating and uses calculateRelativePosition,
     * because that is exactly what StructureTemplate.placeInWorld does. Any
     * other order and the hologram would show something the server never builds.
     *
     * Yes, BlockState.rotate(Rotation) is deprecated in favour of the
     * LevelAccessor variant - and no, do not "fix" it here. placeInWorld still
     * calls this exact method, so switching would let the preview and the real
     * result drift apart, which is the one thing this class exists to prevent.
     */
    public static List<StructureTemplate.StructureBlockInfo> transformed(
            LoadedSchematic schematic, StructurePlaceSettings settings, BlockPos origin) {

        List<StructureTemplate.StructureBlockInfo> out = new ArrayList<>(schematic.blocks().size());
        for (StructureTemplate.StructureBlockInfo info : schematic.blocks()) {
            BlockPos pos = StructureTemplate.calculateRelativePosition(settings, info.pos()).offset(origin);
            BlockState state = info.state().mirror(settings.getMirror()).rotate(settings.getRotation());
            out.add(new StructureTemplate.StructureBlockInfo(pos, state, info.nbt()));
        }
        return out;
    }
}
