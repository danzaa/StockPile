package dev.danzaa.stockpile;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;

public final class InventoryStore implements AutoCloseable {
    public record Item(String id, String name, int count, int maxStack) {}
    public record PackedItem(String id, String name, int count, int maxStack, List<String> path) {}
    public record Container(String id, String name, String type, String dimension,
                            List<Integer> position, List<String> members, long lastSeen, List<Item> items,
                            List<PackedItem> packedItems, Boolean packedScanComplete) {
        public Container(String id, String name, String type, String dimension, List<Integer> position,
                         List<String> members, long lastSeen, List<Item> items) {
            this(id, name, type, dimension, position, members, lastSeen, items, null, null);
        }
    }
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final Path file;
    private final Logger logger;
    private final Map<String, World> worlds = new LinkedHashMap<>();
    private final ScheduledExecutorService writer = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "Stockpile save"); t.setDaemon(true); return t;
    });
    private ScheduledFuture<?> pendingSave;
    private long capturedAt;
    private boolean connected;
    private boolean trackingEnabled = true;
    private String minecraftVersion;

    private static final class World {
        final String id;
        String name;
        final Map<String, Container> containers = new LinkedHashMap<>();
        World(String id, String name) { this.id = id; this.name = name; }
    }

    public InventoryStore(Path file, Logger logger) {
        this.file = file;
        this.logger = logger;
        if (!Files.exists(file)) return;
        try {
            if (Files.size(file) > 32 * 1024 * 1024) throw new IOException("Inventory exceeds 32 MB");
            JsonObject root = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            if (root.get("schemaVersion").getAsInt() != 1) throw new IOException("Unsupported inventory schema");
            for (var entry : root.getAsJsonArray("worlds")) {
                JsonObject o = entry.getAsJsonObject();
                World world = new World(o.get("id").getAsString(), o.get("name").getAsString());
                for (var value : o.getAsJsonArray("containers")) {
                    Container c = GSON.fromJson(value, Container.class);
                    if (c.id() == null || c.items() == null || c.position() == null || c.position().size() != 3)
                        throw new IOException("Invalid saved container");
                    world.containers.put(c.id(), c);
                }
                worlds.put(world.id, world);
            }
            capturedAt = root.get("capturedAt").getAsLong();
        } catch (Exception e) {
            worlds.clear();
            logger.error("Could not read Stockpile inventory; preserving the original as a backup", e);
            try { Files.copy(file, file.resolveSibling(file.getFileName() + ".backup-" + System.currentTimeMillis())); }
            catch (IOException backupFailure) { throw new IllegalStateException("Cannot preserve unread inventory", backupFailure); }
        }
    }

    public synchronized void connected(boolean value) { connected = value; }
    public synchronized void tracking(boolean value) { trackingEnabled = value; }
    public synchronized void minecraftVersion(String value) { minecraftVersion = value; }

    public synchronized List<Container> containers(String worldId) {
        World world = worlds.get(worldId);
        return world == null ? List.of() : List.copyOf(world.containers.values());
    }

    public synchronized void put(String worldId, String worldName, Container container) {
        World world = worlds.computeIfAbsent(worldId, id -> new World(id, worldName));
        world.name = worldName;
        List<String> members = container.members() == null ? List.of(container.id()) : container.members();
        world.containers.values().removeIf(old -> !old.id().equals(container.id()) &&
                (members.contains(old.id()) || (old.members() != null && old.members().stream().anyMatch(members::contains))));
        world.containers.put(container.id(), container);
        capturedAt = container.lastSeen();
        queueSave();
    }

    public synchronized boolean forget(String worldId, String containerId) {
        World world = worlds.get(worldId);
        if (world == null || world.containers.remove(containerId) == null) return false;
        capturedAt = System.currentTimeMillis();
        queueSave();
        return true;
    }

    public synchronized String snapshot() {
        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", 1);
        root.addProperty("capturedAt", capturedAt);
        root.addProperty("connected", connected);
        root.addProperty("trackingEnabled", trackingEnabled);
        if (minecraftVersion != null) root.addProperty("minecraftVersion", minecraftVersion);
        JsonArray array = new JsonArray();
        for (World world : worlds.values()) {
            JsonObject w = new JsonObject();
            w.addProperty("id", world.id); w.addProperty("name", world.name);
            w.add("containers", GSON.toJsonTree(new ArrayList<>(world.containers.values())));
            array.add(w);
        }
        root.add("worlds", array);
        return GSON.toJson(root);
    }

    private synchronized void queueSave() {
        if (pendingSave != null) pendingSave.cancel(false);
        pendingSave = writer.schedule(this::saveNow, 750, TimeUnit.MILLISECONDS);
    }

    private void saveNow() {
        String content = snapshot();
        try {
            Files.createDirectories(file.getParent());
            Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(temporary, content, StandardCharsets.UTF_8);
            try { Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException e) { Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING); }
        } catch (IOException e) { logger.error("Could not save Stockpile inventory", e); }
    }

    @Override public void close() {
        synchronized (this) { if (pendingSave != null) pendingSave.cancel(false); }
        writer.execute(this::saveNow);
        writer.shutdown();
        try { if (!writer.awaitTermination(5, TimeUnit.SECONDS)) logger.warn("Stockpile save is still finishing"); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
