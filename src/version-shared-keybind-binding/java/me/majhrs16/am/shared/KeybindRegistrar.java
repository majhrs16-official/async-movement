package me.majhrs16.am.shared;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;

/**
 * Fabric API key registration for Minecraft 1.16.5 - 1.21.11.
 */
public final class KeybindRegistrar {

    private KeybindRegistrar() {
    }

    public static KeyMapping register(KeyMapping key) {
        return KeyBindingHelper.registerKeyBinding(key);
    }
}
