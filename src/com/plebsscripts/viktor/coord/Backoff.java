package com.plebsscripts.viktor.coord;

import com.plebsscripts.viktor.util.Logs;

/**
 * Exponential backoff utility for retrying failed operations.
 * Doubles delay on each increase, up to a maximum.
 *
 * Used by CoordinatorClient for network retry logic.
 */
public class Backoff {
    private long initialDelayMs;
    private long currentDelayMs;
    private long maxDelayMs;
    private int attempts;

    // Default: 500ms initial, 30s max
    public Backoff() {
        this(500, 30_000);
    }

    public Backoff(long initialDelayMs, long maxDelayMs) {
        this.initialDelayMs = initialDelayMs;
        this.maxDelayMs = maxDelayMs;
        this.currentDelayMs = initialDelayMs;
        this.attempts = 0;
    }

    /**
     * Reset to initial delay (call after successful operation)
     */
    public void reset() {
        currentDelayMs = initialDelayMs;
        attempts = 0;
    }

    /**
     * Double the delay, up to max (call after failed operation)
     */
    public void increase() {
        attempts++;
        currentDelayMs = Math.min(currentDelayMs * 2, maxDelayMs);
        Logs.info("Backoff increased to " + currentDelayMs + "ms (attempt " + attempts + ")");
    }

    /**
     * Sleep for current delay duration
     */
    public void sleep() {
        try {
            Thread.sleep(currentDelayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Sleep with a custom delay (doesn't affect backoff state)
     */
    public static void sleepMs(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Sleep with randomization (prevents thundering herd)
     */
    public void sleepWithJitter() {
        long jitter = (long)(Math.random() * currentDelayMs * 0.3); // ±30% jitter
        long actualDelay = currentDelayMs + jitter;
        try {
            Thread.sleep(actualDelay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // Getters
    public long getCurrentDelay() { return currentDelayMs; }
    public int getAttempts() { return attempts; }
    public boolean hasExceededMaxAttempts(int maxAttempts) {
        return attempts >= maxAttempts;
    }

    /**
     * Check if we should give up retrying
     */
    public boolean shouldGiveUp(int maxAttempts) {
        if (attempts >= maxAttempts) {
            Logs.warn("Backoff: Exceeded max attempts (" + maxAttempts + ")");
            return true;
        }
        return false;
    }
}
