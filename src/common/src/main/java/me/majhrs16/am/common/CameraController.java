package me.majhrs16.am.common;

/**
 * Shared state between the asynchronous camera poller and the Minecraft camera
 * mixin.
 *
 * <p>The mixin must suppress vanilla camera handling while the poller owns the
 * camera. The suppression check is thread-aware: the poller invokes vanilla's
 * own {@code MouseHandler} math on its thread, while the client thread must not
 * apply the same delta a second time.</p>
 *
 * <p>GLFW recenters the hidden cursor in {@code glfwPollEvents}. A poller that
 * only samples the cursor every few milliseconds can miss the last movement
 * before that recenter. The mixin therefore takes an extra client-thread sample
 * at the start of each mouse-move callback. The poller consumes it asynchronously
 * before the next regular sample, without applying camera movement from the
 * client thread.</p>
 */
public final class CameraController {

    private static final Object LOCK = new Object();
    // At the default 300 Hz the gap is about 3 ms. Samples older than this are
    // not worth using: the client may have recentered the cursor again and the
    // absolute positions would no longer be comparable.
    private static final long MAX_SAMPLE_AGE_NANOS = 20_000_000L;

    private static final ThreadLocal<double[]> SAMPLE_BUFFER = new ThreadLocal<double[]>() {
        @Override
        protected double[] initialValue() {
            return new double[2];
        }
    };

    private static volatile boolean active;
    private static volatile Thread pollerThread;
    private static volatile CameraCursorSampler sampler;

    private static boolean hasPending;
    private static double pendingX;
    private static double pendingY;
    private static long pendingTime;

    private CameraController() {
    }

    public static void setSampler(CameraCursorSampler sampler) {
        CameraController.sampler = sampler;
        clearPending();
    }

    public static void setActive(boolean active, Thread pollerThread) {
        CameraController.active = active;
        CameraController.pollerThread = active ? pollerThread : null;
        if (!active) {
            clearPending();
        }
    }

    public static boolean isActive() {
        return active;
    }

    /**
     * Returns true when the current call comes from the client thread while the
     * async camera owns the input. Calls from the poller thread are allowed
     * through so they can reuse vanilla sensitivity, inversion and smoothing.
     */
    public static boolean shouldSuppressVanilla() {
        return active && Thread.currentThread() != pollerThread;
    }

    /**
     * Captures a cursor sample from the client thread. The mixin calls this at
     * the start of {@code MouseHandler.onMove}, before GLFW recenters the
     * cursor. No camera rotation is applied here.
     */
    public static void sampleFromClientThread() {
        if (!active) {
            return;
        }

        CameraCursorSampler current = sampler;
        if (current == null) {
            return;
        }

        double[] buffer = SAMPLE_BUFFER.get();
        boolean read;
        try {
            read = current.read(buffer);
        } catch (Throwable t) {
            return;
        }
        if (!read) {
            return;
        }

        synchronized (LOCK) {
            if (!active) {
                return;
            }
            pendingX = buffer[0];
            pendingY = buffer[1];
            pendingTime = System.nanoTime();
            hasPending = true;
        }
    }

    /**
     * Consumes a client-thread sample, if one is fresh. Called by the poller.
     * Returns the sample timestamp, or zero when there was no fresh sample.
     * Stale samples are discarded so a paused client does not produce a jump.
     */
    public static long consumePending(double[] position) {
        synchronized (LOCK) {
            if (!hasPending) {
                return 0L;
            }
            hasPending = false;
            if (System.nanoTime() - pendingTime > MAX_SAMPLE_AGE_NANOS) {
                return 0L;
            }
            position[0] = pendingX;
            position[1] = pendingY;
            return pendingTime;
        }
    }

    public static void clearPending() {
        synchronized (LOCK) {
            hasPending = false;
        }
    }

    public static Object lock() {
        return LOCK;
    }
}
