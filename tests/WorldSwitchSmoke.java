import dev.danzaa.stockpile.InventoryStore;
import com.google.gson.*;
import java.nio.file.Files;
import java.util.List;
import org.slf4j.LoggerFactory;

public final class WorldSwitchSmoke {
    static int checks;
    static void check(boolean ok,String reason){if(!ok)throw new AssertionError(reason);checks++;}
    static JsonObject snapshot(InventoryStore store){return JsonParser.parseString(store.snapshot()).getAsJsonObject();}
    public static void main(String[] args)throws Exception{
        var file=Files.createTempDirectory("stockpile-world-switch-").resolve("inventory.json");var log=LoggerFactory.getLogger("world-switch-test");
        try(var store=new InventoryStore(file,log)){
            store.put("old","Old world",new InventoryStore.Container("chest","Chest","chest","minecraft:overworld",List.of(0,64,0),List.of("chest"),1,List.of(new InventoryStore.Item("minecraft:stone","Stone",64,64))));
            store.tracking(false);store.activeWorld("new","New world");var root=snapshot(store);
            check(root.get("connected").getAsBoolean()&&root.get("activeWorldId").getAsString().equals("new"),"Joined world is explicit even with tracking paused");
            check(store.containers("new").isEmpty()&&store.containers("old").getFirst().items().getFirst().count()==64,"New world starts empty without changing the old inventory");
            check(root.getAsJsonArray("worlds").size()==2&&root.getAsJsonArray("worlds").get(1).getAsJsonObject().get("id").getAsString().equals("new"),"New world is registered before the first container is opened");
            String session=root.get("worldSession").getAsString();store.activeWorld("new","New world");
            check(snapshot(store).get("worldSession").getAsString().equals(session),"Ordinary ticks do not create new connections");
            store.connected(false);root=snapshot(store);
            check(root.has("activeWorldId")&&root.get("activeWorldId").isJsonNull()&&root.get("worldSession").isJsonNull(),"Disconnect publishes an explicitly empty live identity");
            store.activeWorld("new","New world");check(!snapshot(store).get("worldSession").getAsString().equals(session),"Rejoining the same world starts a new connection session");
            store.activeWorld("old","Old world");check(snapshot(store).get("activeWorldId").getAsString().equals("old")&&store.containers("old").getFirst().items().getFirst().count()==64,"Returning to an old world restores only its saved counts");
        }
        try(var reloaded=new InventoryStore(file,log)){
            var root=snapshot(reloaded);check(!root.get("connected").getAsBoolean()&&root.has("activeWorldId")&&root.get("activeWorldId").isJsonNull(),"Saved active identity is not restored at startup");
            check(reloaded.containers("old").size()==1&&reloaded.containers("new").isEmpty(),"Saved inventories remain isolated after restart");
        }
        System.out.println("World switching checks passed: "+checks);
    }
}
