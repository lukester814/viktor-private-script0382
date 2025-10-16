package com.plebsscripts.viktor.coord;

import com.plebsscripts.viktor.util.Logs;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * Lightweight HTTP server that tracks which bots hit limits on which items.
 * When Bot A hits 4h limit on "Maple logs", Bot B can query and take over.
 *
 * Run standalone: java CoordinatorServer [port]
 * Default port: 8888
 */
public class CoordinatorServer implements Runnable {
    private final int port;
    private final Map<String, LimitEntry> limits = new ConcurrentHashMap<>();
    private volatile boolean running = true;
    private long startTime;
    private int totalRequests = 0;

    public CoordinatorServer(int port) {
        this.port = port;
        this.startTime = System.currentTimeMillis();
    }

    public void start() {
        Thread thread = new Thread(this, "CoordinatorServer");
        thread.setDaemon(false); // Keep JVM alive
        thread.start();
    }

    public void stop() {
        running = false;
        Logs.info("CoordinatorServer stopping...");
    }

    @Override
    public void run() {
        ServerSocket server = null;
        try {
            server = new ServerSocket(port);
            Logs.info("Coordinator listening on port " + port);
            Logs.info("Endpoints: /report, /list, /health, /stats");

            while (running) {
                try {
                    Socket client = server.accept();
                    // Handle in same thread (simple for now, could use thread pool)
                    handleClient(client);
                } catch (SocketException e) {
                    if (running) {
                        Logs.warn("Socket error: " + e.getMessage());
                    }
                } catch (Exception e) {
                    if (running) {
                        Logs.warn("Client error: " + e.getMessage());
                    }
                }
            }
        } catch (IOException e) {
            Logs.error("Server error: " + e.getMessage());
        } finally {
            if (server != null && !server.isClosed()) {
                try {
                    server.close();
                    Logs.info("Server socket closed");
                } catch (IOException ignored) {}
            }
        }
    }

    private void handleClient(Socket client) {
        totalRequests++;

        try (BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream()));
             PrintWriter out = new PrintWriter(client.getOutputStream(), true)) {

            String line = in.readLine();
            if (line == null) return;

            // Log request
            String clientIP = client.getInetAddress().getHostAddress();
            Logs.info("Request from " + clientIP + ": " + line);

            // Route to handlers
            if (line.startsWith("GET /report") || line.startsWith("GET /blocked")) {
                handleReport(line, out);

            } else if (line.startsWith("GET /list")) {
                handleList(out);

            } else if (line.startsWith("GET /health")) {
                handleHealth(out);

            } else if (line.startsWith("GET /stats")) {
                handleStats(out);

            } else if (line.startsWith("GET /clear")) {
                handleClear(out);

            } else {
                out.println("HTTP/1.1 404 Not Found\r\nContent-Type: application/json\r\n\r\n{\"error\":\"Unknown endpoint\"}");
            }

        } catch (Exception e) {
            Logs.warn("handleClient error: " + e.getMessage());
        } finally {
            try { client.close(); } catch (IOException ignored) {}
        }
    }

    private void handleReport(String line, PrintWriter out) {
        String[] parts = line.split(" ");
        if (parts.length < 2 || !parts[1].contains("?")) {
            out.println("HTTP/1.1 400 Bad Request\r\nContent-Type: application/json\r\n\r\n{\"error\":\"Missing query params\"}");
            return;
        }

        String query = parts[1].split("\\?")[1];
        Map<String, String> params = parseQuery(query);
        String item = params.get("item");
        String account = params.get("account");

        if (item != null && account != null) {
            limits.put(item, new LimitEntry(account, System.currentTimeMillis()));
            out.println("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\n\r\n{\"status\":\"recorded\"}");
            Logs.info("Recorded: " + account + " blocked on " + item);
        } else {
            out.println("HTTP/1.1 400 Bad Request\r\nContent-Type: application/json\r\n\r\n{\"error\":\"Missing item or account\"}");
        }
    }

    private void handleList(PrintWriter out) {
        StringBuilder json = new StringBuilder("{\"blocked\":[");
        boolean first = true;

        long now = System.currentTimeMillis();
        Iterator<Map.Entry<String, LimitEntry>> it = limits.entrySet().iterator();

        while (it.hasNext()) {
            Map.Entry<String, LimitEntry> e = it.next();

            // Remove stale entries (4h + 5min buffer)
            long age = now - e.getValue().timestamp;
            if (age > (4 * 60 * 60 * 1000 + 5 * 60 * 1000)) {
                it.remove();
                continue;
            }

            if (!first) json.append(",");
            json.append("{\"item\":\"").append(escapeJson(e.getKey()))
                    .append("\",\"account\":\"").append(escapeJson(e.getValue().account))
                    .append("\",\"age\":").append(age / 1000) // seconds
                    .append("}");
            first = false;
        }

        json.append("]}");
        out.println("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\n\r\n" + json.toString());
    }

    private void handleHealth(PrintWriter out) {
        long uptime = System.currentTimeMillis() - startTime;
        String response = String.format("{\"status\":\"ok\",\"blockedCount\":%d,\"uptimeSeconds\":%d}",
                limits.size(), uptime / 1000);
        out.println("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\n\r\n" + response);
    }

    private void handleStats(PrintWriter out) {
        long uptime = System.currentTimeMillis() - startTime;

        // Build detailed stats
        StringBuilder json = new StringBuilder("{");
        json.append("\"uptime\":").append(uptime / 1000).append(",");
        json.append("\"totalRequests\":").append(totalRequests).append(",");
        json.append("\"blockedItems\":").append(limits.size()).append(",");
        json.append("\"items\":[");

        boolean first = true;
        for (Map.Entry<String, LimitEntry> e : limits.entrySet()) {
            if (!first) json.append(",");
            long age = (System.currentTimeMillis() - e.getValue().timestamp) / 1000;
            json.append("{\"item\":\"").append(escapeJson(e.getKey()))
                    .append("\",\"account\":\"").append(escapeJson(e.getValue().account))
                    .append("\",\"ageSeconds\":").append(age)
                    .append("}");
            first = false;
        }

        json.append("]}");
        out.println("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\n\r\n" + json.toString());
    }

    private void handleClear(PrintWriter out) {
        int count = limits.size();
        limits.clear();
        String response = String.format("{\"status\":\"cleared\",\"removedCount\":%d}", count);
        out.println("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\n\r\n" + response);
        Logs.info("Cleared " + count + " limit entries");
    }

    private Map<String, String> parseQuery(String query) {
        Map<String, String> map = new HashMap<>();
        if (query == null || query.isEmpty()) return map;

        for (String param : query.split("&")) {
            String[] kv = param.split("=", 2);
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

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private static class LimitEntry {
        final String account;
        final long timestamp;

        LimitEntry(String account, long timestamp) {
            this.account = account;
            this.timestamp = timestamp;
        }
    }

    // Standalone runner
    public static void main(String[] args) {
        int port = 8888;

        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid port: " + args[0]);
                System.exit(1);
            }
        }

        System.out.println("═══════════════════════════════════════════");
        System.out.println("  Viktor Coordinator Server v1.0");
        System.out.println("═══════════════════════════════════════════");
        System.out.println("Port: " + port);
        System.out.println();
        System.out.println("Endpoints:");
        System.out.println("  GET /report?item=<name>&account=<name>  - Report limit hit");
        System.out.println("  GET /list                                - Get blocked items");
        System.out.println("  GET /health                              - Health check");
        System.out.println("  GET /stats                               - Detailed stats");
        System.out.println("  GET /clear                               - Clear all limits");
        System.out.println();
        System.out.println("Press Ctrl+C to stop");
        System.out.println("═══════════════════════════════════════════");
        System.out.println();

        CoordinatorServer server = new CoordinatorServer(port);

        // Shutdown hook for graceful stop
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\nShutting down coordinator...");
            server.stop();
        }));

        server.start();

        // Keep main thread alive
        try {
            Thread.sleep(Long.MAX_VALUE);
        } catch (InterruptedException e) {
            server.stop();
        }
    }
}
