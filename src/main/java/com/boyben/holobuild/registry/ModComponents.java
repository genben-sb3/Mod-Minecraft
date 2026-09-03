package com.boyben.holobuild.registry;

import com.boyben.holobuild.HoloBuild;
import com.boyben.holobuild.component.BlueprintSettings;
import com.boyben.holobuild.component.BuildConfirm;
import com.boyben.holobuild.component.SaveOptions;
import com.boyben.holobuild.component.Selection;

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

    /** The two corners marked with the Selection Wand. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Selection>> SELECTION =
            COMPONENTS.registerComponentType("selection", builder -> builder
                    .persistent(Selection.CODEC)
                    .networkSynchronized(Selection.STREAM_CODEC));

    /** A standing offer to build anyway, after a warning about missing materials. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<BuildConfirm>> BUILD_CONFIRM =
            COMPONENTS.registerComponentType("build_confirm", builder -> builder
                    .persistent(BuildConfirm.CODEC)
                    .networkSynchronized(BuildConfirm.STREAM_CODEC));

    /** What the Selection Wand keeps when it saves: air, contents, entities. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<SaveOptions>> SAVE_OPTIONS =
            COMPONENTS.registerComponentType("save_options", builder -> builder
                    .persistent(SaveOptions.CODEC)
                    .networkSynchronized(SaveOptions.STREAM_CODEC));
}
