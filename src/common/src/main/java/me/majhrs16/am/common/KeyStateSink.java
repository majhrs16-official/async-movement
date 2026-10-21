package me.majhrs16.am.common;

/**
 * Applies raw key state to Minecraft key mappings. This is deliberately kept
 * separate from the source so the version modules can choose how a key code is
 * translated (GLFW key code vs. SDL scancode).
 */
public interface KeyStateSink {

    void setKeyDown(int key, boolean down);

    /**
     * Applies the state of a mouse button using the same code namespace as
     * {@link KeyStateSource#mouseButtonCodes()}.
     */
    default void setMouseButtonDown(int button, boolean down) {
    }

    /**
     * Releases every key mapping, including mouse buttons. Used when the mod is
     * disabled, the game is paused, a screen opens, or the client is stopping.
     */
    void releaseAll();
}
