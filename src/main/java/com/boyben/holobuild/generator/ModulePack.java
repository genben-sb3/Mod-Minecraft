package com.boyben.holobuild.generator;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.boyben.holobuild.schematic.LoadedSchematic;
import com.boyben.holobuild.schematic.SchematicLoader;

import net.minecraft.core.Vec3i;
import net.minecraft.world.level.LevelReader;

/**
 * The pieces of one building set, ready to be stacked.
 *
 * Pieces are found purely by their name, so adding a new floor shape means
 * saving a schematic called {@code <set>_mid_<something>} - no code change,
 * no config file. Anyone can extend a set with their own floors.
 *
 * <ul>
 *   <li>{@code <set>_base} - the ground floor, exactly one</li>
 *   <li>{@code <set>_mid_*} - floors that get repeated, at least one</li>
 *   <li>{@code <set>_top} - the roof, exactly one</li>
 * </ul>
 */
public record ModulePack(String setName, LoadedSchematic base,
                         List<LoadedSchematic> middles, LoadedSchematic top) {

    /** Why a set could not be assembled, in words a player can act on. */
    public static final class MissingPieceException extends Exception {
        public MissingPieceException(String message) {
            super(message);
        }
    }

    /**
     * Collects every piece belonging to a set.
     *
     * @throws MissingPieceException if a piece is missing or the footprints differ
     */
    public static ModulePack find(String setName, LevelReader level)
            throws IOException, MissingPieceException {

        String prefix = setName.toLowerCase(Locale.ROOT) + "_";
        LoadedSchematic base = null;
        LoadedSchematic top = null;
        List<LoadedSchematic> middles = new ArrayList<>();

        for (String name : SchematicLoader.allNames()) {
            String lower = name.toLowerCase(Locale.ROOT);
            if (!lower.startsWith(prefix)) {
                continue;
            }
            String role = lower.substring(prefix.length());

            if (role.equals("base")) {
                base = SchematicLoader.loadByName(name, level);
            } else if (role.equals("top")) {
                top = SchematicLoader.loadByName(name, level);
            } else if (role.startsWith("mid")) {
                LoadedSchematic middle = SchematicLoader.loadByName(name, level);
                if (middle != null) {
                    middles.add(middle);
                }
            }
        }

        if (base == null) {
            throw new MissingPieceException(setName + "_base");
        }
        if (top == null) {
            throw new MissingPieceException(setName + "_top");
        }
        if (middles.isEmpty()) {
            throw new MissingPieceException(setName + "_mid_1");
        }

        // Pieces of different widths cannot be stacked into anything sensible.
        Vec3i footprint = base.size();
        checkFootprint(footprint, top, setName);
        for (LoadedSchematic middle : middles) {
            checkFootprint(footprint, middle, setName);
        }

        return new ModulePack(setName, base, List.copyOf(middles), top);
    }

    private static void checkFootprint(Vec3i expected, LoadedSchematic piece, String setName)
            throws MissingPieceException {
        Vec3i size = piece.size();
        if (size.getX() != expected.getX() || size.getZ() != expected.getZ()) {
            throw new MissingPieceException(piece.name() + " ("
                    + size.getX() + "x" + size.getZ() + " statt "
                    + expected.getX() + "x" + expected.getZ() + ")");
        }
    }

    /** Ground area shared by every piece in the set. */
    public Vec3i footprint() {
        return base.size();
    }
}
