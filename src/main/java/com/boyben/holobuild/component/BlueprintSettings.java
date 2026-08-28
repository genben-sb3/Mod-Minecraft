package com.boyben.holobuild.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * Everything the wand remembers between right-clicks: which schematic is
 * selected and how it should be oriented.
 *
 * Stored on the ItemStack as a data component, so Minecraft keeps client and
 * server in sync for us - the hologram on the client and the blocks placed on
 * the server always read the same values.
 *
 * @param schematic file name without .nbt, empty means "nothing selected yet"
 * @param rotation  0..3, in 90 degree steps clockwise
 * @param mirrored  flipped along the front-to-back axis
 * @param yOffset   how many blocks up or down to shift the build
 */
public record BlueprintSettings(String schematic, int rotation, boolean mirrored, int yOffset) {

    public static final BlueprintSettings EMPTY = new BlueprintSettings("", 0, false, 0);

    public static final Codec<BlueprintSettings> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.optionalFieldOf("schematic", "").forGetter(BlueprintSettings::schematic),
            Codec.INT.optionalFieldOf("rotation", 0).forGetter(BlueprintSettings::rotation),
            Codec.BOOL.optionalFieldOf("mirrored", false).forGetter(BlueprintSettings::mirrored),
            Codec.INT.optionalFieldOf("y_offset", 0).forGetter(BlueprintSettings::yOffset)
    ).apply(instance, BlueprintSettings::new));

    public static final StreamCodec<ByteBuf, BlueprintSettings> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, BlueprintSettings::schematic,
            ByteBufCodecs.VAR_INT, BlueprintSettings::rotation,
            ByteBufCodecs.BOOL, BlueprintSettings::mirrored,
            ByteBufCodecs.VAR_INT, BlueprintSettings::yOffset,
            BlueprintSettings::new);

    public boolean hasSchematic() {
        return !schematic.isEmpty();
    }

    public Rotation toRotation() {
        return switch (Math.floorMod(rotation, 4)) {
            case 1 -> Rotation.CLOCKWISE_90;
            case 2 -> Rotation.CLOCKWISE_180;
            case 3 -> Rotation.COUNTERCLOCKWISE_90;
            default -> Rotation.NONE;
        };
    }

    public Mirror toMirror() {
        return mirrored ? Mirror.FRONT_BACK : Mirror.NONE;
    }

    public BlueprintSettings withSchematic(String name) {
        return new BlueprintSettings(name, rotation, mirrored, yOffset);
    }

    /** Turns by the given number of quarter turns, wrapping around. */
    public BlueprintSettings rotatedBy(int quarterTurns) {
        return new BlueprintSettings(schematic, Math.floorMod(rotation + quarterTurns, 4), mirrored, yOffset);
    }

    public BlueprintSettings toggleMirror() {
        return new BlueprintSettings(schematic, rotation, !mirrored, yOffset);
    }

    public BlueprintSettings shiftedBy(int blocks) {
        return new BlueprintSettings(schematic, rotation, mirrored, yOffset + blocks);
    }
}
