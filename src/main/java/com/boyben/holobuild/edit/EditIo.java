package com.boyben.holobuild.edit;

import java.io.IOException;
import java.nio.file.Path;

import com.boyben.holobuild.schematic.LoadedSchematic;
import com.boyben.holobuild.schematic.SchematicCache;
import com.boyben.holobuild.schematic.SchematicLoader;
import com.boyben.holobuild.schematic.format.StructureNbt;

import net.minecraft.nbt.NbtIo;
import net.minecraft.world.level.LevelReader;

/**
 * Reading a schematic to edit, and writing the result back.
 *
 * There are two ways into the editing - the commands and the 3D editor - and
 * without this there would be two copies of the same loading and saving. Those
 * copies always start identical and end up differing in exactly the check that
 * matters, which here would be the name check standing between a message off
 * the network and a file path.
 */
public final class EditIo {
    private EditIo() {}

    /**
     * @return a working copy of the named schematic, or null if the name is not
     *         allowed or nothing goes by it
     */
    public static SchematicEdit open(String name, LevelReader level) throws IOException {
        if (!SchematicLoader.isSafeName(name)) {
            return null;
        }
        LoadedSchematic schematic = SchematicCache.get(name, level);
        return schematic == null ? null : SchematicEdit.of(schematic);
    }

    /**
     * Writes the edited copy under a new name.
     *
     * @return false if the name cannot be used; an unwritable file throws
     */
    public static boolean save(SchematicEdit edit, String targetName) throws IOException {
        Path target = SchematicLoader.pathForNewFile(targetName);
        if (target == null) {
            return false;
        }
        SchematicLoader.ensureFolderExists();
        NbtIo.writeCompressed(StructureNbt.toCompound(edit.toRaw()), target);

        // That name may now stand for different contents than a moment ago.
        SchematicCache.clear();
        return true;
    }
}
