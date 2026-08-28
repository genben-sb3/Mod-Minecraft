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

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderGetter;
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
     * Loads one .nbt file into a StructureTemplate plus our own block list.
     *
     * @throws IOException if the file is missing or not a valid structure file
     */
    public static LoadedSchematic load(Path file, LevelReader level) throws IOException {
        CompoundTag tag = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());

        HolderGetter<Block> blockLookup = level.holderLookup(Registries.BLOCK);
        StructureTemplate template = new StructureTemplate();
        template.load(blockLookup, tag);

        return new LoadedSchematic(displayName(file), template, readBlocks(tag, blockLookup));
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
