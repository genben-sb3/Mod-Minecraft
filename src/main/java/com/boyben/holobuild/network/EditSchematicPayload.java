package com.boyben.holobuild.network;

import java.io.IOException;
import java.util.List;
import java.util.Set;

import com.boyben.holobuild.HoloBuild;
import com.boyben.holobuild.edit.EditIo;
import com.boyben.holobuild.edit.SchematicEdit;
import com.boyben.holobuild.schematic.SchematicLoader;

import io.netty.buffer.ByteBuf;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * "I clicked these blocks away - save the result under this name."
 *
 * Only the positions travel, never the finished building. A client that could
 * hand over a whole schematic could hand over anything, and the server would
 * have no way of telling a genuine edit from an invented one. With positions,
 * the server loads the real file itself and can only ever remove from it.
 *
 * @param source  the building that was opened
 * @param target  what to call the result
 * @param removed which blocks to leave out, in schematic coordinates
 */
public record EditSchematicPayload(String source, String target, List<BlockPos> removed)
        implements CustomPacketPayload {

    /**
     * As many single blocks as anyone will click away by hand in one sitting.
     *
     * The cap is here because this arrives over the network: without one, a
     * modified client could send a list long enough to keep the server busy or
     * fill its memory. Anyone wanting to remove more than this has
     * {@code /holobuild erase}, which does it by block type in one go.
     */
    public static final int MAX_REMOVED = 4096;

    public static final Type<EditSchematicPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(HoloBuild.MODID, "edit_schematic"));

    public static final StreamCodec<ByteBuf, EditSchematicPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.stringUtf8(SchematicLoader.MAX_NAME_LENGTH),
                    EditSchematicPayload::source,
                    ByteBufCodecs.stringUtf8(SchematicLoader.MAX_NAME_LENGTH),
                    EditSchematicPayload::target,
                    BlockPos.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_REMOVED)),
                    EditSchematicPayload::removed,
                    EditSchematicPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** The positions as a set, which is how the edit wants them. */
    public Set<BlockPos> removedSet() {
        return Set.copyOf(removed);
    }

    /**
     * Applies the edit to a working copy.
     *
     * Kept apart from the network handling so the rule can be tested without a
     * connection: an edit that removes nothing, or one that would leave the
     * building empty, must be refused rather than written out.
     *
     * @return how many blocks went, or a negative number for a refusal
     */
    public static int apply(SchematicEdit edit, Set<BlockPos> removed) {
        int gone = edit.removeAt(removed);
        if (gone == 0) {
            return NOTHING_MATCHED;
        }
        if (edit.blockCount() == 0) {
            return WOULD_BE_EMPTY;
        }
        return gone;
    }

    /**
     * Loads the real file, applies the removals and saves the result.
     *
     * The source is loaded here rather than taken from the message, so the only
     * thing a client can do is name a building and ask for blocks to be left
     * out of it. Names go through the same check as everywhere else - they end
     * up as file paths, and a name off the network is never to be trusted.
     */
    public static void handle(EditSchematicPayload payload, IPayloadContext context) {
        Player player = context.player();
        if (!(player instanceof ServerPlayer sender)) {
            return;
        }

        // Same bar as /holobuild save, which also writes a file. Creative
        // counts too: someone who may place any block at will is not being
        // held back by a schematic file.
        if (!sender.hasPermissions(2) && !sender.isCreative()) {
            say(sender, "message.holobuild.edit_not_allowed", ChatFormatting.RED);
            return;
        }
        if (payload.removed().size() > MAX_REMOVED) {
            // The codec caps this too; this is the second line, in case the
            // limits ever drift apart.
            say(sender, "message.holobuild.edit_too_many", ChatFormatting.RED);
            return;
        }

        SchematicEdit edit;
        try {
            edit = EditIo.open(payload.source(), sender.level());
        } catch (IOException | RuntimeException e) {
            HoloBuild.LOGGER.error("Schematic zum Bearbeiten nicht ladbar: {}", payload.source(), e);
            say(sender, "message.holobuild.load_failed", ChatFormatting.RED, payload.source());
            return;
        }
        if (edit == null) {
            say(sender, "message.holobuild.missing_file", ChatFormatting.RED, payload.source());
            return;
        }

        int gone = apply(edit, payload.removedSet());
        if (gone == NOTHING_MATCHED) {
            say(sender, "message.holobuild.edit_nothing_removed", ChatFormatting.YELLOW);
            return;
        }
        if (gone == WOULD_BE_EMPTY) {
            say(sender, "message.holobuild.edit_would_empty", ChatFormatting.YELLOW);
            return;
        }

        try {
            if (!EditIo.save(edit, payload.target())) {
                say(sender, "message.holobuild.bad_name", ChatFormatting.RED, payload.target());
                return;
            }
        } catch (IOException e) {
            HoloBuild.LOGGER.error("Bearbeitetes Schematic nicht gespeichert: {}", payload.target(), e);
            say(sender, "message.holobuild.save_failed", ChatFormatting.RED, payload.target());
            return;
        }

        sender.displayClientMessage(Component.translatable("message.holobuild.edit_saved",
                gone, payload.target()).withStyle(ChatFormatting.GREEN), false);
    }

    private static void say(ServerPlayer player, String key, ChatFormatting colour, Object... args) {
        player.displayClientMessage(
                Component.translatable(key, args).withStyle(colour), false);
    }

    /** None of the given spots held a block. */
    public static final int NOTHING_MATCHED = -1;

    /** Removing them all would leave a file that builds nothing. */
    public static final int WOULD_BE_EMPTY = -2;
}
