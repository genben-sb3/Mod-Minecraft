package com.boyben.holobuild.schematic;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
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

    /** All .nbt files in the schematic folder, sorted by name. Never null. */
    public static List<Path> listSchematics() {
        Path folder = getSchematicFolder();
        if (!Files.isDirectory(folder)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(folder)) {
            return files.filter(Files::isRegularFile)
                    .filter(p -> hasSchematicExtension(p.getFileName().toString()))
                    .sorted(Comparator.comparing(p -> p.getFileName().toString()))
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
    public static final int MAX_NAME_LENGTH = 128;

    /**
     * Only plain file names. Anything that could climb out of the schematics
     * folder - slashes, backslashes, "..", a drive letter - is rejected.
     *
     * Names reach us from the network and from commands, so this check and
     * {@link #findByName} together are what keep those inputs harmless.
     */
    public static boolean isSafeName(String name) {
        if (name.isEmpty() || name.length() > MAX_NAME_LENGTH) {
            return false;
        }
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            boolean allowed = (c >= 'a' && c <= 'z')
                    || (c >= 'A' && c <= 'Z')
                    || (c >= '0' && c <= '9')
                    || c == '_' || c == '-' || c == ' ' || c == '.';
            if (!allowed) {
                return false;
            }
        }
        return !name.contains("..");
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
        return file.startsWith(folder) ? file : null;
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

    /** File name without the extension, for showing to the player. */
    public static String displayName(Path file) {
        String fileName = file.getFileName().toString();
        String lower = fileName.toLowerCase(Locale.ROOT);
        for (String extension : EXTENSIONS) {
            if (lower.endsWith(extension)) {
                return fileName.substring(0, fileName.length() - extension.length());
            }
        }
        return fileName;
    }
}
