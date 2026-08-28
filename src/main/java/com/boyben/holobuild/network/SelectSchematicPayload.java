package com.boyben.holobuild.network;

import java.nio.file.Files;
import java.nio.file.Path;

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

    /** Longer than any sensible file name; stops a client sending a huge string. */
    private static final int MAX_NAME_LENGTH = 128;

    public static final Type<SelectSchematicPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(HoloBuild.MODID, "select_schematic"));

    public static final StreamCodec<ByteBuf, SelectSchematicPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(MAX_NAME_LENGTH), SelectSchematicPayload::schematic,
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
        if (!isSafeName(name) || !existsInFolder(name)) {
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
     * Only plain file names. Anything that could climb out of the schematics
     * folder - slashes, backslashes, "..", a drive letter - is rejected.
     */
    private static boolean isSafeName(String name) {
        if (name.isEmpty() || name.length() > MAX_NAME_LENGTH) {
            return false;
        }
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            boolean allowed = (c >= 'a' && c <= 'z')
                    || (c >= 'A' && c <= 'Z')
                    || (c >= '0' && c <= '9')
                    || c == '_' || c == '-' || c == ' ' || c == '.';
            if (!allowed) {
                return false;
            }
        }
        return !name.contains("..");
    }

    /** Second line of defence: the file has to really sit in the folder. */
    private static boolean existsInFolder(String name) {
        Path folder = SchematicLoader.getSchematicFolder().toAbsolutePath().normalize();
        Path file = folder.resolve(name + ".nbt").toAbsolutePath().normalize();
        return file.startsWith(folder) && Files.isRegularFile(file);
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
