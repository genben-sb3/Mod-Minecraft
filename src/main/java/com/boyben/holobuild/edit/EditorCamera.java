package com.boyben.holobuild.edit;

import java.util.List;

import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Where the editor is looking from, and which block is under the cursor.
 *
 * The important decision here is that there is only one transform. Drawing a
 * turning model and working out what was clicked are the same maths, and if
 * they were written twice they would eventually disagree - the model would turn
 * one way and the clicks would land as if it had turned the other. So this
 * builds a matrix, the screen draws with it, and picking projects with it. One
 * source of truth, and neither side can drift.
 *
 * <p>Nothing here touches Minecraft. JOML is a plain maths library, so the
 * angles, the clamping and the picking can all be tested without a game -
 * which matters, because every mistake in this file is a mistake that looks
 * like the mouse is broken rather than like the code is.
 */
public final class EditorCamera {

    /** Straight down or straight up would flip the model inside out. */
    public static final float MAX_PITCH = 89.0f;

    /**
     * How far forward the model sits in the menu.
     *
     * Far enough to be in front of the panel behind it and behind any tooltip
     * in front. A constant offset, so it changes nothing about which block is
     * nearest - only where the whole model sits in the stack of things drawn.
     */
    private static final float GUI_DEPTH = 200.0f;

    public static final float MIN_ZOOM = 0.25f;
    public static final float MAX_ZOOM = 4.0f;

    private float yaw = 35.0f;
    private float pitch = 22.0f;
    private float zoom = 1.0f;

    public float yaw() {
        return yaw;
    }

    public float pitch() {
        return pitch;
    }

    public float zoom() {
        return zoom;
    }

    /**
     * Turns the model by a drag of the mouse.
     *
     * Yaw wraps, because spinning right round is fine and a number that grew
     * for ever would eventually lose its precision. Pitch stops short of
     * straight up and down, where the model would turn inside out and the
     * controls would reverse.
     */
    public void turn(float byYaw, float byPitch) {
        yaw = wrap(yaw + byYaw);
        pitch = Math.max(-MAX_PITCH, Math.min(MAX_PITCH, pitch + byPitch));
    }

    /** @param factor above 1 moves closer, below 1 further away */
    public void zoomBy(float factor) {
        if (factor <= 0 || !Float.isFinite(factor)) {
            return;      // a scroll event gone wrong must not blank the view
        }
        zoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, zoom * factor));
    }

    public void reset() {
        yaw = 35.0f;
        pitch = 22.0f;
        zoom = 1.0f;
    }

    private static float wrap(float degrees) {
        float wrapped = degrees % 360.0f;
        return wrapped < 0 ? wrapped + 360.0f : wrapped;
    }

    /**
     * The transform from block position to screen position.
     *
     * Built in the same order the screen applies it: move to the middle of the
     * panel, scale (with the vertical axis negated, because a GUI counts y
     * downwards and a model counts it up), tilt, spin, and finally shift so the
     * building turns around its middle rather than its corner.
     *
     * @param unitScale how many pixels one block is across before zoom
     */
    public Matrix4f transform(float centreX, float centreY, float unitScale,
                              float sizeX, float sizeY, float sizeZ) {

        float scale = unitScale * zoom;
        return new Matrix4f()
                .translate(centreX, centreY, GUI_DEPTH)
                .scale(scale, -scale, scale)
                .rotateX((float) Math.toRadians(pitch))
                .rotateY((float) Math.toRadians(yaw))
                .translate(-sizeX / 2.0f, -sizeY / 2.0f, -sizeZ / 2.0f);
    }

    /**
     * Which block the cursor is over.
     *
     * Every block is projected and the ones near the cursor are collected; of
     * those, the one nearest the viewer wins. Front-to-back matters: clicking a
     * wall should hit the wall, not the far side of the room behind it.
     *
     * <p>Nearest means the largest z. In a GUI Minecraft draws higher z on top,
     * so what is drawn last - and therefore visible - is what a click has to
     * pick. Getting that sign backwards would let every click reach through the
     * building and grab the block behind it.
     *
     * @param centres block centres in model coordinates
     * @param radius  how close in pixels counts as a hit
     * @return index into {@code centres}, or -1 for nothing
     */
    public static int pick(Matrix4f transform, List<Vector3f> centres,
                           float mouseX, float mouseY, float radius) {

        int best = -1;
        float bestDepth = 0;
        float radiusSquared = radius * radius;

        Vector3f screen = new Vector3f();
        for (int i = 0; i < centres.size(); i++) {
            transform.transformPosition(centres.get(i), screen);

            float dx = screen.x - mouseX;
            float dy = screen.y - mouseY;
            if (dx * dx + dy * dy > radiusSquared) {
                continue;
            }
            if (best == -1 || screen.z > bestDepth) {
                best = i;
                bestDepth = screen.z;
            }
        }
        return best;
    }
}
