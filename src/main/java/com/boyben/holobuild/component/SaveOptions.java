package com.boyben.holobuild.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * What goes into a schematic when the Selection Wand saves an area.
 *
 * These ride on the wand rather than in the config file for a reason: the
 * config is one setting for the whole game, but a player saving a furnished
 * house wants different things than one saving a bare wall shape. On the item
 * the choice stays with whoever made it, survives logging out, and is visible
 * in the tooltip instead of buried in a file.
 */
public record SaveOptions(boolean air, boolean contents, boolean entities) {

    /**
     * The settings that surprise a player least.
     *
     * Air is off because saving it turns a building into a cutter: every empty
     * spot inside the selection would erase whatever stood there. Contents are
     * on because a chest that comes back empty looks like the save lost them.
     * Entities are off because a selection usually catches passing animals, and
     * a house that arrives with somebody else's cow is worse than one missing
     * an item frame.
     */
    public static final SaveOptions DEFAULT = new SaveOptions(false, true, false);

    public static final Codec<SaveOptions> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.BOOL.optionalFieldOf("air", DEFAULT.air()).forGetter(SaveOptions::air),
            Codec.BOOL.optionalFieldOf("contents", DEFAULT.contents()).forGetter(SaveOptions::contents),
            Codec.BOOL.optionalFieldOf("entities", DEFAULT.entities()).forGetter(SaveOptions::entities)
    ).apply(instance, SaveOptions::new));

    public static final StreamCodec<ByteBuf, SaveOptions> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, SaveOptions::air,
            ByteBufCodecs.BOOL, SaveOptions::contents,
            ByteBufCodecs.BOOL, SaveOptions::entities,
            SaveOptions::new);

    public SaveOptions withAir(boolean value) {
        return new SaveOptions(value, contents, entities);
    }

    public SaveOptions withContents(boolean value) {
        return new SaveOptions(air, value, entities);
    }

    public SaveOptions withEntities(boolean value) {
        return new SaveOptions(air, contents, value);
    }
}
