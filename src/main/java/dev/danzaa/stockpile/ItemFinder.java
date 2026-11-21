package dev.danzaa.stockpile;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Searches saved observations, never arbitrary browser-supplied coordinates. */
public final class ItemFinder {
    public record Request(String worldId, String containerId, String itemId, boolean includePacked) {
        public Request {
            if (worldId == null || worldId.isBlank() || worldId.length() > 200 ||
                    containerId != null && (containerId.isBlank() || containerId.length() > 500) ||
                    (itemId == null ? containerId == null : itemId.length() > 256 || !itemId.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")))
                throw new IllegalArgumentException("Invalid find request");
        }
    }
    public record Reply(int status, String message) {}
    public static long quantity(InventoryStore.Container c, String itemId, boolean includePacked) {
        long count = c.items().stream().filter(i -> i.count() > 0 && itemId.equals(i.id())).mapToLong(InventoryStore.Item::count).sum();
        if (includePacked && c.packedItems() != null)
            count += c.packedItems().stream().filter(i -> i.count() > 0 && itemId.equals(i.id())).mapToLong(InventoryStore.PackedItem::count).sum();
        return count;
    }
    public static List<InventoryStore.Container> search(InventoryStore store, Request request, String activeWorld,
                                                       String dimension, double x, double y, double z) {
        if (!Objects.equals(request.worldId(), activeWorld)) return List.of();
        return store.containers(activeWorld).stream()
                .filter(c -> request.containerId() == null || request.containerId().equals(c.id()))
                .filter(c -> request.containerId()!=null || !store.excluded(activeWorld,c.id()))
                .filter(c -> request.itemId()==null || quantity(c, request.itemId(), request.includePacked()) > 0)
                .sorted(Comparator.<InventoryStore.Container>comparingInt(c -> dimension.equals(c.dimension()) ? 0 : 1)
                        .thenComparingDouble(c -> distance(c, x, y, z)).thenComparing(InventoryStore.Container::id))
                .toList();
    }
    private static double distance(InventoryStore.Container c, double x, double y, double z) {
        double dx = c.position().get(0) + .5 - x, dy = c.position().get(1) + .5 - y, dz = c.position().get(2) + .5 - z;
        return dx * dx + dy * dy + dz * dz;
    }
    public record PackedLocation(List<String> path,long count){}
    public static List<PackedLocation> packedLocations(InventoryStore.Container c,String itemId){
        if(itemId==null||c.packedItems()==null)return List.of();
        var grouped=new java.util.LinkedHashMap<List<String>,Long>();
        for(var item:c.packedItems())if(itemId.equals(item.id())&&item.count()>0)grouped.merge(List.copyOf(item.path()),(long)item.count(),Long::sum);
        return grouped.entrySet().stream().map(e->new PackedLocation(e.getKey(),e.getValue())).toList();
    }
    public static boolean ender(InventoryStore.Container c) { return c.id().startsWith("ender:") || c.type().endsWith("ender_chest"); }
    private ItemFinder() {}
}
