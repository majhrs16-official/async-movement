package me.majhrs16.am.shared;

import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.sdl.SDLMouse;

import me.majhrs16.am.common.KeyStateSource;

/**
 * SDL3 source for Minecraft 26.3+. In 26.x the input backend is SDL and key
 * mapping values are SDL scancodes. Unlike GLFW's documented main-thread
 * restriction, SDL_GetKeyboardState is explicitly safe to call from any thread.
 *
 * <p>Mouse buttons are read from SDL_GetMouseState because the poller has to
 * provide one unified source. SDL documents that function as main-thread-only;
 * like the existing GLFW keyboard path, this is an intentional trade-off for
 * the asynchronous requirement. SDL only refreshes that cache when the main
 * thread pumps events, so the poller cannot force a newer mouse snapshot.</p>
 */
public final class SdlKeySource implements KeyStateSource {

    private static final int SDL_SCANCODE_COUNT = 512;
    private static final int[] MOUSE_BUTTONS = {1, 2, 3, 4, 5, 6, 7, 8};

    @Override
    public int keyCount() {
        return SDL_SCANCODE_COUNT;
    }

    @Override
    public boolean isDown(int key) {
        return InputConstants.isKeyDown(key);
    }

    @Override
    public int[] mouseButtonCodes() {
        return MOUSE_BUTTONS;
    }

    @Override
    public boolean isMouseButtonDown(int button) {
        // SDL uses 1-based button numbers and a bit mask. This is the same
        // numbering that InputConstants.Type.MOUSE exposes in 26.3.
        int mask = SDLMouse.SDL_GetMouseState(null, null);
        return (mask & (1 << (button - 1))) != 0;
    }
}
