package com.boyben.holobuild.registry;

import com.boyben.holobuild.HoloBuild;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The HoloBuild tab in the creative inventory. */
public final class ModTabs {
    private ModTabs() {}

    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, HoloBuild.MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> HOLOBUILD_TAB =
            TABS.register("holobuild_tab", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.holobuild"))
                    .withTabsBefore(CreativeModeTabs.SPAWN_EGGS)
                    .icon(() -> ModItems.BLUEPRINT_WAND.get().getDefaultInstance())
                    .displayItems((parameters, output) -> output.accept(ModItems.BLUEPRINT_WAND.get()))
                    .build());
}
