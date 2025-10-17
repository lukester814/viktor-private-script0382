package com.plebsscripts.viktor.core;

import com.plebsscripts.viktor.config.ItemConfig;
import com.plebsscripts.viktor.util.Logs;
import java.util.Random;

/**
 * Simulates human mistakes and corrections.
 * Makes bot behavior less perfect and more human-like.
 */
public class HumanBehavior {
    private final Random random = new Random();
    private final AntiBan antiBan;

    public HumanBehavior(AntiBan antiBan) {
        this.antiBan = antiBan;
    }

    /**
     * Sometimes enter wrong price (1-2% chance)
     * Then "notice" and correct it
     */
    public int maybeWrongPrice(int correctPrice, String action) {
        // 1-2% chance to make mistake
        if (random.nextInt(100) >= 2) {
            return correctPrice; // No mistake
        }

        // Make a realistic mistake
        int wrongPrice = correctPrice;
        int mistakeType = random.nextInt(3);

        switch (mistakeType) {
            case 0: // Typo: off by 1 digit
                int digitPos = random.nextInt(3); // Last 3 digits
                int adjustment = (int) Math.pow(10, digitPos) * (random.nextBoolean() ? 1 : -1);
                wrongPrice = correctPrice + adjustment;
                break;

            case 1: // Wrong magnitude (10x or 0.1x)
                wrongPrice = random.nextBoolean() ? correctPrice * 10 : correctPrice / 10;
                break;

            case 2: // Off by small percentage (5-10%)
                double factor = 1.0 + (0.05 + random.nextDouble() * 0.05) * (random.nextBoolean() ? 1 : -1);
                wrongPrice = (int) (correctPrice * factor);
                break;
        }

        wrongPrice = Math.max(1, wrongPrice); // Min 1gp

        Logs.info("Human mistake: Entered " + wrongPrice + " instead of " + correctPrice + " (" + action + ")");

        // Simulate "noticing" the mistake after 1-3 seconds
        antiBan.sleep(1000, 3000);
        Logs.info("Correcting mistake to " + correctPrice);

        return correctPrice; // Return correct price (as if we corrected it)
    }

    /**
     * Sometimes cancel an offer by accident (0.5% chance)
     */
    public boolean shouldAccidentallyCancel() {
        return random.nextInt(200) == 0; // 0.5% chance
    }

    /**
     * Sometimes misclick and need to retry (1% chance)
     */
    public boolean shouldMisclick() {
        return random.nextInt(100) < 1;
    }

    /**
     * Simulate a misclick: pause, correct
     */
    public void handleMisclick() {
        Logs.debug("Misclick - correcting...");
        antiBan.sleep(500, 1500); // Notice the mistake
        // In real implementation, would move mouse to correct position
    }

    /**
     * Sometimes check item details before trading (15% chance)
     */
    public boolean shouldCheckItemFirst() {
        return random.nextInt(100) < 15;
    }

    /**
     * Simulate checking item on GE
     */
    public void checkItemDetails(ItemConfig item) {
        Logs.info("Checking GE details for " + item.itemName);
        antiBan.sleep(1500, 4000); // Read the info
    }

    /**
     * Sometimes hesitate before confirming offer (10% chance)
     */
    public void maybeHesitate() {
        if (random.nextInt(100) < 10) {
            Logs.debug("Hesitating before confirming...");
            antiBan.sleep(1000, 3000);
        }
    }

    /**
     * Sometimes check offers in wrong order (5% chance)
     */
    public boolean shouldCheckOffersRandomly() {
        return random.nextInt(100) < 5;
    }

    /**
     * Simulate distraction (alt-tab, look away)
     */
    public void simulateDistraction() {
        int duration = 3000 + random.nextInt(12000); // 3-15 seconds
        Logs.debug("Simulating distraction for " + (duration / 1000) + "s");
        antiBan.sleepExact(duration);
    }

    /**
     * Sometimes adjust quantity slightly (±1-2)
     */
    public int maybeAdjustQuantity(int targetQty) {
        if (random.nextInt(100) < 5) { // 5% chance
            int adjustment = random.nextInt(3) - 1; // -1, 0, or +1
            int newQty = targetQty + adjustment;
            if (newQty > 0) {
                Logs.debug("Adjusted quantity: " + targetQty + " → " + newQty);
                return newQty;
            }
        }
        return targetQty;
    }
}