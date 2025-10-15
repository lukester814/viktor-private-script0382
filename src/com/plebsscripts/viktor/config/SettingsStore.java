package com.plebsscripts.viktor.config;

import com.plebsscripts.viktor.util.Logs;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public class SettingsStore {

    public static Settings loadOrDefault(File dataDir) {
        try {
            File f = ensureFile(dataDir, "settings.json");
            if (!f.exists() || f.length() == 0) {
                // First run: write defaults
                Settings def = new Settings();
                save(dataDir, def);
                return def;
            }
            String json = new String(java.nio.file.Files.readAllBytes(f.toPath()), java.nio.charset.StandardCharsets.UTF_8);
            return fromJson(json);
        } catch (Exception e) {
            Logs.warn("Settings load failed, using defaults: " + e.getMessage());
            return new Settings();
        }
    }

    public static void save(File dataDir, Settings s) {
        try {
            File f = ensureFile(dataDir, "settings.json");
            try (Writer w = new OutputStreamWriter(new FileOutputStream(f), StandardCharsets.UTF_8)) {
                w.write(toJson(s));
            }
        } catch (Exception e) {
            Logs.error("Settings save failed: " + e.getMessage());
        }
    }

    // -------- helpers --------
    private static File ensureFile(File dataDir, String name) throws IOException {
        if (!dataDir.exists()) dataDir.mkdirs();
        File f = new File(dataDir, name);
        if (!f.exists()) {
            f.getParentFile().mkdirs();
            f.createNewFile();
        }
        // ensure subfolders exist (limits/profiles/logs)
        new File(dataDir, "limits").mkdirs();
        new File(dataDir, "profiles").mkdirs();
        new File(dataDir, "logs").mkdirs();
        return f;
    }

    // Extremely small JSON I/O to avoid adding libs
    private static String toJson(Settings s) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        // strings
        kv(sb, "inputPath", s.inputPath); comma(sb);
        kv(sb, "coordinatorUrl", s.coordinatorUrl); comma(sb);
        kv(sb, "botId", s.botId); comma(sb);
        // numbers
        kv(sb, "offerSlots", s.offerSlots); comma(sb);
        kv(sb, "maxGpInFlight", s.maxGpInFlight); comma(sb);
        kv(sb, "buyStaleMinutes", s.buyStaleMinutes); comma(sb);
        kv(sb, "sellStaleMinutes", s.sellStaleMinutes); comma(sb);
        kv(sb, "reprobeMinMinutes", s.reprobeMinMinutes); comma(sb);
        // booleans
        kv(sb, "enableCoordinator", s.enableCoordinator); comma(sb);
        // discord block
        sb.append("\"discord\":{");
        kv(sb, "enabled", s.discord.enabled); comma(sb);
        kv(sb, "webhookUrl", s.discord.webhookUrl); comma(sb);
        kv(sb, "sendProbe", s.discord.sendProbe); comma(sb);
        kv(sb, "sendTrades", s.discord.sendTrades); comma(sb);
        kv(sb, "sendLimits", s.discord.sendLimits); comma(sb);
        kv(sb, "sendErrors", s.discord.sendErrors); comma(sb);
        kv(sb, "minTradeMarginGp", s.discord.minTradeMarginGp);
        sb.append("}");
        sb.append("}");
        return sb.toString();
    }

    private static Settings fromJson(String json) {
        // Super-minimal parser (not robust to fancy whitespace/comments)
        Settings s = new Settings();
        s.inputPath           = str(json, "inputPath", s.inputPath);
        s.coordinatorUrl      = str(json, "coordinatorUrl", s.coordinatorUrl);
        s.botId               = str(json, "botId", s.botId);
        s.offerSlots          = num(json, "offerSlots", s.offerSlots);
        s.maxGpInFlight       = num(json, "maxGpInFlight", s.maxGpInFlight);
        s.buyStaleMinutes     = num(json, "buyStaleMinutes", s.buyStaleMinutes);
        s.sellStaleMinutes    = num(json, "sellStaleMinutes", s.sellStaleMinutes);
        s.reprobeMinMinutes   = num(json, "reprobeMinMinutes", s.reprobeMinMinutes);
        s.enableCoordinator   = bool(json, "enableCoordinator", s.enableCoordinator);

        // discord.*
        s.discord.enabled           = bool(json, "enabled", s.discord.enabled, "discord");
        s.discord.webhookUrl        = str(json, "webhookUrl", s.discord.webhookUrl, "discord");
        s.discord.sendProbe         = bool(json, "sendProbe", s.discord.sendProbe, "discord");
        s.discord.sendTrades        = bool(json, "sendTrades", s.discord.sendTrades, "discord");
        s.discord.sendLimits        = bool(json, "sendLimits", s.discord.sendLimits, "discord");
        s.discord.sendErrors        = bool(json, "sendErrors", s.discord.sendErrors, "discord");
        s.discord.minTradeMarginGp  = num(json, "minTradeMarginGp", s.discord.minTradeMarginGp, "discord");
        return s;
    }

    // --- tiny JSON helpers ---
    private static void kv(StringBuilder sb, String k, String v){ sb.append("\"").append(esc(k)).append("\":\"").append(esc(v)).append("\""); }
    private static void kv(StringBuilder sb, String k, int v){ sb.append("\"").append(esc(k)).append("\":").append(v); }
    private static void kv(StringBuilder sb, String k, boolean v){ sb.append("\"").append(esc(k)).append("\":").append(v); }
    private static void comma(StringBuilder sb){ sb.append(","); }
    private static String esc(String s){ return s.replace("\\","\\\\").replace("\"","\\\""); }

    private static String str(String json, String key, String def) {
        String m = "\"" + key + "\":\"";
        int i = json.indexOf(m);
        if (i < 0) return def;
        int start = i + m.length();
        int end = json.indexOf("\"", start);
        if (end < 0) return def;
        return json.substring(start, end).replace("\\\"", "\"").replace("\\\\","\\");
    }
    private static String str(String json, String key, String def, String parent) {
        int parentIdx = json.indexOf("\"" + parent + "\":{");
        if (parentIdx < 0) return def;
        int startObj = parentIdx;
        int endObj = json.indexOf("}", startObj);
        String sub = json.substring(startObj, endObj+1);
        return str(sub, key, def);
    }
    private static int num(String json, String key, int def) {
        String m = "\"" + key + "\":";
        int i = json.indexOf(m);
        if (i < 0) return def;
        int start = i + m.length();
        int end = start;
        while (end < json.length() && "-0123456789".indexOf(json.charAt(end)) >= 0) end++;
        try { return Integer.parseInt(json.substring(start, end)); } catch (Exception e) { return def; }
    }
    private static int num(String json, String key, int def, String parent) {
        int parentIdx = json.indexOf("\"" + parent + "\":{");
        if (parentIdx < 0) return def;
        int startObj = parentIdx;
        int endObj = json.indexOf("}", startObj);
        String sub = json.substring(startObj, endObj+1);
        return num(sub, key, def);
    }
    private static boolean bool(String json, String key, boolean def) {
        String m = "\"" + key + "\":";
        int i = json.indexOf(m);
        if (i < 0) return def;
        int start = i + m.length();
        String tail = json.substring(start).trim();
        if (tail.startsWith("true")) return true;
        if (tail.startsWith("false")) return false;
        return def;
    }
    private static boolean bool(String json, String key, boolean def, String parent) {
        int parentIdx = json.indexOf("\"" + parent + "\":{");
        if (parentIdx < 0) return def;
        int startObj = parentIdx;
        int endObj = json.indexOf("}", startObj);
        String sub = json.substring(startObj, endObj+1);
        return bool(sub, key, def);
    }
}
