import dev.danzaa.stockpile.*;
import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import org.slf4j.LoggerFactory;

public final class PlanningSmoke {
    static int checks;
    static void check(boolean ok,String reason){if(!ok)throw new AssertionError(reason);checks++;}
    static InventoryStore.Container chest(String id,int count,List<String> members){return new InventoryStore.Container(id,"Supplies","chest","minecraft:overworld",List.of(0,64,0),members,System.currentTimeMillis(),List.of(new InventoryStore.Item("minecraft:stone","Stone",count,64)));}
    static JsonObject request(String world,String project,long revision,long count){
        var q=new JsonObject();q.addProperty("worldId",world);q.addProperty("projectId",project);q.addProperty("action","reserve");q.addProperty("name",project);q.addProperty("revision",revision);
        var claims=new JsonArray();var c=new JsonObject();c.addProperty("containerId","a");c.addProperty("itemId","minecraft:stone");c.addProperty("packed",false);c.addProperty("count",count);claims.add(c);q.add("claims",claims);return q;
    }
    static long revision(InventoryStore store,String world){return store.worldSnapshot(world).getAsJsonObject("planning").get("revision").getAsLong();}
    public static void main(String[] args)throws Exception{
        var file=Files.createTempDirectory("stockpile-planning-").resolve("inventory.json");var log=LoggerFactory.getLogger("planning-test");
        try(var store=new InventoryStore(file,log)){
            store.put("world","World",chest("a",64,List.of("a")));long rev=revision(store,"world");
            check(store.reservation(request("world","road",rev,40),"local").status()==200,"First project reserves atomically");
            check(store.reservation(request("world","tower",rev,40),"local").status()==409,"Stale revision rejected across tabs");
            check(store.reservation(request("world","tower",revision(store,"world"),25),"local").status()==409,"Other project cannot double-spend supplies");
            check(store.reservation(request("world","tower",revision(store,"world"),24),"local").status()==200,"Remaining supplies can be reserved");
            var release=request("world","road",revision(store,"world"),1);release.addProperty("action","release");
            check(store.reservation(release,"teammate").status()==403,"A teammate cannot release another owner's reservation");
            store.put("world","World",chest("a",20,List.of("a")));
            check(store.reservation(request("world","new",revision(store,"world"),1),"local").status()==409,"Stock drop prevents new overlapping claims");
            var before=store.worldSnapshot("world").getAsJsonObject("planning").getAsJsonArray("history");
            check(before.size()==2&&before.get(0).getAsJsonObject().get("reason").getAsString().equals("discovered")&&before.get(1).getAsJsonObject().get("reason").getAsString().equals("observed"),"History distinguishes discovery from observed use");
            store.put("world","World",chest("a",20,List.of("a")));
            check(store.worldSnapshot("world").getAsJsonObject("planning").getAsJsonArray("history").size()==2,"Unchanged polling does not grow history");
            store.put("world","World",chest("a|b",20,List.of("a","b")));
            var claims=store.worldSnapshot("world").getAsJsonObject("planning").getAsJsonObject("reservations").getAsJsonObject("road").getAsJsonArray("claims");
            check(claims.get(0).getAsJsonObject().get("containerId").getAsString().equals("a|b"),"Double-chest replacement remaps physical claims");
            store.forget("world","a|b");check(store.worldSnapshot("world").getAsJsonObject("planning").getAsJsonArray("history").asList().stream().anyMatch(e->e.getAsJsonObject().get("reason").getAsString().equals("forgotten")),"Removing memory is not labeled consumption");
            // Separate local race: the same inventory revision has exactly one winner.
            store.put("race","Race",chest("a",64,List.of("a")));long version=revision(store,"race");
            try(var pool=Executors.newFixedThreadPool(2)){
                var futures=pool.invokeAll(List.of(()->store.reservation(request("race","one",version,64),"local").status(),()->store.reservation(request("race","two",version,64),"local").status()));
                check(futures.stream().filter(f->{try{return f.get().equals(200);}catch(Exception e){throw new RuntimeException(e);}}).count()==1,"Concurrent tabs have exactly one reservation winner");
            }
            var packet=JsonParser.parseString("{\"format\":\"stockpile-shared-storage\",\"version\":1,\"teamId\":\"builders\",\"serverId\":\"Friends\",\"name\":\"Builders\",\"containers\":[]}").getAsJsonObject();
            var c=new Gson().toJsonTree(chest("a",16,List.of("minecraft:overworld:0,64,0"))).getAsJsonObject();packet.getAsJsonArray("containers").add(c);
            String imported=store.importTeam(packet);check(store.containers(imported).size()==1,"Team file creates a separate workspace");
            c.addProperty("lastSeen",1);c.getAsJsonArray("items").get(0).getAsJsonObject().addProperty("count",99);store.importTeam(packet);
            check(store.containers(imported).getFirst().items().getFirst().count()==16,"Older teammate file cannot overwrite newer observation");
            var privateChest=c.deepCopy();privateChest.addProperty("type","ender_chest");packet.getAsJsonArray("containers").add(privateChest);
            try{store.importTeam(packet);throw new AssertionError("Shared Ender Chest accepted");}catch(IllegalArgumentException expected){checks++;}
            check(store.containers(imported).size()==1,"Malformed team import has no partial mutation");
            var shared=chest("team-storage",12,List.of("team-storage"));store.put("personal","Personal",shared);
            store.sharedSnapshot("shared:team","Team","personal",List.of(shared),new PlanningState(),"viewer","shared:remote");
            check(store.containers("personal").isEmpty()&&store.externalShared("personal",shared),"Automatic team storage cannot also be counted in personal stock");
            store.sharedOffline();check(!store.worldSnapshot("shared:team").get("live").getAsBoolean(),"Team connection state clears on disconnect");
            store.revokeShared(Set.of(),"personal");check(store.worldSnapshot("shared:team")==null,"Revocation removes the cached live workspace");
        }
        try(var store=new InventoryStore(file,log)){
            check(store.worldSnapshot("race").getAsJsonObject("planning").getAsJsonObject("reservations").size()==1,"Reservations survive restart");
            check(store.releaseOwned("race","local")==1&&store.worldSnapshot("race").getAsJsonObject("planning").getAsJsonObject("reservations").isEmpty(),"Revoked team members cannot strand their material claims");
            check(store.worldSnapshot("world").getAsJsonObject("planning").getAsJsonArray("history").size()>0,"History survives restart");
        }
        var registry=new SharedTeamRegistry();registry.create("builders");String member=UUID.randomUUID().toString();registry.team("builders").members.add(member);
        check(registry.allowed("builders",member)&&!registry.allowed("builders",UUID.randomUUID().toString()),"Shared team access is explicit and UUID-scoped");
        var grants=file.resolveSibling("grants.json");registry.save(grants);check(SharedTeamRegistry.load(grants).authority.equals(registry.authority),"Server authority identity persists");
        check(SharedPayload.TYPE.id().toString().equals("stockpile:shared_v1"),"Shared protocol has its own mod namespace");
        String json="{\"world\":\""+"stone".repeat(2000)+"\"}";check(SharedPayload.expand(SharedPayload.compress(json)).equals(json),"Bounded shared transport round-trips");
        try{SharedPayload.compress("x".repeat(SharedPayload.MAX_EXPANDED+1));throw new AssertionError("Oversized packet accepted");}catch(IllegalArgumentException expected){checks++;}
        try{SharedPayload.expand(new byte[]{1,2,3});throw new AssertionError("Malformed packet accepted");}catch(IllegalArgumentException expected){checks++;}
        System.out.println("Planning, history, sharing and transport checks passed: "+checks);
    }
}
