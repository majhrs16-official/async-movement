package me.majhrs16.am.shared;

/**
 * Implemented by the version-specific {@code MouseHandler} mixin. Keeping the
 * interface in the shared layer lets {@link MinecraftCameraSink} compile for
 * every version, including 26.3 where the mixin is intentionally absent.
 */
public interface CameraMouseHandler {

    /**
     * Applies a cursor delta using vanilla sensitivity, inversion and smoothing
     * rules. Called from the poller thread.
     */
    void asyncmovement$applyTurn(double dx, double dy);
}
