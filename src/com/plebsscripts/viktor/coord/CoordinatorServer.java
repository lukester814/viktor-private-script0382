package com.plebsscripts.viktor.coord;

import com.plebsscripts.viktor.util.Logs;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * Lightweight HTTP server that tracks which bots hit limits on which items.
 * When Bot A hits 4h limit on "Maple logs", Bot B can query and take over.
 */

public class CoordinatorServer implements Runnable {
    private final int port;
    private final Map<String, LimitEntry> limits = new ConcurrentHashMap<>();
    private volatile boolean running = true;

    public CoordinatorServer(int port) {
        this.port = port;
    }

    public void start() {
        new Thread(this, "CoordinatorServer").start();
    }

    public void stop() {
        running = false;
    }

    @Override
    public void run() {
        try (ServerSocket server = new ServerSocket(port)) {
            Logs.info("Coordinator listening on port " + port);

            while (running) {
                try {
                    Socket client = server.accept();
                    handleClient(client);
                } catch (Exception e) {
                    if (running) Logs.warn("Client error: " + e.getMessage());
                }
            }
        } catch (IOException e) {
            Logs.warn("Server error: " + e.getMessage());
        }
    }

    private void handleClient(Socket client) {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream()));
             PrintWriter out = new PrintWriter(client.getOutputStream(), true)) {

            String line = in.readLine();
            if (line == null) return;

            // Parse: GET /blocked?item=Maple+logs&account=Bot1
            // Parse: GET /unblocked?item=Maple+logs
            // Parse: GET /list

            if (line.startsWith("GET /blocked")) {
                String query = line.split(" ")[1].split("\\?")[1];
                Map<String, String> params = parseQuery(query);
                String item = params.get("item");
                String account = params.get("account");

                if (item != null && account != null) {
                    limits.put(item, new LimitEntry(account, System.currentTimeMillis()));
                    out.println("HTTP/1.1 200 OK\r\n\r\n{\"status\":\"recorded\"}");
                    Logs.info("Coordinator: " + account + " blocked on " + item);
                }

            } else if (line.startsWith("GET /list")) {
                // Return JSON list of blocked items
                StringBuilder json = new StringBuilder("{\"blocked\":[");
                boolean first = true;

                long now = System.currentTimeMillis();
                for (Map.Entry<String, LimitEntry> e : limits.entrySet()) {
                    // Remove stale entries (4h + 5min buffer)
                    if (now - e.getValue().timestamp > (4 * 60 * 60 * 1000 + 5 * 60 * 1000)) {
                        limits.remove(e.getKey());
                        continue;
                    }

                    if (!first) json.append(",");
                    json.append("{\"item\":\"").append(e.getKey())
                            .append("\",\"account\":\"").append(e.getValue().account)
                            .append("\"}");
                    first = false;
                }

                json.append("]}");
                out.println("HTTP/1.1 200 OK\r\n\r\n" + json.toString());

            } else {
                out.println("HTTP/1.1 404 Not Found\r\n\r\n");
            }

        } catch (Exception e) {
            Logs.warn("handleClient error: " + e.getMessage());
        } finally {
            try { client.close(); } catch (IOException ignored) {}
        }
    }

    private Map<String, String> parseQuery(String query) {
        Map<String, String> map = new HashMap<>();
        for (String param : query.split("&")) {
            String[] kv = param.split("=");
            if (kv.length == 2) {
                try {
                    map.put(kv[0], URLDecoder.decode(kv[1], "UTF-8"));
                } catch (UnsupportedEncodingException e) {
                    map.put(kv[0], kv[1]);
                }
            }
        }
        return map;
    }

    private static class LimitEntry {
        final String account;
        final long timestamp;

        LimitEntry(String account, long timestamp) {
            this.account = account;
            this.timestamp = timestamp;
        }
    }
}
