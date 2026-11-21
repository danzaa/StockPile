package dev.danzaa.stockpile;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.block.state.properties.ChestType;

final class SchematicFunnel {
    private static final int MAX_SOURCES = 128;
    private static final int MAX_MATERIALS = 1000;

    record Settings(boolean enabled, boolean autoClear) {
        static final Settings DEFAULT = new Settings(true, true);

        JsonObject json() {
            var data = new JsonObject();
            data.addProperty("enabled", enabled);
            data.addProperty("autoClear", autoClear);
            return data;
        }
    }

    record SettingsUpdate(Boolean enabled, Boolean autoClear) {
        static SettingsUpdate parse(JsonObject data) {
            var enabled = flag(data, "enabled");
            var autoClear = flag(data, "autoClear");
            if (enabled == null && autoClear == null) throw new IllegalArgumentException();
            return new SettingsUpdate(enabled, autoClear);
        }

        private static Boolean flag(JsonObject data, String key) {
            var value = data.get(key);
            if (value == null) return null;
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) throw new IllegalArgumentException();
            return value.getAsBoolean();
        }

        Settings apply(Settings current) {
            return new Settings(enabled == null ? current.enabled() : enabled,
                    autoClear == null ? current.autoClear() : autoClear);
        }
    }

    record Request(String worldId, String session, String destination, String destinationMark,
                   List<String> sources, Map<String, Long> materials) {
        static Request parse(JsonObject data) {
            String world = DashboardServer.requiredString(data, "worldId");
            String session = DashboardServer.requiredString(data, "worldSession");
            String destination = DashboardServer.requiredString(data, "destination");
            String mark = data.has("destinationMark") ? DashboardServer.requiredString(data, "destinationMark") : null;
            var rawSources = data.getAsJsonArray("sources");
            var rawMaterials = data.getAsJsonArray("materials");
            if ((mark != null && (mark.isBlank() || mark.length() > 80)) || rawSources.size() > MAX_SOURCES ||
                    rawMaterials.isEmpty() || rawMaterials.size() > MAX_MATERIALS) throw new IllegalArgumentException();
            var sources = new LinkedHashSet<String>();
            for (var value : rawSources) {
                if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) throw new IllegalArgumentException();
                String source = value.getAsString();
                if (source.isBlank() || source.length() > 500 || source.equals(destination) || !sources.add(source)) {
                    throw new IllegalArgumentException();
                }
            }
            var materials = new LinkedHashMap<String, Long>();
            for (var value : rawMaterials) {
                var material = value.getAsJsonObject();
                String id = DashboardServer.requiredString(material, "id");
                var count = material.getAsJsonPrimitive("count");
                if (count == null || !count.isNumber()) throw new IllegalArgumentException();
                long quantity = count.getAsBigDecimal().longValueExact();
                if (!validMaterial(id, quantity) || materials.putIfAbsent(id, quantity) != null) {
                    throw new IllegalArgumentException();
                }
            }
            return new Request(world, session, destination, mark, List.copyOf(sources),
                    java.util.Collections.unmodifiableMap(materials));
        }
    }

    record Result(int status, String message, long moved, Map<String, Long> missing, int skipped, boolean autoCleared) {
        static Result unavailable(int status, String message) {
            return new Result(status, message, 0, Map.of(), 0, false);
        }
        JsonObject json() {
            var result = new JsonObject();
            result.addProperty("message", message);
            result.addProperty("moved", moved);
            result.addProperty("skipped", skipped);
            result.addProperty("autoCleared", autoCleared);
            var items = new JsonArray();
            missing.forEach((id, count) -> {
                var item = new JsonObject();
                item.addProperty("id", id);
                item.addProperty("count", count);
                items.add(item);
            });
            result.add("missing", items);
            return result;
        }
    }

    private record Storage(InventoryStore.Container saved, Container slots) {}
    private record SlotChange(Container slots, int index, ItemStack before, ItemStack after) {}

    private static boolean validMaterial(String id, long quantity) {
        return id.length() <= 256 && id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+") &&
                quantity > 0 && quantity <= 999_000_000_000L;
    }

    static Map<String, Long> loadRequirements(JsonObject data) {
        if (data == null || data.size() > MAX_MATERIALS) return Map.of();
        var items = new LinkedHashMap<String, Long>();
        try {
            for (var entry : data.entrySet()) {
                var value = entry.getValue().getAsJsonPrimitive();
                if (!value.isNumber()) return Map.of();
                long quantity = value.getAsBigDecimal().longValueExact();
                if (!validMaterial(entry.getKey(), quantity)) return Map.of();
                items.put(entry.getKey(), quantity);
            }
        } catch (RuntimeException invalid) {
            return Map.of();
        }
        return Map.copyOf(items);
    }

    static Result fill(ServerLevel level, InventoryStore store, String worldName, Request request) {
        // The game server owns the slots. Holding the store lock also keeps browser reservations
        // and exclusions from changing between the stock check and the refreshed observations.
        synchronized (store) {
            if (!store.canFunnel(request.worldId(), request.session())) {
                return Result.unavailable(409, "The single-player session changed. Refresh the planner and try again.");
            }
            if (request.destinationMark() != null &&
                    !store.isSchematicChest(request.worldId(), request.destination(), request.destinationMark())) {
                return Result.unavailable(409, "The checked build chest changed. Review the build again.");
            }
            if (request.sources().isEmpty() && !store.isSchematicChest(request.worldId(), request.destination())) {
                return Result.unavailable(409, "Check Build chest in Minecraft first or select a source chest.");
            }
            var destination = resolve(level, store, request.worldId(), request.destination(), true);
            if (destination == null) {
                return Result.unavailable(409, "Open the destination chest and check that it is loaded, included and unlocked.");
            }
            var sources = new ArrayList<Storage>();
            var usedLocations = new LinkedHashSet<>(destination.saved.members());
            for (String id : request.sources()) {
                var source = resolve(level, store, request.worldId(), id, false);
                if (source != null && java.util.Collections.disjoint(source.saved.members(), usedLocations)) {
                    sources.add(source);
                    usedLocations.addAll(source.saved.members());
                }
            }
            var requirements = new LinkedHashMap<>(request.materials());
            var originalTargetItems = contents(destination.slots);
            var targetItems = new ArrayList<>(originalTargetItems);
            subtractStock(requirements, targetItems, store.funnelClaims(request.worldId(), destination.saved.id()));
            int skipped = request.sources().size() - sources.size();
            long moved = 0;
            var changes = new ArrayList<SlotChange>();
            for (var source : sources) {
                var sourceItems = contents(source.slots);
                var claims = store.funnelClaims(request.worldId(), source.saved.id());
                var stock = stock(sourceItems);
                for (int slot = 0; slot < sourceItems.size(); slot++) {
                    var item = sourceItems.get(slot);
                    if (!loose(item) || !source.slots.canTakeItem(destination.slots, slot, item)) continue;
                    String id = itemId(item);
                    long free = Math.max(0, stock.get(id) - claims.getOrDefault(id, 0L));
                    int wanted = (int) Math.min(item.getCount(), Math.min(free, requirements.getOrDefault(id, 0L)));
                    if (wanted == 0) continue;
                    int inserted = stageInsert(destination.slots, targetItems, item, wanted);
                    if (inserted == 0) continue;
                    changes.add(new SlotChange(source.slots, slot, item, item.copyWithCount(item.getCount() - inserted)));
                    requirements.merge(id, -(long) inserted, Long::sum);
                    stock.merge(id, -(long) inserted, Long::sum);
                    moved += inserted;
                }
            }
            for (int slot = 0; slot < targetItems.size(); slot++) {
                var before = originalTargetItems.get(slot);
                var after = targetItems.get(slot);
                if (!ItemStack.matches(before, after)) changes.add(new SlotChange(destination.slots, slot, before, after));
            }
            commit(changes);
            for (var source : sources) refresh(store, request.worldId(), worldName, source);
            refresh(store, request.worldId(), worldName, destination);
            requirements.values().removeIf(value -> value == 0);
            boolean cleared = store.recordSchematicFill(request, requirements.isEmpty());
            return new Result(200, requirements.isEmpty() ? "The schematic chest has all listed materials."
                    : "Some materials are still needed. Check loose stock, reservations and space in the chest.",
                    moved, requirements, skipped, cleared);
        }
    }

    static void checkCompletion(ServerLevel level, InventoryStore store, String world) {
        synchronized (store) {
            var pending = store.schematicCheck(world);
            if (pending == null) return;
            var target = resolve(level, store, world, pending.chest(), true);
            if (target == null) return;
            var missing = new LinkedHashMap<>(pending.materials());
            subtractStock(missing, contents(target.slots), store.funnelClaims(world, pending.chest()));
            if (missing.values().stream().allMatch(count -> count == 0)) store.completeSchematicChest(world);
        }
    }

    private static void commit(List<SlotChange> changes) {
        var touched = new LinkedHashSet<Container>();
        int applied = 0;
        try {
            for (var change : changes) {
                applied++;
                change.slots.setItem(change.index, change.after);
                touched.add(change.slots);
            }
            touched.forEach(Container::setChanged);
        } catch (RuntimeException failure) {
            for (int i = applied - 1; i >= 0; i--) {
                var change = changes.get(i);
                try {
                    change.slots.setItem(change.index, change.before);
                    change.slots.setChanged();
                } catch (RuntimeException rollbackFailure) {
                    failure.addSuppressed(rollbackFailure);
                }
            }
            throw failure;
        }
    }

    private static Storage resolve(ServerLevel level, InventoryStore store, String world, String id, boolean destination) {
        var saved = store.getContainer(world, id);
        if (saved == null || saved.members() == null || !saved.dimension().equals(level.dimension().identifier().toString()) ||
                store.excluded(world, id) || store.externalShared(world, saved)) return null;
        var pos = new BlockPos(saved.position().get(0), saved.position().get(1), saved.position().get(2));
        if (!level.hasChunkAt(pos)) return null;
        var state = level.getBlockState(pos);
        if (!BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath().equals(saved.type())) return null;
        var block = level.getBlockEntity(pos);
        if (!accessible(block)) return null;
        Container slots;
        var members = new LinkedHashSet<String>();
        members.add(location(saved.dimension(), pos));
        if (block instanceof ChestBlockEntity && state.getBlock() instanceof ChestBlock chest) {
            if (state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
                var other = ChestBlock.getConnectedBlockPos(pos, state);
                if (!level.hasChunkAt(other) || !accessible(level.getBlockEntity(other))) return null;
                members.add(location(saved.dimension(), other));
            }
            slots = ChestBlock.getContainer(chest, state, level, pos, false);
        } else if (!destination && (block instanceof BarrelBlockEntity || block instanceof ShulkerBoxBlockEntity)) {
            slots = (Container) block;
        } else {
            return null;
        }
        return slots != null && slots.getContainerSize() == members.size() * 27 &&
                members.equals(new LinkedHashSet<>(saved.members())) ? new Storage(saved, slots) : null;
    }

    private static String location(String dimension, BlockPos pos) {
        return dimension + ":" + pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    private static boolean accessible(net.minecraft.world.level.block.entity.BlockEntity entity) {
        return entity instanceof RandomizableContainerBlockEntity storage && !storage.isLocked() && storage.getLootTable() == null;
    }

    private static List<ItemStack> contents(Container container) {
        var items = new ArrayList<ItemStack>(container.getContainerSize());
        for (int slot = 0; slot < container.getContainerSize(); slot++) items.add(container.getItem(slot).copy());
        return items;
    }

    private static boolean loose(ItemStack item) {
        return !item.isEmpty() && ItemContainerContents.EMPTY.equals(
                item.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY));
    }

    private static String itemId(ItemStack item) {
        return BuiltInRegistries.ITEM.getKey(item.getItem()).toString();
    }

    private static Map<String, Long> stock(List<ItemStack> items) {
        var stock = new LinkedHashMap<String, Long>();
        for (var item : items) if (loose(item)) stock.merge(itemId(item), (long) item.getCount(), Long::sum);
        return stock;
    }

    private static void subtractStock(Map<String, Long> requirements, List<ItemStack> items, Map<String, Long> claims) {
        var stock = stock(items);
        requirements.replaceAll((id, needed) ->
                Math.max(0, needed - Math.max(0, stock.getOrDefault(id, 0L) - claims.getOrDefault(id, 0L))));
    }

    private static int stageInsert(Container target, List<ItemStack> slots, ItemStack item, int wanted) {
        int remaining = wanted;
        for (boolean empty : new boolean[]{false, true}) {
            for (int slot = 0; slot < slots.size() && remaining > 0; slot++) {
                var current = slots.get(slot);
                if (current.isEmpty() != empty || (!empty && !ItemStack.isSameItemSameComponents(current, item)) ||
                        !target.canPlaceItem(slot, item)) continue;
                int max = Math.min(item.getMaxStackSize(), target.getMaxStackSize(item));
                int count = Math.min(remaining, max - current.getCount());
                if (count <= 0) continue;
                slots.set(slot, item.copyWithCount(current.getCount() + count));
                remaining -= count;
            }
        }
        return wanted - remaining;
    }

    private static void refresh(InventoryStore store, String world, String name, Storage storage) {
        var scanner = new ItemStackScanner();
        for (int slot = 0; slot < storage.slots.getContainerSize(); slot++) scanner.add(storage.slots.getItem(slot), slot);
        var scan = scanner.result();
        var saved = storage.saved;
        store.put(world, name, new InventoryStore.Container(saved.id(), saved.name(), saved.type(), saved.dimension(),
                saved.position(), saved.members(), System.currentTimeMillis(), scan.items(), scan.packedItems(), scan.complete()), "transfer");
    }
}
