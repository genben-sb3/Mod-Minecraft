package com.boyben.holobuild.item;

import java.util.List;

import com.boyben.holobuild.component.SaveOptions;
import com.boyben.holobuild.component.Selection;
import com.boyben.holobuild.registry.ModComponents;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
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

    /**
     * Spells out the controls, and says which corner is still missing.
     *
     * The middle line is the important one: after the first corner it tells the
     * player to hold SHIFT. Without it the wand seems to do nothing on the
     * second click, because a plain right-click just moves corner one again.
     */
    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                List<Component> lines, TooltipFlag flag) {
        WandTooltip.control(lines, "tooltip.holobuild.rmb", "tooltip.holobuild.selection.set_one");
        WandTooltip.control(lines, "tooltip.holobuild.shift_rmb", "tooltip.holobuild.selection.set_two");
        WandTooltip.blank(lines);

        Selection selection = stack.getOrDefault(ModComponents.SELECTION.get(), Selection.EMPTY);
        if (selection.isComplete()) {
            Vec3i size = selection.size();
            WandTooltip.state(lines, Component.translatable("tooltip.holobuild.selection.ready",
                    size.getX() + "x" + size.getY() + "x" + size.getZ(),
                    selection.volume()), ChatFormatting.GREEN);
            WandTooltip.state(lines, Component.translatable("tooltip.holobuild.selection.save"),
                    ChatFormatting.YELLOW);

        } else if (selection.first().isPresent() || selection.second().isPresent()) {
            // One corner down. This is exactly where players get stuck, so the
            // next step gets its own bright line instead of a hint in grey.
            BlockPos corner = selection.first().orElseGet(() -> selection.second().orElseThrow());
            WandTooltip.state(lines, Component.translatable("tooltip.holobuild.selection.one_done",
                    corner.getX(), corner.getY(), corner.getZ()), ChatFormatting.GRAY);
            WandTooltip.state(lines, Component.translatable("tooltip.holobuild.selection.need_two"),
                    ChatFormatting.YELLOW);

        } else {
            WandTooltip.state(lines, Component.translatable("tooltip.holobuild.selection.empty"),
                    ChatFormatting.DARK_GRAY);
        }

        WandTooltip.state(lines, Component.translatable("tooltip.holobuild.selection.keeps",
                        keptList(stack.getOrDefault(ModComponents.SAVE_OPTIONS.get(), SaveOptions.DEFAULT))),
                ChatFormatting.DARK_GRAY);
    }

    /**
     * Names what a save will include, so the settings are not invisible.
     *
     * Without this line the options would only exist inside a command, and a
     * player wondering why their chests came back empty would have no way to
     * find out that contents had been switched off.
     */
    private static Component keptList(SaveOptions options) {
        MutableComponent list = null;
        list = append(list, options.air(), "tooltip.holobuild.keeps.air");
        list = append(list, options.contents(), "tooltip.holobuild.keeps.contents");
        list = append(list, options.entities(), "tooltip.holobuild.keeps.entities");
        return list == null ? Component.translatable("tooltip.holobuild.keeps.nothing") : list;
    }

    private static MutableComponent append(MutableComponent list, boolean on, String key) {
        if (!on) {
            return list;
        }
        Component part = Component.translatable(key);
        return list == null ? part.copy() : list.append(Component.literal(", ")).append(part);
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
