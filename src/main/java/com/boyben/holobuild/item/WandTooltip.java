package com.boyben.holobuild.item;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/**
 * Builds the hover text the two wands share.
 *
 * This exists because of a real failure: the Selection Wand needs SHIFT for the
 * second corner, nothing in the game said so, and so the wand looked broken. A
 * control nobody can discover is the same as a control that does not work. The
 * tooltip is the one place that is always in front of the player, so the
 * controls live here rather than in a wiki nobody opens.
 */
final class WandTooltip {
    private WandTooltip() {}

    /** One control line: the key in colour, what it does in grey. */
    static void control(List<Component> lines, String keyKey, String actionKey) {
        lines.add(Component.empty()
                .append(Component.translatable(keyKey).withStyle(ChatFormatting.AQUA))
                .append(Component.literal("  "))
                .append(Component.translatable(actionKey).withStyle(ChatFormatting.GRAY)));
    }

    /** A line about what the wand is holding right now, rather than what it can do. */
    static void state(List<Component> lines, Component text, ChatFormatting colour) {
        lines.add(text.copy().withStyle(colour));
    }

    static void blank(List<Component> lines) {
        lines.add(Component.empty());
    }
}
