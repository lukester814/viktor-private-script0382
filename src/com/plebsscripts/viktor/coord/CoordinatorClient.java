package com.plebsscripts.viktor.coord;

import com.plebsscripts.viktor.util.Logs;
import java.io.*;
import java.net.*;
import java.util.*;

/**
 * Client for communicating with CoordinatorServer.
 * Handles limit reporting and querying blocked items across multiple bots.
 */
public class CoordinatorClient {
    private final String serverUrl;
    private final Backoff backoff;
    private volatile boolean serverAvailable = true;
    private long lastHealthCheck = 0;
    private static final long HEALTH_CHECK_INTERVAL = 60_000; // 1 minute

    public CoordinatorClient(String serverUrl) {
        this.serverUrl = serverUrl;
        this.backoff = new Backoff(1000, 30_000); // 1s initial, 30s max
        checkHealth(); // Initial health check
    }

    /**
     * Report that this account hit 4h limit on an item
     */
    public void reportLimit(String item, String account) {
        if (!serverAvailable) {
            Logs.warn("Coordinator server unavailable, skipping report");
            return;
        }

        int maxAttempts = 3;
        int attempt = 0;

        while (attempt < maxAttempts) {
            try {
                String url = serverUrl + "/report?item=" + URLEncoder.encode(item, "UTF-8")
                        + "&account=" + URLEncoder.encode(account, "UTF-8");
                String response = httpGet(url);

                backoff.reset(); // Success!
                Logs.info("Coordinator: reported " + item + " blocked for " + account);
                return;

            } catch (Exception e) {
                attempt++;
                Logs.warn("reportLimit failed (attempt " + attempt + "/" + maxAttempts + "): " + e.getMessage());

                if (attempt < maxAttempts) {
                    backoff.increase();
                    backoff.sleepWithJitter(); // Retry with backoff
                } else {
                    serverAvailable = false; // Mark as unavailable after max retries
                    Logs.error("Coordinator unavailable after " + maxAttempts + " attempts");
                }
            }
        }
    }

    /**
     * Get list of items currently blocked by other bots
     */
    public Set<String> getBlockedItems() {
        if (!serverAvailable) {
            // Periodically retry health check
            if (System.currentTimeMillis() - lastHealthCheck > HEALTH_CHECK_INTERVAL) {
                checkHealth();
            }
            return Collections.emptySet();
        }

        try {
            String response = httpGet(serverUrl + "/list");
            Set<String> items = parseBlockedItems(response);
            backoff.reset(); // Success
            return items;

        } catch (Exception e) {
            Logs.warn("getBlockedItems failed: " + e.getMessage());
            backoff.increase();
            return Collections.emptySet();
        }
    }

    /**
     * Check if coordinator server is reachable
     */
    public boolean checkHealth() {
        try {
            String response = httpGet(serverUrl + "/health");
            serverAvailable = true;
            lastHealthCheck = System.currentTimeMillis();
            Logs.info("Coordinator health check: OK");
            return true;
        } catch (Exception e) {
            serverAvailable = false;
            lastHealthCheck = System.currentTimeMillis();
            Logs.warn("Coordinator health check failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * Get status info for debugging
     */
    public String getStatus() {
        return String.format("Coordinator{url=%s, available=%s, backoff=%dms}",
                serverUrl, serverAvailable, backoff.getCurrentDelay());
    }

    /**
     * Check if a specific item is blocked by another bot
     */
    public boolean isItemBlocked(String itemName) {
        Set<String> blocked = getBlockedItems();
        return blocked.contains(itemName);
    }

    /**
     * Get detailed info about who blocked which items
     */
    public Map<String, String> getBlockedItemsWithAccounts() {
        Map<String, String> result = new HashMap<>();

        try {
            String response = httpGet(serverUrl + "/list");
            // Parse: {"blocked":[{"item":"Maple logs","account":"Bot1"},...]}

            String[] entries = response.split("\\{\"item\":");
            for (int i = 1; i < entries.length; i++) {
                String entry = entries[i];

                // Extract item name
                int itemStart = entry.indexOf("\"") + 1;
                int itemEnd = entry.indexOf("\"", itemStart);
                String item = entry.substring(itemStart, itemEnd);

                // Extract account name
                int accStart = entry.indexOf("\"account\":\"") + 11;
                int accEnd = entry.indexOf("\"", accStart);
                String account = entry.substring(accStart, accEnd);

                result.put(item, account);
            }

        } catch (Exception e) {
            Logs.warn("getBlockedItemsWithAccounts failed: " + e.getMessage());
        }

        return result;
    }

    // === HTTP helpers ===

    private String httpGet(String urlStr) throws IOException {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);
        conn.setRequestProperty("User-Agent", "Viktor-Bot/1.0");

        int responseCode = conn.getResponseCode();
        if (responseCode != 200) {
            throw new IOException("HTTP " + responseCode + " from coordinator");
        }

        BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        StringBuilder response = new StringBuilder();
        String line;
        while ((line = in.readLine()) != null) {
            response.append(line);
        }
        in.close();
        conn.disconnect();

        return response.toString();
    }

    private Set<String> parseBlockedItems(String json) {
        Set<String> items = new HashSet<>();
        try {
            // Simple parse: {"blocked":[{"item":"Maple logs","account":"Bot1"},...]}
            String[] parts = json.split("\"item\":\"");
            for (int i = 1; i < parts.length; i++) {
                String item = parts[i].split("\"")[0];
                items.add(item);
            }
        } catch (Exception e) {
            Logs.warn("JSON parse error: " + e.getMessage());
        }
        return items;
    }

    // === Getters ===

    public boolean isServerAvailable() {
        return serverAvailable;
    }

    public String getServerUrl() {
        return serverUrl;
    }
}
