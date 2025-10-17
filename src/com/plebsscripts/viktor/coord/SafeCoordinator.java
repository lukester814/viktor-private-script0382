package com.plebsscripts.viktor.coord;

import com.plebsscripts.viktor.util.Logs;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SAFER: Local-only coordinator (no network calls).
 * Each bot tracks limits locally to avoid network detection.
 */
public class SafeCoordinator {
    private final String accountName;
    private final Map<String, Long> localBlocks = new ConcurrentHashMap<String, Long>();

    public SafeCoordinator(String accountName) {
        this.accountName = accountName;
        Logs.info("SafeCoordinator initialized for: " + accountName);
    }

    /**
     * Report 4h limit (stores locally only)
     */
    public void reportLimit(String itemName) {
        long unblockAt = System.currentTimeMillis() + (4L * 60L * 60L * 1000L);
        localBlocks.put(itemName.toLowerCase(), unblockAt);
        Logs.info("Locally blocked: " + itemName + " until " + new Date(unblockAt));
    }

    /**
     * Check if item is blocked locally
     */
    public boolean isBlocked(String itemName) {
        Long unblockAt = localBlocks.get(itemName.toLowerCase());
        if (unblockAt == null) return false;

        long now = System.currentTimeMillis();
        if (now >= unblockAt) {
            localBlocks.remove(itemName.toLowerCase());
            return false;
        }
        return true;
    }

    /**
     * Get all blocked items (removes expired)
     */
    public Set<String> getBlockedItems() {
        Set<String> blocked = new HashSet<String>();
        long now = System.currentTimeMillis();

        Iterator<Map.Entry<String, Long>> it = localBlocks.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Long> entry = it.next();
            if (entry.getValue() > now) {
                blocked.add(entry.getKey());
            } else {
                it.remove(); // Clean up expired
            }
        }

        return blocked;
    }

    /**
     * Get remaining time for blocked item (in seconds)
     */
    public long getRemainingTime(String itemName) {
        Long unblockAt = localBlocks.get(itemName.toLowerCase());
        if (unblockAt == null) return 0;

        long remaining = (unblockAt - System.currentTimeMillis()) / 1000;
        return Math.max(0, remaining);
    }

    /**
     * Manual unblock (for testing)
     */
    public void unblock(String itemName) {
        localBlocks.remove(itemName.toLowerCase());
        Logs.info("Manually unblocked: " + itemName);
    }

    /**
     * Clear all blocks
     */
    public void clearAll() {
        int count = localBlocks.size();
        localBlocks.clear();
        Logs.info("Cleared " + count + " local blocks");
    }

    /**
     * Always "available" since it's local
     */
    public boolean isServerAvailable() {
        return true;
    }

    /**
     * Get status for debugging
     */
    public String getStatus() {
        return "SafeCoordinator{account=" + accountName + ", blocked=" + getBlockedItems().size() + "}";
    }
}