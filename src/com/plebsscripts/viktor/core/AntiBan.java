// AntiBan.java - FINAL VERSION v3 (All methods included)

package com.plebsscripts.viktor.core;

import com.plebsscripts.viktor.config.Settings;
import org.dreambot.api.utilities.Logger;
import org.dreambot.api.utilities.Sleep;

import java.util.Random;

public class AntiBan {
    private final Random random = new Random();
    private Settings settings;

    private final int accountTimingOffset;
    private final double accountSpeedMultiplier;

    private com.plebsscripts.viktor.util.SmartMouse smartMouse;

    public AntiBan() {
        this(null);
    }

    public AntiBan(Settings settings) {
        this.settings = settings;

        this.accountTimingOffset = -200 + random.nextInt(400);
        this.accountSpeedMultiplier = 0.85 + (random.nextDouble() * 0.3);

        Logger.log("[AntiBan] Initialized for account (offset: " + accountTimingOffset +
                "ms, speed: " + String.format("%.2f", accountSpeedMultiplier) + "x)");
    }

    public void updateSettings(Settings settings) {
        this.settings = settings;
    }

    // ============================================================================
    // SmartMouse
    // ============================================================================

    private com.plebsscripts.viktor.util.SmartMouse getSmartMouse() {
        if (smartMouse == null) {
            smartMouse = new com.plebsscripts.viktor.util.SmartMouse();
            Logger.log("[AntiBan] SmartMouse initialized");
        }
        return smartMouse;
    }

    public com.plebsscripts.viktor.util.SmartMouse getSmartMouseInstance() {
        return getSmartMouse();
    }

    // ============================================================================
    // Sleep Methods (PUBLIC for HumanBehavior)
    // ============================================================================

    public void shortPause() {
        if (!isEnabled()) return;
        int base = getConfig().minDelayMs;
        sleep(base, base + 400);
    }

    public void mediumPause() {
        if (!isEnabled()) return;
        int base = getConfig().minDelayMs;
        sleep(base + 400, base + 1200);
    }

    public void longPause() {
        if (!isEnabled()) return;
        int base = getConfig().maxDelayMs;
        sleep(base, base + 800);
    }

    public void idleSleep() {
        if (!isEnabled()) return;
        sleep(2000, 5000);
    }

    public void sleep(int min, int max) {
        int baseSleep = min + random.nextInt(max - min);
        int adjustedSleep = (int) (baseSleep * accountSpeedMultiplier) + accountTimingOffset;
        adjustedSleep = Math.max(100, adjustedSleep);
        Sleep.sleep(adjustedSleep);
    }

    public void sleepExact(int ms) {
        Sleep.sleep(ms);
    }

    // ============================================================================
    // Mouse Movement
    // ============================================================================

    public void randomMouseMovement() {
        if (!isEnabled() || !getConfig().randomMouseMovements) {
            return;
        }

        try {
            java.awt.Point current = org.dreambot.api.input.Mouse.getPosition();

            int movementType = random.nextInt(100);

            if (movementType < 40) {
                int offsetX = random.nextInt(50) - 25;
                int offsetY = random.nextInt(50) - 25;

                java.awt.Point target = new java.awt.Point(
                        Math.max(0, current.x + offsetX),
                        Math.max(0, current.y + offsetY)
                );

                getSmartMouse().move(target);
                Logger.log("[AntiBan] Small mouse fidget");

            } else if (movementType < 70) {
                int gameWidth = 765;
                int gameHeight = 503;

                java.awt.Point target = new java.awt.Point(
                        100 + random.nextInt(gameWidth - 200),
                        100 + random.nextInt(gameHeight - 200)
                );

                getSmartMouse().move(target);
                Logger.log("[AntiBan] Random viewport movement");

            } else if (movementType < 85) {
                java.awt.Point target = new java.awt.Point(
                        600 + random.nextInt(150),
                        200 + random.nextInt(300)
                );

                getSmartMouse().move(target);
                sleep(500, 1500);
                Logger.log("[AntiBan] Hovering UI element");

            } else {
                getSmartMouse().moveOffScreen();
                sleep(1000, 3000);
                Logger.log("[AntiBan] Moved mouse off-screen");
            }

        } catch (Exception e) {
            Logger.log("[AntiBan] SmartMouse movement failed: " + e.getMessage());
            shortPause();
        }
    }

    // ============================================================================
    // Camera Movement
    // ============================================================================

    public void randomCameraRotation() {
        if (!isEnabled() || !getConfig().randomCameraRotation) {
            return;
        }
        mediumPause();
    }

    // ============================================================================
    // Breaks - Multiple Types
    // ============================================================================

    public boolean shouldTakeAfkBreak() {
        if (!isEnabled()) return false;

        int chancePerHour = getConfig().afkBreakChance;
        int chancePerCheck = chancePerHour / 60;

        return random.nextInt(100) < chancePerCheck;
    }

    public void takeAfkBreak() {
        if (!isEnabled()) return;

        int breakMinutes = 5 + random.nextInt(20);
        Logger.log("[AntiBan] Taking AFK break for " + breakMinutes + " minutes");

        getSmartMouse().moveOffScreen();

        Sleep.sleep(breakMinutes * 60 * 1000);
        Logger.log("[AntiBan] AFK break complete");
    }

    /**
     * NEW: Should take a regular break? (called by StateMachine)
     */
    public boolean shouldTakeBreak() {
        if (!isEnabled()) return false;
        return random.nextInt(1000) < 1; // 0.1% chance per check
    }

    /**
     * NEW: Take a short break (30 seconds - 2 minutes)
     */
    public void takeShortBreak() {
        int seconds = 30 + random.nextInt(90); // 30-120 seconds
        Logger.log("[AntiBan] Taking short break for " + seconds + " seconds");

        getSmartMouse().moveOffScreen();
        Sleep.sleep(seconds * 1000);

        Logger.log("[AntiBan] Short break complete");
    }

    /**
     * NEW: Take a medium break (2-5 minutes)
     */
    public void takeMediumBreak() {
        int minutes = 2 + random.nextInt(3); // 2-5 minutes
        Logger.log("[AntiBan] Taking medium break for " + minutes + " minutes");

        getSmartMouse().moveOffScreen();
        Sleep.sleep(minutes * 60 * 1000);

        Logger.log("[AntiBan] Medium break complete");
    }

    // ============================================================================
    // State Management
    // ============================================================================

    /**
     * NEW: Reset anti-ban state (called by StateMachine)
     */
    public void reset() {
        Logger.log("[AntiBan] Reset");
        // Reset any internal counters or state if needed
    }

    // ============================================================================
    // Action-Specific Delays
    // ============================================================================

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

    // ============================================================================
    // Helper Methods
    // ============================================================================

    private boolean isEnabled() {
        return settings != null && settings.antiBan != null && settings.antiBan.enabled;
    }

    private Settings.AntiBanBlock getConfig() {
        if (settings == null || settings.antiBan == null) {
            Settings.AntiBanBlock defaults = new Settings.AntiBanBlock();
            defaults.enabled = false;
            return defaults;
        }
        return settings.antiBan;
    }
}