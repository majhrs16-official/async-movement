package me.majhrs16.am.shared;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.system.MemoryStack;

import java.nio.DoubleBuffer;

import me.majhrs16.am.common.KeyStateSource;

/**
 * GLFW source for versions whose {@code InputConstants.isKeyDown} takes a
 * {@link Window} instance instead of a raw handle (1.21.10 - 26.2). It reads
 * keyboard keys and mouse buttons; the handle is obtained from the window.
 */
public final class WindowObjectKeySource implements KeyStateSource {

    // macOS dissociates the hardware cursor from the window in disabled mode,
    // so glfwGetCursorPos no longer exposes relative movement. Keep vanilla
    // camera there instead of freezing it.
    private static final boolean MACOS =
            System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).startsWith("mac");

    private static final int[] MOUSE_BUTTONS = {
            GLFW.GLFW_MOUSE_BUTTON_1,
            GLFW.GLFW_MOUSE_BUTTON_2,
            GLFW.GLFW_MOUSE_BUTTON_3,
            GLFW.GLFW_MOUSE_BUTTON_4,
            GLFW.GLFW_MOUSE_BUTTON_5,
            GLFW.GLFW_MOUSE_BUTTON_6,
            GLFW.GLFW_MOUSE_BUTTON_7,
            GLFW.GLFW_MOUSE_BUTTON_8
    };

    private final Window window;
    private final boolean rawMouseMotionSupported;

    public WindowObjectKeySource(Minecraft client) {
        this.window = client.getWindow();
        boolean supported;
        try {
            supported = GLFW.glfwRawMouseMotionSupported();
        } catch (Throwable t) {
            supported = false;
        }
        this.rawMouseMotionSupported = supported;
    }

    @Override
    public int keyCount() {
        return GLFW.GLFW_KEY_LAST + 1;
    }

    @Override
    public boolean isDown(int key) {
        return InputConstants.isKeyDown(window, key);
    }

    @Override
    public int[] mouseButtonCodes() {
        return MOUSE_BUTTONS;
    }

    @Override
    public boolean isMouseButtonDown(int button) {
        return GLFW.glfwGetMouseButton(window.handle(), button) == GLFW.GLFW_PRESS;
    }

    @Override
    public boolean supportsCameraCapture() {
        return !MACOS;
    }

    @Override
    public boolean isCameraCaptureAvailable() {
        return !rawMouseMotionSupported || !isRawMouseMotionEnabled();
    }

    @Override
    public boolean readCameraCursor(double[] position) {
        if (isRawMouseMotionEnabled()) {
            return false;
        }
        if (GLFW.glfwGetInputMode(window.handle(), GLFW.GLFW_CURSOR) != GLFW.GLFW_CURSOR_DISABLED) {
            return false;
        }

        try (MemoryStack stack = MemoryStack.stackPush()) {
            DoubleBuffer x = stack.mallocDouble(1);
            DoubleBuffer y = stack.mallocDouble(1);
            GLFW.glfwGetCursorPos(window.handle(), x, y);
            position[0] = x.get(0);
            position[1] = y.get(0);
        }
        return true;
    }

    @Override
    public boolean isCameraRecenter(double previousX, double previousY,
                                    double currentX, double currentY) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            java.nio.IntBuffer width = stack.mallocInt(1);
            java.nio.IntBuffer height = stack.mallocInt(1);
            GLFW.glfwGetWindowSize(window.handle(), width, height);
            double centerX = width.get(0) / 2.0D;
            double centerY = height.get(0) / 2.0D;
            return nearCenter(currentX, centerX)
                    && nearCenter(currentY, centerY)
                    && !(nearCenter(previousX, centerX) && nearCenter(previousY, centerY));
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean nearCenter(double value, double center) {
        return Math.abs(value - center) <= 1.0D;
    }

    private boolean isRawMouseMotionEnabled() {
        return rawMouseMotionSupported
                && GLFW.glfwGetInputMode(window.handle(), GLFW.GLFW_RAW_MOUSE_MOTION) == GLFW.GLFW_TRUE;
    }
}
