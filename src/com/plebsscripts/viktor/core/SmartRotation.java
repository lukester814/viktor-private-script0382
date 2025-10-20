package com.plebsscripts.viktor.core;

import com.plebsscripts.viktor.config.ItemConfig;
import com.plebsscripts.viktor.coord.JsonCoordinator;
import com.plebsscripts.viktor.limits.LimitTracker;
import com.plebsscripts.viktor.util.Logs;

import java.util.*;

/**
 * Smart item rotation with priority takeover (JSON FILE VERSION).
 *
 * Uses JsonCoordinator instead of HTTP server - safer and simpler!
 *
 * When Bot A hits 4h limit on an item, Bot B automatically prioritizes it.
 * This maximizes GP/hour across all bots by ensuring items are always traded.
 *
 * Priority Queue Logic:
 * 1. TAKEOVER items (other bots hit limits) - HIGHEST PRIORITY
 * 2. HIGH-PROFIT items (margin >= 1.5x minimum)
 * 3. REGULAR items (everything else)
 *
 * Each category is sorted by profit margin (best first).
 */
public class SmartRotation {
    private final JsonCoordinator coordinator;  // Changed from CoordinatorClient
    private final LimitTracker localLimits;
    private final String botId;

    // Cache to avoid excessive file reads
    private Set<String> lastKnownBlocked = new HashSet<>();
    private long lastUpdateTime = 0;
    private static final long UPDATE_INTERVAL_MS = 30_000; // 30 seconds

    public SmartRotation(JsonCoordinator coordinator, LimitTracker localLimits, String botId) {
        this.coordinator = coordinator;
        this.localLimits = localLimits;
        this.botId = botId;

        Logs.info("SmartRotation initialized for bot: " + botId);
    }

    /**
     * Build prioritized item queue with takeover logic.
     *
     * Priority order:
     * 1. Items OTHER bots hit limits on (takeover opportunity)
     * 2. High-profit items we haven't hit limits on
     * 3. Remaining items
     *
     * @param allItems All available items from CSV
     * @return Prioritized list of items to trade
     */
    public List<ItemConfig> buildPrioritizedQueue(List<ItemConfig> allItems) {
        if (allItems == null || allItems.isEmpty()) {
            return new ArrayList<>();
        }

        // Get items other bots are blocked on
        Set<String> otherBotsBlocked = getItemsBlockedByOtherBots();

        // Categorize items
        List<ItemConfig> takeoverItems = new ArrayList<>();     // Other bots hit limit - PRIORITY
        List<ItemConfig> highProfitItems = new ArrayList<>();   // Best margins - NORMAL
        List<ItemConfig> regularItems = new ArrayList<>();      // Everything else - LOW

        for (ItemConfig item : allItems) {
            String itemName = item.itemName.toLowerCase();

            // Skip if WE hit the limit locally
            if (localLimits.isBlocked(item.itemName)) {
                Logs.debug("Skipping " + item.itemName + " - we hit local limit");
                continue;
            }

            // Check if another bot is blocked on this item
            if (otherBotsBlocked.contains(itemName)) {
                takeoverItems.add(item);
                Logs.info("TAKEOVER OPPORTUNITY: " + item.itemName + " (another bot hit limit)");
                continue;
            }

            // Categorize by profit margin
            int margin = item.getSellPrice() - item.getBuyPrice();
            if (margin >= item.minMarginGp * 1.5) {
                highProfitItems.add(item);
            } else {
                regularItems.add(item);
            }
        }

        // Sort each category by profit (descending)
        Comparator<ItemConfig> profitSort = (a, b) -> {
            int marginA = a.getSellPrice() - a.getBuyPrice();
            int marginB = b.getSellPrice() - b.getBuyPrice();
            return Integer.compare(marginB, marginA); // Descending
        };

        takeoverItems.sort(profitSort);
        highProfitItems.sort(profitSort);
        regularItems.sort(profitSort);

        // Build final queue: takeovers first, then high-profit, then regular
        List<ItemConfig> prioritizedQueue = new ArrayList<>();
        prioritizedQueue.addAll(takeoverItems);
        prioritizedQueue.addAll(highProfitItems);
        prioritizedQueue.addAll(regularItems);

        Logs.info("Smart Queue: " + takeoverItems.size() + " takeovers, " +
                highProfitItems.size() + " high-profit, " +
                regularItems.size() + " regular items");

        return prioritizedQueue;
    }

    /**
     * Get items that OTHER bots hit limits on (not us)
     */
    private Set<String> getItemsBlockedByOtherBots() {
        // Coordinator not available - return empty
        if (coordinator == null) {
            return new HashSet<>();
        }

        // Rate limit file reads (cache for 30s)
        long now = System.currentTimeMillis();
        if (now - lastUpdateTime < UPDATE_INTERVAL_MS && !lastKnownBlocked.isEmpty()) {
            Logs.debug("Using cached blocked items list (" + lastKnownBlocked.size() + " items)");
            return new HashSet<>(lastKnownBlocked); // Return cached
        }

        Set<String> otherBotsBlocked = new HashSet<>();

        try {
            // Get detailed block info from JSON file
            Map<String, String> blockedWithAccounts = coordinator.getBlockedItemsWithAccounts();

            for (Map.Entry<String, String> entry : blockedWithAccounts.entrySet()) {
                String itemName = entry.getKey().toLowerCase();
                String accountName = entry.getValue();

                // Only include if ANOTHER bot blocked it (not us)
                if (!accountName.equalsIgnoreCase(botId)) {
                    otherBotsBlocked.add(itemName);
                    Logs.debug("Other bot blocked: " + itemName + " (by " + accountName + ")");
                }
            }

            // Update cache
            lastKnownBlocked = otherBotsBlocked;
            lastUpdateTime = now;

            Logs.info("JSON query: " + otherBotsBlocked.size() + " takeover opportunities");

        } catch (Exception e) {
            Logs.warn("Failed to read coordination file: " + e.getMessage());
            // Return cached on failure (don't break bot if file corrupted)
            return new HashSet<>(lastKnownBlocked);
        }

        return otherBotsBlocked;
    }

    /**
     * Get next item to trade (with smart prioritization)
     *
     * Strategy:
     * 1. Try takeover items first (top 3)
     * 2. If none available, pick randomly from top 10 (avoid patterns)
     * 3. Ensure we're not locally blocked
     */
    public ItemConfig getNextItem(List<ItemConfig> prioritizedQueue) {
        if (prioritizedQueue == null || prioritizedQueue.isEmpty()) {
            Logs.warn("Empty prioritized queue - no items available");
            return null;
        }

        // Try takeover items first (top 3 of queue)
        for (int i = 0; i < Math.min(3, prioritizedQueue.size()); i++) {
            ItemConfig item = prioritizedQueue.get(i);

            // Double-check we're not locally blocked (edge case)
            if (!localLimits.isBlocked(item.itemName)) {
                Logs.info("Selected item (priority " + (i + 1) + "): " + item.itemName);
                return item;
            }
        }

        // Fallback: pick random from top 10 (avoid predictable patterns)
        int pickFrom = Math.min(10, prioritizedQueue.size());
        Random random = new Random();

        for (int attempt = 0; attempt < pickFrom; attempt++) {
            int idx = random.nextInt(pickFrom);
            ItemConfig item = prioritizedQueue.get(idx);

            if (!localLimits.isBlocked(item.itemName)) {
                Logs.info("Selected item (random from top " + pickFrom + "): " + item.itemName);
                return item;
            }
        }

        Logs.warn("All items in queue are blocked locally!");
        return null; // All items blocked
    }

    /**
     * Report that we hit a limit (write to JSON file)
     */
    public void reportLimitHit(String itemName) {
        if (coordinator == null) {
            Logs.debug("No coordinator - skipping limit report");
            return;
        }

        try {
            coordinator.reportLimit(itemName);
            Logs.info("Reported 4h limit to JSON: " + itemName);

            // Invalidate cache so next query gets fresh data
            lastKnownBlocked.remove(itemName.toLowerCase());
            coordinator.invalidateCache(); // Force file re-read

        } catch (Exception e) {
            Logs.warn("Failed to report limit: " + e.getMessage());
        }
    }

    /**
     * Get takeover statistics for display/logging
     */
    public String getTakeoverStats() {
        Set<String> takeovers = getItemsBlockedByOtherBots();

        if (takeovers.isEmpty()) {
            return "No takeover opportunities available";
        }

        return takeovers.size() + " takeover opportunities: " +
                String.join(", ", takeovers);
    }

    /**
     * Force refresh of blocked items cache
     * (useful for testing or when coordinator data changes rapidly)
     */
    public void refreshCache() {
        lastUpdateTime = 0;
        lastKnownBlocked.clear();
        if (coordinator != null) {
            coordinator.invalidateCache();
        }
        Logs.info("SmartRotation cache cleared - will refresh on next query");
    }

    /**
     * Periodic cleanup of expired entries
     * Call this occasionally (e.g., once per hour)
     */
    public void cleanup() {
        if (coordinator != null) {
            coordinator.cleanup();
        }
    }
}