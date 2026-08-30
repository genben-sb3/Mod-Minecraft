package com.boyben.holobuild.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.boyben.holobuild.HoloBuild;
import com.boyben.holobuild.schematic.LoadedSchematic;
import com.boyben.holobuild.schematic.SchematicLoader;

import net.minecraft.core.Vec3i;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * What a schematic contains, worked out once for the picker.
 *
 * Only the numbers are kept, never the blocks themselves - a hundred entries
 * of metadata cost nothing, a hundred loaded structures would not.
 *
 * @param size     width, height, length
 * @param blocks   how many blocks it places
 * @param topBlock the most common material, already in readable form
 * @param topCount how many of that material
 * @param distinct how many different materials it uses
 */
public record SchematicInfo(Vec3i size, int blocks, String topBlock, int topCount, int distinct) {

    /** Entries computed so far, kept until the game closes. */
    private static final Map<String, SchematicInfo> CACHE = new ConcurrentHashMap<>();

    /** Stands in for an entry that could not be read, so it is not retried every frame. */
    private static final SchematicInfo UNREADABLE =
            new SchematicInfo(Vec3i.ZERO, 0, "", 0, 0);

    public boolean readable() {
        return blocks > 0;
    }

    public String dimensions() {
        return size.getX() + "x" + size.getY() + "x" + size.getZ();
    }

    /**
     * Reads a schematic just far enough to describe it.
     *
     * @return the description, or null if the file could not be read
     */
    public static SchematicInfo of(String name, Level level) {
        SchematicInfo cached = CACHE.get(name);
        if (cached != null) {
            return cached.readable() ? cached : null;
        }

        SchematicInfo info;
        try {
            LoadedSchematic schematic = SchematicLoader.loadByName(name, level);
            info = schematic == null ? UNREADABLE : describe(schematic);
        } catch (Exception e) {
            // A damaged file must not stop the picker from opening.
            HoloBuild.LOGGER.warn("Konnte '{}' fuer die Vorschau nicht lesen: {}", name, e.toString());
            info = UNREADABLE;
        }

        CACHE.put(name, info);
        return info.readable() ? info : null;
    }

    private static SchematicInfo describe(LoadedSchematic schematic) {
        Map<String, Integer> counts = new HashMap<>();
        int total = 0;

        for (StructureTemplate.StructureBlockInfo block : schematic.blocks()) {
            BlockState state = block.state();
            if (state.isAir()) {
                continue;       // air is filler, not material
            }
            total++;
            counts.merge(state.getBlock().getName().getString(), 1, Integer::sum);
        }

        List<Map.Entry<String, Integer>> sorted = new ArrayList<>(counts.entrySet());
        sorted.sort(Comparator.comparingInt((Map.Entry<String, Integer> e) -> e.getValue()).reversed());

        String topName = sorted.isEmpty() ? "" : sorted.get(0).getKey();
        int topCount = sorted.isEmpty() ? 0 : sorted.get(0).getValue();

        return new SchematicInfo(schematic.size(), total, topName, topCount, counts.size());
    }

    /** Forgets everything, for when files on disk may have changed. */
    public static void clear() {
        CACHE.clear();
    }
}
