import com.google.gson.JsonParser;
import dev.danzaa.stockpile.DashboardServer;
import dev.danzaa.stockpile.InventoryStore;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.util.List;
import org.slf4j.LoggerFactory;

public final class BackendSmoke {
    static int assertions;
    static void check(boolean condition, String reason) {
        if (!condition) throw new AssertionError(reason);
        assertions++;
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
            try (var dashboard = new DashboardServer(store); var client = HttpClient.newHttpClient()) {
                URI uri = URI.create(dashboard.url());
                String origin = "http://"+uri.getAuthority();
                String token = uri.getFragment().substring("token=".length());
                check(uri.getHost().equals("127.0.0.1"), "Binds to loopback");
                var page=client.send(HttpRequest.newBuilder(URI.create(origin+"/")).GET().build(),HttpResponse.BodyHandlers.ofString());
                check(page.statusCode()==200&&page.body().contains("Stockpile"),"Serves dashboard assets");
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
