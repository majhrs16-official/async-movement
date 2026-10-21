package me.majhrs16.am.common;

/**
 * Applies a camera delta captured by the asynchronous poller.
 *
 * <p>The implementation lives in the Minecraft-specific layer because the
 * sensitivity, inversion and smoothing rules belong to
 * {@code net.minecraft.client.MouseHandler}. The common core only computes the
 * cursor delta and hands it over.</p>
 */
public interface CameraSink {

    /**
     * Returns whether the sink is actually connected to a patched Minecraft
     * mouse handler. If it is not, the core must leave vanilla camera handling
     * untouched.
     */
    default boolean isAvailable() {
        return true;
    }

    void applyDelta(double dx, double dy);
}
