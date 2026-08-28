package com.boyben.holobuild.client;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.boyben.holobuild.HoloBuild;
import com.boyben.holobuild.schematic.SchematicLoader;

/**
 * Remembers which schematics the player starred.
 *
 * A plain text file, one name per line, sitting next to the schematics so it is
 * easy to find and edit by hand. Client side only - starring is a personal
 * convenience, the server has no business knowing about it.
 */
public final class Favourites {
    private Favourites() {}

    private static final String FILE_NAME = "favourites.txt";

    /** Loaded on first use, then kept in memory. */
    private static Set<String> names;

    public static boolean isFavourite(String name) {
        return all().contains(name);
    }

    /** Stars an unstarred name, unstars a starred one. */
    public static void toggle(String name) {
        Set<String> current = all();
        if (!current.remove(name)) {
            current.add(name);
        }
        save();
    }

    public static Set<String> all() {
        if (names == null) {
            names = load();
        }
        return names;
    }

    private static Path file() {
        return SchematicLoader.getSchematicFolder().resolveSibling(FILE_NAME);
    }

    private static Set<String> load() {
        Path path = file();
        if (!Files.isRegularFile(path)) {
            return new LinkedHashSet<>();
        }
        try {
            List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
            Set<String> loaded = new LinkedHashSet<>();
            for (String line : lines) {
                String trimmed = line.trim();
                // Skip blanks and anything that could not be a file name anyway.
                if (!trimmed.isEmpty() && SchematicLoader.isSafeName(trimmed)) {
                    loaded.add(trimmed);
                }
            }
            return loaded;
        } catch (IOException e) {
            HoloBuild.LOGGER.error("Favoriten konnten nicht gelesen werden: {}", path, e);
            return new LinkedHashSet<>();
        }
    }

    private static void save() {
        Path path = file();
        try {
            Files.createDirectories(path.getParent());
            Files.write(path, all(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            // Losing a star is annoying but must never break the screen.
            HoloBuild.LOGGER.error("Favoriten konnten nicht gespeichert werden: {}", path, e);
        }
    }
}
