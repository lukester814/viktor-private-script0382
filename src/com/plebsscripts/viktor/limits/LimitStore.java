package com.plebsscripts.viktor.limits;

import com.plebsscripts.viktor.util.Logs;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Persistent storage for 4-hour trade limit blocks.
 * Saves/loads per-account limit data as simple JSON files.
 *
 * File format: data/limits/AccountName.json
 * JSON format: {"item_name": epoch_seconds, ...}
 *
 * Example:
 * {
 *   "Dragon bones": 1729008000,
 *   "Abyssal whip": 1729010400
 * }
 */
public class LimitStore {

    /**
     * Load limit tracker from disk for a specific account.
     * Returns empty tracker if file doesn't exist or fails to load.
     *
     * @param dataDir Base data directory (e.g., "data/")
     * @param account Account name (will be sanitized)
     * @return LimitTracker with restored blocks
     */
    public static LimitTracker loadForAccount(File dataDir, String account) {
        try {
            // Create limits directory if needed
            File dir = new File(dataDir, "limits");
            dir.mkdirs();

            // Get account-specific file
            File f = new File(dir, safe(account) + ".json");

            // Return empty tracker if file doesn't exist
            LimitTracker lt = new LimitTracker();
            if (!f.exists()) {
                Logs.info("No saved limits found for " + account);
                return lt;
            }

            // Read and parse JSON
            String json = readAll(f);
            Map<String, Long> map = parseJson(json);

            // Restore all blocks
            for (Map.Entry<String, Long> e : map.entrySet()) {
                lt.restore(e.getKey(), e.getValue());
            }

            Logs.info("Loaded " + map.size() + " limit blocks for " + account);
            return lt;

        } catch (Exception e) {
            Logs.warn("LimitStore load failed: " + e.getMessage());
            return new LimitTracker();
        }
    }

    /**
     * Save limit tracker to disk for a specific account.
     * Only saves blocks that are still active (in the future).
     *
     * @param dataDir Base data directory (e.g., "data/")
     * @param account Account name (will be sanitized)
     * @param tracker LimitTracker to save
     */
    public static void saveForAccount(File dataDir, String account, LimitTracker tracker) {
        try {
            // Create limits directory if needed
            File dir = new File(dataDir, "limits");
            dir.mkdirs();

            // Get account-specific file
            File f = new File(dir, safe(account) + ".json");

            // Get snapshot and convert to JSON
            Map<String, Long> snapshot = tracker.snapshot();
            String json = toJson(snapshot);

            // Write to file
            writeAll(f, json);

            Logs.info("Saved " + snapshot.size() + " limit blocks for " + account);

        } catch (Exception e) {
            Logs.warn("LimitStore save failed: " + e.getMessage());
        }
    }

    // === Helper Methods ===

    /**
     * Sanitize account name for use as filename.
     * Removes all non-alphanumeric characters except underscore and dash.
     */
    private static String safe(String s) {
        return s.replaceAll("[^a-zA-Z0-9_-]", "_");
    }

    /**
     * Read entire file as UTF-8 string
     */
    private static String readAll(File f) throws IOException {
        FileInputStream in = new FileInputStream(f);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;

        while ((n = in.read(buf)) > 0) {
            out.write(buf, 0, n);
        }

        in.close();
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }

    /**
     * Write string to file as UTF-8
     */
    private static void writeAll(File f, String s) throws IOException {
        OutputStream os = new FileOutputStream(f);
        os.write(s.getBytes(StandardCharsets.UTF_8));
        os.close();
    }

    /**
     * Parse simple JSON format: {"item_name": epoch_seconds, ...}
     * Very lightweight - no external dependencies.
     *
     * Format examples:
     * {"Dragon bones":1729008000}
     * {"Dragon bones":1729008000,"Abyssal whip":1729010400}
     */
    private static Map<String, Long> parseJson(String json) {
        Map<String, Long> m = new HashMap<>();
        json = json.trim();

        // Empty JSON
        if (json.length() < 2) {
            return m;
        }

        // Remove outer braces and split by comma
        String[] parts = json.replace("{", "").replace("}", "").split(",");

        for (String p : parts) {
            p = p.trim();
            if (p.length() == 0) continue;

            // Find item name in quotes
            int k1 = p.indexOf('"');
            int k2 = p.indexOf('"', k1 + 1);
            if (k1 < 0 || k2 <= k1) continue;

            String key = p.substring(k1 + 1, k2);

            // Find value after colon
            int colon = p.indexOf(':', k2 + 1);
            if (colon < 0) continue;

            String num = p.substring(colon + 1).trim();

            // Parse epoch seconds
            try {
                m.put(key, Long.parseLong(num));
            } catch (NumberFormatException ignored) {
                // Skip invalid entries
            }
        }

        return m;
    }

    /**
     * Convert map to simple JSON format: {"item_name": epoch_seconds, ...}
     * Very lightweight - no external dependencies.
     */
    private static String toJson(Map<String, Long> map) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");

        boolean first = true;
        for (Map.Entry<String, Long> e : map.entrySet()) {
            if (!first) {
                sb.append(",");
            }

            // Escape quotes in item names
            String escapedKey = e.getKey().replace("\"", "\\\"");

            sb.append("\"").append(escapedKey).append("\":").append(e.getValue());
            first = false;
        }

        sb.append("}");
        return sb.toString();
    }
}
