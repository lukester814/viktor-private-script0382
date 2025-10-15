package com.plebsscripts.viktor.notify;

import com.plebsscripts.viktor.config.ItemConfig;
import com.plebsscripts.viktor.config.Settings;
import com.plebsscripts.viktor.util.Logs;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class DiscordNotifier {

    private final String webhookUrl; // may be null/empty

    public DiscordNotifier() {
        this.webhookUrl = null;
    }

    public DiscordNotifier(String webhookUrl) {
        this.webhookUrl = webhookUrl;
    }

    /** Convenience creator that tries to read a webhook URL from Settings in a tolerant way. */
    public static DiscordNotifier fromSettings(Settings s) {
        String url = null;
        if (s != null) {
            // Try common field names without hard dependency on your Settings API
            try {
                // If you actually have getters, switch to s.getDiscordWebhook()
                java.lang.reflect.Field f = s.getClass().getDeclaredField("discordWebhook");
                f.setAccessible(true);
                Object v = f.get(s);
                if (v != null) url = String.valueOf(v);
            } catch (Throwable ignored) {}
        }
        return new DiscordNotifier(url);
    }

    // ---------- public events ----------

    public void info(String msg)  { send("[Info] " + msg); }
    public void warn(String msg)  { send("[Warn] " + msg); }
    public void error(String msg) { send("[Error] " + msg); }

    public void probeOk(ItemConfig item, int buy, int sell) {
        send("✅ **Probe OK** — **" + safe(item.itemName) + "** | buy @" + buy + " • sell @" + sell + " • Δ " + (sell - buy) + " gp");
    }

    public void probeFail(ItemConfig item) {
        send("❌ **Probe FAIL** — **" + safe(item.itemName) + "**");
    }

    public void limitHit(ItemConfig item, long untilEpoch) {
        send("⛔ **4h limit** — **" + safe(item.itemName) + "** • until <t:" + untilEpoch + ":R>");
    }

    public void tradeBuyPlaced(ItemConfig item, int price, int qty) {
        send("🟢 **Buy placed** — **" + safe(item.itemName) + "** • " + qty + " × " + price + " gp");
    }

    public void tradeSellPlaced(ItemConfig item, int price, int qty) {
        send("🟣 **Sell placed** — **" + safe(item.itemName) + "** • " + qty + " × " + price + " gp");
    }

    // ---------- internals ----------

    private void send(String content) {
        if (webhookUrl == null || webhookUrl.trim().isEmpty()) {
            // No webhook configured — just log locally
            Logs.info("[Discord] " + content);
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

    private String escape(String s) { return s.replace("\\","\\\\").replace("\"","\\\""); }
    private String safe(String s)   { return s == null ? "" : s; }
}
