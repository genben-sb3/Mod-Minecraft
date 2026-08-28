package com.boyben.holobuild;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Settings players can change without touching the code.
 *
 * Server side, because building happens on the server - in single player that
 * is still your own game, so the file lives with your world.
 */
public final class Config {
    private Config() {}

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.IntValue BLOCKS_PER_TICK = BUILDER
            .comment("How many blocks to place per tick when building.",
                    "Minecraft runs 20 ticks per second, so 40 means about 800 blocks a second.",
                    "Set to 0 to place everything at once, with no animation.")
            .defineInRange("blocksPerTick", 40, 0, 100_000);

    public static final ModConfigSpec.BooleanValue PLACE_SOUND = BUILDER
            .comment("Play the normal block placing sound while building.")
            .define("placeSound", true);

    public static final ModConfigSpec SPEC = BUILDER.build();

    public static int blocksPerTick() {
        return BLOCKS_PER_TICK.get();
    }

    public static boolean placeSound() {
        return PLACE_SOUND.get();
    }
}
