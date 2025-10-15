package com.plebsscripts.viktor.limits;

import com.plebsscripts.viktor.util.Logs;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class LimitStore {

    public static LimitTracker loadForAccount(File dataDir, String account) {
        try {
            File dir = new File(dataDir, "limits"); dir.mkdirs();
            File f = new File(dir, safe(account) + ".json");
            LimitTracker lt = new LimitTracker();
            if (!f.exists()) return lt;

            String json = readAll(f);
            Map<String, Long> map = parseJson(json);
            for (Map.Entry<String, Long> e : map.entrySet()) {
                lt.restore(e.getKey(), e.getValue());
            }
            Logs.info("Loaded limits for " + account + " (" + map.size() + " items)");
            return lt;
        } catch (Exception e) {
            Logs.warn("LimitStore load failed: " + e.getMessage());
            return new LimitTracker();
        }
    }

    public static void saveForAccount(File dataDir, String account, LimitTracker tracker) {
        try {
            File dir = new File(dataDir, "limits"); dir.mkdirs();
            File f = new File(dir, safe(account) + ".json");
            String json = toJson(tracker.snapshot());
            writeAll(f, json);
        } catch (Exception e) {
            Logs.warn("LimitStore save failed: " + e.getMessage());
        }
    }

    // ----- helpers -----
    private static String safe(String s){ return s.replaceAll("[^a-zA-Z0-9_-]","_"); }

    private static String readAll(File f) throws IOException {
        FileInputStream in = new FileInputStream(f);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[4096]; int n;
        while ((n = in.read(buf)) > 0) out.write(buf,0,n);
        in.close();
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }
    private static void writeAll(File f, String s) throws IOException {
        OutputStream os = new FileOutputStream(f);
        os.write(s.getBytes(StandardCharsets.UTF_8));
        os.close();
    }

    // very small JSON ({"item":{"until":epoch},...})
    private static Map<String, Long> parseJson(String json) {
        Map<String, Long> m = new HashMap<String, Long>();
        json = json.trim();
        if (json.length() < 2) return m;
        // naive parse: "name":12345
        String[] parts = json.replace("{","").replace("}","").split(",");
        for (int i=0;i<parts.length;i++) {
            String p = parts[i].trim();
            if (p.length()==0) continue;
            int k1 = p.indexOf('"');
            int k2 = p.indexOf('"', k1+1);
            if (k1<0||k2<=k1) continue;
            String key = p.substring(k1+1, k2);
            int colon = p.indexOf(':', k2+1);
            if (colon<0) continue;
            String num = p.substring(colon+1).trim();
            try { m.put(key, Long.parseLong(num)); } catch (Exception ignored) {}
        }
        return m;
    }

    private static String toJson(Map<String, Long> map) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        boolean first = true;
        for (Map.Entry<String, Long> e : map.entrySet()) {
            if (!first) sb.append(",");
            sb.append("\"").append(e.getKey().replace("\"","\\\"")).append("\":").append(e.getValue());
            first = false;
        }
        sb.append("}");
        return sb.toString();
    }
}
