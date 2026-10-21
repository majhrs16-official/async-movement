package me.majhrs16.am.shared;

import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;

/**
 * Fabric API key registration for Minecraft 26.x, where the Fabric API module
 * was renamed from key-binding to key-mapping.
 */
public final class KeybindRegistrar {

    private KeybindRegistrar() {
    }

    public static KeyMapping register(KeyMapping key) {
        return KeyMappingHelper.registerKeyMapping(key);
    }
}
