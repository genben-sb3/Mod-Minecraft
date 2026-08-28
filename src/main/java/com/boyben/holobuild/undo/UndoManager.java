package com.boyben.holobuild.undo;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * Remembers what the world looked like before a build, so it can be put back.
 *
 * Server side only. History lives in memory and is gone when the server stops -
 * undo is meant for "oops, wrong spot", not as a save system.
 */
public final class UndoManager {
    private UndoManager() {}

    /** Above this, recording the old state would eat more memory than it is worth. */
    public static final int MAX_BLOCKS = 250_000;

    private static final int MAX_STEPS = 5;

    private record Saved(BlockState state, CompoundTag blockEntity) {}

    private record Snapshot(ResourceKey<Level> dimension, Map<BlockPos, Saved> before) {}

    private static final Map<UUID, Deque<Snapshot>> HISTORY = new ConcurrentHashMap<>();

    /**
     * Saves the current contents of the area.
     *
     * @return false if the area is too big to record - the caller should still
     *         build, just without undo support
     */
    public static boolean record(ServerPlayer player, ServerLevel level, BoundingBox box) {
        long volume = (long) box.getXSpan() * box.getYSpan() * box.getZSpan();
        if (volume > MAX_BLOCKS) {
            return false;
        }

        Map<BlockPos, Saved> before = new HashMap<>();
        for (BlockPos pos : BlockPos.betweenClosed(
                box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
            BlockPos immutable = pos.immutable();
            BlockEntity entity = level.getBlockEntity(immutable);
            CompoundTag entityTag = entity != null
                    ? entity.saveWithFullMetadata(level.registryAccess())
                    : null;
            before.put(immutable, new Saved(level.getBlockState(immutable), entityTag));
        }

        Deque<Snapshot> stack = HISTORY.computeIfAbsent(player.getUUID(), id -> new ArrayDeque<>());
        stack.push(new Snapshot(level.dimension(), before));
        while (stack.size() > MAX_STEPS) {
            stack.removeLast();
        }
        return true;
    }

    /**
     * Puts back the most recent build.
     *
     * @return how many blocks were restored, or -1 if there was nothing to undo
     *         in this dimension
     */
    public static int undo(ServerPlayer player, ServerLevel level) {
        Deque<Snapshot> stack = HISTORY.get(player.getUUID());
        if (stack == null || stack.isEmpty()) {
            return -1;
        }
        if (!stack.peek().dimension().equals(level.dimension())) {
            return -1;
        }

        Snapshot snapshot = stack.pop();
        for (Map.Entry<BlockPos, Saved> entry : snapshot.before().entrySet()) {
            BlockPos pos = entry.getKey();
            Saved saved = entry.getValue();
            level.setBlock(pos, saved.state(), Block.UPDATE_CLIENTS);
            if (saved.blockEntity() != null) {
                BlockEntity entity = level.getBlockEntity(pos);
                if (entity != null) {
                    entity.loadWithComponents(saved.blockEntity(), level.registryAccess());
                }
            }
        }
        return snapshot.before().size();
    }

    public static boolean hasUndo(ServerPlayer player) {
        Deque<Snapshot> stack = HISTORY.get(player.getUUID());
        return stack != null && !stack.isEmpty();
    }

    /** Drop a player's history when they leave, so it does not linger. */
    public static void forget(UUID playerId) {
        HISTORY.remove(playerId);
    }
}
