package me.majhrs16.am;

import com.mojang.blaze3d.platform.InputConstants;
import me.majhrs16.am.common.KeyStateSink;
import me.majhrs16.am.common.KeyStateSource;
import me.majhrs16.am.shared.AbstractAsyncMovementClient;
import me.majhrs16.am.shared.MinecraftKeySink;
import me.majhrs16.am.shared.LongWindowKeySource;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/**
 * Minecraft 1.21.8 adapter.
 */
public final class AsyncMovement extends AbstractAsyncMovementClient {

    @Override
    protected KeyMapping createToggleKey() {
        return new KeyMapping("key.asyncmovement.toggle", GLFW.GLFW_KEY_F6, "key.categories.misc");
    }

    @Override
    protected KeyStateSource createSource(Minecraft client) {
        return new LongWindowKeySource(client);
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
