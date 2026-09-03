package com.boyben.holobuild.registry;

import com.boyben.holobuild.HoloBuild;
import com.boyben.holobuild.item.BlueprintWandItem;
import com.boyben.holobuild.item.SelectionWandItem;
import com.boyben.holobuild.item.SiteWandItem;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** All items HoloBuild adds to the game. */
public final class ModItems {
    private ModItems() {}

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(HoloBuild.MODID);

    /**
     * Extra reach while a wand is held.
     *
     * Vanilla gives 4.5 blocks (5 in creative) and caps the attribute at 64.
     * Adding 27.5 lands on 32 - far enough to place a whole building without
     * walking around it, still close enough that small mouse movements do not
     * throw the aim across the landscape.
     */
    private static final double EXTRA_REACH = 27.5;

    private static final ResourceLocation REACH_MODIFIER_ID =
            ResourceLocation.fromNamespaceAndPath(HoloBuild.MODID, "wand_reach");

    /** Right-click a block with this to place the selected schematic there. */
    public static final DeferredItem<BlueprintWandItem> BLUEPRINT_WAND =
            ITEMS.registerItem("blueprint_wand", BlueprintWandItem::new, wandProperties());

    /** Mark two corners, then save the area with /holobuild save. */
    public static final DeferredItem<SelectionWandItem> SELECTION_WAND =
            ITEMS.registerItem("selection_wand", SelectionWandItem::new, wandProperties());

    /** Empties the space the chosen building would fill, before building it. */
    public static final DeferredItem<SiteWandItem> SITE_WAND =
            ITEMS.registerItem("site_wand", SiteWandItem::new, wandProperties());

    /**
     * The reach bonus rides on the item, so it applies only while a wand is in
     * the main hand and disappears the moment it is put away - no lasting change
     * to the player.
     */
    private static Item.Properties wandProperties() {
        return new Item.Properties()
                .stacksTo(1)
                .attributes(ItemAttributeModifiers.builder()
                        .add(Attributes.BLOCK_INTERACTION_RANGE,
                                new AttributeModifier(REACH_MODIFIER_ID, EXTRA_REACH,
                                        AttributeModifier.Operation.ADD_VALUE),
                                EquipmentSlotGroup.MAINHAND)
                        .build());
    }
}
