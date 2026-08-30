package com.boyben.holobuild.schematic;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

import com.boyben.holobuild.HoloBuild;
import com.boyben.holobuild.schematic.format.LitematicaReader;
import com.boyben.holobuild.schematic.format.RawSchematic;
import com.boyben.holobuild.schematic.format.SpongeSchematicReader;
import com.boyben.holobuild.schematic.format.StructureNbt;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.neoforged.fml.loading.FMLPaths;

/**
 * Reads schematic files from disk into Minecraft's own StructureTemplate.
 *
 * Understands Minecraft .nbt structures, Sponge .schem and .litematic files.
 */
public final class SchematicLoader {
    private SchematicLoader() {}

    private static final String FOLDER_NAME = "holobuild/schematics";

    /** Minecraft's own structure format, written by the structure block. */
    private static final String NBT_EXTENSION = ".nbt";

    /** Sponge Schematic, what WorldEdit and the schematic websites hand out. */
    private static final String SCHEM_EXTENSION = ".schem";

    /** Litematica's own format. */
    private static final String LITEMATIC_EXTENSION = ".litematic";

    private static final List<String> EXTENSIONS =
            List.of(NBT_EXTENSION, SCHEM_EXTENSION, LITEMATIC_EXTENSION);

    /** The folder players drop their schematic files into. */
    public static Path getSchematicFolder() {
        return FMLPaths.GAMEDIR.get().resolve(FOLDER_NAME);
    }

    public static void ensureFolderExists() {
        try {
            Files.createDirectories(getSchematicFolder());
        } catch (IOException e) {
            HoloBuild.LOGGER.error("Konnte den Schematic-Ordner nicht anlegen: {}", getSchematicFolder(), e);
        }
    }

    /** How deep subfolders may nest, so a symlink loop cannot spin forever. */
    private static final int MAX_FOLDER_DEPTH = 8;

    /**
     * Every schematic in the folder and its subfolders, sorted by path.
     *
     * Subfolders are how schematics get organised - "haeuser/villa" is simply a
     * file called villa.nbt inside a folder called haeuser.
     */
    public static List<Path> listSchematics() {
        Path folder = getSchematicFolder();
        if (!Files.isDirectory(folder)) {
            return List.of();
        }
        try (Stream<Path> files = Files.walk(folder, MAX_FOLDER_DEPTH)) {
            return files.filter(Files::isRegularFile)
                    .filter(p -> hasSchematicExtension(p.getFileName().toString()))
                    .sorted(Comparator.comparing(Path::toString))
                    .toList();
        } catch (IOException e) {
            HoloBuild.LOGGER.error("Konnte den Schematic-Ordner nicht lesen: {}", folder, e);
            return List.of();
        }
    }

    /**
     * Loads a schematic file into a StructureTemplate plus our own block list.
     *
     * Foreign formats are converted to Minecraft's structure layout first, so
     * everything downstream only ever deals with one kind of data.
     *
     * @throws IOException if the file is missing or cannot be understood
     */
    public static LoadedSchematic load(Path file, LevelReader level) throws IOException {
        CompoundTag fileTag = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
        HolderLookup<Block> blockLookup = level.holderLookup(Registries.BLOCK);

        CompoundTag structureTag;
        List<StructureTemplate.StructureBlockInfo> blocks;

        String lowerName = fileName(file);
        if (lowerName.endsWith(SCHEM_EXTENSION)) {
            RawSchematic raw = SpongeSchematicReader.read(fileTag, blockLookup);
            structureTag = StructureNbt.toCompound(raw);
            blocks = raw.blocks();
        } else if (lowerName.endsWith(LITEMATIC_EXTENSION)) {
            RawSchematic raw = LitematicaReader.read(fileTag, blockLookup);
            structureTag = StructureNbt.toCompound(raw);
            blocks = raw.blocks();
        } else {
            structureTag = fileTag;
            blocks = readBlocks(structureTag, blockLookup);
        }

        StructureTemplate template = new StructureTemplate();
        template.load(blockLookup, structureTag);

        return new LoadedSchematic(displayName(file), template, blocks);
    }

    private static String fileName(Path file) {
        return file.getFileName().toString().toLowerCase(Locale.ROOT);
    }

    /**
     * Pulls every block out of the raw NBT.
     *
     * Same layout the structure block writes: a palette of block states, and a
     * block list holding a position and an index into that palette.
     */
    private static List<StructureTemplate.StructureBlockInfo> readBlocks(
            CompoundTag tag, HolderGetter<Block> blockLookup) {

        ListTag paletteTag = paletteOf(tag);
        List<BlockState> palette = new ArrayList<>(paletteTag.size());
        for (int i = 0; i < paletteTag.size(); i++) {
            palette.add(NbtUtils.readBlockState(blockLookup, paletteTag.getCompound(i)));
        }

        ListTag blocksTag = tag.getList("blocks", Tag.TAG_COMPOUND);
        List<StructureTemplate.StructureBlockInfo> blocks = new ArrayList<>(blocksTag.size());
        for (int i = 0; i < blocksTag.size(); i++) {
            CompoundTag entry = blocksTag.getCompound(i);
            int stateIndex = entry.getInt("state");
            if (stateIndex < 0 || stateIndex >= palette.size()) {
                continue;   // damaged file - skip the block rather than crash
            }
            ListTag posTag = entry.getList("pos", Tag.TAG_INT);
            if (posTag.size() < 3) {
                continue;
            }
            BlockPos pos = new BlockPos(posTag.getInt(0), posTag.getInt(1), posTag.getInt(2));
            CompoundTag blockEntity = entry.contains("nbt", Tag.TAG_COMPOUND)
                    ? entry.getCompound("nbt")
                    : null;
            blocks.add(new StructureTemplate.StructureBlockInfo(pos, palette.get(stateIndex), blockEntity));
        }
        return blocks;
    }

    /**
     * Structure files use "palette" for a single variant, or "palettes" holding
     * several. We take the first variant, which is what a plain save produces.
     */
    private static ListTag paletteOf(CompoundTag tag) {
        if (tag.contains("palette", Tag.TAG_LIST)) {
            return tag.getList("palette", Tag.TAG_COMPOUND);
        }
        ListTag variants = tag.getList("palettes", Tag.TAG_LIST);
        return variants.isEmpty() ? new ListTag() : variants.getList(0);
    }

    private static boolean hasSchematicExtension(String fileName) {
        String lower = fileName.toLowerCase(Locale.ROOT);
        return EXTENSIONS.stream().anyMatch(lower::endsWith);
    }

    /** Longer than any sensible file name; stops a huge string arriving over the network. */
    public static final int MAX_NAME_LENGTH = SchematicName.MAX_LENGTH;

    /**
     * Only plain file names. Anything that could climb out of the schematics
     * folder - slashes, backslashes, "..", a drive letter - is rejected.
     *
     * Names reach us from the network and from commands, so this check and
     * {@link #findByName} together are what keep those inputs harmless.
     */
    public static boolean isSafeName(String name) {
        return SchematicName.isSafe(name);
    }

    /**
     * Where a new schematic of this name would be written.
     *
     * @return the path, or null if the name is not safe to use
     */
    public static Path pathForNewFile(String name) {
        if (!isSafeName(name)) {
            return null;
        }
        Path folder = getSchematicFolder().toAbsolutePath().normalize();
        Path file = folder.resolve(name + NBT_EXTENSION).toAbsolutePath().normalize();
        if (!file.startsWith(folder)) {
            return null;
        }

        // A name like "haeuser/villa" means the folder has to exist first.
        try {
            Files.createDirectories(file.getParent());
        } catch (IOException e) {
            HoloBuild.LOGGER.error("Konnte den Ordner nicht anlegen: {}", file.getParent(), e);
            return null;
        }
        return file;
    }

    /**
     * Finds the file behind a display name, trying every supported extension.
     *
     * This is the single place that turns a name into a path, and it refuses to
     * leave the schematics folder. Names arrive over the network, so a name like
     * "../../secrets" must never resolve to anything.
     *
     * @return the file, or null if there is none by that name
     */
    public static Path findByName(String name) {
        Path folder = getSchematicFolder().toAbsolutePath().normalize();
        for (String extension : EXTENSIONS) {
            Path candidate = folder.resolve(name + extension).toAbsolutePath().normalize();
            if (candidate.startsWith(folder) && Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * The name shown to the player: path inside the schematics folder, without
     * the extension. A file in a subfolder becomes "haeuser/villa".
     *
     * Always with forward slashes, even on Windows - the same spelling the name
     * check allows and the network carries.
     */
    public static String displayName(Path file) {
        Path folder = getSchematicFolder().toAbsolutePath().normalize();
        Path absolute = file.toAbsolutePath().normalize();

        String relative = absolute.startsWith(folder)
                ? folder.relativize(absolute).toString().replace('\\', '/')
                : file.getFileName().toString();

        String lower = relative.toLowerCase(Locale.ROOT);
        for (String extension : EXTENSIONS) {
            if (lower.endsWith(extension)) {
                return relative.substring(0, relative.length() - extension.length());
            }
        }
        return relative;
    }

    /**
     * Loads a schematic by the name shown to the player.
     *
     * A file in the schematics folder is tried first, then the library that
     * ships inside the mod - so anyone can override a built-in building simply
     * by saving their own under the same name.
     *
     * @return the schematic, or null if nothing goes by that name
     */
    public static LoadedSchematic loadByName(String name, LevelReader level) throws IOException {
        Path file = findByName(name);
        if (file != null) {
            return load(file, level);
        }

        CompoundTag builtIn = BuiltInLibrary.load(name);
        if (builtIn == null) {
            return null;
        }
        HolderLookup<Block> blockLookup = level.holderLookup(Registries.BLOCK);
        StructureTemplate template = new StructureTemplate();
        template.load(blockLookup, builtIn);
        return new LoadedSchematic(name, template, readBlocks(builtIn, blockLookup));
    }

    /** Every name a player can choose: their own files plus the built-in library. */
    public static List<String> allNames() {
        Set<String> names = new LinkedHashSet<>();
        for (Path file : listSchematics()) {
            names.add(displayName(file));
        }
        names.addAll(BuiltInLibrary.names());
        return List.copyOf(names);
    }

    /** True if that name resolves to either a file or a built-in building. */
    public static boolean exists(String name) {
        return findByName(name) != null || BuiltInLibrary.contains(name);
    }

    /** True if the name only exists inside the mod, with no file of the player's own. */
    public static boolean isBuiltIn(String name) {
        return findByName(name) == null && BuiltInLibrary.contains(name);
    }
}
