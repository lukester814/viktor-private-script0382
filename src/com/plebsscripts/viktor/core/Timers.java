package com.plebsscripts.viktor.core;

import java.util.Random;

/**
 * Centralized timing utility for bot delays.
 * Adds randomization to appear more human-like.
 */
public class Timers {
    private final Random random = new Random();

    // === Sleep methods (blocking) ===

    /**
     * Short sleep with randomization (300-700ms)
     */
    public void sleepShort() {
        try {
            Thread.sleep(shortWait());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Medium sleep with randomization (600-1400ms)
     */
    public void sleepMedium() {
        try {
            Thread.sleep(mediumWait());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Long sleep with randomization (1000-2200ms)
     */
    public void sleepLong() {
        try {
            Thread.sleep(longWait());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // === Wait duration methods (returns ms, doesn't block) ===

    /**
     * Short random wait duration (300-700ms)
     * Use for quick actions like clicks
     */
    public int shortWait() {
        return 300 + random.nextInt(400);
    }

    /**
     * Medium random wait duration (600-1400ms)
     * Use for navigation or moderate actions
     */
    public int mediumWait() {
        return 600 + random.nextInt(800);
    }

    /**
     * Long random wait duration (1000-2200ms)
     * Use for major actions like trading
     */
    public int longWait() {
        return 1000 + random.nextInt(1200);
    }

    /**
     * Extra long wait for cooldowns (2000-4000ms)
     */
    public int cooldownWait() {
        return 2000 + random.nextInt(2000);
    }

    /**
     * Custom random wait (min to max milliseconds)
     */
    public int randomWait(int minMs, int maxMs) {
        if (minMs >= maxMs) return minMs;
        return minMs + random.nextInt(maxMs - minMs);
    }

    /**
     * Custom sleep with range
     */
    public void sleep(int minMs, int maxMs) {
        try {
            Thread.sleep(randomWait(minMs, maxMs));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Exact sleep (no randomization)
     */
    public void sleepExact(int ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Gaussian distributed sleep (more natural)
     * Most delays will cluster around meanMs
     */
    public void sleepGaussian(int meanMs, int stdDevMs) {
        double gaussian = random.nextGaussian();
        int duration = (int)(meanMs + gaussian * stdDevMs);
        duration = Math.max(50, duration); // Min 50ms
        sleepExact(duration);
    }
}
