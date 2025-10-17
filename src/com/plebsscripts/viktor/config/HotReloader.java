package com.plebsscripts.viktor.config;

import com.plebsscripts.viktor.util.Logs;
import java.io.File;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Watches the CSV file for changes and reloads it automatically.
 * Useful for when your client updates the CSV with new prices/items.
 *
 * Future: Can also watch a Pastebin URL for live updates.
 */

public class HotReloader implements Runnable {
    private final String csvPath;
    private final Callback callback;
    private final long checkIntervalMs;
    private volatile boolean running = true;
    private final AtomicLong lastModified = new AtomicLong(0);

    public interface Callback {
        void onReload(List<ItemConfig> newItems);
    }

    /**
     * @param csvPath Path to CSV file to watch
     * @param callback Called when CSV changes
     * @param checkIntervalMs How often to check file (default: 30 seconds)
     */
    public HotReloader(String csvPath, Callback callback, long checkIntervalMs) {
        this.csvPath = csvPath;
        this.callback = callback;
        this.checkIntervalMs = checkIntervalMs;
    }

    public HotReloader(String csvPath, Callback callback) {
        this(csvPath, callback, 30000); // Default 30 seconds
    }

    public void start() {
        Thread t = new Thread(this, "HotReloader");
        t.setDaemon(true);
        t.start();
        Logs.info("HotReloader watching: " + csvPath);
    }

    public void stop() {
        running = false;
    }

    @Override
    public void run() {
        // Generate account-specific check interval
        String accountId = csvPath; // Use file path as seed
        long seed = accountId.hashCode();
        Random accountRng = new Random(seed);

        // Each bot has slightly different check interval (±30%)
        long baseInterval = checkIntervalMs;
        long accountVariance = (long) (baseInterval * 0.3 * accountRng.nextDouble());
        long accountInterval = baseInterval + accountVariance;

        Logs.info("HotReloader: Check interval = " + accountInterval + "ms");

        // Initial load
        File file = new File(csvPath);
        if (file.exists()) {
            lastModified.set(file.lastModified());
        }

        while (running) {
            try {
                // Add jitter to each check (±20%)
                long jitter = (long) (accountInterval * 0.2 * (Math.random() * 2 - 1));
                long actualWait = accountInterval + jitter;

                Thread.sleep(actualWait);

                if (!file.exists()) {
                    Logs.warn("CSV file not found: " + csvPath);
                    continue;
                }

                long currentModified = file.lastModified();

                if (currentModified > lastModified.get()) {
                    // Don't reload immediately - add human delay
                    long humanDelay = 2000 + (long) (Math.random() * 5000); // 2-7 seconds
                    Logs.info("CSV changed, waiting " + (humanDelay / 1000) + "s before reload...");
                    Thread.sleep(humanDelay);

                    Logs.info("CSV changed, reloading...");

                    List<ItemConfig> newItems = CSVConfigLoader.load(csvPath);

                    if (newItems != null && !newItems.isEmpty()) {
                        lastModified.set(currentModified);
                        callback.onReload(newItems);
                        Logs.info("Reloaded " + newItems.size() + " items");
                    } else {
                        Logs.warn("Reload failed - keeping old items");
                    }
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                Logs.warn("HotReloader error: " + e.getMessage());
            }
        }

        Logs.info("HotReloader stopped");
    }

    /**
     * Future: Load from Pastebin URL instead of file
     */
    /**
     * IMPLEMENTED: Load from Pastebin URL instead of file
     */
    public static class PastebinReloader implements Runnable {
        private final String pastebinUrl;
        private final Callback callback;
        private final long checkIntervalMs;
        private volatile boolean running = true;
        private String lastContent = "";

        public PastebinReloader(String pastebinUrl, Callback callback, long checkIntervalMs) {
            this.pastebinUrl = pastebinUrl;
            this.callback = callback;
            this.checkIntervalMs = checkIntervalMs;
        }

        public void start() {
            Thread t = new Thread(this, "PastebinReloader");
            t.setDaemon(true);
            t.start();
            Logs.info("PastebinReloader watching: " + pastebinUrl);
        }

        public void stop() {
            running = false;
        }

        @Override
        public void run() {
            // Add random offset per account
            long seed = pastebinUrl.hashCode();
            Random rng = new Random(seed);
            long accountOffset = rng.nextInt(30000); // 0-30s offset

            try {
                Thread.sleep(accountOffset); // Initial delay
            } catch (InterruptedException e) {
                return;
            }

            while (running) {
                try {
                    // Add jitter (±20%)
                    long jitter = (long)(checkIntervalMs * 0.2 * (Math.random() * 2 - 1));
                    Thread.sleep(checkIntervalMs + jitter);

                    // Fetch from URL
                    String content = com.plebsscripts.viktor.util.HTTPFetcher.fetch(pastebinUrl);

                    if (content != null && !content.equals(lastContent)) {
                        Logs.info("Pastebin changed, reloading...");

                        // Parse CSV from string
                        List<ItemConfig> newItems = parseCSVContent(content);

                        if (newItems != null && !newItems.isEmpty()) {
                            lastContent = content;
                            callback.onReload(newItems);
                            Logs.info("Reloaded " + newItems.size() + " items from Pastebin");
                        }
                    }

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    Logs.warn("PastebinReloader error: " + e.getMessage());
                    // Don't spam errors - wait longer on failure
                    try {
                        Thread.sleep(checkIntervalMs * 2);
                    } catch (InterruptedException ie) {
                        break;
                    }
                }
            }
        }

        /**
         * Parse CSV content from string
         */
        private List<ItemConfig> parseCSVContent(String content) {
            try {
                // Write to temp file and use existing loader
                File temp = File.createTempFile("viktor_pastebin_", ".csv");
                temp.deleteOnExit();

                java.nio.file.Files.write(temp.toPath(), content.getBytes(java.nio.charset.StandardCharsets.UTF_8));

                List<ItemConfig> items = CSVConfigLoader.load(temp.getAbsolutePath());

                temp.delete();

                return items;

            } catch (Exception e) {
                Logs.warn("CSV parse failed: " + e.getMessage());
                return null;
            }
        }
    }}
