package com.plebsscripts.viktor.config;

import com.plebsscripts.viktor.util.Logs;

import java.io.*;
import java.nio.charset.StandardCharsets;

public class SettingsStore {

    public static Settings loadOrDefault(File dataDir) {
        try {
            File f = ensureFile(dataDir, "settings.json");
            if (!f.exists() || f.length() == 0) {
                // First run: write defaults
                Settings def = new Settings();
                save(dataDir, def);
                Logs.info("Created default settings.json");
                return def;
            }
            String json = new String(java.nio.file.Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
            Settings loaded = fromJson(json);
            Logs.info("Loaded settings: " + loaded.toString());
            return loaded;
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
            Logs.info("Saved settings to " + f.getPath());
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
        // ensure subfolders exist
        new File(dataDir, "limits").mkdirs();
        new File(dataDir, "profiles").mkdirs();
        new File(dataDir, "logs").mkdirs();
        return f;
    }

    // UPDATED: JSON serialization with new fields
    private static String toJson(Settings s) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");

        // Strings
        kvIndent(sb, "inputPath", s.inputPath, 1); comma(sb);
        kvIndent(sb, "coordinatorUrl", s.coordinatorUrl, 1); comma(sb);
        kvIndent(sb, "botId", s.botId, 1); comma(sb);

        // Numbers
        kvIndent(sb, "offerSlots", s.offerSlots, 1); comma(sb);
        kvIndent(sb, "maxGpInFlight", s.maxGpInFlight, 1); comma(sb);
        kvIndent(sb, "maxGpPerFlip", s.maxGpPerFlip, 1); comma(sb); // NEW
        kvIndent(sb, "buyStaleMinutes", s.buyStaleMinutes, 1); comma(sb);
        kvIndent(sb, "sellStaleMinutes", s.sellStaleMinutes, 1); comma(sb);
        kvIndent(sb, "reprobeMinMinutes", s.reprobeMinMinutes, 1); comma(sb);

        // Booleans
        kvIndent(sb, "enableCoordinator", s.enableCoordinator, 1); comma(sb);
        kvIndent(sb, "respectLimits", s.respectLimits, 1); comma(sb); // NEW

        // Discord block
        sb.append("  \"discord\": {\n");
        kvIndent(sb, "enabled", s.discord.enabled, 2); comma(sb);
        kvIndent(sb, "webhookUrl", s.discord.webhookUrl, 2); comma(sb);
        kvIndent(sb, "sendProbe", s.discord.sendProbe, 2); comma(sb);
        kvIndent(sb, "sendTrades", s.discord.sendTrades, 2); comma(sb);
        kvIndent(sb, "sendLimits", s.discord.sendLimits, 2); comma(sb);
        kvIndent(sb, "sendErrors", s.discord.sendErrors, 2); comma(sb);
        kvIndent(sb, "minTradeMarginGp", s.discord.minTradeMarginGp, 2); sb.append("\n");
        sb.append("  },\n");

        // AntiBan block (NEW)
        sb.append("  \"antiBan\": {\n");
        kvIndent(sb, "enabled", s.antiBan.enabled, 2); comma(sb);
        kvIndent(sb, "minDelayMs", s.antiBan.minDelayMs, 2); comma(sb);
        kvIndent(sb, "maxDelayMs", s.antiBan.maxDelayMs, 2); comma(sb);
        kvIndent(sb, "randomMouseMovements", s.antiBan.randomMouseMovements, 2); comma(sb);
        kvIndent(sb, "randomCameraRotation", s.antiBan.randomCameraRotation, 2); comma(sb);
        kvIndent(sb, "afkBreakChance", s.antiBan.afkBreakChance, 2); sb.append("\n");
        sb.append("  },\n");

        // HotReload block (NEW)
        sb.append("  \"hotReload\": {\n");
        kvIndent(sb, "enabled", s.hotReload.enabled, 2); comma(sb);
        kvIndent(sb, "pastebinUrl", s.hotReload.pastebinUrl, 2); comma(sb);
        kvIndent(sb, "checkIntervalSeconds", s.hotReload.checkIntervalSeconds, 2); sb.append("\n");
        sb.append("  }\n");

        sb.append("}");
        return sb.toString();
    }

    // UPDATED: JSON deserialization with new fields
    private static Settings fromJson(String json) {
        Settings s = new Settings();

        // Root level
        s.inputPath           = str(json, "inputPath", s.inputPath);
        s.coordinatorUrl      = str(json, "coordinatorUrl", s.coordinatorUrl);
        s.botId               = str(json, "botId", s.botId);
        s.offerSlots          = num(json, "offerSlots", s.offerSlots);
        s.maxGpInFlight       = num(json, "maxGpInFlight", s.maxGpInFlight);
        s.maxGpPerFlip        = numLong(json, "maxGpPerFlip", s.maxGpPerFlip); // NEW
        s.buyStaleMinutes     = num(json, "buyStaleMinutes", s.buyStaleMinutes);
        s.sellStaleMinutes    = num(json, "sellStaleMinutes", s.sellStaleMinutes);
        s.reprobeMinMinutes   = num(json, "reprobeMinMinutes", s.reprobeMinMinutes);
        s.enableCoordinator   = bool(json, "enableCoordinator", s.enableCoordinator);
        s.respectLimits       = bool(json, "respectLimits", s.respectLimits); // NEW

        // Discord block
        s.discord.enabled           = bool(json, "enabled", s.discord.enabled, "discord");
        s.discord.webhookUrl        = str(json, "webhookUrl", s.discord.webhookUrl, "discord");
        s.discord.sendProbe         = bool(json, "sendProbe", s.discord.sendProbe, "discord");
        s.discord.sendTrades        = bool(json, "sendTrades", s.discord.sendTrades, "discord");
        s.discord.sendLimits        = bool(json, "sendLimits", s.discord.sendLimits, "discord");
        s.discord.sendErrors        = bool(json, "sendErrors", s.discord.sendErrors, "discord");
        s.discord.minTradeMarginGp  = num(json, "minTradeMarginGp", s.discord.minTradeMarginGp, "discord");

        // AntiBan block (NEW)
        s.antiBan.enabled              = bool(json, "enabled", s.antiBan.enabled, "antiBan");
        s.antiBan.minDelayMs           = num(json, "minDelayMs", s.antiBan.minDelayMs, "antiBan");
        s.antiBan.maxDelayMs           = num(json, "maxDelayMs", s.antiBan.maxDelayMs, "antiBan");
        s.antiBan.randomMouseMovements = bool(json, "randomMouseMovements", s.antiBan.randomMouseMovements, "antiBan");
        s.antiBan.randomCameraRotation = bool(json, "randomCameraRotation", s.antiBan.randomCameraRotation, "antiBan");
        s.antiBan.afkBreakChance       = num(json, "afkBreakChance", s.antiBan.afkBreakChance, "antiBan");

        // HotReload block (NEW)
        s.hotReload.enabled              = bool(json, "enabled", s.hotReload.enabled, "hotReload");
        s.hotReload.pastebinUrl          = str(json, "pastebinUrl", s.hotReload.pastebinUrl, "hotReload");
        s.hotReload.checkIntervalSeconds = num(json, "checkIntervalSeconds", s.hotReload.checkIntervalSeconds, "hotReload");

        return s;
    }

    // --- JSON helpers (with pretty printing) ---
    private static void kvIndent(StringBuilder sb, String k, String v, int indent) {
        for (int i = 0; i < indent; i++) sb.append("  ");
        sb.append("\"").append(esc(k)).append("\": \"").append(esc(v)).append("\"");
    }

    private static void kvIndent(StringBuilder sb, String k, int v, int indent) {
        for (int i = 0; i < indent; i++) sb.append("  ");
        sb.append("\"").append(esc(k)).append("\": ").append(v);
    }

    private static void kvIndent(StringBuilder sb, String k, long v, int indent) {
        for (int i = 0; i < indent; i++) sb.append("  ");
        sb.append("\"").append(esc(k)).append("\": ").append(v);
    }

    private static void kvIndent(StringBuilder sb, String k, boolean v, int indent) {
        for (int i = 0; i < indent; i++) sb.append("  ");
        sb.append("\"").append(esc(k)).append("\": ").append(v);
    }

    private static void comma(StringBuilder sb) { sb.append(",\n"); }
    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("\\","\\\\").replace("\"","\\\"").replace("\n", "\\n");
    }

    // String parser
    private static String str(String json, String key, String def) {
        String m = "\"" + key + "\":";
        int i = json.indexOf(m);
        if (i < 0) return def;
        int start = i + m.length();
        // Skip whitespace and opening quote
        while (start < json.length() && (json.charAt(start) == ' ' || json.charAt(start) == '\n' || json.charAt(start) == '\t')) start++;
        if (start >= json.length() || json.charAt(start) != '"') return def;
        start++;
        int end = json.indexOf("\"", start);
        if (end < 0) return def;
        return json.substring(start, end).replace("\\\"", "\"").replace("\\\\","\\").replace("\\n", "\n");
    }

    private static String str(String json, String key, String def, String parent) {
        int parentIdx = json.indexOf("\"" + parent + "\":");
        if (parentIdx < 0) return def;
        int startObj = json.indexOf("{", parentIdx);
        if (startObj < 0) return def;
        int endObj = findMatchingBrace(json, startObj);
        String sub = json.substring(startObj, endObj+1);
        return str(sub, key, def);
    }

    // Number parsers
    private static int num(String json, String key, int def) {
        String m = "\"" + key + "\":";
        int i = json.indexOf(m);
        if (i < 0) return def;
        int start = i + m.length();
        // Skip whitespace
        while (start < json.length() && (json.charAt(start) == ' ' || json.charAt(start) == '\n' || json.charAt(start) == '\t')) start++;
        int end = start;
        while (end < json.length() && "-0123456789".indexOf(json.charAt(end)) >= 0) end++;
        try { return Integer.parseInt(json.substring(start, end).trim()); } catch (Exception e) { return def; }
    }

    private static long numLong(String json, String key, long def) {
        String m = "\"" + key + "\":";
        int i = json.indexOf(m);
        if (i < 0) return def;
        int start = i + m.length();
        while (start < json.length() && (json.charAt(start) == ' ' || json.charAt(start) == '\n' || json.charAt(start) == '\t')) start++;
        int end = start;
        while (end < json.length() && "-0123456789".indexOf(json.charAt(end)) >= 0) end++;
        try { return Long.parseLong(json.substring(start, end).trim()); } catch (Exception e) { return def; }
    }

    private static int num(String json, String key, int def, String parent) {
        int parentIdx = json.indexOf("\"" + parent + "\":");
        if (parentIdx < 0) return def;
        int startObj = json.indexOf("{", parentIdx);
        if (startObj < 0) return def;
        int endObj = findMatchingBrace(json, startObj);
        String sub = json.substring(startObj, endObj+1);
        return num(sub, key, def);
    }

    // Boolean parser
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
        int parentIdx = json.indexOf("\"" + parent + "\":");
        if (parentIdx < 0) return def;
        int startObj = json.indexOf("{", parentIdx);
        if (startObj < 0) return def;
        int endObj = findMatchingBrace(json, startObj);
        String sub = json.substring(startObj, endObj+1);
        return bool(sub, key, def);
    }

    // Helper to find matching closing brace
    private static int findMatchingBrace(String json, int start) {
        int depth = 0;
        for (int i = start; i < json.length(); i++) {
            if (json.charAt(i) == '{') depth++;
            if (json.charAt(i) == '}') {
                depth--;
                if (depth == 0) return i;
            }
        }
        return json.length() - 1;
    }
}
