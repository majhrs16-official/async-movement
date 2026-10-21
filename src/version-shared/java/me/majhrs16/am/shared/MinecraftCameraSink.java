package me.majhrs16.am.shared;

import me.majhrs16.am.common.CameraController;
import me.majhrs16.am.common.CameraSink;
import net.minecraft.client.Minecraft;

/**
 * Bridges the common camera poller to the patched {@code MouseHandler}.
 *
 * <p>The mixin is only present in GLFW modules. On SDL/26.3 the mouse handler
 * does not implement {@link CameraMouseHandler}, so {@link #isAvailable()}
 * returns false and the core leaves vanilla camera handling untouched.</p>
 */
public final class MinecraftCameraSink implements CameraSink {

    private final Minecraft client;

    public MinecraftCameraSink(Minecraft client) {
        this.client = client;
    }

    @Override
    public boolean isAvailable() {
        return client.mouseHandler instanceof CameraMouseHandler
                && client.mouseHandler.isMouseGrabbed();
    }

    @Override
    public void applyDelta(double dx, double dy) {
        if (!CameraController.isActive() || !client.isWindowActive()) {
            return;
        }
        if (client.mouseHandler instanceof CameraMouseHandler) {
            ((CameraMouseHandler) client.mouseHandler).asyncmovement$applyTurn(dx, dy);
        }
    }
}
