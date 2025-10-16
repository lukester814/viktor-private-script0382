package com.plebsscripts.viktor.limits;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Tracks 4-hour trade limits for items.
 * Stores item names in lowercase for case-insensitive matching.
 * Uses epoch seconds for efficient storage and comparison.
 */
public class LimitTracker {
    private final Map<String, Long> blockedUntil = new HashMap<>(); // epoch seconds

    /**
     * Check if an item is currently blocked (4h limit hit)
     * @param itemName Item name (case-insensitive)
     * @return true if blocked, false if available
     */
    public boolean isBlocked(String itemName) {
        Long t = blockedUntil.get(itemName.toLowerCase());
        return t != null && Instant.now().getEpochSecond() < t;
    }

    /**
     * Block an item for 4 hours from now
     * @param itemName Item name (case-insensitive)
     * @return Epoch seconds when block expires
     */
    public long blockFor4h(String itemName) {
        long until = Instant.now().getEpochSecond() + 4 * 3600;
        blockedUntil.put(itemName.toLowerCase(), until);
        return until;
    }

    /**
     * Get remaining block time in seconds
     * @param itemName Item name (case-insensitive)
     * @return Seconds remaining until unblocked (0 if not blocked)
     */
    public long getRemainingBlockTime(String itemName) {
        Long until = blockedUntil.get(itemName.toLowerCase());
        if (until == null) {
            return 0;
        }

        long remaining = until - Instant.now().getEpochSecond();
        return Math.max(0, remaining);
    }

    /**
     * Get remaining time formatted as human-readable string
     * @param itemName Item name (case-insensitive)
     * @return Formatted time like "3h 45m" or "Not blocked"
     */
    public String getRemainingTimeFormatted(String itemName) {
        long seconds = getRemainingBlockTime(itemName);
        if (seconds == 0) {
            return "Not blocked";
        }

        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;

        if (hours > 0) {
            return hours + "h " + minutes + "m";
        }
        return minutes + "m";
    }

    /**
     * Get all currently blocked items with their expiry times
     * @return Map of item name (lowercase) -> epoch seconds
     */
    public Map<String, Long> getAllBlocks() {
        // Clean up expired blocks
        long now = Instant.now().getEpochSecond();
        blockedUntil.entrySet().removeIf(entry -> entry.getValue() <= now);

        return new HashMap<>(blockedUntil);
    }

    /**
     * Get count of currently blocked items
     * @return Number of blocked items
     */
    public int getBlockedCount() {
        // Clean up expired blocks first
        long now = Instant.now().getEpochSecond();
        blockedUntil.entrySet().removeIf(entry -> entry.getValue() <= now);

        return blockedUntil.size();
    }

    /**
     * Manually unblock an item (for testing/debugging)
     * @param itemName Item name (case-insensitive)
     */
    public void unblock(String itemName) {
        blockedUntil.remove(itemName.toLowerCase());
    }

    /**
     * Clear all blocks (for testing/debugging)
     */
    public void clearAll() {
        blockedUntil.clear();
    }

    // === Persistence Methods (for LimitStore) ===

    /**
     * Get snapshot of all blocks for saving
     * @return Map of item name (lowercase) -> epoch seconds
     */
    public Map<String, Long> snapshot() {
        return new HashMap<>(blockedUntil);
    }

    /**
     * Restore a block from saved data
     * @param itemName Item name (will be converted to lowercase)
     * @param untilEpoch Epoch seconds when block expires
     */
    public void restore(String itemName, long untilEpoch) {
        blockedUntil.put(itemName.toLowerCase(), untilEpoch);
    }
}
