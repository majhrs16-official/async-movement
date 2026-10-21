package me.majhrs16.am.common;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Small, dependency-free configuration. It intentionally uses properties so the
 * common module can be compiled with Java 8 and consumed by every Minecraft
 * version module.
 */
public final class Config {

    public static final int DEFAULT_POLL_RATE_HZ = 300;
    public static final int MIN_POLL_RATE_HZ = 1;
    public static final int MAX_POLL_RATE_HZ = 1000;

    private static final String HEADER =
            "Async Movement\n" +
            "poll-rate-hz: how many times per second raw key state is read and applied (default 300).\n" +
            "only-when-focused: do not poll while the window is not focused.\n" +
            "mouse-buttons: also poll mouse buttons (default true).\n" +
            "camera: capture camera movement from the cursor at the poll rate (default true).\n" +
            "        Requires Raw Mouse Input to be disabled; otherwise the camera stays vanilla.\n" +
            "log-stats: print the measured poll rate every 5 seconds.\n" +
            "enabled: persisted on/off state. You can also toggle it with the keybind (default F6).";

    private final Path path;

    private volatile boolean enabled = true;
    private volatile int pollRateHz = DEFAULT_POLL_RATE_HZ;
    private volatile boolean onlyWhenFocused = true;
    private volatile boolean mouseButtons = true;
    private volatile boolean camera = true;
    private volatile boolean logStats = false;
    private volatile String lastError;

    private Config(Path path) {
        this.path = path;
    }

    public static Config load(Path path) {
        Config config = new Config(path);
        config.reload();
        if (!Files.isRegularFile(path)) {
            config.save();
        }
        return config;
    }

    public synchronized void reload() {
        Properties properties = new Properties();
        if (Files.isRegularFile(path)) {
            try (InputStream input = Files.newInputStream(path)) {
                properties.load(input);
                lastError = null;
            } catch (IOException e) {
                lastError = "Could not read " + path + ": " + e.getMessage();
            }
        }

        enabled = parseBoolean(properties.getProperty("enabled"), true);
        pollRateHz = clamp(parseInt(properties.getProperty("poll-rate-hz"), DEFAULT_POLL_RATE_HZ));
        onlyWhenFocused = parseBoolean(properties.getProperty("only-when-focused"), true);
        mouseButtons = parseBoolean(properties.getProperty("mouse-buttons"), true);
        camera = parseBoolean(properties.getProperty("camera"), true);
        logStats = parseBoolean(properties.getProperty("log-stats"), false);
    }

    public synchronized void save() {
        Properties properties = new Properties();
        properties.setProperty("enabled", Boolean.toString(enabled));
        properties.setProperty("poll-rate-hz", Integer.toString(pollRateHz));
        properties.setProperty("only-when-focused", Boolean.toString(onlyWhenFocused));
        properties.setProperty("mouse-buttons", Boolean.toString(mouseButtons));
        properties.setProperty("camera", Boolean.toString(camera));
        properties.setProperty("log-stats", Boolean.toString(logStats));

        try {
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            try (OutputStream output = Files.newOutputStream(path)) {
                properties.store(output, HEADER);
            }
            lastError = null;
        } catch (IOException e) {
            lastError = "Could not write " + path + ": " + e.getMessage();
        }
    }

    public Path getPath() {
        return path;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getPollRateHz() {
        return clamp(pollRateHz);
    }

    public void setPollRateHz(int pollRateHz) {
        this.pollRateHz = clamp(pollRateHz);
    }

    public boolean isOnlyWhenFocused() {
        return onlyWhenFocused;
    }

    public boolean isMouseButtons() {
        return mouseButtons;
    }

    public boolean isCamera() {
        return camera;
    }

    public boolean isLogStats() {
        return logStats;
    }

    public String getLastError() {
        return lastError;
    }

    public static int clamp(int hz) {
        if (hz < MIN_POLL_RATE_HZ) {
            return MIN_POLL_RATE_HZ;
        }
        if (hz > MAX_POLL_RATE_HZ) {
            return MAX_POLL_RATE_HZ;
        }
        return hz;
    }

    private static boolean parseBoolean(String value, boolean fallback) {
        if (value == null) {
            return fallback;
        }
        return "true".equalsIgnoreCase(value.trim()) || "1".equals(value.trim());
    }

    private static int parseInt(String value, int fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
