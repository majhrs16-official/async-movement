package me.majhrs16.am;

import com.mojang.blaze3d.platform.InputConstants;
import me.majhrs16.am.common.KeyStateSink;
import me.majhrs16.am.common.KeyStateSource;
import me.majhrs16.am.shared.AbstractAsyncMovementClient;
import me.majhrs16.am.shared.MinecraftKeySink;
import me.majhrs16.am.shared.SdlKeySource;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

/**
 * Minecraft 26.3 adapter.
 */
public final class AsyncMovement extends AbstractAsyncMovementClient {

    @Override
    protected KeyMapping createToggleKey() {
        return new KeyMapping("key.asyncmovement.toggle", InputConstants.KEY_F6, KeyMapping.Category.MISC);
    }

    @Override
    protected KeyStateSource createSource(Minecraft client) {
        return new SdlKeySource();
    }

    @Override
    protected KeyStateSink createSink(Minecraft client) {
        return MinecraftKeySink.create(client, InputConstants.Type.KEYBOARD::getOrCreate);
    }

    @Override
    protected boolean canPoll(Minecraft client, boolean onlyWhenFocused) {
        return client.player != null
                && client.level != null
                && client.gui.screen() == null
                && (!onlyWhenFocused || client.isWindowActive());
    }
}
