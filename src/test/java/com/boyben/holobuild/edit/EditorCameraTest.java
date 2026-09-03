package com.boyben.holobuild.edit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for the editor's camera and for picking a block with the mouse.
 *
 * <p><b>What these do and do not show.</b> They prove the maths is consistent
 * with itself: turning the model moves the blocks, the nearer block wins a
 * click, the limits hold. They cannot prove the picture on screen matches -
 * only a person looking at it can do that. The reason the risk is small anyway
 * is that drawing and picking share one matrix, so if the model is drawn at all,
 * clicks land where it is drawn.
 */
class EditorCameraTest {

    /** A 4x4x4 building drawn into a 200x200 panel, one block = 10 pixels. */
    private static Matrix4f transformOf(EditorCamera camera) {
        return camera.transform(100, 100, 10, 4, 4, 4);
    }

    @Test
    @DisplayName("The middle of the building lands in the middle of the panel")
    void centreOfModelIsCentreOfPanel() {
        EditorCamera camera = new EditorCamera();
        Vector3f centre = new Vector3f();
        transformOf(camera).transformPosition(new Vector3f(2, 2, 2), centre);

        assertEquals(100.0f, centre.x, 0.001f, "waagerecht mittig");
        assertEquals(100.0f, centre.y, 0.001f, "senkrecht mittig");
    }

    @Test
    @DisplayName("Up in the model is up on the screen, not down")
    void verticalAxisIsFlippedForTheScreen() {
        // A GUI counts y downwards. Without the negated scale the building
        // would be drawn standing on its head, and nobody would suspect the
        // camera - it would just look like the schematic was upside down.
        EditorCamera camera = new EditorCamera();
        Matrix4f transform = transformOf(camera);

        Vector3f low = new Vector3f();
        Vector3f high = new Vector3f();
        transform.transformPosition(new Vector3f(2, 0, 2), low);
        transform.transformPosition(new Vector3f(2, 4, 2), high);

        assertTrue(high.y < low.y,
                "Der obere Block muss weiter oben auf dem Bildschirm landen (kleineres y): "
                        + high.y + " gegen " + low.y);
    }

    @Test
    @DisplayName("Turning moves the building")
    void turningChangesWhereBlocksLand() {
        EditorCamera camera = new EditorCamera();
        Vector3f before = new Vector3f();
        transformOf(camera).transformPosition(new Vector3f(0, 2, 0), before);

        camera.turn(90, 0);
        Vector3f after = new Vector3f();
        transformOf(camera).transformPosition(new Vector3f(0, 2, 0), after);

        assertNotEquals(before.x, after.x, 0.001f, "Nach einer Vierteldrehung muss sich etwas tun");
    }

    @Test
    @DisplayName("Pitch stops before the model turns inside out")
    void pitchIsClamped() {
        EditorCamera camera = new EditorCamera();
        camera.turn(0, 1000);
        assertEquals(EditorCamera.MAX_PITCH, camera.pitch(), 0.001f);

        camera.turn(0, -5000);
        assertEquals(-EditorCamera.MAX_PITCH, camera.pitch(), 0.001f);
    }

    @Test
    @DisplayName("Yaw wraps instead of growing for ever")
    void yawWraps() {
        EditorCamera camera = new EditorCamera();
        camera.turn(100000, 0);
        assertTrue(camera.yaw() >= 0 && camera.yaw() < 360,
                "Der Winkel muss im Kreis bleiben, war " + camera.yaw());

        camera.turn(-100000, 0);
        assertTrue(camera.yaw() >= 0 && camera.yaw() < 360,
                "Auch rueckwaerts, war " + camera.yaw());
    }

    @Test
    @DisplayName("Zoom keeps to its limits and survives a nonsense scroll")
    void zoomIsClamped() {
        EditorCamera camera = new EditorCamera();

        for (int i = 0; i < 50; i++) {
            camera.zoomBy(2.0f);
        }
        assertEquals(EditorCamera.MAX_ZOOM, camera.zoom(), 0.001f);

        for (int i = 0; i < 50; i++) {
            camera.zoomBy(0.5f);
        }
        assertEquals(EditorCamera.MIN_ZOOM, camera.zoom(), 0.001f);

        // A stray zero or NaN must not blank the view for the rest of the session.
        float before = camera.zoom();
        camera.zoomBy(0);
        camera.zoomBy(Float.NaN);
        camera.zoomBy(-3);
        assertEquals(before, camera.zoom(), 0.001f);
    }

    @Test
    @DisplayName("A click picks the block in front, not the one behind it")
    void pickTakesTheNearestBlock() {
        // Two blocks at the same spot on screen, different depths. Whichever
        // the transform puts nearer the viewer is the one that was clicked -
        // getting this backwards would let every click reach through a wall.
        Matrix4f transform = new Matrix4f();       // identity: model = screen
        List<Vector3f> blocks = List.of(
                new Vector3f(50, 50, -5),          // further away
                new Vector3f(50, 50, 7),           // nearer the viewer
                new Vector3f(50, 50, 1));

        assertEquals(1, EditorCamera.pick(transform, blocks, 50, 50, 4));
    }

    @Test
    @DisplayName("Clicking empty space picks nothing")
    void pickReturnsNothingWhenNothingIsNear() {
        Matrix4f transform = new Matrix4f();
        List<Vector3f> blocks = List.of(new Vector3f(10, 10, 0));

        assertEquals(-1, EditorCamera.pick(transform, blocks, 200, 200, 4),
                "Weit weg vom Modell darf nichts getroffen werden");
        assertEquals(-1, EditorCamera.pick(transform, List.of(), 10, 10, 4),
                "In einem leeren Bauwerk erst recht nicht");
    }

    @Test
    @DisplayName("The hit radius is a circle, not a square")
    void pickUsesRoundDistance() {
        Matrix4f transform = new Matrix4f();
        List<Vector3f> blocks = List.of(new Vector3f(0, 0, 0));

        // (3,3) is 4.24 away - outside a radius of 4, though both x and y are
        // within 4 on their own. A square test would wrongly count this as a hit.
        assertEquals(-1, EditorCamera.pick(transform, blocks, 3, 3, 4));
        assertEquals(0, EditorCamera.pick(transform, blocks, 2, 2, 4));
    }

    @Test
    @DisplayName("Reset brings the view back to the start")
    void resetRestoresTheStartingView() {
        EditorCamera camera = new EditorCamera();
        float yaw = camera.yaw();
        float pitch = camera.pitch();
        float zoom = camera.zoom();

        camera.turn(123, 45);
        camera.zoomBy(3);
        camera.reset();

        assertEquals(yaw, camera.yaw(), 0.001f);
        assertEquals(pitch, camera.pitch(), 0.001f);
        assertEquals(zoom, camera.zoom(), 0.001f);
    }
}
