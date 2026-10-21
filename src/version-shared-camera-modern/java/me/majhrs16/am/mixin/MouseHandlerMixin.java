package me.majhrs16.am.mixin;

import com.mojang.blaze3d.Blaze3D;
import me.majhrs16.am.common.CameraController;
import me.majhrs16.am.shared.CameraMouseHandler;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Camera mixin for the newer MouseHandler shape (1.20.6 - 26.2), where deltas
 * accumulate in {@code onMove} and are consumed by
 * {@code handleAccumulatedMovement()} on the client thread.
 *
 * <p>The async poller bypasses the client-thread consumer and calls vanilla's
 * private {@code turnPlayer(double)} with the elapsed time. This keeps
 * sensitivity, inversion and smooth-camera math identical to vanilla.</p>
 */
@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin implements CameraMouseHandler {

    private static final double MAX_FRAME_DELTA = 0.1D;

    @Shadow
    private double accumulatedDX;

    @Shadow
    private double accumulatedDY;

    @Shadow
    private double xpos;

    @Shadow
    private double ypos;

    @Shadow
    private double lastHandleMovementTime;

    @Shadow
    private void turnPlayer(double frameTime) {
        throw new AssertionError("shadow method");
    }

    @Inject(method = "onMove", at = @At("HEAD"), cancellable = true)
    private void asyncmovement$skipVanillaMove(long window, double cursorX, double cursorY, CallbackInfo ci) {
        CameraController.sampleFromClientThread();
        if (CameraController.shouldSuppressVanilla()) {
            this.xpos = cursorX;
            this.ypos = cursorY;
            ci.cancel();
        }
    }

    @Inject(method = "handleAccumulatedMovement", at = @At("HEAD"), cancellable = true)
    private void asyncmovement$skipVanillaMovement(CallbackInfo ci) {
        if (CameraController.shouldSuppressVanilla()) {
            synchronized (CameraController.lock()) {
                accumulatedDX = 0.0D;
                accumulatedDY = 0.0D;
            }
            ci.cancel();
        }
    }

    @Override
    public void asyncmovement$applyTurn(double dx, double dy) {
        synchronized (CameraController.lock()) {
            double now = Blaze3D.getTime();
            double frameDelta = now - lastHandleMovementTime;
            lastHandleMovementTime = now;
            if (frameDelta < 0.0D) {
                frameDelta = 0.0D;
            } else if (frameDelta > MAX_FRAME_DELTA) {
                frameDelta = MAX_FRAME_DELTA;
            }

            accumulatedDX += dx;
            accumulatedDY += dy;
            try {
                turnPlayer(frameDelta);
            } finally {
                accumulatedDX = 0.0D;
                accumulatedDY = 0.0D;
            }
        }
    }
}
