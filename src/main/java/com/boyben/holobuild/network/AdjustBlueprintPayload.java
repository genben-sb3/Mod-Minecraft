package com.boyben.holobuild.network;

import com.boyben.holobuild.HoloBuild;
import com.boyben.holobuild.component.BlueprintSettings;
import com.boyben.holobuild.item.BlueprintWandItem;
import com.boyben.holobuild.registry.ModComponents;

import io.netty.buffer.ByteBuf;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Sent when the player presses a HoloBuild key: turn, mirror or move up/down.
 *
 * The wand lives on the server, so the key press has to travel there. The
 * changed data component comes back to the client on its own, which is what
 * makes the hologram update.
 *
 * @param quarterTurns how far to turn, in 90 degree steps
 * @param toggleMirror flip the mirror setting
 * @param yShift       how many blocks to move up (negative for down)
 */
public record AdjustBlueprintPayload(int quarterTurns, boolean toggleMirror, int yShift)
        implements CustomPacketPayload {

    public static final Type<AdjustBlueprintPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(HoloBuild.MODID, "adjust_blueprint"));

    public static final StreamCodec<ByteBuf, AdjustBlueprintPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, AdjustBlueprintPayload::quarterTurns,
            ByteBufCodecs.BOOL, AdjustBlueprintPayload::toggleMirror,
            ByteBufCodecs.VAR_INT, AdjustBlueprintPayload::yShift,
            AdjustBlueprintPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Runs on the server, on the main thread - NeoForge takes care of that for us. */
    public static void handle(AdjustBlueprintPayload payload, IPayloadContext context) {
        Player player = context.player();
        if (!player.isCreative()) {
            return;
        }

        ItemStack stack = findWand(player);
        if (stack == null) {
            return;
        }

        BlueprintSettings settings = stack.getOrDefault(ModComponents.BLUEPRINT.get(), BlueprintSettings.EMPTY);
        if (payload.quarterTurns() != 0) {
            settings = settings.rotatedBy(payload.quarterTurns());
        }
        if (payload.toggleMirror()) {
            settings = settings.toggleMirror();
        }
        if (payload.yShift() != 0) {
            settings = settings.shiftedBy(payload.yShift());
        }
        stack.set(ModComponents.BLUEPRINT.get(), settings);

        player.displayClientMessage(Component.translatable("message.holobuild.adjusted",
                settings.rotation() * 90,
                Component.translatable(settings.mirrored()
                        ? "message.holobuild.mirror_on"
                        : "message.holobuild.mirror_off"),
                settings.yOffset()).withStyle(ChatFormatting.AQUA), true);
    }

    private static ItemStack findWand(Player player) {
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = player.getItemInHand(hand);
            if (stack.getItem() instanceof BlueprintWandItem) {
                return stack;
            }
        }
        return null;
    }
}
