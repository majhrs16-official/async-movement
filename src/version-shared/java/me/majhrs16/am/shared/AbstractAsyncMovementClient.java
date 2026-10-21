package me.majhrs16.am.shared;

import me.majhrs16.am.common.AsyncMovementCore;
import me.majhrs16.am.common.Config;
import me.majhrs16.am.common.KeyStateSink;
import me.majhrs16.am.common.KeyStateSource;
import me.majhrs16.am.common.Log;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

/**
 * Shared Minecraft glue. Version modules only provide the pieces that actually
 * changed between Minecraft versions: the keybind constructor, the raw input
 * source, the key-code namespace and the "is a screen open?" check.
 */
public abstract class AbstractAsyncMovementClient implements ClientModInitializer {

    public static final String MOD_ID = "asyncmovement";
    public static final String MOD_NAME = "Async Movement";

    protected static final Log LOG = new Slf4jLog(LoggerFactory.getLogger(MOD_ID));

    private Config config;
    private AsyncMovementCore core;
    private KeyMapping toggleKey;

    @Override
    public void onInitializeClient() {
        Path configPath = FabricLoader.getInstance().getConfigDir().resolve(MOD_ID + ".properties");
        config = Config.load(configPath);
        LOG.info("Async Movement config: " + configPath);

        toggleKey = KeybindRegistrar.register(createToggleKey());

        ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
            KeyStateSource source = createSource(client);
            KeyStateSink sink = createSink(client);
            core = new AsyncMovementCore(config, LOG, source, sink,
                    onlyWhenFocused -> shouldPoll(client, onlyWhenFocused),
                    new MinecraftCameraSink(client));
            core.start();
        });

        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            if (core != null) {
                core.stop();
            }
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (toggleKey == null) {
                return;
            }
            while (toggleKey.consumeClick()) {
                if (core != null) {
                    core.toggle();
                } else {
                    config.setEnabled(!config.isEnabled());
                    config.save();
                    LOG.info("Async Movement " + (config.isEnabled() ? "enabled" : "disabled"));
                }
            }
        });
    }

    private boolean shouldPoll(Minecraft client, boolean onlyWhenFocused) {
        return client.isRunning() && canPoll(client, onlyWhenFocused);
    }

    protected abstract KeyMapping createToggleKey();

    protected abstract KeyStateSource createSource(Minecraft client);

    protected abstract KeyStateSink createSink(Minecraft client);

    /**
     * Version-specific check: in-game, no GUI screen open, and (optionally) the
     * window has focus. Called from the poller thread.
     */
    protected abstract boolean canPoll(Minecraft client, boolean onlyWhenFocused);

    protected final Config config() {
        return config;
    }
}
