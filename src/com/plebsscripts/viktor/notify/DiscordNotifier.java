package com.plebsscripts.viktor.notify;

import com.plebsscripts.viktor.config.ItemConfig;
import com.plebsscripts.viktor.config.Settings;
import com.plebsscripts.viktor.util.Logs;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Sends notifications to Discord via webhook.
 * Gracefully handles missing webhook URL (logs locally instead).
 */
public class DiscordNotifier {

    private final String webhookUrl; // may be null/empty

    public DiscordNotifier() {
        this.webhookUrl = null;
    }

    public DiscordNotifier(String webhookUrl) {
        this.webhookUrl = webhookUrl;
    }

    /**
     * Create notifier from Settings (tries to read webhook URL)
     * Returns notifier that logs locally if no webhook configured
     */
    public static DiscordNotifier fromSettings(Settings s) {
        String url = null;
        if (s != null) {
            try {
                // Try reflection to get webhook URL (tolerant approach)
                java.lang.reflect.Field f = s.getClass().getDeclaredField("discordWebhookUrl");
                f.setAccessible(true);
                Object v = f.get(s);
                if (v != null) url = String.valueOf(v);
            } catch (Throwable ignored) {
                // Field doesn't exist or inaccessible
            }
        }
        return new DiscordNotifier(url);
    }

    // === Event Notifications ===

    public void info(String msg) {
        send("[Info] " + msg);
    }

    public void warn(String msg) {
        send("[Warn] " + msg);
    }

    public void error(String msg) {
        send("[Error] " + msg);
    }

    public void probeOk(ItemConfig item, int buy, int sell) {
        int margin = sell - buy;
        send("✅ **Probe OK** — **" + safe(item.itemName) + "**\n" +
                "Buy: " + buy + " gp • Sell: " + sell + " gp • Margin: " + margin + " gp");
    }

    public void probeFail(ItemConfig item) {
        send("❌ **Probe FAIL** — **" + safe(item.itemName) + "**");
    }

    /**
     * Notify about 4h limit hit
     * @param item Item config
     * @param remainingSeconds Seconds until unblocked (not epoch!)
     */
    public void limitHit(ItemConfig item, long remainingSeconds) {
        long hours = remainingSeconds / 3600;
        long minutes = (remainingSeconds % 3600) / 60;

        send("⛔ **4h Limit Hit** — **" + safe(item.itemName) + "**\n" +
                "Cooldown: " + hours + "h " + minutes + "m remaining");
    }

    public void tradeBuyPlaced(ItemConfig item, int price, int qty) {
        long totalCost = (long) qty * price;
        send("🟢 **Buy Placed** — **" + safe(item.itemName) + "**\n" +
                "Quantity: " + qty + " × " + price + " gp = " + formatGp(totalCost));
    }

    public void tradeSellPlaced(ItemConfig item, int price, int qty) {
        long totalValue = (long) qty * price;
        send("🟣 **Sell Placed** — **" + safe(item.itemName) + "**\n" +
                "Quantity: " + qty + " × " + price + " gp = " + formatGp(totalValue));
    }

    /**
     * Notify about completed trade cycle
     */
    public void tradeComplete(ItemConfig item, long profit) {
        String emoji = profit >= 0 ? "💰" : "📉";
        send(emoji + " **Trade Complete** — **" + safe(item.itemName) + "**\n" +
                "Profit: " + formatGp(profit));
    }

    /**
     * Notify about milestone (e.g., 1M gp profit)
     */
    public void milestone(String message) {
        send("🎉 **Milestone** — " + message);
    }

    // === Internals ===

    private void send(String content) {
        if (webhookUrl == null || webhookUrl.trim().isEmpty()) {
            // No webhook configured — just log locally
            Logs.info("[Discord] " + content.replace("\n", " | "));
            return;
        }

        try {
            URL url = new URL(webhookUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);

            String json = "{\"content\":\"" + escape(content) + "\"}";

            OutputStream os = conn.getOutputStream();
            os.write(json.getBytes("UTF-8"));
            os.flush();
            os.close();

            int code = conn.getResponseCode();
            if (code != 204 && code != 200) {
                Logs.warn("[Discord] HTTP " + code);
            }

            conn.disconnect();

        } catch (Exception e) {
            Logs.warn("[Discord] send failed: " + e.getMessage());
        }
    }

    private String escape(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n");
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }

    private String formatGp(long gp) {
        if (gp >= 1_000_000) {
            return String.format("%.2fM gp", gp / 1_000_000.0);
        } else if (gp >= 1_000) {
            return String.format("%.1fK gp", gp / 1_000.0);
        }
        return gp + " gp";
    }
}
