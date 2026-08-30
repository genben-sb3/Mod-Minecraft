package com.boyben.holobuild.gametest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.boyben.holobuild.HoloBuild;
import com.boyben.holobuild.schematic.LoadedSchematic;
import com.boyben.holobuild.schematic.SchematicCache;
import com.boyben.holobuild.schematic.SchematicLoader;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Tests that HoloBuild really reads foreign schematic formats.
 *
 * The unit tests cover the number decoding on its own; these run the whole way
 * through - file on disk, palette, block data, into a usable structure.
 *
 * The test files are written here with bytes worked out by hand, deliberately
 * not with HoloBuild's own encoders. Writing and reading with the same code
 * would only prove the two agree with each other, not that either matches the
 * format other mods use.
 */
@GameTestHolder(HoloBuild.MODID)
@PrefixGameTestTemplate(false)
public class FormatGameTests {

    private static final String ROOM = "test_platform";

    /**
     * Both test files describe the same tiny 2x2x2 shape: stone wherever x is 1,
     * air everywhere else. Four stone blocks, easy to check by eye.
     */
    private static final int SIZE = 2;

    @GameTest(template = ROOM, timeoutTicks = 200)
    public static void readsSpongeSchem(GameTestHelper helper) throws Exception {
        Path file = SchematicLoader.getSchematicFolder().resolve("gametest_sponge.schem");
        try {
            writeSpongeFile(file);
            checkLoaded(helper, "gametest_sponge", helper.getLevel());
        } finally {
            Files.deleteIfExists(file);
            SchematicCache.clear();
        }
    }

    @GameTest(template = ROOM, timeoutTicks = 200)
    public static void readsLitematic(GameTestHelper helper) throws Exception {
        Path file = SchematicLoader.getSchematicFolder().resolve("gametest_lite.litematic");
        try {
            writeLitematicFile(file);
            checkLoaded(helper, "gametest_lite", helper.getLevel());
        } finally {
            Files.deleteIfExists(file);
            SchematicCache.clear();
        }
    }

    /** Both formats have to end up describing exactly the same thing. */
    private static void checkLoaded(GameTestHelper helper, String name, ServerLevel level)
            throws IOException {

        SchematicCache.clear();
        LoadedSchematic loaded = SchematicLoader.loadByName(name, level);
        if (loaded == null) {
            helper.fail("'" + name + "' konnte nicht geladen werden");
            return;
        }

        if (loaded.size().getX() != SIZE || loaded.size().getY() != SIZE || loaded.size().getZ() != SIZE) {
            helper.fail("Groesse ist " + loaded.size() + " statt " + SIZE + "x" + SIZE + "x" + SIZE);
            return;
        }

        int stone = 0;
        for (StructureTemplate.StructureBlockInfo info : loaded.blocks()) {
            BlockState state = info.state();
            if (state.isAir()) {
                continue;
            }
            if (!state.is(Blocks.STONE)) {
                helper.fail("Unerwarteter Block " + state.getBlock() + " bei " + info.pos());
                return;
            }
            // Every stone must sit at x = 1, that is what the test data says.
            if (info.pos().getX() != 1) {
                helper.fail("Stein bei " + info.pos() + ", erwartet wurde x=1 - "
                        + "die Positionen werden falsch herum ausgepackt");
                return;
            }
            stone++;
        }

        if (stone != 4) {
            helper.fail(stone + " Steine gelesen, erwartet waren 4");
            return;
        }
        helper.succeed();
    }

    /**
     * A Sponge .schem by hand.
     *
     * Block data is one varint per position, running x fastest, then z, then y.
     * Values below 128 take a single byte, so the eight positions are literally
     * the eight bytes 0,1,0,1,0,1,0,1 - palette index 1 is stone.
     */
    private static void writeSpongeFile(Path file) throws IOException {
        CompoundTag palette = new CompoundTag();
        palette.putInt("minecraft:air", 0);
        palette.putInt("minecraft:stone", 1);

        CompoundTag root = new CompoundTag();
        root.putInt("Version", 2);
        root.putShort("Width", (short) SIZE);
        root.putShort("Height", (short) SIZE);
        root.putShort("Length", (short) SIZE);
        root.put("Palette", palette);
        root.putInt("PaletteMax", 2);
        root.putByteArray("BlockData", new byte[] {0, 1, 0, 1, 0, 1, 0, 1});

        Files.createDirectories(file.getParent());
        NbtIo.writeCompressed(root, file);
    }

    /**
     * A .litematic by hand.
     *
     * With a two-entry palette each index takes two bits, so all eight fit in
     * one long. Positions 1, 3, 5 and 7 hold index 1:
     *   1 at bits 2-3   ->      4
     *   1 at bits 6-7   ->     64
     *   1 at bits 10-11 ->   1024
     *   1 at bits 14-15 ->  16384
     * which adds up to 17476.
     */
    private static void writeLitematicFile(Path file) throws IOException {
        ListTag palette = new ListTag();
        palette.add(blockEntry("minecraft:air"));
        palette.add(blockEntry("minecraft:stone"));

        CompoundTag region = new CompoundTag();
        region.put("Position", vec(0, 0, 0));
        region.put("Size", vec(SIZE, SIZE, SIZE));
        region.put("BlockStatePalette", palette);
        region.putLongArray("BlockStates", new long[] {17476L});
        region.put("TileEntities", new ListTag());
        region.put("Entities", new ListTag());

        CompoundTag regions = new CompoundTag();
        regions.put("main", region);

        CompoundTag root = new CompoundTag();
        root.putInt("Version", 6);
        root.put("Regions", regions);

        Files.createDirectories(file.getParent());
        NbtIo.writeCompressed(root, file);
    }

    private static CompoundTag blockEntry(String name) {
        CompoundTag tag = new CompoundTag();
        tag.putString("Name", name);
        return tag;
    }

    private static CompoundTag vec(int x, int y, int z) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", x);
        tag.putInt("y", y);
        tag.putInt("z", z);
        return tag;
    }
}
