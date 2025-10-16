package com.plebsscripts.viktor.config;

import com.plebsscripts.viktor.util.Logs;
import java.io.File;
import java.util.List;
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
        // Initial load
        File file = new File(csvPath);
        if (file.exists()) {
            lastModified.set(file.lastModified());
        }

        while (running) {
            try {
                Thread.sleep(checkIntervalMs);

                if (!file.exists()) {
                    Logs.warn("CSV file not found: " + csvPath);
                    continue;
                }

                long currentModified = file.lastModified();

                if (currentModified > lastModified.get()) {
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
            while (running) {
                try {
                    Thread.sleep(checkIntervalMs);

                    // TODO: Fetch pastebin content via HTTP
                    String content = fetchPastebin(pastebinUrl);

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
                }
            }
        }

        private String fetchPastebin(String url) {
            // TODO: Implement HTTP GET
            // Use java.net.URL and HttpURLConnection
            return null;
        }

        private List<ItemConfig> parseCSVContent(String content) {
            // TODO: Parse CSV from string instead of file
            // Could write to temp file and use CSVConfigLoader.load()
            // Or refactor CSVConfigLoader to accept BufferedReader
            return null;
        }
    }
}
