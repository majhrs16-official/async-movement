package me.majhrs16.am.common;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;

/**
 * Fixed-rate poller running on a dedicated daemon thread.
 *
 * <p>It does not touch rendering. It only invokes {@link Runnable#run()} at the
 * configured rate. The loop uses absolute deadlines instead of {@code sleep()}
 * so the average rate does not drift when a poll takes slightly too long.</p>
 */
public final class AsyncPoller {

    private final String threadName;
    private final Runnable task;
    private final Log log;

    private final AtomicLong pollCount = new AtomicLong();
    private final AtomicLong lateCount = new AtomicLong();

    private volatile boolean running;
    private volatile long periodNanos;
    private Thread thread;

    public AsyncPoller(String threadName, Runnable task, Log log) {
        this.threadName = threadName;
        this.task = task;
        this.log = log;
    }

    public synchronized void start(int rateHz) {
        stop();
        setRateHz(rateHz);
        running = true;
        thread = new Thread(this::loop, threadName);
        thread.setDaemon(true);
        thread.setPriority(Math.min(Thread.MAX_PRIORITY, Thread.NORM_PRIORITY + 2));
        thread.start();
    }

    public void setRateHz(int rateHz) {
        this.periodNanos = 1_000_000_000L / Config.clamp(rateHz);
    }

    public synchronized void stop() {
        running = false;
        Thread current = thread;
        thread = null;
        if (current != null) {
            current.interrupt();
            try {
                current.join(500L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public boolean isRunning() {
        return running;
    }

    public long getPollCount() {
        return pollCount.get();
    }

    public long getLateCount() {
        return lateCount.get();
    }

    public long getPeriodNanos() {
        return periodNanos;
    }

    private void loop() {
        long next = System.nanoTime();

        while (running) {
            long period = periodNanos;
            if (period <= 0L) {
                period = 1_000_000_000L / Config.DEFAULT_POLL_RATE_HZ;
            }

            long now = System.nanoTime();
            // If the machine was suspended or the thread was starved for a long
            // time, do not try to "catch up" hundreds of polls in a burst.
            if (now - next > period * 8L) {
                next = now;
                lateCount.incrementAndGet();
            }

            try {
                task.run();
            } catch (Throwable t) {
                log.warn("Async poll failed", t);
            }
            pollCount.incrementAndGet();

            next += period;
            long sleep = next - System.nanoTime();
            if (sleep > 0L) {
                LockSupport.parkNanos(sleep);
            }

            if (Thread.interrupted() && !running) {
                return;
            }
        }
    }
}
