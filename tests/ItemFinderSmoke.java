import dev.danzaa.stockpile.*;
import java.nio.file.Files;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.gizmos.SimpleGizmoCollector;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.slf4j.LoggerFactory;

public final class ItemFinderSmoke {
    private static int checks;
    private static void check(boolean ok, String reason) { if (!ok) throw new AssertionError(reason); checks++; }
    private static InventoryStore.Container chest(String id, String dimension, int x, int amount, boolean packed) {
        return new InventoryStore.Container(id, "Supplies", "chest", dimension, List.of(x,64,0), List.of(id), 1,
                List.of(new InventoryStore.Item("minecraft:stone", "Stone", amount, 64)),
                packed ? List.of(new InventoryStore.PackedItem("minecraft:diamond", "Diamond", 8, 64, List.of("Blue box (slot 3)"))) : List.of(), true);
    }
    public static void main(String[] args) throws Exception {
        try (var store = new InventoryStore(Files.createTempDirectory("stockpile-find-").resolve("inventory.json"), LoggerFactory.getLogger("find-test"))) {
            store.put("a", "A", chest("near", "minecraft:overworld", 2, 64, true));
            store.put("a", "A", chest("far", "minecraft:overworld", 200, 32, false));
            store.put("a", "A", chest("other-dimension", "minecraft:the_nether", 0, 16, false));
            store.put("b", "B", chest("foreign-world", "minecraft:overworld", 0, 999, false));
            var request = new ItemFinder.Request("a", null, "minecraft:stone", true);
            var found = ItemFinder.search(store, request, "a", "minecraft:overworld", 0,64,0);
            check(found.stream().map(InventoryStore.Container::id).toList().equals(List.of("near","far","other-dimension")), "Current dimension first, then nearest, no foreign world");
            check(ItemFinder.search(store, request, "b", "minecraft:overworld",0,64,0).isEmpty(), "Rejects a world switch");
            check(ItemFinder.search(store,new ItemFinder.Request("a","near","minecraft:diamond",true),"a","minecraft:overworld",0,64,0).size()==1,"Packed contents locate the parent chest");
            check(ItemFinder.search(store,new ItemFinder.Request("a","near","minecraft:diamond",false),"a","minecraft:overworld",0,64,0).isEmpty(),"Loose-only search excludes packed items");
            check(ItemFinder.search(store,new ItemFinder.Request("a","foreign-world","minecraft:stone",true),"a","minecraft:overworld",0,64,0).isEmpty(),"Container IDs cannot escape world scope");
            store.forget("a","near");
            check(ItemFinder.search(store,new ItemFinder.Request("a","near","minecraft:stone",true),"a","minecraft:overworld",0,64,0).isEmpty(),"Deleted memories cannot be targeted");
            store.put("a","A",chest("empty","minecraft:overworld",1,0,false));
            check(ItemFinder.search(store,request,"a","minecraft:overworld",0,64,0).size()==2,"Empty quantities are not locations");
        }
        try { new ItemFinder.Request("a",null,"minecraft:stone /tp",true); throw new AssertionError("Invalid ID accepted"); }
        catch (IllegalArgumentException expected) { checks++; }
        var doubleChest = new InventoryStore.Container("double", "Large chest", "chest", "minecraft:overworld", List.of(0,64,0),
                List.of("minecraft:overworld:0,64,0","minecraft:overworld:1,64,0","minecraft:overworld:100,64,0"),1,List.of());
        check(FindHighlight.positions(doubleChest).equals(List.of(new BlockPos(0,64,0),new BlockPos(1,64,0))),"Highlight both adjacent halves, never remote member positions");
        var collector = new SimpleGizmoCollector();
        try (var ignored = Gizmos.withCollector(collector)) { FindHighlight.draw(new BlockPos(1,64,2)); }
        check(collector.getGizmos().size()==1 && collector.getGizmos().getFirst().isAlwaysOnTop(), "Real Minecraft gizmo overlay is emitted with through-wall visibility");
        ListTag criteria = new ListTag(); CompoundTag item = new CompoundTag();
        item.putString("type","whereisit:item");item.putString("item","minecraft:stone");criteria.add(item);
        check("minecraft:stone".equals(ChestTrackerFindCompat.simpleItemId(criteria)),"Optional bridge accepts ID searches");
        CompoundTag component = new CompoundTag();component.putString("type","whereisit:component");criteria.add(component);
        check(ChestTrackerFindCompat.simpleItemId(criteria)==null,"Bridge leaves component searches to Chest Tracker");
        System.out.println("Item finder checks passed: " + checks);
    }
}
