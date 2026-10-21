package me.majhrs16.am.common;

import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Version-independent core.
 *
 * <p>The async thread reads raw keyboard, mouse-button and cursor state. No
 * rendering code is invoked from this class. Keyboard and mouse-button changes
 * are written into Minecraft's key mappings. Camera deltas are handed to an
 * optional {@link CameraSink}, which reuses vanilla's sensitivity, inversion
 * and smoothing rules on the poller thread. Camera capture is only enabled when
 * the cursor position is actually readable, which in practice means Raw Mouse
 * Input must be disabled.</p>
 */
public final class AsyncMovementCore {

    private static final long STATS_INTERVAL_NANOS = 5_000_000_000L;
    private static final double MAX_CAMERA_DELTA = 2_000.0D;

    private final Config config;
    private final Log log;
    private final KeyStateSource source;
    private final KeyStateSink sink;
    private final CameraSink cameraSink;
    private final PollGuard guard;
    private final AsyncPoller poller;

    private final AtomicBoolean enabled = new AtomicBoolean();
    private final boolean[] lastState;
    private final int[] mouseButtons;
    private final boolean[] lastMouseState;
    private final double[] cursorPosition = new double[2];

    private boolean anyDown;
    private boolean cameraHasPosition;
    private boolean cameraWarned;
    private double lastCursorX;
    private double lastCursorY;
    private long lastCursorSampleTime;

    private long lastStatsTime;
    private long lastStatsPolls;

    public AsyncMovementCore(Config config, Log log, KeyStateSource source,
                             KeyStateSink sink, PollGuard guard) {
        this(config, log, source, sink, guard, null);
    }

    public AsyncMovementCore(Config config, Log log, KeyStateSource source,
                             KeyStateSink sink, PollGuard guard, CameraSink cameraSink) {
        this.config = config;
        this.log = log;
        this.source = source;
        this.sink = sink;
        this.cameraSink = cameraSink;
        this.guard = guard;
        this.enabled.set(config.isEnabled());
        this.lastState = new boolean[Math.max(0, source.keyCount())];
        int[] sourceMouseButtons = source.mouseButtonCodes();
        this.mouseButtons = sourceMouseButtons == null ? new int[0] : sourceMouseButtons.clone();
        this.lastMouseState = new boolean[this.mouseButtons.length];
        this.poller = new AsyncPoller("AsyncMovement-Poller", this::pollOnce, log);
    }

    public void start() {
        if (config.getLastError() != null) {
            log.warn(config.getLastError(), null);
        }

        CameraController.setSampler(config.isCamera() && source.supportsCameraCapture()
                ? source::readCameraCursor
                : null);

        boolean cameraConfigured = config.isCamera()
                && cameraSink != null
                && cameraSink.isAvailable()
                && source.supportsCameraCapture();
        boolean cameraAvailable = cameraConfigured && source.isCameraCaptureAvailable();
        if (cameraConfigured && !cameraAvailable) {
            cameraWarned = true;
            log.warn("Async camera is disabled because Raw Mouse Input is enabled "
                    + "or the cursor is not available. Turn Raw Mouse Input off to "
                    + "let the poller capture camera movement.", null);
        }

        log.info("Async Movement starting: target " + config.getPollRateHz()
                + " Hz, key range " + lastState.length
                + ", mouse buttons " + (config.isMouseButtons() ? mouseButtons.length : 0)
                + ", camera " + (cameraAvailable ? "cursor" : "vanilla")
                + ", initially " + (enabled.get() ? "enabled" : "disabled"));
        poller.start(config.getPollRateHz());
    }

    public void stop() {
        poller.stop();
        releaseKeys();
        CameraController.setSampler(null);
        log.info("Async Movement stopped");
    }

    /**
     * Called from the client tick when the keybind is pressed. Persists the new
     * state so it survives a restart.
     */
    public void toggle() {
        boolean nowEnabled = !enabled.get();
        enabled.set(nowEnabled);
        config.setEnabled(nowEnabled);
        config.save();
        if (!nowEnabled) {
            releaseKeys();
        }
        log.info("Async Movement " + (nowEnabled ? "enabled" : "disabled")
                + " (" + config.getPollRateHz() + " Hz)");
    }

    public boolean isEnabled() {
        return enabled.get();
    }

    public int getPollRateHz() {
        return config.getPollRateHz();
    }

    public void setPollRateHz(int rateHz) {
        config.setPollRateHz(rateHz);
        config.save();
        poller.setRateHz(config.getPollRateHz());
        log.info("Async Movement poll rate set to " + config.getPollRateHz() + " Hz");
    }

    private void pollOnce() {
        if (!enabled.get()) {
            setCameraActive(false);
            if (anyDown) {
                releaseKeys();
            }
            return;
        }

        boolean safe;
        try {
            safe = guard.shouldPoll(config.isOnlyWhenFocused());
        } catch (Throwable t) {
            // A half-initialised client (e.g. during shutdown) must not kill the
            // poller thread.
            safe = false;
        }

        if (!safe) {
            setCameraActive(false);
            cameraHasPosition = false;
            if (anyDown) {
                releaseKeys();
            }
            return;
        }

        boolean anyDown = false;
        for (int key = 0; key < lastState.length; key++) {
            boolean pressed;
            try {
                pressed = source.isDown(key);
            } catch (Throwable t) {
                pressed = false;
            }

            anyDown |= pressed;
            if (pressed == lastState[key]) {
                continue;
            }

            lastState[key] = pressed;
            sink.setKeyDown(key, pressed);
        }

        if (config.isMouseButtons()) {
            for (int index = 0; index < mouseButtons.length; index++) {
                int button = mouseButtons[index];
                boolean pressed;
                try {
                    pressed = source.isMouseButtonDown(button);
                } catch (Throwable t) {
                    pressed = false;
                }

                anyDown |= pressed;
                if (pressed == lastMouseState[index]) {
                    continue;
                }

                lastMouseState[index] = pressed;
                sink.setMouseButtonDown(button, pressed);
            }
        }

        this.anyDown = anyDown;
        pollCamera();
        logStatsIfNeeded();
    }

    private void pollCamera() {
        boolean sinkAvailable;
        try {
            sinkAvailable = cameraSink != null && cameraSink.isAvailable();
        } catch (Throwable t) {
            sinkAvailable = false;
        }
        if (!config.isCamera() || !sinkAvailable || !source.supportsCameraCapture()) {
            setCameraActive(false);
            cameraHasPosition = false;
            return;
        }

        boolean available;
        try {
            available = source.isCameraCaptureAvailable();
        } catch (Throwable t) {
            available = false;
        }
        if (!available) {
            setCameraActive(false);
            cameraHasPosition = false;
            if (!cameraWarned) {
                cameraWarned = true;
                log.warn("Async camera is disabled because Raw Mouse Input is enabled "
                        + "or the cursor is not available. Turn Raw Mouse Input off to "
                        + "let the poller capture camera movement.", null);
            }
            return;
        }

        // Prefer the sample taken by the mouse callback just before GLFW
        // recenters the hidden cursor. It closes the small gap between the last
        // regular poll and the platform recenter. A sample older than the last
        // one already consumed is ignored so a race cannot rotate backwards.
        long sampleTime = CameraController.consumePending(cursorPosition);
        boolean read = sampleTime != 0L && sampleTime > lastCursorSampleTime;
        if (!read) {
            try {
                read = source.readCameraCursor(cursorPosition);
            } catch (Throwable t) {
                read = false;
            }
            if (read) {
                sampleTime = System.nanoTime();
            }
        }

        if (!read) {
            setCameraActive(false);
            cameraHasPosition = false;
            return;
        }

        lastCursorSampleTime = sampleTime;

        if (!cameraHasPosition) {
            lastCursorX = cursorPosition[0];
            lastCursorY = cursorPosition[1];
            cameraHasPosition = true;
            // From this point on the poller owns camera capture. Suppress vanilla
            // immediately so the next mouse event is not applied twice.
            setCameraActive(true);
            return;
        }

        boolean recenter;
        try {
            recenter = source.isCameraRecenter(lastCursorX, lastCursorY,
                    cursorPosition[0], cursorPosition[1]);
        } catch (Throwable t) {
            recenter = false;
        }
        if (recenter) {
            // GLFW moved the hidden cursor back to the window center. The
            // movement that led there was already sampled by the mouse callback.
            lastCursorX = cursorPosition[0];
            lastCursorY = cursorPosition[1];
            return;
        }

        double dx = cursorPosition[0] - lastCursorX;
        double dy = cursorPosition[1] - lastCursorY;
        lastCursorX = cursorPosition[0];
        lastCursorY = cursorPosition[1];

        // A focus change or a cursor recenter can produce a huge synthetic jump.
        // Discard it instead of snapping the view.
        if (Math.abs(dx) > MAX_CAMERA_DELTA || Math.abs(dy) > MAX_CAMERA_DELTA) {
            return;
        }

        if (dx == 0.0D && dy == 0.0D) {
            return;
        }

        setCameraActive(true);
        try {
            cameraSink.applyDelta(dx, dy);
        } catch (Throwable t) {
            setCameraActive(false);
            cameraHasPosition = false;
            log.warn("Async camera update failed; falling back to vanilla camera", t);
        }
    }

    private void setCameraActive(boolean active) {
        CameraController.setActive(active, active ? Thread.currentThread() : null);
    }

    private void releaseKeys() {
        setCameraActive(false);
        cameraHasPosition = false;
        try {
            sink.releaseAll();
        } catch (Throwable t) {
            log.warn("Could not release key mappings", t);
        }
        Arrays.fill(lastState, false);
        Arrays.fill(lastMouseState, false);
        anyDown = false;
    }

    private void logStatsIfNeeded() {
        if (!config.isLogStats()) {
            return;
        }

        long now = System.nanoTime();
        if (lastStatsTime == 0L) {
            lastStatsTime = now;
            lastStatsPolls = poller.getPollCount();
            return;
        }

        long elapsed = now - lastStatsTime;
        if (elapsed < STATS_INTERVAL_NANOS) {
            return;
        }

        long polls = poller.getPollCount();
        double measured = (polls - lastStatsPolls) * 1_000_000_000.0D / elapsed;
        log.info(String.format(Locale.ROOT,
                "Async Movement rate: target=%d Hz, measured=%.1f Hz, late=%d",
                config.getPollRateHz(), measured, poller.getLateCount()));

        lastStatsTime = now;
        lastStatsPolls = polls;
    }
}
