package com.boyben.holobuild.registry;

import com.boyben.holobuild.HoloBuild;
import com.boyben.holobuild.item.BlueprintWandItem;
import com.boyben.holobuild.item.SelectionWandItem;

import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** All items HoloBuild adds to the game. */
public final class ModItems {
    private ModItems() {}

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(HoloBuild.MODID);

    /** Right-click a block with this to place the selected schematic there. */
    public static final DeferredItem<BlueprintWandItem> BLUEPRINT_WAND =
            ITEMS.registerItem("blueprint_wand", BlueprintWandItem::new, new Item.Properties().stacksTo(1));

    /** Mark two corners, then save the area with /holobuild save. */
    public static final DeferredItem<SelectionWandItem> SELECTION_WAND =
            ITEMS.registerItem("selection_wand", SelectionWandItem::new, new Item.Properties().stacksTo(1));
}
