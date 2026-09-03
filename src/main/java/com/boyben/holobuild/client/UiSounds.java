package com.boyben.holobuild.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

/**
 * The small noises the menu makes.
 *
 * Buttons already click by themselves; these are for the things that happen
 * without a button being pressed in the usual way, where a sound is the only
 * confirmation that anything happened at all. Choosing a blueprint is the
 * clearest case: the menu stays open and the list looks the same, so without a
 * sound the click feels like it was missed.
 *
 * <p>All of them are vanilla sounds. Shipping our own would mean a sound pack,
 * a licence question and a bigger download, for something a well-chosen pitch
 * does just as well.
 */
public final class UiSounds {
    private UiSounds() {}

    private static void play(SoundEvent sound, float pitch) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getSoundManager() != null) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch));
        }
    }

    /** A blueprint was picked. Bright and short, so it reads as "yes, that one". */
    public static void chosen() {
        play(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.5f);
    }

    /** A star was switched on or off - the pitch says which way. */
    public static void favourite(boolean on) {
        play(SoundEvents.AMETHYST_BLOCK_CHIME, on ? 1.4f : 0.8f);
    }

    /** Turning a page in the guide. */
    public static void page() {
        play(SoundEvents.BOOK_PAGE_TURN, 1.0f);
    }
}
