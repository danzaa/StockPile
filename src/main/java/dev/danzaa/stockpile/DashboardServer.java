package dev.danzaa.stockpile;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class DashboardServer implements AutoCloseable {
    private static final Map<String, String> FILES = Map.ofEntries(
            Map.entry("/", "index.html"), Map.entry("/index.html", "index.html"),
            Map.entry("/style.css", "style.css"), Map.entry("/app.js", "app.js"),
            Map.entry("/core.js", "core.js"), Map.entry("/demo.js", "demo.js"),
            Map.entry("/schematic.js", "schematic.js"), Map.entry("/schematic-worker.js", "schematic-worker.js"),
            Map.entry("/item-icons.js", "item-icons.js"), Map.entry("/logo.png", "logo.png"),
            Map.entry("/favicon.png", "favicon.png"), Map.entry("/favicon.svg", "favicon.svg"),
            Map.entry("/ui/unknown.svg", "ui/unknown.svg"));
    private final InventoryStore store;
    private final HttpServer server;
    private final ExecutorService executor;
    private final String token;
    private final String host;
    private volatile String registry = "{}";

    public DashboardServer(InventoryStore store) throws IOException {
        this.store = store;
        byte[] secret = new byte[32]; new SecureRandom().nextBytes(secret);
        token = Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
        HttpServer chosen = null;
        for (int port = 19482; port <= 19492; port++) {
            try { chosen = HttpServer.create(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), port), 16); break; }
            catch (java.net.BindException ignored) {}
        }
        if (chosen == null) throw new IOException("Dashboard ports 19482–19492 are in use");
        server = chosen;
        host = "127.0.0.1:" + server.getAddress().getPort();
        executor = Executors.newFixedThreadPool(2, r -> { Thread t = new Thread(r, "Stockpile browser"); t.setDaemon(true); return t; });
        server.setExecutor(executor);
        server.createContext("/", this::handle);
        server.start();
    }

    public String url() { return "http://" + host + "/#token=" + token; }
    public void registry(String json) { registry = json; }

    private void handle(HttpExchange request) throws IOException {
        try (request) {
            request.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
            request.getResponseHeaders().set("Referrer-Policy", "no-referrer");
            request.getResponseHeaders().set("Cache-Control", "no-store");
            request.getResponseHeaders().set("Content-Security-Policy", "default-src 'self'; img-src 'self' data:; style-src 'self'; script-src 'self'; worker-src 'self'; connect-src 'self'; frame-ancestors 'none'; base-uri 'none'; form-action 'none'");
            if (!host.equals(request.getRequestHeaders().getFirst("Host"))) { send(request, 403, "text/plain", "Invalid host"); return; }
            String origin = request.getRequestHeaders().getFirst("Origin");
            if (origin != null && !origin.equals("http://" + host)) { send(request, 403, "text/plain", "Invalid origin"); return; }
            String path = request.getRequestURI().getPath();
            if (path.startsWith("/api/")) {
                String provided = request.getRequestHeaders().getFirst("X-Stockpile-Token");
                if (provided == null || !MessageDigest.isEqual(token.getBytes(StandardCharsets.UTF_8), provided.getBytes(StandardCharsets.UTF_8))) {
                    send(request, 401, "text/plain", "Run /stockpile start in Minecraft and click its dashboard link."); return;
                }
                if (path.equals("/api/snapshot") && request.getRequestMethod().equals("GET")) {
                    send(request, 200, "application/json", store.snapshot()); return;
                }
                if (path.equals("/api/registry") && request.getRequestMethod().equals("GET")) {
                    send(request, 200, "application/json", registry); return;
                }
                if (path.equals("/api/forget") && request.getRequestMethod().equals("POST")) {
                    byte[] body = request.getRequestBody().readNBytes(4097);
                    if (body.length > 4096) { send(request, 413, "text/plain", "Request too large"); return; }
                    try {
                        JsonObject json = JsonParser.parseString(new String(body, StandardCharsets.UTF_8)).getAsJsonObject();
                        boolean removed = store.forget(json.get("worldId").getAsString(), json.get("containerId").getAsString());
                        send(request, removed ? 200 : 404, "application/json", "{\"removed\":" + removed + "}");
                    } catch (RuntimeException e) { send(request, 400, "text/plain", "Invalid container request"); }
                    return;
                }
                send(request, 404, "text/plain", "No such endpoint"); return;
            }
            if (!request.getRequestMethod().equals("GET")) { send(request, 405, "text/plain", "GET only"); return; }
            String file = FILES.get(path);
            if (file == null && path.matches("/items/[a-z0-9_]+\\.png")) file = path.substring(1);
            if (file == null) { send(request, 404, "text/plain", "Not found"); return; }
            try (var input = DashboardServer.class.getResourceAsStream("/stockpile-web/" + file)) {
                if (input == null) { send(request, 404, "text/plain", "Dashboard asset missing"); return; }
                String mime = file.endsWith(".png") ? "image/png" : file.endsWith(".js") ? "text/javascript" : file.endsWith(".css") ? "text/css" : file.endsWith(".svg") ? "image/svg+xml" : "text/html";
                byte[] bytes = input.readAllBytes();
                request.getResponseHeaders().set("Content-Type", mime + (file.endsWith(".png") ? "" : "; charset=utf-8"));
                request.sendResponseHeaders(200, bytes.length);
                request.getResponseBody().write(bytes);
            }
        }
    }

    private static void send(HttpExchange request, int status, String type, String text) throws IOException {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        request.getResponseHeaders().set("Content-Type", type + "; charset=utf-8");
        request.sendResponseHeaders(status, bytes.length);
        request.getResponseBody().write(bytes);
    }

    @Override public void close() { server.stop(0); executor.shutdownNow(); }
}
