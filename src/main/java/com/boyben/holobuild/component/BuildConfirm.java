package com.boyben.holobuild.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * A note that the player has just been told they are short of materials.
 *
 * In survival the wand refuses a build it cannot finish and says what is
 * missing. Clicking again straight afterwards goes ahead anyway - some people
 * do want the foundation now and the roof later, and a tool that simply says no
 * would be worse than one that half-builds.
 *
 * <p>It lives on the wand rather than in a map of players for a reason we have
 * already paid for once: the undo snapshots sat in exactly such a map, nothing
 * ever removed them, and they piled up for the life of the server. Data on an
 * item needs no cleaning up - it goes when the item goes.
 *
 * @param schematic    which building the warning was about
 * @param warnedAtTick the game time the warning was given
 */
public record BuildConfirm(String schematic, long warnedAtTick) {

    public static final BuildConfirm NONE = new BuildConfirm("", 0L);

    /**
     * How long the offer stands, in ticks. Ten seconds: long enough to read the
     * list of what is missing, short enough that a click a minute later is
     * clearly a new decision rather than an old one being carried out.
     */
    public static final long WINDOW_TICKS = 200L;

    public static final Codec<BuildConfirm> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.optionalFieldOf("schematic", "").forGetter(BuildConfirm::schematic),
            Codec.LONG.optionalFieldOf("warned_at", 0L).forGetter(BuildConfirm::warnedAtTick)
    ).apply(instance, BuildConfirm::new));

    public static final StreamCodec<ByteBuf, BuildConfirm> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, BuildConfirm::schematic,
            ByteBufCodecs.VAR_LONG, BuildConfirm::warnedAtTick,
            BuildConfirm::new);

    /**
     * May this build go ahead without asking again?
     *
     * @param wanted the building about to be placed
     * @param now    the current game time
     */
    public boolean allows(String wanted, long now) {
        if (schematic.isEmpty() || !schematic.equals(wanted)) {
            // A warning about the cottage says nothing about the lighthouse.
            return false;
        }
        long waited = now - warnedAtTick;
        // A negative wait means the clock went backwards - a different world,
        // or one loaded from an older save. Treat that as no warning at all
        // rather than as one that is somehow always valid.
        return waited >= 0 && waited <= WINDOW_TICKS;
    }
}
