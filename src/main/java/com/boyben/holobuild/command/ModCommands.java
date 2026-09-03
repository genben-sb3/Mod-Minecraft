package com.boyben.holobuild.command;

import com.boyben.holobuild.HoloBuild;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** Hooks HoloBuild's commands into the game. */
@EventBusSubscriber(modid = HoloBuild.MODID)
public final class ModCommands {
    private ModCommands() {}

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        // The build context is what lets a command argument name a block:
        // block ids only mean something against a registry.
        HoloBuildCommand.register(event.getDispatcher(), event.getBuildContext());
    }
}
