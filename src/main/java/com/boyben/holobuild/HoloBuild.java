package com.boyben.holobuild;

import com.boyben.holobuild.registry.ModComponents;
import com.boyben.holobuild.registry.ModItems;
import com.boyben.holobuild.registry.ModTabs;
import com.boyben.holobuild.schematic.SchematicLoader;
import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

import org.slf4j.Logger;

/**
 * HoloBuild - preview schematics as a hologram and build them instantly.
 *
 * Inspired by Schematica and Litematica, but written from scratch:
 * no code is taken from those mods, only the idea.
 */
@Mod(HoloBuild.MODID)
public class HoloBuild {
    /** Must match mod_id in gradle.properties. */
    public static final String MODID = "holobuild";

    public static final Logger LOGGER = LogUtils.getLogger();

    public HoloBuild(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(this::commonSetup);

        ModComponents.COMPONENTS.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModTabs.TABS.register(modEventBus);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        // Make sure the folder exists before the player goes looking for it,
        // so there is somewhere obvious to drop schematic files.
        SchematicLoader.ensureFolderExists();
        LOGGER.info("HoloBuild bereit. Schematics gehoeren nach: {}", SchematicLoader.getSchematicFolder());
    }
}
