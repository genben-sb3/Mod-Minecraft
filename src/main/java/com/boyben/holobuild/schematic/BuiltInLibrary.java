package com.boyben.holobuild.schematic;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import com.boyben.holobuild.HoloBuild;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;

/**
 * Buildings that ship inside the mod itself.
 *
 * They live in the jar under data/holobuild/structure/ and are read straight
 * through the class loader, which works the same on client and server without
 * involving the resource pack system.
 *
 * A file in the player's schematics folder with the same name wins - that way
 * anyone can replace a built-in building with their own version.
 */
public final class BuiltInLibrary {
    private BuiltInLibrary() {}

    private static final String BASE = "/data/holobuild/structure/";
    private static final String INDEX = BASE + "index.txt";

    /** Read once on first use; the contents of the jar cannot change at runtime. */
    private static List<String> names;

    public static List<String> names() {
        if (names == null) {
            names = readIndex();
        }
        return names;
    }

    public static boolean contains(String name) {
        return names().contains(name);
    }

    /**
     * Reads one built-in structure.
     *
     * @return its NBT, or null if there is no built-in building by that name
     */
    public static CompoundTag load(String name) throws IOException {
        if (!contains(name)) {
            return null;
        }
        try (InputStream in = BuiltInLibrary.class.getResourceAsStream(BASE + name + ".nbt")) {
            if (in == null) {
                // Listed in the index but missing from the jar - a packaging slip.
                HoloBuild.LOGGER.error("Eingebautes Bauwerk '{}' fehlt in der Mod-Datei", name);
                return null;
            }
            return NbtIo.readCompressed(in, NbtAccounter.unlimitedHeap());
        }
    }

    private static List<String> readIndex() {
        List<String> found = new ArrayList<>();
        try (InputStream in = BuiltInLibrary.class.getResourceAsStream(INDEX)) {
            if (in == null) {
                HoloBuild.LOGGER.warn("Keine eingebaute Bau-Bibliothek gefunden ({})", INDEX);
                return List.of();
            }
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String trimmed = line.trim();
                    if (!trimmed.isEmpty() && SchematicLoader.isSafeName(trimmed)) {
                        found.add(trimmed);
                    }
                }
            }
        } catch (IOException e) {
            HoloBuild.LOGGER.error("Bau-Bibliothek konnte nicht gelesen werden", e);
            return List.of();
        }
        return List.copyOf(found);
    }
}
