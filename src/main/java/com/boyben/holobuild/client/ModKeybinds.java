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
    public static final KeyMapping OPEN = key("open", GLFW.GLFW_KEY_G);

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
            event.register(OPEN);
        }
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || !holdingWand(player)) {
            // Still drain the queue, otherwise presses pile up and all fire at
            // once the moment the wand is picked up.
            drain();
            return;
        }

        // Opening a screen mid-tick is fine, but only when nothing else is open.
        if (OPEN.consumeClick() && mc.screen == null) {
            mc.setScreen(new SchematicScreen());
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
        while (OPEN.consumeClick()) { /* discard */ }
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
