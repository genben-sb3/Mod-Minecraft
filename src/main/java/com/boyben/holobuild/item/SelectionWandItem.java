package com.boyben.holobuild.item;

import com.boyben.holobuild.component.Selection;
import com.boyben.holobuild.registry.ModComponents;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/**
 * Marks the two corners of an area, ready to be saved as a schematic.
 *
 * Right-click sets corner one, shift + right-click sets corner two. Saving is
 * done with /holobuild save, which keeps the wand itself simple.
 */
public class SelectionWandItem extends Item {

    public SelectionWandItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }

        if (!player.isCreative()) {
            if (!level.isClientSide) {
                say(player, Component.translatable("message.holobuild.creative_only"), ChatFormatting.RED);
            }
            return InteractionResult.FAIL;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        ItemStack stack = context.getItemInHand();
        BlockPos pos = context.getClickedPos();
        Selection selection = stack.getOrDefault(ModComponents.SELECTION.get(), Selection.EMPTY);

        boolean secondCorner = player.isShiftKeyDown();
        selection = secondCorner ? selection.withSecond(pos) : selection.withFirst(pos);
        stack.set(ModComponents.SELECTION.get(), selection);

        say(player, Component.translatable(
                secondCorner ? "message.holobuild.corner_two" : "message.holobuild.corner_one",
                pos.getX(), pos.getY(), pos.getZ()), ChatFormatting.AQUA);

        if (selection.isComplete()) {
            Vec3i size = selection.size();
            say(player, Component.translatable("message.holobuild.selection_size",
                    size.getX() + "x" + size.getY() + "x" + size.getZ(),
                    selection.volume()), ChatFormatting.GREEN);
        }

        return InteractionResult.CONSUME;
    }

    private static void say(Player player, Component message, ChatFormatting colour) {
        player.displayClientMessage(message.copy().withStyle(colour), true);
    }
}
