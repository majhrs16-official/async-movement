package me.majhrs16.am.mixin;

import me.majhrs16.am.common.CameraController;
import me.majhrs16.am.shared.CameraMouseHandler;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Camera mixin for the legacy MouseHandler shape (1.16.5 - 1.20.4), where
 * {@code turnPlayer()} applies accumulated deltas directly.
 *
 * <p>While the async poller owns the camera, vanilla mouse movement is ignored.
 * The poller calls {@link #asyncmovement$applyTurn(double, double)}, which feeds
 * the cursor delta back into the same vanilla method. That preserves the
 * configured sensitivity, inversion and smooth-camera behavior.</p>
 */
@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin implements CameraMouseHandler {

    @Shadow
    private double accumulatedDX;

    @Shadow
    private double accumulatedDY;

    @Shadow
    private double xpos;

    @Shadow
    private double ypos;

    @Shadow
    public abstract void turnPlayer();

    @Inject(method = "onMove", at = @At("HEAD"), cancellable = true)
    private void asyncmovement$skipVanillaMove(long window, double cursorX, double cursorY, CallbackInfo ci) {
        CameraController.sampleFromClientThread();
        if (CameraController.shouldSuppressVanilla()) {
            // Keep vanilla's baseline current so opening a screen or falling
            // back to vanilla does not produce a large one-frame camera jump.
            this.xpos = cursorX;
            this.ypos = cursorY;
            ci.cancel();
        }
    }

    @Inject(method = "turnPlayer", at = @At("HEAD"), cancellable = true)
    private void asyncmovement$skipVanillaTurn(CallbackInfo ci) {
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
            accumulatedDX += dx;
            accumulatedDY += dy;
            turnPlayer();
        }
    }
}
