package com.plebsscripts.viktor.util;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

/**
 * Simple HTTP GET utility for fetching Pastebin/raw URLs
 */
public class HTTPFetcher {

    /**
     * Fetch content from URL with timeout
     */
    public static String fetch(String urlString) throws IOException {
        return fetch(urlString, 10000); // 10 second timeout
    }

    /**
     * Fetch content from URL with custom timeout
     */
    public static String fetch(String urlString, int timeoutMs) throws IOException {
        URL url = new URL(urlString);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();

        try {
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(timeoutMs);
            conn.setReadTimeout(timeoutMs);
            conn.setRequestProperty("User-Agent", "Viktor-Bot/1.0");

            int responseCode = conn.getResponseCode();
            if (responseCode != 200) {
                throw new IOException("HTTP " + responseCode + " from " + urlString);
            }

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));

            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line).append("\n");
            }
            reader.close();

            return response.toString();

        } finally {
            conn.disconnect();
        }
    }
}