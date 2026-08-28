package com.boyben.holobuild.registry;

import com.boyben.holobuild.HoloBuild;
import com.boyben.holobuild.component.BlueprintSettings;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Extra data HoloBuild attaches to item stacks. */
public final class ModComponents {
    private ModComponents() {}

    public static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, HoloBuild.MODID);

    /**
     * Selected schematic plus its orientation, living on the wand itself.
     * Minecraft syncs data components to the client automatically, which is
     * exactly what the hologram renderer needs.
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<BlueprintSettings>> BLUEPRINT =
            COMPONENTS.registerComponentType("blueprint", builder -> builder
                    .persistent(BlueprintSettings.CODEC)
                    .networkSynchronized(BlueprintSettings.STREAM_CODEC));
}
