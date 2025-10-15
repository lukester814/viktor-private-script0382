package com.plebsscripts.viktor.notify;

import javax.net.ssl.HttpsURLConnection;
import java.io.OutputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class DiscordWebhook {
    private final String url;
    public DiscordWebhook(String url) { this.url = url; }

    public boolean send(String content) {
        try {
            URL u = new URL(url);
            HttpsURLConnection c = (HttpsURLConnection) u.openConnection();
            c.setRequestMethod("POST");
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type","application/json");
            String json = "{\"content\":\"" + escape(content) + "\"}";
            byte[] body = json.getBytes(StandardCharsets.UTF_8);
            c.setFixedLengthStreamingMode(body.length);
            OutputStream os = c.getOutputStream(); os.write(body); os.close();
            int code = c.getResponseCode();
            return code >= 200 && code < 300;
        } catch (Exception ignored) { return false; }
    }

    private String escape(String s) {
        return s.replace("\\","\\\\").replace("\"","\\\"")
                .replace("\n","\\n").replace("\r","");
    }
}
