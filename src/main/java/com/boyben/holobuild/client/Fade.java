package com.boyben.holobuild.client;

/**
 * Turns "how long ago did this happen" into "how far along is the animation".
 *
 * The drawing itself cannot be tested - a screen needs a running game - but
 * this can, and this is where animations actually go wrong: a progress that
 * slips past 1 makes a colour overflow, one that goes negative makes a
 * highlight flash inside out, and a duration of zero divides by zero. None of
 * those crash. They just look broken, once, on somebody else's machine.
 */
public final class Fade {
    private Fade() {}

    /**
     * @param start    when the animation began
     * @param now      the current time
     * @param duration how long it should take, in milliseconds
     * @return 0 at the start, 1 at the end, never outside that
     */
    public static float progress(long start, long now, long duration) {
        if (duration <= 0) {
            // Nothing to animate over: count it as finished rather than
            // dividing by zero.
            return 1f;
        }
        long elapsed = now - start;
        if (elapsed <= 0) {
            // Also covers a clock that jumped backwards.
            return 0f;
        }
        if (elapsed >= duration) {
            return 1f;
        }
        return (float) elapsed / duration;
    }

    /**
     * Softens a straight 0..1 ramp so it starts and ends gently.
     *
     * The usual smoothstep. Without it a fade begins and stops abruptly, which
     * reads as a flicker rather than as movement.
     */
    public static float smooth(float t) {
        float clamped = Math.max(0f, Math.min(1f, t));
        return clamped * clamped * (3f - 2f * clamped);
    }

    /**
     * Builds a colour with the alpha faded in.
     *
     * @param rgb  the colour without alpha, e.g. 0xFFD24A
     * @param t    0..1, how opaque it should be
     * @param peak alpha at t = 1, 0..255
     */
    public static int colour(int rgb, float t, int peak) {
        // Never quite reaches zero: Minecraft's font renderer treats a very low
        // alpha as a hint to skip drawing altogether, so a fade that starts at
        // 0 pops into view instead of growing.
        int alpha = Math.round(Math.max(0.06f, Math.min(1f, t)) * peak);
        return (Math.min(255, Math.max(1, alpha)) << 24) | (rgb & 0xFFFFFF);
    }
}
