package com.boyben.holobuild.network;


import com.boyben.holobuild.HoloBuild;
import com.boyben.holobuild.component.BlueprintSettings;
import com.boyben.holobuild.item.BlueprintWandItem;
import com.boyben.holobuild.registry.ModComponents;
import com.boyben.holobuild.schematic.SchematicLoader;

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
 * Sent when the player picks a schematic in the selection screen.
 *
 * @param schematic file name without .nbt
 */
public record SelectSchematicPayload(String schematic) implements CustomPacketPayload {

    public static final Type<SelectSchematicPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(HoloBuild.MODID, "select_schematic"));

    public static final StreamCodec<ByteBuf, SelectSchematicPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(SchematicLoader.MAX_NAME_LENGTH), SelectSchematicPayload::schematic,
            SelectSchematicPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SelectSchematicPayload payload, IPayloadContext context) {
        Player player = context.player();
        if (!player.isCreative()) {
            return;
        }

        String name = payload.schematic();
        if (!SchematicLoader.isSafeName(name) || !existsInFolder(name)) {
            // Never trust a name off the network: it ends up in a file path.
            HoloBuild.LOGGER.warn("Verdaechtiger Schematic-Name von {} abgelehnt: {}",
                    player.getGameProfile().getName(), name);
            player.displayClientMessage(
                    Component.translatable("message.holobuild.missing_file", name)
                            .withStyle(ChatFormatting.RED), false);
            return;
        }

        ItemStack stack = findWand(player);
        if (stack == null) {
            return;
        }

        BlueprintSettings settings = stack.getOrDefault(ModComponents.BLUEPRINT.get(), BlueprintSettings.EMPTY);
        stack.set(ModComponents.BLUEPRINT.get(), settings.withSchematic(name));

        player.displayClientMessage(
                Component.translatable("message.holobuild.selected", name)
                        .withStyle(ChatFormatting.GREEN), true);
    }

    /**
     * Second line of defence: a real file has to exist for that name.
     * findByName does the path check and knows every supported extension.
     */
    private static boolean existsInFolder(String name) {
        return SchematicLoader.findByName(name) != null;
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
