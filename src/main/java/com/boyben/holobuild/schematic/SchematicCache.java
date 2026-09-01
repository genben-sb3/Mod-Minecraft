package com.boyben.holobuild.schematic;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.world.level.LevelReader;

/**
 * Keeps loaded schematics in memory.
 *
 * The hologram asks for the same schematic every single frame, so reading it
 * from disk each time would be wasteful. Entries are keyed by name and
 * last-modified time, so editing a file on disk invalidates its entry by itself.
 *
 * <p>Room for more than one is not a luxury. The moment the menu shows a
 * preview of whatever the mouse is over, two different schematics are wanted at
 * the same time - the hovered one and the one the hologram is still drawing.
 * With a single slot those two would evict each other twice per frame, and the
 * game would read and parse two files sixty times a second.
 *
 * <p>The limit is counted in blocks rather than in entries, because that is
 * what actually fills the memory: eight cottages are nothing, one cathedral is
 * a lot. The entry just loaded is always kept, even if it alone is over budget -
 * dropping it immediately would put us straight back to reading it every frame.
 */
public final class SchematicCache {
    private SchematicCache() {}

    /** Stamp for built-in buildings, whose contents cannot change at runtime. */
    private static final long BUILT_IN = -1L;

    /**
     * Roughly how many blocks may sit in memory at once.
     *
     * Two large builds' worth. Enough for the hologram and a preview side by
     * side with room to spare, far below anything that would trouble a game
     * that is already holding a world.
     */
    private static final long MAX_CACHED_BLOCKS = 1_500_000L;

    private record Key(String name, long lastModified) {}

    /**
     * Access-ordered, so iterating hands out the longest-unused entry first -
     * which is exactly what should go when space runs short.
     */
    private static final LinkedHashMap<Key, LoadedSchematic> CACHE =
            new LinkedHashMap<>(8, 0.75f, true);

    private static long cachedBlocks;

    /** How often a file really had to be read. Lets a test prove the cache works. */
    private static long diskLoads;

    /**
     * @param name name without the extension, a file or a built-in building
     * @return the loaded schematic, or null if nothing goes by that name
     */
    public static synchronized LoadedSchematic get(String name, LevelReader level) throws IOException {
        Path file = SchematicLoader.findByName(name);
        long stamp = file != null ? Files.getLastModifiedTime(file).toMillis() : BUILT_IN;

        Key key = new Key(name, stamp);
        // Also marks the entry as freshly used, because the map is access-ordered.
        LoadedSchematic cached = CACHE.get(key);
        if (cached != null) {
            return cached;
        }

        LoadedSchematic loaded = SchematicLoader.loadByName(name, level);
        if (loaded == null) {
            return null;
        }
        diskLoads++;

        CACHE.put(key, loaded);
        cachedBlocks += loaded.blockCount();
        evictUntilWithinBudget();
        return loaded;
    }

    /**
     * Drops the longest-unused entries until the budget is met again.
     *
     * Never empties the map completely: the entry that was just asked for is
     * the most recently used one and therefore the last in line, so it survives
     * even when it is on its own over the limit.
     */
    private static void evictUntilWithinBudget() {
        Iterator<Map.Entry<Key, LoadedSchematic>> oldestFirst = CACHE.entrySet().iterator();
        while (cachedBlocks > MAX_CACHED_BLOCKS && CACHE.size() > 1 && oldestFirst.hasNext()) {
            cachedBlocks -= oldestFirst.next().getValue().blockCount();
            oldestFirst.remove();
        }
    }

    public static synchronized void clear() {
        CACHE.clear();
        cachedBlocks = 0;
    }

    /** How many schematics are being held. */
    public static synchronized int size() {
        return CACHE.size();
    }

    /** How many times a file has actually been read from disk since startup. */
    public static synchronized long diskLoads() {
        return diskLoads;
    }
}
