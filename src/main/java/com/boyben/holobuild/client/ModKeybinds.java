package com.boyben.holobuild.client;

import com.boyben.holobuild.HoloBuild;
import com.boyben.holobuild.item.BlueprintWandItem;
import com.boyben.holobuild.network.AdjustBlueprintPayload;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import org.lwjgl.glfw.GLFW;

/** Keys for turning, mirroring and raising the blueprint. Client side only. */
@EventBusSubscriber(modid = HoloBuild.MODID, value = Dist.CLIENT)
public final class ModKeybinds {
    private ModKeybinds() {}

    private static final String CATEGORY = "key.categories.holobuild";

    public static final KeyMapping ROTATE = key("rotate", GLFW.GLFW_KEY_R);
    public static final KeyMapping MIRROR = key("mirror", GLFW.GLFW_KEY_M);
    public static final KeyMapping UP = key("up", GLFW.GLFW_KEY_PAGE_UP);
    public static final KeyMapping DOWN = key("down", GLFW.GLFW_KEY_PAGE_DOWN);

    private static KeyMapping key(String name, int defaultKey) {
        return new KeyMapping("key.holobuild." + name, InputConstants.Type.KEYSYM, defaultKey, CATEGORY);
    }

    @EventBusSubscriber(modid = HoloBuild.MODID, value = Dist.CLIENT)
    public static final class Registration {
        private Registration() {}

        @SubscribeEvent
        public static void onRegisterKeys(RegisterKeyMappingsEvent event) {
            event.register(ROTATE);
            event.register(MIRROR);
            event.register(UP);
            event.register(DOWN);
        }
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || !holdingWand(player)) {
            // Still drain the queue, otherwise presses pile up and all fire at
            // once the moment the wand is picked up.
            drain();
            return;
        }

        int turns = 0;
        while (ROTATE.consumeClick()) {
            turns++;
        }
        boolean mirror = false;
        while (MIRROR.consumeClick()) {
            mirror = !mirror;
        }
        int shift = 0;
        while (UP.consumeClick()) {
            shift++;
        }
        while (DOWN.consumeClick()) {
            shift--;
        }

        if (turns != 0 || mirror || shift != 0) {
            PacketDistributor.sendToServer(new AdjustBlueprintPayload(turns, mirror, shift));
        }
    }

    private static void drain() {
        while (ROTATE.consumeClick()) { /* discard */ }
        while (MIRROR.consumeClick()) { /* discard */ }
        while (UP.consumeClick()) { /* discard */ }
        while (DOWN.consumeClick()) { /* discard */ }
    }

    private static boolean holdingWand(LocalPlayer player) {
        for (InteractionHand hand : InteractionHand.values()) {
            if (player.getItemInHand(hand).getItem() instanceof BlueprintWandItem) {
                return true;
            }
        }
        return false;
    }
}
