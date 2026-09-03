package com.boyben.holobuild.network;

import com.boyben.holobuild.HoloBuild;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** Registers the messages HoloBuild sends between client and server. */
@EventBusSubscriber(modid = HoloBuild.MODID)
public final class ModNetwork {
    private ModNetwork() {}

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToServer(
                AdjustBlueprintPayload.TYPE,
                AdjustBlueprintPayload.STREAM_CODEC,
                AdjustBlueprintPayload::handle);
        registrar.playToServer(
                EditSchematicPayload.TYPE,
                EditSchematicPayload.STREAM_CODEC,
                EditSchematicPayload::handle);
        registrar.playToServer(
                SelectSchematicPayload.TYPE,
                SelectSchematicPayload.STREAM_CODEC,
                SelectSchematicPayload::handle);
    }
}
