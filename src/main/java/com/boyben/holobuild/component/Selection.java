package com.boyben.holobuild.component;

import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * The two corners marked with the Selection Wand.
 *
 * Lives on the item as a data component, so the client can draw the box while
 * the server uses the same corners to save the area.
 */
public record Selection(Optional<BlockPos> first, Optional<BlockPos> second) {

    public static final Selection EMPTY = new Selection(Optional.empty(), Optional.empty());

    public static final Codec<Selection> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            BlockPos.CODEC.optionalFieldOf("first").forGetter(Selection::first),
            BlockPos.CODEC.optionalFieldOf("second").forGetter(Selection::second)
    ).apply(instance, Selection::new));

    public static final StreamCodec<ByteBuf, Selection> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.optional(BlockPos.STREAM_CODEC), Selection::first,
            ByteBufCodecs.optional(BlockPos.STREAM_CODEC), Selection::second,
            Selection::new);

    public boolean isComplete() {
        return first.isPresent() && second.isPresent();
    }

    public Selection withFirst(BlockPos pos) {
        return new Selection(Optional.of(pos), second);
    }

    public Selection withSecond(BlockPos pos) {
        return new Selection(first, Optional.of(pos));
    }

    /** The area between the corners, in either order. Only call when complete. */
    public BoundingBox box() {
        BlockPos a = first.orElseThrow();
        BlockPos b = second.orElseThrow();
        return BoundingBox.fromCorners(a, b);
    }

    /** How many blocks across the selection is. Only call when complete. */
    public Vec3i size() {
        BoundingBox box = box();
        return new Vec3i(box.getXSpan(), box.getYSpan(), box.getZSpan());
    }

    public long volume() {
        Vec3i size = size();
        return (long) size.getX() * size.getY() * size.getZ();
    }
}
