import com.google.gson.JsonParser;
import dev.danzaa.stockpile.DashboardServer;
import dev.danzaa.stockpile.InventoryStore;
import dev.danzaa.stockpile.ItemFinder;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.util.List;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.regex.Pattern;
import org.slf4j.LoggerFactory;

public final class BackendSmoke {
    static int assertions;
    static void check(boolean condition, String reason) {
        if (!condition) throw new AssertionError(reason);
        assertions++;
    }
    static void checkModules(HttpClient client, String origin) throws Exception {
        var pending = new ArrayDeque<String>();
        var visited = new HashSet<String>();
        var imports = Pattern.compile("\\b(?:import|export)\\s+(?:[^;]*?\\s+from\\s+)?[\"'](\\./[^\"']+\\.js)[\"']");
        pending.add("/app.js");
        pending.add("/schematic-worker.js");
        while (!pending.isEmpty()) {
            String path = pending.remove();
            if (!visited.add(path)) continue;
            var response = client.send(HttpRequest.newBuilder(URI.create(origin + path)).GET().build(), HttpResponse.BodyHandlers.ofString());
            check(response.statusCode() == 200, "Browser module is reachable: " + path);
            check(response.headers().firstValue("Content-Type").orElse("").startsWith("text/javascript"), "Browser module has JavaScript MIME type: " + path);
            var matcher = imports.matcher(response.body());
            while (matcher.find()) pending.add(URI.create(path).resolve(matcher.group(1)).getPath());
        }
        check(visited.contains("/recipes.js") && visited.contains("/recipes-data.js"), "Application dependency graph includes the recipe engine and data");
    }
    public static void main(String[] args) throws Exception {
        var file = Files.createTempDirectory("stockpile-smoke-").resolve("inventory.json");
        var logger = LoggerFactory.getLogger("stockpile-test");
        try (var store = new InventoryStore(file, logger)) {
            store.minecraftVersion("26.2");
            store.put("world-a", "Survival", new InventoryStore.Container("a", "Chest", "chest", "minecraft:overworld", List.of(0,64,0), List.of("a"),1234,List.of(new InventoryStore.Item("minecraft:stone","Stone",64,64))));
            store.put("world-a", "Survival", new InventoryStore.Container("b", "Chest", "chest", "minecraft:overworld", List.of(1,64,0), List.of("b"),1235,List.of(new InventoryStore.Item("minecraft:stone","Stone",32,64))));
            store.put("world-a", "Survival", new InventoryStore.Container("a|b", "Large Chest", "chest", "minecraft:overworld", List.of(0,64,0), List.of("a","b"),1236,List.of(new InventoryStore.Item("minecraft:stone","Stone",96,64))));
            var root = JsonParser.parseString(store.snapshot()).getAsJsonObject();
            check(root.get("minecraftVersion").getAsString().equals("26.2"), "Exports the recipe catalog's Minecraft version");
            check(root.getAsJsonArray("worlds").get(0).getAsJsonObject().getAsJsonArray("containers").size() == 1, "Double chest deduplicates both halves");
            store.put("world-b", "Other world", new InventoryStore.Container("a", "Chest", "chest", "minecraft:the_nether", List.of(0,64,0), List.of("a"),1237,List.of()));
            check(JsonParser.parseString(store.snapshot()).getAsJsonObject().getAsJsonArray("worlds").size()==2, "Worlds stay separate");
            store.put("world-a","Survival",new InventoryStore.Container("packed","Packed supplies","chest","minecraft:overworld",List.of(2,64,0),List.of("packed"),1238,
                    List.of(new InventoryStore.Item("minecraft:blue_shulker_box","Blue Shulker Box",1,1)),
                    List.of(new InventoryStore.PackedItem("minecraft:diamond","Diamond",16,64,List.of("Mining supplies (slot 2)"))),true));
            var lastFind = new AtomicReference<ItemFinder.Request>();
            var waitingFind = new CompletableFuture<ItemFinder.Reply>();
            try (var dashboard = new DashboardServer(store, query -> {
                lastFind.set(query);
                if ("minecraft:clock".equals(query.itemId())) return waitingFind;
                return CompletableFuture.completedFuture(new ItemFinder.Reply(query.worldId().equals("world-a") ? 200 : 409,
                        query.worldId().equals("world-a") ? "Highlighted remembered storage" : "Join that world first"));
            }); var client = HttpClient.newHttpClient()) {
                URI uri = URI.create(dashboard.url());
                String origin = "http://"+uri.getAuthority();
                String token = uri.getFragment().substring("token=".length());
                check(uri.getHost().equals("127.0.0.1"), "Binds to loopback");
                var page=client.send(HttpRequest.newBuilder(URI.create(origin+"/")).GET().build(),HttpResponse.BodyHandlers.ofString());
                check(page.statusCode()==200&&page.body().contains("Stockpile"),"Serves dashboard assets");
                checkModules(client, origin);
                var logo=client.send(HttpRequest.newBuilder(URI.create(origin+"/logo.png")).GET().build(),HttpResponse.BodyHandlers.ofByteArray());
                check(logo.statusCode()==200&&logo.headers().firstValue("Content-Type").orElse("").equals("image/png"),"Serves the mod logo as PNG");
                var icon=client.send(HttpRequest.newBuilder(URI.create(origin+"/items/iron_ingot.png")).GET().build(),HttpResponse.BodyHandlers.ofByteArray());
                check(icon.statusCode()==200&&icon.body().length>0,"Serves Minecraft item icons");
                var noToken=client.send(HttpRequest.newBuilder(URI.create(origin+"/api/snapshot")).GET().build(),HttpResponse.BodyHandlers.ofString());
                check(noToken.statusCode()==401,"Rejects requests without token");
                var withToken=client.send(HttpRequest.newBuilder(URI.create(origin+"/api/snapshot")).header("X-Stockpile-Token",token).GET().build(),HttpResponse.BodyHandlers.ofString());
                check(withToken.statusCode()==200&&withToken.body().contains("Large Chest"),"Authenticated inventory works");
                var foreign=client.send(HttpRequest.newBuilder(URI.create(origin+"/api/snapshot")).header("X-Stockpile-Token",token).header("Origin","https://unrelated.example").GET().build(),HttpResponse.BodyHandlers.ofString());
                check(foreign.statusCode()==403,"Rejects cross-origin requests");
                URI highlightSettings=URI.create(origin+"/api/highlight/settings");
                var settings=client.send(HttpRequest.newBuilder(highlightSettings).header("X-Stockpile-Token",token).GET().build(),HttpResponse.BodyHandlers.ofString());
                check(settings.statusCode()==200&&JsonParser.parseString(settings.body()).getAsJsonObject().get("seconds").getAsInt()==30,"Highlight settings are available in the connected dashboard");
                String validOptions="{\"color\":\"#3050ef\",\"seconds\":15,\"throughWalls\":false}";
                var updated=client.send(HttpRequest.newBuilder(highlightSettings).header("X-Stockpile-Token",token).POST(HttpRequest.BodyPublishers.ofString(validOptions)).build(),HttpResponse.BodyHandlers.ofString());
                check(updated.statusCode()==200&&!JsonParser.parseString(updated.body()).getAsJsonObject().get("throughWalls").getAsBoolean(),"Browser can send all three highlight preferences");
                var badDuration=client.send(HttpRequest.newBuilder(highlightSettings).header("X-Stockpile-Token",token).POST(HttpRequest.BodyPublishers.ofString(validOptions.replace(":15",":15.5"))).build(),HttpResponse.BodyHandlers.ofString());
                check(badDuration.statusCode()==400,"Highlight duration must be an integer");
                URI terrain=URI.create(origin+"/api/map/terrain");
                String capture="{\"worldId\":\"world-a\",\"dimension\":\"minecraft:overworld\"}";
                var anonymousCapture=client.send(HttpRequest.newBuilder(terrain).POST(HttpRequest.BodyPublishers.ofString(capture)).build(),HttpResponse.BodyHandlers.ofString());
                check(anonymousCapture.statusCode()==401,"Terrain capture requires the dashboard token");
                var badDimension=client.send(HttpRequest.newBuilder(terrain).header("X-Stockpile-Token",token).POST(HttpRequest.BodyPublishers.ofString(capture.replace("minecraft:overworld","../world"))).build(),HttpResponse.BodyHandlers.ofString());
                check(badDimension.statusCode()==400,"Terrain capture validates the dimension");
                URI recentTerrain=URI.create(origin+"/api/map/terrain/recent");
                var unauthorizedTerrain=client.send(HttpRequest.newBuilder(recentTerrain).GET().build(),HttpResponse.BodyHandlers.ofString());
                check(unauthorizedTerrain.statusCode()==401,"Automatic terrain snapshots require the dashboard token");
                var emptyTerrain=client.send(HttpRequest.newBuilder(recentTerrain).header("X-Stockpile-Token",token).GET().build(),HttpResponse.BodyHandlers.ofString());
                check(emptyTerrain.statusCode()==200&&JsonParser.parseString(emptyTerrain.body()).getAsJsonArray().isEmpty(),"Terrain archive starts empty");
                var schematicSettings=HttpRequest.newBuilder(URI.create(origin+"/api/schematic-chest/settings")).header("X-Stockpile-Token",token);
                var defaultSchematic=client.send(schematicSettings.GET().build(),HttpResponse.BodyHandlers.ofString());
                check(defaultSchematic.statusCode()==200&&JsonParser.parseString(defaultSchematic.body()).getAsJsonObject().get("autoClear").getAsBoolean(),
                        "Schematic chest auto-clear is on by default");
                var clearOff=client.send(schematicSettings.POST(HttpRequest.BodyPublishers.ofString("{\"autoClear\":false}")).build(),HttpResponse.BodyHandlers.ofString());
                var clearOffSettings=JsonParser.parseString(clearOff.body()).getAsJsonObject();
                check(clearOff.statusCode()==200&&clearOffSettings.get("enabled").getAsBoolean()&&!clearOffSettings.get("autoClear").getAsBoolean(),
                        "Turning off auto-clear keeps the schematic chest enabled");
                var invalidClear=client.send(schematicSettings.POST(HttpRequest.BodyPublishers.ofString("{\"autoClear\":\"true\"}")).build(),HttpResponse.BodyHandlers.ofString());
                check(invalidClear.statusCode()==400,"Auto-clear accepts a boolean");
                URI schematicChest=URI.create(origin+"/api/schematic-chest");
                String fill="""
                        {"worldId":"world-a","worldSession":"session-a","destination":"target",
                         "sources":["source"],"materials":[{"id":"minecraft:stone","count":64}]}
                        """;
                var foreignFill=client.send(HttpRequest.newBuilder(schematicChest).header("X-Stockpile-Token",token)
                        .header("Origin","https://unrelated.example").POST(HttpRequest.BodyPublishers.ofString(fill)).build(),
                        HttpResponse.BodyHandlers.ofString());
                check(foreignFill.statusCode()==403,"Schematic chest rejects foreign origins");
                var sameChest=client.send(HttpRequest.newBuilder(schematicChest).header("X-Stockpile-Token",token)
                        .POST(HttpRequest.BodyPublishers.ofString(fill.replace("\"source\"","\"target\""))).build(),
                        HttpResponse.BodyHandlers.ofString());
                check(sameChest.statusCode()==400,"A schematic chest cannot also be a source");
                var offlineFill=client.send(HttpRequest.newBuilder(schematicChest).header("X-Stockpile-Token",token)
                        .POST(HttpRequest.BodyPublishers.ofString(fill)).build(),HttpResponse.BodyHandlers.ofString());
                check(offlineFill.statusCode()==409,"Schematic chest is unavailable without a running single-player world");
                String findBody="{\"worldId\":\"world-a\",\"containerId\":\"packed\",\"itemId\":\"minecraft:diamond\",\"includePacked\":false}";
                var findNoToken=client.send(HttpRequest.newBuilder(URI.create(origin+"/api/find")).POST(HttpRequest.BodyPublishers.ofString(findBody)).build(),HttpResponse.BodyHandlers.ofString());
                check(findNoToken.statusCode()==401&&lastFind.get()==null,"Find requires authentication before dispatching");
                var findForeign=client.send(HttpRequest.newBuilder(URI.create(origin+"/api/find")).header("X-Stockpile-Token",token).header("Origin","https://unrelated.example").POST(HttpRequest.BodyPublishers.ofString(findBody)).build(),HttpResponse.BodyHandlers.ofString());
                check(findForeign.statusCode()==403&&lastFind.get()==null,"Find rejects foreign origins before dispatching");
                var findGet=client.send(HttpRequest.newBuilder(URI.create(origin+"/api/find")).header("X-Stockpile-Token",token).GET().build(),HttpResponse.BodyHandlers.ofString());
                check(findGet.statusCode()==405,"Find only accepts POST");
                var findOk=client.send(HttpRequest.newBuilder(URI.create(origin+"/api/find")).header("X-Stockpile-Token",token).POST(HttpRequest.BodyPublishers.ofString(findBody)).build(),HttpResponse.BodyHandlers.ofString());
                check(findOk.statusCode()==200&&lastFind.get().containerId().equals("packed")&&!lastFind.get().includePacked(),"Find dispatches saved IDs and exact packed-content option");
                for(String bad:List.of(findBody.replace("minecraft:diamond","invalid item"),findBody.replace("false","\"false\""),"{}",findBody.replace("\"packed\"","null"))){
                    lastFind.set(null);
                    var invalidFind=client.send(HttpRequest.newBuilder(URI.create(origin+"/api/find")).header("X-Stockpile-Token",token).POST(HttpRequest.BodyPublishers.ofString(bad)).build(),HttpResponse.BodyHandlers.ofString());
                    check(invalidFind.statusCode()==400&&lastFind.get()==null,"Rejects malformed find data before dispatching");
                }
                var wrongWorld=client.send(HttpRequest.newBuilder(URI.create(origin+"/api/find")).header("X-Stockpile-Token",token).POST(HttpRequest.BodyPublishers.ofString(findBody.replace("world-a","world-b"))).build(),HttpResponse.BodyHandlers.ofString());
                check(wrongWorld.statusCode()==409&&wrongWorld.body().contains("Join that world first"),"Forwards client world validation errors");
                var findLarge=client.send(HttpRequest.newBuilder(URI.create(origin+"/api/find")).header("X-Stockpile-Token",token).POST(HttpRequest.BodyPublishers.ofString("x".repeat(4097))).build(),HttpResponse.BodyHandlers.ofString());
                check(findLarge.statusCode()==413,"Caps find request sizes");
                var findTimeout=client.send(HttpRequest.newBuilder(URI.create(origin+"/api/find")).header("X-Stockpile-Token",token).POST(HttpRequest.BodyPublishers.ofString(findBody.replace("minecraft:diamond","minecraft:clock"))).build(),HttpResponse.BodyHandlers.ofString());
                check(findTimeout.statusCode()==503&&waitingFind.isCancelled(),"Timeout cancels queued work rather than finding later in a different session");
                var reservation=new com.google.gson.JsonObject();reservation.addProperty("worldId","world-a");reservation.addProperty("projectId","road");reservation.addProperty("action","reserve");reservation.addProperty("name","Road");
                reservation.addProperty("revision",store.worldSnapshot("world-a").getAsJsonObject("planning").get("revision").getAsLong());
                var claimArray=new com.google.gson.JsonArray();var claim=new com.google.gson.JsonObject();claim.addProperty("containerId","a|b");claim.addProperty("itemId","minecraft:stone");claim.addProperty("packed",false);claim.addProperty("count",80);claimArray.add(claim);reservation.add("claims",claimArray);
                var reserveNoToken=client.send(HttpRequest.newBuilder(URI.create(origin+"/api/reservations")).POST(HttpRequest.BodyPublishers.ofString(reservation.toString())).build(),HttpResponse.BodyHandlers.ofString());
                check(reserveNoToken.statusCode()==401,"Reservations require authentication");
                var reserveOk=client.send(HttpRequest.newBuilder(URI.create(origin+"/api/reservations")).header("X-Stockpile-Token",token).POST(HttpRequest.BodyPublishers.ofString(reservation.toString())).build(),HttpResponse.BodyHandlers.ofString());
                check(reserveOk.statusCode()==200,"HTTP reserves a feasible physical claim");
                reservation.addProperty("projectId","tower");
                var reserveRace=client.send(HttpRequest.newBuilder(URI.create(origin+"/api/reservations")).header("X-Stockpile-Token",token).POST(HttpRequest.BodyPublishers.ofString(reservation.toString())).build(),HttpResponse.BodyHandlers.ofString());
                check(reserveRace.statusCode()==409,"HTTP rejects a concurrent stale reservation revision");
                reservation.addProperty("revision",store.worldSnapshot("world-a").getAsJsonObject("planning").get("revision").getAsLong());claim.addProperty("count",17);
                var reserveOver=client.send(HttpRequest.newBuilder(URI.create(origin+"/api/reservations")).header("X-Stockpile-Token",token).POST(HttpRequest.BodyPublishers.ofString(reservation.toString())).build(),HttpResponse.BodyHandlers.ofString());
                check(reserveOver.statusCode()==409,"HTTP rejects overbooking with a fresh revision too");
                var teamFile="{\"format\":\"stockpile-shared-storage\",\"version\":1,\"teamId\":\"builders\",\"serverId\":\"Friends\",\"name\":\"Builders\",\"containers\":[]}";
                var teamImport=client.send(HttpRequest.newBuilder(URI.create(origin+"/api/shared/import")).header("X-Stockpile-Token",token).POST(HttpRequest.BodyPublishers.ofString(teamFile)).build(),HttpResponse.BodyHandlers.ofString());
                check(teamImport.statusCode()==200&&teamImport.body().contains("file-team:"),"Explicit team files import into a separate local workspace");
                String excludeBody="{\"worldId\":\"world-a\",\"containerId\":\"a|b\",\"excluded\":true}";
                var excludeAuth=client.send(HttpRequest.newBuilder(URI.create(origin+"/api/exclusions")).POST(HttpRequest.BodyPublishers.ofString(excludeBody)).build(),HttpResponse.BodyHandlers.ofString());check(excludeAuth.statusCode()==401,"Exclusions require authentication");
                var excludeBad=client.send(HttpRequest.newBuilder(URI.create(origin+"/api/exclusions")).header("X-Stockpile-Token",token).POST(HttpRequest.BodyPublishers.ofString(excludeBody.replace("true","\"true\""))).build(),HttpResponse.BodyHandlers.ofString());check(excludeBad.statusCode()==400,"Exclusion flag is a strict boolean");
                var excludeOk=client.send(HttpRequest.newBuilder(URI.create(origin+"/api/exclusions")).header("X-Stockpile-Token",token).POST(HttpRequest.BodyPublishers.ofString(excludeBody)).build(),HttpResponse.BodyHandlers.ofString());check(excludeOk.statusCode()==200&&store.excluded("world-a","a|b"),"Exclusion is saved without forgetting the container");
                reservation.addProperty("revision",store.worldSnapshot("world-a").getAsJsonObject("planning").get("revision").getAsLong());claim.addProperty("count",1);
                var reserveExcluded=client.send(HttpRequest.newBuilder(URI.create(origin+"/api/reservations")).header("X-Stockpile-Token",token).POST(HttpRequest.BodyPublishers.ofString(reservation.toString())).build(),HttpResponse.BodyHandlers.ofString());check(reserveExcluded.statusCode()==409&&reserveExcluded.body().contains("excluded chest"),"Excluded chest claims are blocked before local or shared reservation dispatch");
                var directChest=client.send(HttpRequest.newBuilder(URI.create(origin+"/api/find")).header("X-Stockpile-Token",token).POST(HttpRequest.BodyPublishers.ofString(findBody.replace("\"minecraft:diamond\"","null"))).build(),HttpResponse.BodyHandlers.ofString());check(directChest.statusCode()==200&&lastFind.get().itemId()==null,"Map can highlight a saved chest without an item query");
                var traversal=client.send(HttpRequest.newBuilder(URI.create(origin+"/%2e%2e/inventory.json")).GET().build(),HttpResponse.BodyHandlers.ofString());
                check(traversal.statusCode()==404,"No arbitrary filesystem reads");
                var oversized=client.send(HttpRequest.newBuilder(URI.create(origin+"/api/forget")).header("X-Stockpile-Token",token).POST(HttpRequest.BodyPublishers.ofString("x".repeat(4100))).build(),HttpResponse.BodyHandlers.ofString());
                check(oversized.statusCode()==413,"Caps request sizes");
                var forget=client.send(HttpRequest.newBuilder(URI.create(origin+"/api/forget")).header("X-Stockpile-Token",token).POST(HttpRequest.BodyPublishers.ofString("{\"worldId\":\"world-b\",\"containerId\":\"a\"}")).build(),HttpResponse.BodyHandlers.ofString());
                check(forget.statusCode()==200,"Removes a stale record");
                check(page.headers().firstValue("Content-Security-Policy").orElse("").contains("frame-ancestors 'none'"),"Protects against framing");
                check(page.headers().firstValue("Access-Control-Allow-Origin").isEmpty(),"No wildcard CORS");
            }
        }
        check(Files.exists(file),"Saves inventory on close");
        try (var reloaded = new InventoryStore(file,logger)) {
            var data=JsonParser.parseString(reloaded.snapshot()).getAsJsonObject();
            check(data.getAsJsonArray("worlds").get(0).getAsJsonObject().getAsJsonArray("containers").get(0).getAsJsonObject().getAsJsonArray("items").get(0).getAsJsonObject().get("count").getAsInt()==96,"Reloads saved quantities");
            check(!data.get("connected").getAsBoolean(),"Does not restore an old connected flag");
            var packed=data.getAsJsonArray("worlds").get(0).getAsJsonObject().getAsJsonArray("containers").get(1).getAsJsonObject();
            check(packed.getAsJsonArray("packedItems").get(0).getAsJsonObject().get("count").getAsInt()==16,"Reloads shulker contents");
            check(packed.getAsJsonArray("packedItems").get(0).getAsJsonObject().getAsJsonArray("path").get(0).getAsString().contains("Mining supplies"),"Reloads nested item locations");
        }
        System.out.println("Backend smoke checks passed: "+assertions);
    }
}
