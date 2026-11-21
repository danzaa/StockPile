package dev.danzaa.stockpile;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.function.BiPredicate;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.Identifier;

/** Optional Where Is It search provider; no hard link to another mod or its private memory banks. */
public final class ChestTrackerFindCompat {
    private static final String PREFIX = "red.jackf.whereisit.";
    private static boolean registered;
    private static boolean failed;

    @SuppressWarnings("unchecked")
    public static void register(StockpileClient owner) {
        if (registered || !FabricLoader.getInstance().isModLoaded("chesttracker")) return;
        try {
            Class<?> invoker = Class.forName(PREFIX + "client.api.events.SearchInvoker");
            Object listener = provider(invoker, owner::findMemories, (c, pos) -> {
                var client = net.minecraft.client.Minecraft.getInstance();
                return client.level != null && FindHighlight.present(client, c, pos);
            });
            var event = (Event<Object>) invoker.getField("EVENT").get(null);
            var phase = Identifier.fromNamespaceAndPath("stockpile", "remembered_items");
            // Let Chest Tracker's richer results replace ours at overlapping positions, not vice versa.
            event.addPhaseOrdering(phase, Event.DEFAULT_PHASE);
            event.register(phase, listener);
            registered = true;
            StockpileClient.LOGGER.info("Stockpile remembered item search connected to Chest Tracker / Where Is It");
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            StockpileClient.LOGGER.warn("Chest Tracker search API is incompatible; Stockpile's standalone find remains available", e);
        }
    }
    @SuppressWarnings("unchecked")
    static Object provider(Class<?> invoker, Supplier<List<InventoryStore.Container>> memories,
                           BiPredicate<InventoryStore.Container, BlockPos> present) throws ReflectiveOperationException {
        Class<?> requestType = Class.forName(PREFIX + "api.SearchRequest", true, invoker.getClassLoader());
        Class<?> resultType = Class.forName(PREFIX + "api.SearchResult", true, invoker.getClassLoader());
        var toTag = requestType.getMethod("toTag");
        var builder = resultType.getMethod("builder", BlockPos.class);
        var builderType = Class.forName(PREFIX + "api.SearchResult$Builder", true, invoker.getClassLoader());
        var otherPositions = builderType.getMethod("otherPositions", Collection.class);
        var build = builderType.getMethod("build");
        Object listener = Proxy.newProxyInstance(invoker.getClassLoader(), new Class<?>[]{invoker}, (proxy, method, args) -> {
            if (method.getDeclaringClass() == Object.class) return switch (method.getName()) {
                case "toString" -> "Stockpile remembered-item search";
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                default -> null;
            };
            if (failed || !method.getName().equals("search")) return false;
            try {
                String id = simpleItemId((ListTag) toTag.invoke(args[0]));
                // Saved observations contain item IDs, not components. Leave richer queries to Chest Tracker.
                if (id == null) return false;
                List<Object> results = new ArrayList<>();
                for (var c : memories.get()) {
                    if (ItemFinder.quantity(c, id, true) == 0) continue;
                    var positions = FindHighlight.positions(c);
                    var loaded = positions.stream().filter(p -> present.test(c, p)).toList();
                    if (loaded.isEmpty()) continue;
                    Object b = builder.invoke(null, loaded.getFirst());
                    otherPositions.invoke(b, loaded.stream().skip(1).toList());
                    results.add(build.invoke(b));
                    if (results.size() >= FindHighlight.LIMIT) break;
                }
                if (results.isEmpty()) return false;
                ((Consumer<Collection<Object>>) args[1]).accept(results);
                return true;
            } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
                failed = true;
                StockpileClient.LOGGER.warn("Stockpile's optional Chest Tracker search bridge is unavailable; standalone find still works", e);
                return false;
            }
        });
        return listener;
    }
    public static String simpleItemId(ListTag criteria) {
        if (criteria.size() != 1 || !(criteria.get(0) instanceof CompoundTag tag) ||
                !tag.getString("type").orElse("").equals("whereisit:item")) return null;
        String id = tag.getString("item").orElse("");
        return id.length() <= 256 && id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+") ? id : null;
    }
    private ChestTrackerFindCompat() {}
}
