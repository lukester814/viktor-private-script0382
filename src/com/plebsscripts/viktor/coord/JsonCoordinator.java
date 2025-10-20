package com.plebsscripts.viktor.coord;

import com.plebsscripts.viktor.util.Logs;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * JSON file-based coordinator (no network calls - SAFER!)
 *
 * Multiple bots read/write to a shared JSON file: data/coordination.json
 * Format: {"limits": [{"item":"Dragon bones","account":"Bot1","expiresAt":1729278000},...]}
 *
 * Benefits over HTTP server:
 * - No network traffic to detect
 * - No server to maintain
 * - Simple file sharing
 * - Works across machines via shared drive/NFS
 *
 * Usage:
 *   JsonCoordinator coord = new JsonCoordinator("data/coordination.json", "Bot1");
 *   coord.reportLimit("Dragon bones");
 *   Set<String> blocked = coord.getItemsBlockedByOtherBots();
 */
public class JsonCoordinator {
    private final File coordinationFile;
    private final String botId;
    private final Gson gson;

    // Cache to reduce file I/O
    private CoordinationData cachedData;
    private long lastReadTime = 0;
    private static final long CACHE_DURATION_MS = 5000; // 5 seconds

    // File lock to prevent corruption
    private final Object fileLock = new Object();

    public JsonCoordinator(String filePath, String botId) {
        this.coordinationFile = new File(filePath);
        this.botId = botId;
        this.gson = new GsonBuilder().setPrettyPrinting().create();

        // Create file if doesn't exist
        ensureFileExists();

        Logs.info("JsonCoordinator initialized: file=" + filePath + ", botId=" + botId);
    }

    /**
     * Report that this bot hit 4h limit on an item
     */
    public void reportLimit(String itemName) {
        synchronized (fileLock) {
            try {
                CoordinationData data = readData();

                // Add or update limit entry
                LimitEntry entry = new LimitEntry();
                entry.item = itemName;
                entry.account = botId;
                entry.expiresAt = System.currentTimeMillis() + (4L * 60 * 60 * 1000); // 4 hours
                entry.reportedAt = System.currentTimeMillis();

                // Remove old entry for same item/account (if exists)
                data.limits.removeIf(e ->
                        e.item.equalsIgnoreCase(itemName) &&
                                e.account.equalsIgnoreCase(botId));

                // Add new entry
                data.limits.add(entry);

                // Write back to file
                writeData(data);

                Logs.info("Reported limit to JSON: " + itemName + " (expires in 4h)");

            } catch (Exception e) {
                Logs.warn("Failed to report limit: " + e.getMessage());
            }
        }
    }

    /**
     * Get items that OTHER bots have hit limits on (not us)
     */
    public Set<String> getItemsBlockedByOtherBots() {
        Set<String> otherBotsBlocked = new HashSet<>();

        try {
            CoordinationData data = readData();
            long now = System.currentTimeMillis();

            for (LimitEntry entry : data.limits) {
                // Skip expired entries
                if (entry.expiresAt < now) {
                    continue;
                }

                // Only include if ANOTHER bot blocked it (not us)
                if (!entry.account.equalsIgnoreCase(botId)) {
                    otherBotsBlocked.add(entry.item.toLowerCase());
                }
            }

        } catch (Exception e) {
            Logs.warn("Failed to read blocked items: " + e.getMessage());
        }

        return otherBotsBlocked;
    }

    /**
     * Get detailed info about all blocked items (with account names)
     */
    public Map<String, String> getBlockedItemsWithAccounts() {
        Map<String, String> result = new HashMap<>();

        try {
            CoordinationData data = readData();
            long now = System.currentTimeMillis();

            for (LimitEntry entry : data.limits) {
                // Skip expired entries
                if (entry.expiresAt < now) {
                    continue;
                }

                // Only include other bots
                if (!entry.account.equalsIgnoreCase(botId)) {
                    result.put(entry.item, entry.account);
                }
            }

        } catch (Exception e) {
            Logs.warn("Failed to read blocked items: " + e.getMessage());
        }

        return result;
    }

    /**
     * Clean up expired entries (optional maintenance)
     */
    public void cleanup() {
        synchronized (fileLock) {
            try {
                CoordinationData data = readData();
                long now = System.currentTimeMillis();

                int beforeSize = data.limits.size();
                data.limits.removeIf(e -> e.expiresAt < now);
                int afterSize = data.limits.size();

                if (beforeSize > afterSize) {
                    writeData(data);
                    Logs.info("Cleaned up " + (beforeSize - afterSize) + " expired limit entries");
                }

            } catch (Exception e) {
                Logs.warn("Cleanup failed: " + e.getMessage());
            }
        }
    }

    /**
     * Get statistics for monitoring
     */
    public String getStats() {
        try {
            CoordinationData data = readData();
            long now = System.currentTimeMillis();

            int activeCount = 0;
            int myCount = 0;
            int othersCount = 0;

            for (LimitEntry entry : data.limits) {
                if (entry.expiresAt > now) {
                    activeCount++;
                    if (entry.account.equalsIgnoreCase(botId)) {
                        myCount++;
                    } else {
                        othersCount++;
                    }
                }
            }

            return String.format("JsonCoordinator{active=%d, mine=%d, others=%d}",
                    activeCount, myCount, othersCount);

        } catch (Exception e) {
            return "JsonCoordinator{error=" + e.getMessage() + "}";
        }
    }

    /**
     * Always available (no server to check)
     */
    public boolean isAvailable() {
        return coordinationFile.exists();
    }

    /**
     * Force cache refresh on next read
     */
    public void invalidateCache() {
        lastReadTime = 0;
        cachedData = null;
    }

    // ===== Internal Methods =====

    /**
     * Read coordination data from JSON file (with caching)
     */
    private CoordinationData readData() throws IOException {
        long now = System.currentTimeMillis();

        // Return cached data if fresh enough
        if (cachedData != null && (now - lastReadTime) < CACHE_DURATION_MS) {
            return cachedData;
        }

        // Ensure file exists
        if (!coordinationFile.exists()) {
            ensureFileExists();
        }

        // Read from file
        try (Reader reader = new InputStreamReader(
                new FileInputStream(coordinationFile), StandardCharsets.UTF_8)) {

            CoordinationData data = gson.fromJson(reader, CoordinationData.class);

            if (data == null) {
                data = new CoordinationData();
            }
            if (data.limits == null) {
                data.limits = new ArrayList<>();
            }

            // Update cache
            cachedData = data;
            lastReadTime = now;

            return data;
        }
    }

    /**
     * Write coordination data to JSON file
     */
    private void writeData(CoordinationData data) throws IOException {
        // Write to temp file first (atomic write)
        File tempFile = new File(coordinationFile.getParentFile(),
                coordinationFile.getName() + ".tmp");

        try (Writer writer = new OutputStreamWriter(
                new FileOutputStream(tempFile), StandardCharsets.UTF_8)) {
            gson.toJson(data, writer);
        }

        // Atomic rename (replaces old file)
        Files.move(tempFile.toPath(), coordinationFile.toPath(),
                StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);

        // Invalidate cache
        cachedData = data;
        lastReadTime = System.currentTimeMillis();
    }

    /**
     * Create empty coordination file if doesn't exist
     */
    private void ensureFileExists() {
        try {
            if (!coordinationFile.exists()) {
                coordinationFile.getParentFile().mkdirs();

                CoordinationData emptyData = new CoordinationData();
                emptyData.limits = new ArrayList<>();

                try (Writer writer = new OutputStreamWriter(
                        new FileOutputStream(coordinationFile), StandardCharsets.UTF_8)) {
                    gson.toJson(emptyData, writer);
                }

                Logs.info("Created coordination file: " + coordinationFile.getPath());
            }
        } catch (Exception e) {
            Logs.warn("Failed to create coordination file: " + e.getMessage());
        }
    }

    // ===== Data Classes =====

    /**
     * Root JSON structure
     */
    private static class CoordinationData {
        List<LimitEntry> limits;
    }

    /**
     * Single limit entry
     */
    private static class LimitEntry {
        String item;        // Item name (e.g., "Dragon bones")
        String account;     // Bot ID (e.g., "Bot1")
        long expiresAt;     // Epoch millis when limit expires
        long reportedAt;    // Epoch millis when reported
    }
}