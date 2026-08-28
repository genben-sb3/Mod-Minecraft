package com.boyben.holobuild.schematic;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.world.level.LevelReader;

/**
 * Keeps loaded schematics in memory.
 *
 * The hologram renderer asks for the same schematic every frame, so reading it
 * from disk each time would be wasteful. Entries are keyed by file name and
 * last-modified time, so editing a file on disk invalidates its entry by itself.
 */
public final class SchematicCache {
    private SchematicCache() {}

    private record Key(String name, long lastModified) {}

    private static final Map<Key, LoadedSchematic> CACHE = new ConcurrentHashMap<>();

    /**
     * @param name file name without the extension
     * @return the loaded schematic, or null if there is no such file
     */
    public static LoadedSchematic get(String name, LevelReader level) throws IOException {
        Path file = SchematicLoader.findByName(name);
        if (file == null) {
            return null;
        }
        Key key = new Key(name, Files.getLastModifiedTime(file).toMillis());
        LoadedSchematic cached = CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        LoadedSchematic loaded = SchematicLoader.load(file, level);
        CACHE.clear();       // only ever hold one generation, keeps memory predictable
        CACHE.put(key, loaded);
        return loaded;
    }

    public static void clear() {
        CACHE.clear();
    }
}
