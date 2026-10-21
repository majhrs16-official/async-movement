package me.majhrs16.am.shared;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ToggleKeyMapping;

import java.util.function.IntFunction;
import java.util.function.Predicate;

import me.majhrs16.am.common.KeyStateSink;

/**
 * Applies a raw keyboard or mouse-button code to all Minecraft key mappings
 * bound to that input.
 *
 * <p>{@link KeyMapping#set} is the same API vanilla uses for input events. We
 * deliberately do not synthesise {@link KeyMapping#click} calls: vanilla already
 * generates click events from its input callbacks, and duplicating them would
 * trigger actions twice. Mouse buttons therefore update the held state
 * asynchronously, while attack/use clicks keep coming from vanilla.</p>
 *
 * <p>Toggle mappings (sprint/use when toggle mode is enabled) are skipped. They
 * change state on every {@code set(true)} call, so re-applying the same physical
 * input state from a polling thread would toggle them off again.</p>
 */
public final class MinecraftKeySink implements KeyStateSink {

    private final IntFunction<InputConstants.Key> keyFactory;
    private final Predicate<Integer> skipKey;
    private final IntFunction<InputConstants.Key> mouseKeyFactory;
    private final Predicate<Integer> skipMouseButton;

    public MinecraftKeySink(IntFunction<InputConstants.Key> keyFactory,
                            Predicate<Integer> skipKey,
                            IntFunction<InputConstants.Key> mouseKeyFactory,
                            Predicate<Integer> skipMouseButton) {
        this.keyFactory = keyFactory;
        this.skipKey = skipKey;
        this.mouseKeyFactory = mouseKeyFactory;
        this.skipMouseButton = skipMouseButton;
    }

    /**
     * Creates a sink that polls both keyboard keys and mouse buttons. The mouse
     * namespace is always {@link InputConstants.Type#MOUSE}; only the keyboard
     * namespace changes between GLFW and SDL versions.
     */
    public static MinecraftKeySink create(Minecraft client,
                                          IntFunction<InputConstants.Key> keyFactory) {
        return new MinecraftKeySink(
                keyFactory,
                toggleKeyFilter(client),
                InputConstants.Type.MOUSE::getOrCreate,
                toggleMouseFilter(client));
    }

    @Override
    public void setKeyDown(int key, boolean down) {
        if (skipKey.test(key)) {
            return;
        }
        KeyMapping.set(keyFactory.apply(key), down);
    }

    @Override
    public void setMouseButtonDown(int button, boolean down) {
        if (mouseKeyFactory == null || (skipMouseButton != null && skipMouseButton.test(button))) {
            return;
        }
        KeyMapping.set(mouseKeyFactory.apply(button), down);
    }

    @Override
    public void releaseAll() {
        KeyMapping.releaseAll();
    }

    public static Predicate<Integer> toggleKeyFilter(Minecraft client) {
        return code -> isToggleKeyMapping(client.options.keyUse, code)
                || isToggleKeyMapping(client.options.keySprint, code)
                || isToggleKeyMapping(client.options.keyAttack, code);
    }

    public static Predicate<Integer> toggleMouseFilter(Minecraft client) {
        return button -> isToggleMouseMapping(client.options.keyUse, button)
                || isToggleMouseMapping(client.options.keySprint, button)
                || isToggleMouseMapping(client.options.keyAttack, button);
    }

    private static boolean isToggleKeyMapping(KeyMapping mapping, int code) {
        if (!(mapping instanceof ToggleKeyMapping)) {
            return false;
        }
        try {
            InputConstants.Key key = InputConstants.getKey(mapping.saveString());
            // Mouse buttons use their own namespace; do not let mouse button 0/1
            // shadow keyboard codes 0/1.
            return key.getType() != InputConstants.Type.MOUSE && key.getValue() == code;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean isToggleMouseMapping(KeyMapping mapping, int button) {
        if (!(mapping instanceof ToggleKeyMapping)) {
            return false;
        }
        try {
            InputConstants.Key key = InputConstants.getKey(mapping.saveString());
            return key.getType() == InputConstants.Type.MOUSE && key.getValue() == button;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
