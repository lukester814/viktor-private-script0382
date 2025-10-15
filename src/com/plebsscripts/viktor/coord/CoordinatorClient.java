package com.plebsscripts.viktor.coord;

import com.plebsscripts.viktor.util.Logs;
import java.io.*;
import java.net.*;
import java.util.*;

public class CoordinatorClient {
    private final String serverUrl; // e.g., "http://localhost:8888"

    public CoordinatorClient(String serverUrl) {
        this.serverUrl = serverUrl;
    }

    /** Report that this account hit 4h limit on an item */
    public void reportLimit(String item, String account) {
        try {
            String url = serverUrl + "/blocked?item=" + URLEncoder.encode(item, "UTF-8")
                    + "&account=" + URLEncoder.encode(account, "UTF-8");
            String response = httpGet(url);
            Logs.info("Coordinator: reported " + item + " blocked for " + account);
        } catch (Exception e) {
            Logs.warn("reportLimit failed: " + e.getMessage());
        }
    }

    /** Get list of items currently blocked by other bots */
    public Set<String> getBlockedItems() {
        try {
            String response = httpGet(serverUrl + "/list");
            return parseBlockedItems(response);
        } catch (Exception e) {
            Logs.warn("getBlockedItems failed: " + e.getMessage());
            return Collections.emptySet();
        }
    }

    private String httpGet(String urlStr) throws IOException {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);

        BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        StringBuilder response = new StringBuilder();
        String line;
        while ((line = in.readLine()) != null) {
            response.append(line);
        }
        in.close();
        return response.toString();
    }

    private Set<String> parseBlockedItems(String json) {
        // Simple parse: {"blocked":[{"item":"Maple logs","account":"Bot1"},...]}
        Set<String> items = new HashSet<>();
        try {
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
}
