package com.boyben.holobuild.schematic;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import com.boyben.holobuild.HoloBuild;

import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.neoforged.fml.loading.FMLPaths;

/**
 * Reads schematic files from disk into Minecraft's own StructureTemplate.
 *
 * Only vanilla .nbt (structure block format) for now - .schem and .litematic
 * support is planned for a later stage.
 */
public final class SchematicLoader {
    private SchematicLoader() {}

    private static final String FOLDER_NAME = "holobuild/schematics";
    private static final String EXTENSION = ".nbt";

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
     * Loads one .nbt file into a StructureTemplate.
     *
     * @throws IOException if the file is missing or not a valid structure file
     */
    public static LoadedSchematic load(Path file, LevelReader level) throws IOException {
        CompoundTag tag = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());

        HolderGetter<Block> blocks = level.holderLookup(Registries.BLOCK);
        StructureTemplate template = new StructureTemplate();
        template.load(blocks, tag);

        // The template itself does not expose how many blocks it holds,
        // so count them from the raw NBT while we still have it.
        int blockCount = tag.getList("blocks", Tag.TAG_COMPOUND).size();

        return new LoadedSchematic(displayName(file), template, blockCount);
    }

    private static boolean hasSchematicExtension(String fileName) {
        return fileName.toLowerCase(Locale.ROOT).endsWith(EXTENSION);
    }

    /** File name without the .nbt ending, for showing to the player. */
    public static String displayName(Path file) {
        String fileName = file.getFileName().toString();
        return hasSchematicExtension(fileName)
                ? fileName.substring(0, fileName.length() - EXTENSION.length())
                : fileName;
    }
}
