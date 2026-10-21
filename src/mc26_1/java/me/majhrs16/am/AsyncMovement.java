package me.majhrs16.am;

import com.mojang.blaze3d.platform.InputConstants;
import me.majhrs16.am.common.KeyStateSink;
import me.majhrs16.am.common.KeyStateSource;
import me.majhrs16.am.shared.AbstractAsyncMovementClient;
import me.majhrs16.am.shared.MinecraftKeySink;
import me.majhrs16.am.shared.WindowObjectKeySource;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/**
 * Minecraft 26.1 adapter.
 */
public final class AsyncMovement extends AbstractAsyncMovementClient {

    @Override
    protected KeyMapping createToggleKey() {
        return new KeyMapping("key.asyncmovement.toggle", GLFW.GLFW_KEY_F6, KeyMapping.Category.MISC);
    }

    @Override
    protected KeyStateSource createSource(Minecraft client) {
        return new WindowObjectKeySource(client);
    }

    @Override
    protected KeyStateSink createSink(Minecraft client) {
        return MinecraftKeySink.create(client, InputConstants.Type.KEYSYM::getOrCreate);
    }

    @Override
    protected boolean canPoll(Minecraft client, boolean onlyWhenFocused) {
        return client.player != null
                && client.level != null
                && client.screen == null
                && (!onlyWhenFocused || client.isWindowActive());
    }
}
