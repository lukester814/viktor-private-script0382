package com.plebsscripts.viktor.core;

import com.plebsscripts.viktor.config.Settings;
import com.plebsscripts.viktor.util.Logs;
import java.util.Random;

/**
 * Anti-detection system to make bot behavior appear more human-like.
 * Adds random delays, mouse movements, and other humanizing behaviors.
 */
public class AntiBan {
    private final Settings settings;
    private final Random random = new Random();
    private long lastAction = System.currentTimeMillis();
    private long lastBreak = System.currentTimeMillis();
    private int actionCount = 0;

    public AntiBan() {
        this(null);
    }

    public AntiBan(Settings settings) {
        this.settings = settings;
    }

    /**
     * Standard idle sleep between actions (600-1000ms)
     */
    public void idleSleep() {
        if (settings != null && settings.antiBan != null && settings.antiBan.enabled) {
            sleep(settings.antiBan.minDelayMs, settings.antiBan.maxDelayMs);
        } else {
            sleep(600, 1000);
        }
        updateActivity();
    }

    /**
     * Short pause for quick actions (100-200ms)
     */
    public void shortPause() {
        sleep(100, 200);
        updateActivity();
    }

    /**
     * Medium pause for moderate actions (300-600ms)
     */
    public void mediumPause() {
        sleep(300, 600);
        updateActivity();
    }

    /**
     * Long pause for major actions (1500-3000ms)
     */
    public void longPause() {
        sleep(1500, 3000);
        updateActivity();
    }

    /**
     * Variable sleep based on action type
     */
    public void sleepForAction(String action) {
        switch (action.toLowerCase()) {
            case "click":
            case "hover":
                shortPause();
                break;
            case "typing":
            case "navigate":
                mediumPause();
                break;
            case "trade":
            case "offer":
                longPause();
                break;
            default:
                idleSleep();
        }
    }

    /**
     * Random sleep between min and max milliseconds
     */
    public void sleep(int minMs, int maxMs) {
        if (minMs >= maxMs) {
            sleepExact(minMs);
            return;
        }

        int duration = minMs + random.nextInt(maxMs - minMs);
        sleepExact(duration);
    }

    /**
     * Sleep for exact duration
     */
    public void sleepExact(int ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Check if we should take a break (random AFK behavior)
     */
    public boolean shouldTakeBreak() {
        if (settings == null || settings.antiBan == null || !settings.antiBan.enabled) {
            return false;
        }

        long timeSinceBreak = System.currentTimeMillis() - lastBreak;
        long oneHour = 60 * 60 * 1000;

        // Check every hour with configured % chance
        if (timeSinceBreak > oneHour) {
            int roll = random.nextInt(100);
            if (roll < settings.antiBan.afkBreakChance) {
                Logs.info("AntiBan: Taking random break");
                return true;
            }
            lastBreak = System.currentTimeMillis(); // Reset timer
        }

        return false;
    }

    /**
     * Take a short AFK break (30s - 2min)
     */
    public void takeShortBreak() {
        int duration = 30_000 + random.nextInt(90_000); // 30s - 2min
        Logs.info("AntiBan: Short break for " + (duration / 1000) + " seconds");
        sleepExact(duration);
        lastBreak = System.currentTimeMillis();
    }

    /**
     * Take a medium break (2-5 minutes)
     */
    public void takeMediumBreak() {
        int duration = 120_000 + random.nextInt(180_000); // 2-5min
        Logs.info("AntiBan: Medium break for " + (duration / 1000) + " seconds");
        sleepExact(duration);
        lastBreak = System.currentTimeMillis();
    }

    /**
     * Simulate human reaction time variance
     */
    public void humanReactionDelay() {
        // Humans have 200-500ms reaction time
        sleep(200, 500);
    }

    /**
     * Random mouse movement (placeholder - needs DreamBot API)
     */
    public void randomMouseMovement() {
        if (settings == null || settings.antiBan == null || !settings.antiBan.randomMouseMovements) {
            return;
        }

        // TODO: Implement with DreamBot mouse API
        // Mouse.move(randomPoint());
        shortPause();
    }

    /**
     * Random camera rotation (placeholder - needs DreamBot API)
     */
    public void randomCameraMovement() {
        if (settings == null || settings.antiBan == null || !settings.antiBan.randomCameraRotation) {
            return;
        }

        // TODO: Implement with DreamBot camera API
        // Camera.rotateToYaw(randomYaw());
        mediumPause();
    }

    /**
     * Check random events (placeholder)
     */
    public void checkRandomEvent() {
        // TODO: Implement random event detection
        // if (RandomEvents.hasEvent()) { RandomEvents.solve(); }
    }

    /**
     * Periodic idle actions to look human
     */
    public void performIdleAction() {
        if (!shouldPerformIdleAction()) {
            return;
        }

        int roll = random.nextInt(100);

        if (roll < 30) {
            randomMouseMovement();
        } else if (roll < 50) {
            randomCameraMovement();
        } else if (roll < 60) {
            // Check stats tab
            Logs.info("AntiBan: Checking stats");
            mediumPause();
        } else if (roll < 70) {
            // Hover over random item
            Logs.info("AntiBan: Hovering item");
            shortPause();
        }
        // 30% chance: do nothing
    }

    /**
     * Should we perform an idle action? (every 50-100 actions)
     */
    private boolean shouldPerformIdleAction() {
        return actionCount % (50 + random.nextInt(50)) == 0;
    }

    /**
     * Track activity for idle action triggers
     */
    private void updateActivity() {
        lastAction = System.currentTimeMillis();
        actionCount++;

        // Check if we should take a break
        if (shouldTakeBreak()) {
            takeShortBreak();
        }

        // Occasionally do idle actions
        if (random.nextInt(100) < 5) { // 5% chance
            performIdleAction();
        }
    }

    /**
     * Get time since last action (in seconds)
     */
    public long getTimeSinceLastAction() {
        return (System.currentTimeMillis() - lastAction) / 1000;
    }

    /**
     * Get total action count
     */
    public int getActionCount() {
        return actionCount;
    }

    /**
     * Reset activity tracking
     */
    public void reset() {
        actionCount = 0;
        lastAction = System.currentTimeMillis();
        Logs.info("AntiBan: Activity reset");
    }

    /**
     * Sleep with Gaussian distribution (more natural timing)
     */
    public void gaussianSleep(int meanMs, int stdDevMs) {
        double gaussian = random.nextGaussian();
        int duration = (int)(meanMs + gaussian * stdDevMs);
        duration = Math.max(50, duration); // Min 50ms
        sleepExact(duration);
        updateActivity();
    }
}
