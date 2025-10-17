package com.plebsscripts.viktor.coord;

import com.plebsscripts.viktor.util.Logs;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SAFER: In-memory coordinator (no network calls).
 * Each bot tracks limits locally in a shared file.
 * Less detectable than HTTP coordinator.
 */
public class SafeCoordinator {
    private final String accountName;
    private final Map<String, Long> localBlocks = new ConcurrentHashMap<String, Long>();
    private final Random random = new Random();

    public SafeCoordinator(String accountName) {
        this.accountName = accountName;
    }

    /**
     * Report 4h limit (stores locally only)
     */
    public void reportLimit(String itemName) {
        long unblockAt = System.currentTimeMillis() + (4 * 60 * 60 * 1000);
        localBlocks.put(itemName.toLowerCase(), unblockAt);
        Logs.info("Locally blocked: " + itemName);

        // Optional: Write to shared file (not recommended - file I/O detectable)
        // saveToSharedFile();
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
     * Get blocked items
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
     * NO network calls - all local
     */
    public boolean isServerAvailable() {
        return true; // Always "available" since it's local
    }
}