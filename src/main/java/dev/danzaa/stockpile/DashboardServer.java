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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

public final class DashboardServer implements AutoCloseable {
    private static final Map<String, String> FILES = Map.ofEntries(
            Map.entry("/", "index.html"), Map.entry("/index.html", "index.html"),
            Map.entry("/style.css", "style.css"), Map.entry("/app.js", "app.js"),
            Map.entry("/i18n.js", "i18n.js"),
            Map.entry("/core.js", "core.js"), Map.entry("/demo.js", "demo.js"),
            Map.entry("/map.js", "map.js"), Map.entry("/find.js", "find.js"), Map.entry("/planning-state.js","planning-state.js"), Map.entry("/sharing.js","sharing.js"),
            Map.entry("/recipes.js", "recipes.js"), Map.entry("/recipes-data.js", "recipes-data.js"),
            Map.entry("/schematic.js", "schematic.js"), Map.entry("/schematic-worker.js", "schematic-worker.js"),
            Map.entry("/item-icons.js", "item-icons.js"), Map.entry("/logo.png", "logo.png"),
            Map.entry("/favicon.png", "favicon.png"), Map.entry("/favicon.svg", "favicon.svg"),
            Map.entry("/ui/unknown.svg", "ui/unknown.svg"));
    private final InventoryStore store;
    private final HttpServer server;
    private final ExecutorService executor;
    private final String token;
    private final String host;
    private final Function<ItemFinder.Request, CompletableFuture<ItemFinder.Reply>> find;
    private final Function<JsonObject,CompletableFuture<PlanningState.Result>> reservations;
    private final Function<TerrainSnapshot.Request, CompletableFuture<TerrainSnapshot.Result>> terrain;
    private final Function<HighlightOptions, CompletableFuture<HighlightOptions>> highlightSettings;
    private final SessionTerrain sessionTerrain;
    private final Function<SchematicFunnel.Request, CompletableFuture<SchematicFunnel.Result>> funnel;
    private final Function<SchematicFunnel.SettingsUpdate, CompletableFuture<SchematicFunnel.Settings>> schematicChestSettings;
    private volatile String registry = "{}";

    public DashboardServer(InventoryStore store) throws IOException {
        this(store, request -> CompletableFuture.completedFuture(new ItemFinder.Reply(503, "In-game finding is unavailable.")));
    }
    public DashboardServer(InventoryStore store, Function<ItemFinder.Request, CompletableFuture<ItemFinder.Reply>> find) throws IOException {
        this(store,find,data->CompletableFuture.completedFuture(store.shared(data.get("worldId").getAsString())?new PlanningState.Result(409,"Team server required"):store.reservation(data,"local")));
    }
    public DashboardServer(InventoryStore store, Function<ItemFinder.Request, CompletableFuture<ItemFinder.Reply>> find, Function<JsonObject,CompletableFuture<PlanningState.Result>> reservations) throws IOException {
        this(store, find, reservations, query -> CompletableFuture.completedFuture(
                new TerrainSnapshot.Result(503, "Terrain capture is unavailable.", null)),
                options -> CompletableFuture.completedFuture(options == null ? HighlightOptions.DEFAULT : options));
    }

    DashboardServer(InventoryStore store, Function<ItemFinder.Request, CompletableFuture<ItemFinder.Reply>> find,
                    Function<JsonObject, CompletableFuture<PlanningState.Result>> reservations,
                    Function<TerrainSnapshot.Request, CompletableFuture<TerrainSnapshot.Result>> terrain,
                    Function<HighlightOptions, CompletableFuture<HighlightOptions>> highlightSettings) throws IOException {
        this(store, find, reservations, terrain, highlightSettings, new SessionTerrain(), request ->
                CompletableFuture.completedFuture(SchematicFunnel.Result.unavailable(409, "Join a single-player world to fill a schematic chest.")),
                update -> CompletableFuture.completedFuture(store.schematicSettings(update)));
    }

    DashboardServer(InventoryStore store, Function<ItemFinder.Request, CompletableFuture<ItemFinder.Reply>> find,
                    Function<JsonObject, CompletableFuture<PlanningState.Result>> reservations,
                    Function<TerrainSnapshot.Request, CompletableFuture<TerrainSnapshot.Result>> terrain,
                    Function<HighlightOptions, CompletableFuture<HighlightOptions>> highlightSettings,
                    SessionTerrain sessionTerrain,
                    Function<SchematicFunnel.Request, CompletableFuture<SchematicFunnel.Result>> funnel,
                    Function<SchematicFunnel.SettingsUpdate, CompletableFuture<SchematicFunnel.Settings>> schematicChestSettings) throws IOException {
        this.store = store;
        this.find = find;
        this.reservations = reservations;
        this.terrain = terrain;
        this.highlightSettings = highlightSettings;
        this.sessionTerrain = sessionTerrain;
        this.funnel = funnel;
        this.schematicChestSettings = schematicChestSettings;
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
                    var snapshot = JsonParser.parseString(store.snapshot()).getAsJsonObject();
                    snapshot.addProperty("terrainRevision", sessionTerrain.revision());
                    send(request, 200, "application/json", snapshot.toString()); return;
                }
                if (path.equals("/api/registry") && request.getRequestMethod().equals("GET")) {
                    send(request, 200, "application/json", registry); return;
                }
                if(path.equals("/api/shared/import")){
                    if(!request.getRequestMethod().equals("POST")){send(request,405,"text/plain","POST only");return;}
                    byte[] body=request.getRequestBody().readNBytes(2*1024*1024+1);
                    if(body.length>2*1024*1024){send(request,413,"text/plain","Team file exceeds 2 MB");return;}
                    try{String id=store.importTeam(JsonParser.parseString(new String(body,StandardCharsets.UTF_8)).getAsJsonObject());var response=new JsonObject();response.addProperty("worldId",id);send(request,200,"application/json",response.toString());}
                    catch(RuntimeException e){send(request,400,"text/plain","Invalid team storage file");}return;
                }
                if(path.equals("/api/exclusions")){
                    if(!request.getRequestMethod().equals("POST")){send(request,405,"text/plain","POST only");return;}
                    byte[] body=request.getRequestBody().readNBytes(4097);if(body.length>4096){send(request,413,"text/plain","Request too large");return;}
                    try{var data=JsonParser.parseString(new String(body,StandardCharsets.UTF_8)).getAsJsonObject();var value=data.get("excluded");
                        if(value==null||!value.isJsonPrimitive()||!value.getAsJsonPrimitive().isBoolean())throw new IllegalArgumentException();
                        boolean found=store.exclude(requiredString(data,"worldId"),requiredString(data,"containerId"),value.getAsBoolean());
                        send(request,found?200:404,"application/json",found?"{\"saved\":true}":"{\"message\":\"Saved container not found.\"}");
                    }catch(RuntimeException e){send(request,400,"text/plain","Invalid exclusion request");}return;
                }
                if(path.equals("/api/reservations")){
                    if(!request.getRequestMethod().equals("POST")){send(request,405,"text/plain","POST only");return;}
                    byte[] body=request.getRequestBody().readNBytes(256001);
                    if(body.length>256000){send(request,413,"text/plain","Reservation is too large");return;}
                    CompletableFuture<PlanningState.Result> pending=null;
                    PlanningState.Result result;
                    try{var data=JsonParser.parseString(new String(body,StandardCharsets.UTF_8)).getAsJsonObject();requiredString(data,"worldId");requiredString(data,"projectId");requiredString(data,"action");
                        if(store.excludedClaims(data)){send(request,409,"application/json","{\"message\":\"The plan contains an excluded chest. Refresh and recalculate.\"}");return;}
                        pending=reservations.apply(data);result=pending.get(9,TimeUnit.SECONDS);
                    }catch(TimeoutException e){if(pending!=null)pending.cancel(false);result=new PlanningState.Result(503,"Team server timed out. Refresh before retrying.");}
                    catch(InterruptedException e){if(pending!=null)pending.cancel(false);Thread.currentThread().interrupt();result=new PlanningState.Result(503,"Request interrupted.");}
                    catch(Exception e){result=new PlanningState.Result(400,"Invalid reservation request.");}
                    var response=new JsonObject();response.addProperty("message",result.message());send(request,result.status(),"application/json",response.toString());return;
                }
                if (path.equals("/api/map/terrain/recent") && request.getRequestMethod().equals("GET")) {
                    send(request, 200, "application/json", sessionTerrain.json().toString());
                    return;
                }
                if (path.equals("/api/schematic-chest/settings")) {
                    SchematicFunnel.SettingsUpdate update = null;
                    if (request.getRequestMethod().equals("POST")) {
                        var json = readControls(request);
                        if (json == null) return;
                        try { update = SchematicFunnel.SettingsUpdate.parse(json); }
                        catch (RuntimeException invalid) {
                            sendMessage(request, 400, "Choose a valid schematic chest setting.");
                            return;
                        }
                    } else if (!request.getRequestMethod().equals("GET")) {
                        send(request, 405, "text/plain", "GET or POST only"); return;
                    }
                    var settings = awaitClient(request, schematicChestSettings.apply(update));
                    if (settings != null) send(request, 200, "application/json", settings.json().toString());
                    return;
                }
                if (path.equals("/api/schematic-chest")) {
                    if (!request.getRequestMethod().equals("POST")) {
                        send(request, 405, "text/plain", "POST only"); return;
                    }
                    var body = request.getRequestBody().readNBytes(262145);
                    if (body.length > 262144) {
                        sendMessage(request, 413, "This request has too many materials or chests."); return;
                    }
                    SchematicFunnel.Request query;
                    try {
                        query = SchematicFunnel.Request.parse(JsonParser.parseString(new String(body, StandardCharsets.UTF_8)).getAsJsonObject());
                    } catch (RuntimeException e) {
                        sendMessage(request, 400, "Choose a schematic chest, distinct source chests and a valid material list."); return;
                    }
                    var result = awaitClient(request, funnel.apply(query));
                    if (result != null) send(request, result.status(), "application/json", result.json().toString());
                    return;
                }
                if (path.equals("/api/map/terrain")) {
                    if (!request.getRequestMethod().equals("POST")) {
                        send(request, 405, "text/plain", "POST only"); return;
                    }
                    var json = readControls(request);
                    if (json == null) return;
                    TerrainSnapshot.Request query;
                    try {
                        query = new TerrainSnapshot.Request(requiredString(json, "worldId"), requiredString(json, "dimension"));
                    } catch (RuntimeException e) {
                        sendMessage(request, 400, "Choose a valid world and dimension."); return;
                    }
                    var result = awaitClient(request, terrain.apply(query));
                    if (result != null) send(request, result.status(), "application/json", result.json().toString());
                    return;
                }
                if (path.equals("/api/highlight/settings")) {
                    HighlightOptions options = null;
                    if (request.getRequestMethod().equals("POST")) {
                        var json = readControls(request);
                        if (json == null) return;
                        try { options = HighlightOptions.parse(json); }
                        catch (RuntimeException e) {
                            sendMessage(request, 400, "Choose a valid color and a duration between 5 and 120 seconds."); return;
                        }
                    } else if (!request.getRequestMethod().equals("GET")) {
                        send(request, 405, "text/plain", "GET or POST only"); return;
                    }
                    var settings = awaitClient(request, highlightSettings.apply(options));
                    if (settings != null) send(request, 200, "application/json", settings.json().toString());
                    return;
                }
                if (path.equals("/api/find")) {
                    if (!request.getRequestMethod().equals("POST")) { send(request, 405, "text/plain", "POST only"); return; }
                    byte[] body = request.getRequestBody().readNBytes(4097);
                    if (body.length > 4096) { send(request, 413, "text/plain", "Request too large"); return; }
                    ItemFinder.Request query;
                    try {
                        JsonObject json = JsonParser.parseString(new String(body, StandardCharsets.UTF_8)).getAsJsonObject();
                        var packed = json.get("includePacked");
                        if (packed != null && (!packed.isJsonPrimitive() || !packed.getAsJsonPrimitive().isBoolean())) throw new IllegalArgumentException();
                        query = new ItemFinder.Request(requiredString(json, "worldId"), requiredString(json, "containerId"),
                                json.has("itemId")&&!json.get("itemId").isJsonNull()?requiredString(json,"itemId"):null, packed == null || packed.getAsBoolean());
                    } catch (RuntimeException e) { send(request, 400, "text/plain", "Invalid find request"); return; }
                    CompletableFuture<ItemFinder.Reply> pending = null;
                    ItemFinder.Reply reply;
                    try {
                        pending = find.apply(query);
                        reply = pending.get(3, TimeUnit.SECONDS);
                    } catch (TimeoutException e) {
                        if (pending != null) pending.cancel(false);
                        reply = new ItemFinder.Reply(503, "Minecraft did not respond. Return to the game and retry.");
                    } catch (InterruptedException e) {
                        if (pending != null) pending.cancel(false);
                        Thread.currentThread().interrupt();
                        reply = new ItemFinder.Reply(503, "Find request interrupted. Retry from the dashboard.");
                    } catch (Exception e) { reply = new ItemFinder.Reply(503, "In-game finding is unavailable. Check the game log."); }
                    JsonObject response = new JsonObject(); response.addProperty("message", reply.message());
                    send(request, reply.status(), "application/json", response.toString()); return;
                }
                if (path.equals("/api/forget") && request.getRequestMethod().equals("POST")) {
                    byte[] body = request.getRequestBody().readNBytes(4097);
                    if (body.length > 4096) { send(request, 413, "text/plain", "Request too large"); return; }
                    try {
                        JsonObject json = JsonParser.parseString(new String(body, StandardCharsets.UTF_8)).getAsJsonObject();
                        if(store.shared(json.get("worldId").getAsString())){send(request,403,"text/plain","Shared containers are managed by the server operator.");return;}
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
    static String requiredString(JsonObject json, String key) {
        var value = json.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) throw new IllegalArgumentException();
        return value.getAsString();
    }

    private static JsonObject readControls(HttpExchange request) throws IOException {
        byte[] body = request.getRequestBody().readNBytes(4097);
        if (body.length > 4096) {
            sendMessage(request, 413, "Request too large.");
            return null;
        }
        try { return JsonParser.parseString(new String(body, StandardCharsets.UTF_8)).getAsJsonObject(); }
        catch (RuntimeException e) {
            sendMessage(request, 400, "Invalid dashboard request.");
            return null;
        }
    }

    private static <T> T awaitClient(HttpExchange request, CompletableFuture<T> pending) throws IOException {
        try {
            return pending.get(3, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            pending.cancel(false);
        } catch (InterruptedException e) {
            pending.cancel(false);
            Thread.currentThread().interrupt();
        } catch (java.util.concurrent.ExecutionException e) {
            StockpileClient.LOGGER.warn("Dashboard action failed", e.getCause());
        }
        sendMessage(request, 503, "Minecraft did not complete this action. Return to the game and try again.");
        return null;
    }

    private static void sendMessage(HttpExchange request, int status, String message) throws IOException {
        var response = new JsonObject();
        response.addProperty("message", message);
        send(request, status, "application/json", response.toString());
    }

    private static void send(HttpExchange request, int status, String type, String text) throws IOException {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        request.getResponseHeaders().set("Content-Type", type + "; charset=utf-8");
        request.sendResponseHeaders(status, bytes.length);
        request.getResponseBody().write(bytes);
    }

    @Override public void close() { server.stop(0); executor.shutdownNow(); }
}
