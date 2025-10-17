package com.plebsscripts.viktor.core;

import com.plebsscripts.viktor.config.Settings;
import com.plebsscripts.viktor.util.Logs;
import java.util.Random;

/**
 * IMPROVED: Human-like timing with Gaussian distribution and account-specific variance
 */
public class AntiBan {
    private final Settings settings;
    private final Random random;
    private final Random accountRandom; // Consistent per-account randomness
    private long lastAction = System.currentTimeMillis();
    private long lastBreak = System.currentTimeMillis();
    private int actionCount = 0;

    // Account-specific timing offsets (makes each account unique)
    private final int accountTimingOffset;
    private final double accountSpeedMultiplier;

    public AntiBan() {
        this(null);
    }

    public AntiBan(Settings settings) {
        this.settings = settings;
        this.random = new Random();

        // Generate account-specific seed (consistent across sessions)
        String accountId = getAccountIdentifier();
        long seed = accountId.hashCode();
        this.accountRandom = new Random(seed);

        // Each account has slightly different timing (±20%)
        this.accountTimingOffset = accountRandom.nextInt(400) - 200; // -200ms to +200ms
        this.accountSpeedMultiplier = 0.9 + (accountRandom.nextDouble() * 0.2); // 0.9x to 1.1x speed

        Logs.info("AntiBan initialized for account (offset: " + accountTimingOffset + "ms, speed: " +
                String.format("%.2f", accountSpeedMultiplier) + "x)");
    }

    /**
     * Standard idle sleep with Gaussian distribution (600-1000ms centered at 800ms)
     */
    public void idleSleep() {
        if (settings != null && settings.antiBan != null && settings.antiBan.enabled) {
            int mean = (settings.antiBan.minDelayMs + settings.antiBan.maxDelayMs) / 2;
            int stdDev = (settings.antiBan.maxDelayMs - settings.antiBan.minDelayMs) / 6;
            gaussianSleepWithAccount(mean, stdDev);
        } else {
            gaussianSleepWithAccount(800, 133); // Mean 800ms, stddev 133ms
        }
        updateActivity();
    }

    /**
     * Short pause for quick actions (100-300ms, Gaussian)
     */
    public void shortPause() {
        gaussianSleepWithAccount(200, 50);
        updateActivity();
    }

    /**
     * Medium pause for moderate actions (300-700ms, Gaussian)
     */
    public void mediumPause() {
        gaussianSleepWithAccount(500, 100);
        updateActivity();
    }

    /**
     * Long pause for major actions (1500-3500ms, Gaussian)
     */
    public void longPause() {
        gaussianSleepWithAccount(2500, 400);
        updateActivity();
    }

    /**
     * IMPROVED: Gaussian sleep with account-specific variance
     */
    private void gaussianSleepWithAccount(int meanMs, int stdDevMs) {
        // Apply account speed multiplier
        double adjustedMean = meanMs * accountSpeedMultiplier;
        double adjustedStdDev = stdDevMs * accountSpeedMultiplier;

        // Generate Gaussian-distributed delay
        double gaussian = random.nextGaussian();
        int duration = (int)(adjustedMean + gaussian * adjustedStdDev);

        // Add account-specific offset
        duration += accountTimingOffset;

        // Clamp to reasonable bounds (min 50ms)
        duration = Math.max(50, duration);

        // 5% chance for "distraction" delay (human looked away)
        if (random.nextInt(100) < 5) {
            int distractionDelay = 2000 + random.nextInt(6000); // 2-8 seconds
            duration += distractionDelay;
            Logs.debug("Distraction delay: +" + distractionDelay + "ms");
        }

        sleepExact(duration);
    }

    /**
     * IMPROVED: Sleep with exact duration
     */
    public void sleepExact(int ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * IMPROVED: Variable sleep with micro-adjustments (prevents pattern detection)
     */
    public void sleep(int minMs, int maxMs) {
        if (minMs >= maxMs) {
            sleepExact(minMs);
            return;
        }

        // Calculate mean and use Gaussian distribution
        int mean = (minMs + maxMs) / 2;
        int stdDev = (maxMs - minMs) / 6;

        gaussianSleepWithAccount(mean, stdDev);
    }

    /**
     * IMPROVED: Check if should take break (fatigue model)
     */
    public boolean shouldTakeBreak() {
        if (settings == null || settings.antiBan == null || !settings.antiBan.enabled) {
            return false;
        }

        long timeSinceBreak = System.currentTimeMillis() - lastBreak;
        long oneHour = 60 * 60 * 1000;

        // Fatigue increases chance of break over time
        double hoursActive = timeSinceBreak / (double) oneHour;
        int fatigueBonus = (int) Math.min(20, hoursActive * 5); // +5% per hour, max +20%

        // Check with fatigue-adjusted chance
        int breakChance = settings.antiBan.afkBreakChance + fatigueBonus;

        if (timeSinceBreak > oneHour && random.nextInt(100) < breakChance) {
            Logs.info("AntiBan: Taking break (fatigue: " + fatigueBonus + "% bonus)");
            return true;
        }

        return false;
    }

    /**
     * IMPROVED: Take break with variable duration
     */
    public void takeShortBreak() {
        // Gaussian distribution: mean 60s, stddev 20s
        double gaussian = random.nextGaussian();
        int duration = (int)(60_000 + gaussian * 20_000);
        duration = Math.max(15_000, Math.min(180_000, duration)); // 15s to 3min

        Logs.info("AntiBan: Short break for " + (duration / 1000) + " seconds");
        sleepExact(duration);
        lastBreak = System.currentTimeMillis();
    }

    public void takeMediumBreak() {
        // Gaussian: mean 4min, stddev 1.5min
        double gaussian = random.nextGaussian();
        int duration = (int)(240_000 + gaussian * 90_000);
        duration = Math.max(120_000, Math.min(600_000, duration)); // 2-10min

        Logs.info("AntiBan: Medium break for " + (duration / 60000) + " minutes");
        sleepExact(duration);
        lastBreak = System.currentTimeMillis();
    }

    /**
     * Human reaction time with realistic variance
     */
    public void humanReactionDelay() {
        // Humans: 200-400ms reaction time with Gaussian distribution
        gaussianSleepWithAccount(300, 60);
    }

    /**
     * IMPROVED: Occasionally make "mistakes" (human-like)
     */
    public boolean shouldMakeMistake() {
        // 1-3% chance to make a mistake (cancel offer, wrong price, etc.)
        int mistakeChance = 1 + accountRandom.nextInt(3);
        return random.nextInt(100) < mistakeChance;
    }

    /**
     * Random mouse movement (placeholder - needs DreamBot API)
     */
    public void randomMouseMovement() {
        if (settings == null || settings.antiBan == null || !settings.antiBan.randomMouseMovements) {
            return;
        }
        // TODO: Implement with DreamBot Mouse API
        shortPause();
    }

    /**
     * Random camera rotation (placeholder - needs DreamBot API)
     */
    public void randomCameraMovement() {
        if (settings == null || settings.antiBan == null || !settings.antiBan.randomCameraRotation) {
            return;
        }
        // TODO: Implement with DreamBot Camera API
        mediumPause();
    }

    /**
     * Periodic idle actions
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
            Logs.debug("AntiBan: Checking stats");
            mediumPause();
        } else if (roll < 70) {
            Logs.debug("AntiBan: Hovering item");
            shortPause();
        }
        // 30% chance: do nothing
    }

    private boolean shouldPerformIdleAction() {
        // Variable threshold based on account
        int threshold = 50 + accountRandom.nextInt(50); // 50-100 actions
        return actionCount % threshold == 0;
    }

    private void updateActivity() {
        lastAction = System.currentTimeMillis();
        actionCount++;

        // Check for break
        if (shouldTakeBreak()) {
            takeShortBreak();
        }

        // Idle actions
        if (random.nextInt(100) < 3) { // 3% chance
            performIdleAction();
        }
    }

    // Getters
    public long getTimeSinceLastAction() {
        return (System.currentTimeMillis() - lastAction) / 1000;
    }

    public int getActionCount() {
        return actionCount;
    }

    public void reset() {
        actionCount = 0;
        lastAction = System.currentTimeMillis();
        Logs.info("AntiBan: Activity reset");
    }

    /**
     * Get account identifier (username hash)
     */
    private String getAccountIdentifier() {
        // Try to get from settings or generate default
        if (settings != null && settings.botId != null && !settings.botId.isEmpty()) {
            return settings.botId;
        }
        return "Account" + System.currentTimeMillis();
    }

    /**
     * IMPROVED: Gaussian sleep (accessible)
     */
    public void gaussianSleep(int meanMs, int stdDevMs) {
        gaussianSleepWithAccount(meanMs, stdDevMs);
    }

    /**
     * Variable sleep for action types
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
}