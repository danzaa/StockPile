import dev.danzaa.stockpile.*;
import java.nio.file.Files;
import java.util.List;
import org.slf4j.LoggerFactory;
public final class ChestMapSmoke {
    static int checks;static void check(boolean value,String reason){if(!value)throw new AssertionError(reason);checks++;}
    static InventoryStore.Container chest(String id,List<String> members,List<InventoryStore.Item> items,List<InventoryStore.PackedItem> packed){return new InventoryStore.Container(id,"Chest","chest","minecraft:overworld",List.of(0,64,0),members,1,items,packed,true);}
    public static void main(String[] args)throws Exception{
        var file=Files.createTempDirectory("stockpile-map-").resolve("inventory.json");var log=LoggerFactory.getLogger("map-test");
        try(var store=new InventoryStore(file,log)){
            var rows=new java.util.ArrayList<InventoryStore.PackedItem>();for(int i=0;i<27;i++)rows.add(new InventoryStore.PackedItem("minecraft:stone","Stone",64,64,List.of("Shulker Box (slot 22)")));
            rows.add(new InventoryStore.PackedItem("minecraft:stone","Stone",1,64,List.of("Shulker Box (slot 23)")));
            var box=chest("a",List.of("a"),List.of(),rows);store.put("world","World",box);
            var grouped=ItemFinder.packedLocations(box,"minecraft:stone");check(grouped.size()==2&&grouped.getFirst().count()==1728,"Repeated stack paths group into one total without merging other slots");
            store.put("world","World",chest("empty",List.of("empty"),List.of(),List.of()));
            check(ItemFinder.search(store,new ItemFinder.Request("world","empty",null,false),"world","minecraft:overworld",0,64,0).size()==1,"Map can highlight an empty saved chest");
            check(ItemFinder.search(store,new ItemFinder.Request("world","empty","minecraft:stone",true),"world","minecraft:overworld",0,64,0).isEmpty(),"Item searches still require remembered items");
            check(store.exclude("world","a",true)&&store.containers("world").size()==2,"Exclusion retains saved observations");
            check(ItemFinder.search(store,new ItemFinder.Request("world",null,"minecraft:stone",true),"world","minecraft:overworld",0,64,0).isEmpty(),"Broad item finding respects excluded inventory sources");
            check(ItemFinder.search(store,new ItemFinder.Request("world","a",null,false),"world","minecraft:overworld",0,64,0).size()==1,"An excluded chest can still be highlighted explicitly from its map record");
            store.put("world","World",chest("a|b",List.of("a","b"),List.of(),rows));check(store.excluded("world","a|b"),"Exclusion follows a double-chest merge");
            store.exclude("world","a|b",true);
            store.put("world","World",chest("a",List.of("a"),List.of(),rows));store.put("world","World",chest("b",List.of("b"),List.of(),List.of()));
            check(store.excluded("world","a")&&store.excluded("world","b"),"Excluding a double chest retains both exclusions after splitting");
            store.exclude("world","a",false);store.exclude("world","b",false);store.put("world","World",chest("a|b",List.of("a","b"),List.of(),rows));
            check(!store.excluded("world","a|b"),"Reincluding both halves does not leave a hidden compound exclusion");store.exclude("world","a|b",true);
        }
        try(var reloaded=new InventoryStore(file,log)){check(reloaded.excluded("world","a|b"),"Chest exclusions survive restart");check(reloaded.worldSnapshot("world").getAsJsonArray("excludedContainerIds").get(0).getAsString().equals("a|b"),"Snapshot exposes current excluded container IDs");}
        try{new ItemFinder.Request("world",null,null,false);throw new AssertionError("Unscoped empty highlight accepted");}catch(IllegalArgumentException expected){checks++;}
        System.out.println("Chest map, grouping and exclusion checks passed: "+checks);
    }
}
