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
 * from disk each time would be wasteful. Entries are keyed by name and
 * last-modified time, so editing a file on disk invalidates its entry by itself.
 */
public final class SchematicCache {
    private SchematicCache() {}

    /** Stamp for built-in buildings, whose contents cannot change at runtime. */
    private static final long BUILT_IN = -1L;

    private record Key(String name, long lastModified) {}

    private static final Map<Key, LoadedSchematic> CACHE = new ConcurrentHashMap<>();

    /**
     * @param name name without the extension, a file or a built-in building
     * @return the loaded schematic, or null if nothing goes by that name
     */
    public static LoadedSchematic get(String name, LevelReader level) throws IOException {
        Path file = SchematicLoader.findByName(name);
        long stamp = file != null ? Files.getLastModifiedTime(file).toMillis() : BUILT_IN;

        Key key = new Key(name, stamp);
        LoadedSchematic cached = CACHE.get(key);
        if (cached != null) {
            return cached;
        }

        LoadedSchematic loaded = SchematicLoader.loadByName(name, level);
        if (loaded == null) {
            return null;
        }
        CACHE.clear();       // only ever hold one generation, keeps memory predictable
        CACHE.put(key, loaded);
        return loaded;
    }

    public static void clear() {
        CACHE.clear();
    }
}
