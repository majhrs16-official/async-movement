package me.majhrs16.am.common;

/**
 * Reads raw key state. Implementations live in the Minecraft version modules.
 * The poller only calls this from its own daemon thread.
 */
public interface KeyStateSource {

    /**
     * Number of logical key codes to inspect. The source decides the valid range
     * (GLFW key codes or SDL scancodes depending on the Minecraft version).
     */
    int keyCount();

    boolean isDown(int key);

    /**
     * Mouse button codes understood by {@code InputConstants.Type.MOUSE} for
     * this Minecraft version. GLFW versions use 0..7; the SDL version uses 1..8.
     * Returning an empty array disables mouse-button polling for the source.
     */
    default int[] mouseButtonCodes() {
        return new int[0];
    }

    /**
     * Reads one mouse button. The code is one of {@link #mouseButtonCodes()}.
     * This is called from the poller thread, never from the render thread.
     */
    default boolean isMouseButtonDown(int button) {
        return false;
    }

    /**
     * Whether this source can expose the cursor position needed for
     * asynchronous camera capture. GLFW sources return true; the SDL 26.3
     * source returns false because its relative mouse API is main-thread-only
     * and does not expose a cursor delta that can be polled independently.
     */
    default boolean supportsCameraCapture() {
        return false;
    }

    /**
     * Whether asynchronous camera capture is currently usable. GLFW sources
     * return false while {@code Raw Mouse Input} is enabled: in that mode the
     * OS cursor does not move and polling its position would freeze the camera.
     * Called from the poller thread.
     */
    default boolean isCameraCaptureAvailable() {
        return false;
    }

    /**
     * Reads the cursor position into {@code position[0]} and {@code position[1]}.
     * Returns false when the cursor is not in the grabbed/disabled mode or the
     * platform cannot provide a position. Called from the poller thread.
     */
    default boolean readCameraCursor(double[] position) {
        return false;
    }

    /**
     * Returns true when the cursor was moved to the window center by GLFW's
     * disabled-cursor recentering between two samples. The poller discards that
     * synthetic jump. This is only meaningful for GLFW sources.
     */
    default boolean isCameraRecenter(double previousX, double previousY,
                                     double currentX, double currentY) {
        return false;
    }
}
