package me.majhrs16.am.common;

/**
 * Reads the cursor for the asynchronous camera. The method can be called from
 * the client thread as well as from the poller thread. GLFW uses it on the
 * client thread to capture a sample just before GLFW recenters the cursor.
 */
@FunctionalInterface
public interface CameraCursorSampler {

    boolean read(double[] position);
}
