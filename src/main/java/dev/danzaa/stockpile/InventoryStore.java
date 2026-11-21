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
    private static final Gson SNAPSHOT_GSON = new GsonBuilder().setPrettyPrinting().serializeNulls().create();
    private final Path file;
    private final Logger logger;
    private final Map<String, World> worlds = new LinkedHashMap<>();
    private final ScheduledExecutorService writer = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "Stockpile save"); t.setDaemon(true); return t;
    });
    private ScheduledFuture<?> pendingSave;
    private long capturedAt;
    private boolean connected;
    private String activeWorldId;
    private String activeDimension;
    private String worldSession;
    private boolean trackingEnabled = true;
    private boolean schematicFunnelAvailable;
    private SchematicFunnel.Settings schematicSettings = SchematicFunnel.Settings.DEFAULT;
    private String teamConnection = "offline";
    private String minecraftVersion;

    private static final class World {
        final String id;
        String name;
        final Map<String, Container> containers = new LinkedHashMap<>();
        PlanningState planning = new PlanningState();
        boolean shared;
        boolean manualTeam;
        boolean live;
        String authority;
        String viewer="local";
        String remoteWorld;
        String schematicChest;
        String schematicChestMark;
        Map<String, Long> schematicMaterials = Map.of();
        java.util.Set<String> excludedLocations=new java.util.LinkedHashSet<>();
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
                if(o.has("planning")) world.planning=GSON.fromJson(o.get("planning"),PlanningState.class);
                world.planning.validate();world.planning.prune();
                world.shared=o.has("shared")&&o.get("shared").getAsBoolean();
                world.manualTeam=o.has("manualTeam")&&o.get("manualTeam").getAsBoolean();
                if(o.has("excludedLocations"))for(var location:o.getAsJsonArray("excludedLocations"))world.excludedLocations.add(location.getAsString());
                world.authority=o.has("authority")?o.get("authority").getAsString():null;
                world.viewer=o.has("viewer")?o.get("viewer").getAsString():"local";
                world.remoteWorld=o.has("remoteWorld")?o.get("remoteWorld").getAsString():null;
                if (o.has("schematicChestMark")) {
                    world.schematicChestMark = o.get("schematicChestMark").getAsString();
                    if (o.has("schematicChest")) {
                        world.schematicChest = o.get("schematicChest").getAsString();
                        world.schematicMaterials = SchematicFunnel.loadRequirements(o.getAsJsonObject("schematicMaterials"));
                    }
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

    public synchronized void connected(boolean value) {
        connected = value;
        if(!value){activeWorldId=null;activeDimension=null;worldSession=null;schematicFunnelAvailable=false;teamConnection="offline";}
    }
    public synchronized void activeDimension(String dimension) { activeDimension = dimension; }
    public synchronized void schematicFunnelAvailable(boolean available) { schematicFunnelAvailable = available; }
    synchronized SchematicFunnel.Settings schematicSettings(SchematicFunnel.SettingsUpdate update) {
        if (update != null) schematicSettings = update.apply(schematicSettings);
        return schematicSettings;
    }
    synchronized boolean schematicChestEnabled() { return schematicSettings.enabled(); }
    public synchronized void teamConnection(String connection) { teamConnection = connection; }

    synchronized boolean canFunnel(String world, String session) {
        return schematicSettings.enabled() && schematicFunnelAvailable && world.equals(activeWorldId) && session.equals(worldSession);
    }

    synchronized void toggleSchematicChest(String worldId, String id) {
        var world = worlds.get(worldId);
        if (!schematicSettings.enabled() || !schematicFunnelAvailable || !worldId.equals(activeWorldId) ||
                world == null || world.shared || world.manualTeam) return;
        if (id.equals(world.schematicChest)) {
            setSchematicChest(world, null);
            return;
        }
        var chest = world.containers.get(id);
        if (chest == null || excluded(world, chest) || !schematicChestKind(chest.type())) return;
        setSchematicChest(world, id);
    }

    private void setSchematicChest(World world, String id) {
        world.schematicChest = id;
        world.schematicChestMark = java.util.UUID.randomUUID().toString();
        world.schematicMaterials = Map.of();
        queueSave();
    }

    synchronized boolean isSchematicChest(String worldId, String id) {
        var world = worlds.get(worldId);
        return world != null && id.equals(world.schematicChest);
    }

    synchronized boolean isSchematicChest(String worldId, String id, String mark) {
        var world = worlds.get(worldId);
        return world != null && id.equals(world.schematicChest) && mark.equals(world.schematicChestMark);
    }

    synchronized boolean recordSchematicFill(SchematicFunnel.Request request, boolean complete) {
        var world = worlds.get(request.worldId());
        if (world == null || !request.destination().equals(world.schematicChest)) return false;
        if (complete && schematicSettings.autoClear()) {
            setSchematicChest(world, null);
            return true;
        }
        if (!request.materials().equals(world.schematicMaterials)) {
            world.schematicMaterials = request.materials();
            queueSave();
        }
        return false;
    }

    record SchematicCheck(String chest, Map<String, Long> materials) {}

    synchronized SchematicCheck schematicCheck(String worldId) {
        if (!schematicSettings.enabled() || !schematicSettings.autoClear() ||
                !schematicFunnelAvailable || !worldId.equals(activeWorldId)) return null;
        var world = worlds.get(worldId);
        return world == null || world.schematicChest == null || world.schematicMaterials.isEmpty() ? null :
                new SchematicCheck(world.schematicChest, world.schematicMaterials);
    }

    synchronized void completeSchematicChest(String worldId) {
        var world = worlds.get(worldId);
        if (world != null && world.schematicChest != null) setSchematicChest(world, null);
    }

    static boolean schematicChestKind(String type) {
        return "chest".equals(type) || "trapped_chest".equals(type);
    }

    synchronized Map<String, Long> funnelClaims(String world, String container) {
        var claims = new LinkedHashMap<String, Long>();
        for (var reservation : worlds.get(world).planning.reservations.values()) {
            if (!reservation.status().equals("active")) continue;
            for (var claim : reservation.claims()) {
                if (!claim.packed() && claim.containerId().equals(container)) claims.merge(claim.itemId(), claim.count(), Long::sum);
            }
        }
        return claims;
    }

    /** Connection identity is live state; it is never restored from a saved snapshot. */
    public synchronized void activeWorld(String id,String name){
        if(id==null||id.isBlank()||name==null||name.isBlank())throw new IllegalArgumentException("Invalid active world");
        World world=worlds.get(id);
        boolean changed=world==null||!name.equals(world.name);
        if(world==null){world=new World(id,name);worlds.put(id,world);}else world.name=name;
        if(!id.equals(activeWorldId))worldSession=java.util.UUID.randomUUID().toString();
        activeWorldId=id;connected=true;
        if(changed)queueSave();
    }
    public synchronized void tracking(boolean value) { trackingEnabled = value; }
    public synchronized void minecraftVersion(String value) { minecraftVersion = value; }

    public synchronized List<Container> containers(String worldId) {
        World world = worlds.get(worldId);
        return world == null ? List.of() : List.copyOf(world.containers.values());
    }

    public synchronized void put(String worldId, String worldName, Container container) {put(worldId,worldName,container,null);}
    public synchronized void put(String worldId, String worldName, Container container,String reason) {
        World world = worlds.computeIfAbsent(worldId, id -> new World(id, worldName));
        world.name = worldName;
        List<String> members = container.members() == null ? List.of(container.id()) : container.members();
        var replaced=world.containers.values().stream().filter(old -> old.id().equals(container.id()) ||
                members.contains(old.id()) || (old.members()!=null && old.members().stream().anyMatch(members::contains))).toList();
        var oldIds=new java.util.HashSet<String>();replaced.forEach(old->oldIds.add(old.id()));
        world.planning.remap(oldIds,container.id());
        world.planning.observe(replaced,List.of(container),container,reason!=null?reason:replaced.isEmpty()?"discovered":replaced.size()>1 || !replaced.getFirst().id().equals(container.id())?"replacement":"observed");
        world.containers.values().removeIf(old->oldIds.contains(old.id()));
        world.containers.put(container.id(), container);
        if (world.schematicChest != null && !world.schematicChest.equals(container.id()) && oldIds.contains(world.schematicChest)) {
            setSchematicChest(world, null);
        }
        capturedAt = container.lastSeen();
        trimHistory();
        queueSave();
    }

    public synchronized boolean forget(String worldId, String containerId) {
        World world = worlds.get(worldId);
        if (world == null) return false;
        Container old=world.containers.remove(containerId);
        if(old==null)return false;
        if (containerId.equals(world.schematicChest)) setSchematicChest(world, null);
        world.planning.observe(List.of(old),List.of(),old,"forgotten");
        capturedAt = System.currentTimeMillis();
        queueSave();
        return true;
    }

    public String snapshot() { return snapshot(false); }

    private synchronized String snapshot(boolean saving) {
        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", 1);
        root.addProperty("capturedAt", capturedAt);
        root.addProperty("connected", connected);
        root.add("activeWorldId",activeWorldId==null?com.google.gson.JsonNull.INSTANCE:new com.google.gson.JsonPrimitive(activeWorldId));
        root.addProperty("activeDimension", activeDimension);
        root.add("worldSession",worldSession==null?com.google.gson.JsonNull.INSTANCE:new com.google.gson.JsonPrimitive(worldSession));
        root.addProperty("trackingEnabled", trackingEnabled);
        if (schematicFunnelAvailable) root.addProperty("schematicFunnelAvailable", true);
        root.addProperty("schematicChestEnabled", schematicSettings.enabled());
        root.addProperty("schematicChestAutoClear", schematicSettings.autoClear());
        root.addProperty("teamConnection", teamConnection);
        if (minecraftVersion != null) root.addProperty("minecraftVersion", minecraftVersion);
        JsonArray array = new JsonArray();
        for (World world : worlds.values()) {
            JsonObject w = new JsonObject();
            w.addProperty("id", world.id); w.addProperty("name", world.name);
            w.add("planning",GSON.toJsonTree(world.planning));
            w.add("excludedLocations",GSON.toJsonTree(world.excludedLocations));
            w.add("excludedContainerIds",GSON.toJsonTree(world.containers.values().stream().filter(c->excluded(world,c)).map(Container::id).toList()));
            w.addProperty("manualTeam",world.manualTeam);w.addProperty("shared",world.shared);w.addProperty("live",world.live);
            if(world.authority!=null)w.addProperty("authority",world.authority);
            if (world.schematicChestMark != null) w.addProperty("schematicChestMark", world.schematicChestMark);
            if (world.schematicChest != null) w.addProperty("schematicChest", world.schematicChest);
            if (saving && !world.schematicMaterials.isEmpty()) w.add("schematicMaterials", GSON.toJsonTree(world.schematicMaterials));
            w.addProperty("viewer",world.viewer);if(world.remoteWorld!=null)w.addProperty("remoteWorld",world.remoteWorld);
            w.add("containers", GSON.toJsonTree(new ArrayList<>(world.containers.values())));
            array.add(w);
        }
        root.add("worlds", array);
        return SNAPSHOT_GSON.toJson(root);
    }

    public synchronized String importTeam(JsonObject packet) {
        if(!packet.get("format").getAsString().equals("stockpile-shared-storage")||packet.get("version").getAsInt()!=1)throw new IllegalArgumentException();
        String code=packet.get("teamId").getAsString(),server=packet.get("serverId").getAsString(),name=packet.get("name").getAsString();
        if(!code.matches("[a-zA-Z0-9_-]{1,64}")||server.isBlank()||server.length()>100||name.isBlank()||name.length()>100)throw new IllegalArgumentException();
        String id="file-team:"+java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(server.getBytes(StandardCharsets.UTF_8))+":"+code;
        if(!worlds.containsKey(id)&&worlds.values().stream().filter(w->w.manualTeam).count()>=10)throw new IllegalArgumentException("Manual team workspace limit reached (10)");
        var raw=packet.getAsJsonArray("containers");if(raw==null||raw.size()>1000)throw new IllegalArgumentException();
        List<Container> incoming=raw.asList().stream().map(SharedSnapshots::container).toList();
        World w=worlds.computeIfAbsent(id,key->new World(id,"Team snapshot · "+name));w.manualTeam=true;
        for(var c:incoming){if(w.containers.values().stream().anyMatch(old->old.lastSeen()>c.lastSeen()&&old.members()!=null&&old.members().stream().anyMatch(c.members()::contains)))continue;put(id,"Team snapshot · "+name,c,"imported");}
        queueSave();return id;
    }
    public synchronized boolean shared(String worldId) { var w=worlds.get(worldId);return w!=null&&w.shared; }
    private static List<String> anchors(Container c){return c.members()==null||c.members().isEmpty()?List.of(c.id()):c.members();}
    private static boolean excluded(World w,Container c){return anchors(c).stream().anyMatch(w.excludedLocations::contains);}
    public synchronized Container getContainer(String worldId, String containerId) {
        World world = worlds.get(worldId);
        return world == null ? null : world.containers.get(containerId);
    }

    public synchronized boolean isLiveSharedWorkspace(String id, String activeWorld) {
        World world = worlds.get(id);
        return activeWorld != null && world != null && world.shared && world.live && activeWorld.equals(world.authority);
    }

    public synchronized boolean excluded(String worldId,String containerId){var w=worlds.get(worldId);var c=w==null?null:w.containers.get(containerId);return c!=null&&excluded(w,c);}
    public synchronized boolean exclude(String worldId,String containerId,boolean value){
        var w=worlds.get(worldId);var c=w==null?null:w.containers.get(containerId);if(c==null)return false;
        if(value)w.excludedLocations.addAll(anchors(c));else w.excludedLocations.removeAll(anchors(c));
        queueSave();return true;
    }
    public synchronized boolean excludedClaims(JsonObject request){
        if(!"reserve".equals(request.get("action").getAsString()))return false;
        String world=request.get("worldId").getAsString();
        return PlanningState.claims(request.getAsJsonArray("claims")).stream().anyMatch(c->excluded(world,c.containerId()));
    }
    public synchronized int releaseOwned(String worldId,String owner){
        var w=worlds.get(worldId);if(w==null)return 0;
        int before=w.planning.reservations.size();w.planning.reservations.values().removeIf(r->java.util.Objects.equals(r.owner(),owner));
        int removed=before-w.planning.reservations.size();if(removed>0){w.planning.revision++;queueSave();}return removed;
    }
    public synchronized PlanningState.Result reservation(JsonObject data,String owner) {
        String worldId=data.get("worldId").getAsString();
        World w=worlds.get(worldId);
        if(w==null)return new PlanningState.Result(404,"Saved world not found.");
        String action=data.get("action").getAsString(), project=data.get("projectId").getAsString();
        long revision=data.get("revision").getAsBigDecimal().longValueExact();
        PlanningState.Result result=switch(action) {
            case "reserve" -> w.planning.reserve(project,data.get("name").getAsString(),owner,revision,PlanningState.claims(data.getAsJsonArray("claims")),w.containers.values().stream().filter(c->!excluded(w,c)).toList());
            case "release", "complete" -> w.planning.release(project,owner,action.equals("complete"),revision);
            default -> new PlanningState.Result(400,"Unknown reservation action.");
        };
        if(result.status()==200)queueSave();
        return result;
    }
    /** Replace an explicitly shared workspace; never merge personal Ender Chest observations. */
    public synchronized void sharedSnapshot(String id,String name,String authority,List<Container> containers,PlanningState planning) {
        sharedSnapshot(id,name,authority,containers,planning,"local",id);
    }
    public synchronized void sharedSnapshot(String id,String name,String authority,List<Container> containers,PlanningState planning,String viewer,String remoteWorld) {
        if(!id.startsWith("shared:") || containers.size()>256)throw new IllegalArgumentException("Invalid team snapshot");
        World w=worlds.computeIfAbsent(id,key->new World(id,name));
        w.name=name;w.shared=true;w.live=true;w.authority=authority;w.viewer=viewer;w.remoteWorld=remoteWorld;
        w.containers.clear();
        for(var c:containers)if(!ItemFinder.ender(c))w.containers.put(c.id(),c);
        w.planning=planning;w.planning.prune();capturedAt=System.currentTimeMillis();
        var personal=worlds.get(authority);
        if(personal!=null){var moved=personal.containers.values().stream().filter(c->externalShared(authority,c)).toList();
            for(var c:moved){personal.containers.remove(c.id());personal.planning.observe(List.of(c),List.of(),c,"replacement");}}
        trimHistory();queueSave();
    }
    public synchronized boolean externalShared(String personal,Container container){
        var positions=container.members()==null?List.of(container.id()):container.members();
        return worlds.values().stream().filter(w->w.shared&&java.util.Objects.equals(w.authority,personal))
                .flatMap(w->w.containers.values().stream()).anyMatch(c->c.id().equals(container.id())||c.members()!=null&&c.members().stream().anyMatch(positions::contains));
    }
    private void trimHistory(){
        int total=worlds.values().stream().flatMap(w->w.planning.history.stream()).mapToInt(e->Math.max(1,e.changes().size())).sum();
        while(total>40000){World oldest=worlds.values().stream().filter(w->!w.planning.history.isEmpty()).min(java.util.Comparator.comparingLong(w->w.planning.history.getFirst().at())).orElse(null);
            if(oldest==null)break;total-=Math.max(1,oldest.planning.history.removeFirst().changes().size());}
    }
    public synchronized void sharedLive(String id,boolean live){var w=worlds.get(id);if(w!=null&&w.shared)w.live=live;}
    public synchronized void sharedOffline() { worlds.values().forEach(w->{if(w.shared)w.live=false;}); }
    public synchronized void revokeShared(java.util.Set<String> allowed,String authority) {
        boolean removed=worlds.values().removeIf(w->w.shared&&java.util.Objects.equals(w.authority,authority)&&!allowed.contains(w.id));
        if(removed)queueSave();
    }
    public synchronized JsonObject worldSnapshot(String id) {
        var root=JsonParser.parseString(snapshot()).getAsJsonObject();
        for(var value:root.getAsJsonArray("worlds"))if(value.getAsJsonObject().get("id").getAsString().equals(id))return value.getAsJsonObject();
        return null;
    }

    private synchronized void queueSave() {
        if (pendingSave != null) pendingSave.cancel(false);
        pendingSave = writer.schedule(this::saveNow, 750, TimeUnit.MILLISECONDS);
    }

    private void saveNow() {
        String content = snapshot(true);
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
