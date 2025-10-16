package com.plebsscripts.viktor.notify;

import javax.net.ssl.HttpsURLConnection;
import java.io.OutputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Lightweight Discord webhook client.
 * Sends JSON messages to Discord via HTTPS POST.
 */
public class DiscordWebhook {
    private final String url;

    public DiscordWebhook(String url) {
        this.url = url;
    }

    /**
     * Send a message to Discord
     * @param content Message content (will be escaped)
     * @return true if sent successfully (HTTP 2xx), false otherwise
     */
    public boolean send(String content) {
        if (url == null || url.trim().isEmpty()) {
            return false;
        }

        try {
            URL u = new URL(url);
            HttpsURLConnection c = (HttpsURLConnection) u.openConnection();
            c.setRequestMethod("POST");
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/json");
            c.setRequestProperty("User-Agent", "Viktor-GE-Flipper/1.0");

            String json = "{\"content\":\"" + escape(content) + "\"}";
            byte[] body = json.getBytes(StandardCharsets.UTF_8);
            c.setFixedLengthStreamingMode(body.length);

            OutputStream os = c.getOutputStream();
            os.write(body);
            os.close();

            int code = c.getResponseCode();
            c.disconnect();

            return code >= 200 && code < 300;

        } catch (Exception e) {
            // Silent fail - webhook not critical
            return false;
        }
    }

    /**
     * Send a message with custom username
     * @param content Message content
     * @param username Custom username to display
     * @return true if sent successfully
     */
    public boolean send(String content, String username) {
        if (url == null || url.trim().isEmpty()) {
            return false;
        }

        try {
            URL u = new URL(url);
            HttpsURLConnection c = (HttpsURLConnection) u.openConnection();
            c.setRequestMethod("POST");
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/json");
            c.setRequestProperty("User-Agent", "Viktor-GE-Flipper/1.0");

            String json = "{\"content\":\"" + escape(content) +
                    "\",\"username\":\"" + escape(username) + "\"}";
            byte[] body = json.getBytes(StandardCharsets.UTF_8);
            c.setFixedLengthStreamingMode(body.length);

            OutputStream os = c.getOutputStream();
            os.write(body);
            os.close();

            int code = c.getResponseCode();
            c.disconnect();

            return code >= 200 && code < 300;

        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Send an embed message (rich formatting)
     * @param title Embed title
     * @param description Embed description
     * @param color Embed color (decimal, e.g., 0x00FF00 for green)
     * @return true if sent successfully
     */
    public boolean sendEmbed(String title, String description, int color) {
        if (url == null || url.trim().isEmpty()) {
            return false;
        }

        try {
            URL u = new URL(url);
            HttpsURLConnection c = (HttpsURLConnection) u.openConnection();
            c.setRequestMethod("POST");
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/json");
            c.setRequestProperty("User-Agent", "Viktor-GE-Flipper/1.0");

            String json = "{\"embeds\":[{" +
                    "\"title\":\"" + escape(title) + "\"," +
                    "\"description\":\"" + escape(description) + "\"," +
                    "\"color\":" + color +
                    "}]}";
            byte[] body = json.getBytes(StandardCharsets.UTF_8);
            c.setFixedLengthStreamingMode(body.length);

            OutputStream os = c.getOutputStream();
            os.write(body);
            os.close();

            int code = c.getResponseCode();
            c.disconnect();

            return code >= 200 && code < 300;

        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Escape special characters for JSON
     */
    private String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "")
                .replace("\t", "\\t");
    }
}
