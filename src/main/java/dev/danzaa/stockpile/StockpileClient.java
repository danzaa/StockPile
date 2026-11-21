package dev.danzaa.stockpile;

import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import java.net.URI;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.EnderChestBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.storage.LevelResource;
import com.mojang.blaze3d.platform.InputConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class StockpileClient implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("stockpile");
    public static StockpileClient INSTANCE;
    private InventoryStore store;
    private final FindHighlight findHighlight = new FindHighlight();
    private final SessionTerrain sessionTerrain = new SessionTerrain();
    private DashboardServer dashboard;
    private SharedStorageClient sharedStorage;
    private StockpileConfig config;
    private Path configPath;
    private String registryJson = "{}";
    private String lastShortcutSignature;
    private KeyMapping open;
    private Candidate pending;
    private Candidate active;
    private int activeMenu = -1;
    private String worldId;
    private String worldName;
    private CompletableFuture<Void> schematicCheck;
    private boolean wasConnected;
    private boolean settingsRequested;
    private net.minecraft.client.gui.screens.Screen settingsParent;
    private int ticks;
    private String lastCapturedId;
    private List<InventoryStore.Item> lastItems = List.of();
    private List<InventoryStore.PackedItem> lastPackedItems = List.of();
    private final Map<String, Candidate> placedShulkers = new LinkedHashMap<>();
    private long lastStoredAt;
    private record Candidate(String id, String type, String dimension, BlockPos pos,
                             List<String> members, int expectedSlots, long expiresAt) {}

    @Override public void onInitializeClient() {
        INSTANCE = this;
        configPath = FabricLoader.getInstance().getConfigDir().resolve("stockpile/settings.json");
        config = StockpileConfig.load(configPath, LOGGER);
        store = new InventoryStore(FabricLoader.getInstance().getConfigDir().resolve("stockpile/inventory.json"), LOGGER);
        store.minecraftVersion(FabricLoader.getInstance().getModContainer("minecraft").orElseThrow().getMetadata().getVersion().getFriendlyString());
        store.tracking(config.trackingEnabled);
        store.schematicSettings(new SchematicFunnel.SettingsUpdate(config.schematicChestEnabled, config.schematicChestAutoClear));
        sharedStorage=new SharedStorageClient(store,()->{var client=Minecraft.getInstance();if(client.player==null||client.level==null)return null;ensureWorld(client);return worldId;});
        sharedStorage.register();
        open = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.stockpile.open", InputConstants.KEY_B,
                KeyMapping.Category.register(Identifier.fromNamespaceAndPath("stockpile", "main"))));
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
            try {
                JsonObject registry = new JsonObject();
                for (var block : BuiltInRegistries.BLOCK) {
                    String key = BuiltInRegistries.BLOCK.getKey(block).toString();
                    var item = block.asItem();
                    registry.add(key, item == Items.AIR ? JsonNull.INSTANCE : new com.google.gson.JsonPrimitive(BuiltInRegistries.ITEM.getKey(item).toString()));
                }
                registryJson = registry.toString();
                ensureDashboard();
                ChestTrackerFindCompat.register(this);
                LOGGER.info("Stockpile dashboard is ready. Use /stockpile start for the link.");
            } catch (Exception e) { LOGGER.error("Could not start Stockpile dashboard", e); }
        });
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            if (dashboard != null) dashboard.close();
            store.close();
        });
        ClientPlayConnectionEvents.JOIN.register((handler,sender,client)->{
            resetWorld();
            ensureWorld(client);
            if (client.level != null) store.activeDimension(client.level.dimension().identifier().toString());
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> resetWorld());
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) -> StockpileCommands.register(dispatcher,
                () -> openSettings(Minecraft.getInstance().gui.screen()),
                () -> { setTracking(true); showDashboardLink(true); },
                () -> { setTracking(false); if (Minecraft.getInstance().player != null) Minecraft.getInstance().player.sendSystemMessage(StockpileMessages.paused()); },
                () -> showDashboardLink(false), this::openDashboard, this::findItem, () -> {
                    findHighlight.clear();
                    if (Minecraft.getInstance().player != null) Minecraft.getInstance().player.sendSystemMessage(Component.literal("[Stockpile] Find highlight cleared."));
                }, () -> openBrowser("&view=sharing")));
        ChestTrackerCompat.register(this);
        ChestScreenControls.register(this);
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            Minecraft client = Minecraft.getInstance();
            if (!config.trackingEnabled || level != client.level || player != client.player || player.isShiftKeyDown()) return InteractionResult.PASS;
            ensureWorld(client);
            BlockPos clicked = hit.getBlockPos().immutable();
            var entity = level.getBlockEntity(clicked);
            if (!(entity instanceof ChestBlockEntity || entity instanceof BarrelBlockEntity ||
                    entity instanceof EnderChestBlockEntity || entity instanceof ShulkerBoxBlockEntity)) {
                pending = null; return InteractionResult.PASS;
            }
            String dimension = level.dimension().identifier().toString();
            var state = level.getBlockState(clicked);
            List<BlockPos> parts = new ArrayList<>(); parts.add(clicked);
            if (state.getBlock() instanceof ChestBlock && state.hasProperty(ChestBlock.TYPE) && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE)
                parts.add(ChestBlock.getConnectedBlockPos(clicked, state));
            parts.sort(Comparator.comparingInt((BlockPos p) -> p.getX()).thenComparingInt(p -> p.getY()).thenComparingInt(p -> p.getZ()));
            List<String> members = parts.stream().map(p -> dimension + ":" + p.getX() + "," + p.getY() + "," + p.getZ()).toList();
            String id = String.join("|", members);
            if (entity instanceof EnderChestBlockEntity) {
                id = "ender:" + player.getUUID(); members = List.of(id);
            }
            String type = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
            pending = new Candidate(id, type, dimension, parts.getFirst(), members, parts.size() * 27, System.currentTimeMillis() + 2500);
            return InteractionResult.PASS;
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (settingsRequested) {
                settingsRequested = false;
                var parent = settingsParent;
                settingsParent = null;
                client.gui.setScreen(new StockpileSettingsScreen(parent, this));
            }
            ticks++;
            findHighlight.tick(client);
            if(ticks%20==0)sharedStorage.tick();
            boolean connected = client.player != null && client.level != null;
            store.connected(connected);
            if (!connected) { if(worldId!=null||wasConnected)resetWorld(); }
            else {
                ensureWorld(client);
                store.activeDimension(client.level.dimension().identifier().toString());
                var singleplayer = client.getSingleplayerServer();
                store.schematicFunnelAvailable(singleplayer != null && !singleplayer.isPublished());
                if (ticks % 20 == 0 && singleplayer != null && !singleplayer.isPublished() && worldId != null &&
                        store.schematicCheck(worldId) != null && (schematicCheck == null || schematicCheck.isDone())) {
                    String checkedWorld = worldId;
                    var player = client.player.getUUID();
                    schematicCheck = singleplayer.submit(() -> {
                        var owner = singleplayer.getPlayerList().getPlayer(player);
                        if (owner != null && !singleplayer.isPublished()) SchematicFunnel.checkCompletion(owner.level(), store, checkedWorld);
                    });
                }
                if (ticks % 10 == 0) {
                    var connection = client.getConnection();
                    boolean hasCommands = connection != null && connection.getCommands().getRoot().getChild("stockpileteam") != null;
                    store.teamConnection(hasCommands ? "operator" : ClientPlayNetworking.canSend(SharedPayload.TYPE) ? "server" : "client-only");
                }
                if (worldId != null) sessionTerrain.tick(worldId, client.level, client.player.blockPosition());
                if (client.player.containerMenu == client.player.inventoryMenu) { active = null; activeMenu = -1; }
                else if (config.trackingEnabled && ticks % 5 == 0 && active != null && client.player.containerMenu.containerId == activeMenu)
                    capture(client, client.player.containerMenu);
                if (config.trackingEnabled && ticks % 20 == 0) {
                    String dimension = client.level.dimension().identifier().toString();
                    placedShulkers.entrySet().removeIf(entry -> {
                        Candidate candidate = entry.getValue();
                        if (!candidate.dimension().equals(dimension) || !client.level.hasChunkAt(candidate.pos())) return false;
                        if (client.level.getBlockState(candidate.pos()).getBlock() instanceof ShulkerBoxBlock) return false;
                        store.forget(worldId, candidate.id());
                        return true;
                    });
                }
            }
            String shortcutSignature = open.saveString() + ":" + config.keybindEnabled;
            if (connected && config.showStartupMessage && (!wasConnected ||
                    (lastShortcutSignature != null && !shortcutSignature.equals(lastShortcutSignature))))
                client.player.sendSystemMessage(StockpileMessages.startup(config.trackingEnabled,
                        config.keybindEnabled && !open.isUnbound(), open.getTranslatedKeyMessage()));
            lastShortcutSignature = shortcutSignature;
            wasConnected = connected;
            while (open.consumeClick()) if (config.keybindEnabled && !open.isUnbound()) showDashboardLink(true);
        });
    }

    private void resetWorld(){
        store.connected(false);
        findHighlight.clear();sharedStorage.disconnect();
        sessionTerrain.resetSession();
        settingsRequested=false;settingsParent=null;
        pending=null;active=null;activeMenu=-1;worldId=null;worldName=null;wasConnected=false;
        schematicCheck = null;
        lastCapturedId=null;lastItems=List.of();lastPackedItems=List.of();lastStoredAt=0;
        placedShulkers.clear();
    }

    private void ensureWorld(Minecraft client) {
        if (worldId != null || client.player == null || client.level == null || client.getConnection()==null) return;
        String source;
        if (client.getSingleplayerServer() != null) {
            var server = client.getSingleplayerServer();
            source = "local:" + server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
            worldName = server.getWorldData().getLevelName();
        } else if (client.getCurrentServer() != null) {
            source = "server:" + client.getCurrentServer().ip.toLowerCase(Locale.ROOT);
            worldName = client.getCurrentServer().name;
        } else {
            source = "connection:" + client.getConnection().getConnection().getRemoteAddress();
            worldName = "Multiplayer world";
        }
        if(worldName==null||worldName.isBlank())worldName=client.getSingleplayerServer()!=null?"Singleplayer world":"Multiplayer world";
        try {
            worldId = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest((source + ":" + client.player.getUUID()).getBytes(StandardCharsets.UTF_8))).substring(0, 24);
            store.activeWorld(worldId,worldName);
            placedShulkers.clear();
            for (var c : store.containers(worldId)) if (c.type().endsWith("shulker_box")) {
                BlockPos pos = new BlockPos(c.position().get(0), c.position().get(1), c.position().get(2));
                placedShulkers.put(c.id(), new Candidate(c.id(), c.type(), c.dimension(), pos, c.members(), 27, 0));
            }
        } catch (Exception e) { throw new IllegalStateException(e); }
    }

    public void fullSync(int menuId) {
        if (!config.trackingEnabled) return;
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null) return;
        AbstractContainerMenu menu = client.player.containerMenu;
        if (menu.containerId != menuId || menu == client.player.inventoryMenu) return;
        if (pending != null && pending.expiresAt() >= System.currentTimeMillis() &&
                (menu instanceof ChestMenu || menu instanceof ShulkerBoxMenu) &&
                menu.slots.stream().filter(slot -> slot.container != client.player.getInventory()).count() == pending.expectedSlots()) {
            active = pending; activeMenu = menuId; pending = null; lastCapturedId = null;
        }
        if (active != null && activeMenu == menuId) capture(client, menu);
    }

    public void slotSync(int menuId) {
        if (!config.trackingEnabled) return;
        Minecraft client = Minecraft.getInstance();
        if (active == null || activeMenu != menuId || client.player == null || client.player.containerMenu.containerId != menuId) return;
        capture(client, client.player.containerMenu);
    }

    public void openingContainer() { active = null; activeMenu = -1; }

    public void closingContainer() {
        Minecraft client = Minecraft.getInstance();
        if (active != null && client.player != null && client.player.containerMenu.containerId == activeMenu)
            capture(client, client.player.containerMenu);
        active = null; activeMenu = -1;
    }

    private void capture(Minecraft client, AbstractContainerMenu menu) {
        if (!config.trackingEnabled) return;
        ensureWorld(client);
        ItemStackScanner scanner = new ItemStackScanner();
        for (var slot : menu.slots) {
            if (slot.container == client.player.getInventory()) continue;
            scanner.add(slot.getItem(), slot.getContainerSlot());
        }
        var scan = scanner.result();
        List<InventoryStore.Item> items = scan.items();
        long now = System.currentTimeMillis();
        if (active.id().equals(lastCapturedId) && items.equals(lastItems) && scan.packedItems().equals(lastPackedItems) && now - lastStoredAt < 10_000) return;
        lastCapturedId = active.id(); lastItems = items; lastPackedItems = scan.packedItems(); lastStoredAt = now;
        String title = client.gui.screen() instanceof AbstractContainerScreen<?> screen ? screen.getTitle().getString() : active.type();
        BlockPos p = active.pos();
        var snapshot=new InventoryStore.Container(active.id(), title, active.type(), active.dimension(),
                List.of(p.getX(), p.getY(), p.getZ()), active.members(), now, items, scan.packedItems(), scan.complete());
        if(!store.externalShared(worldId,snapshot))store.put(worldId,worldName,snapshot);
        if (active.type().endsWith("shulker_box")) placedShulkers.put(active.id(), active);
    }

    public StockpileConfig config() { return config; }
    public List<InventoryStore.Container> findMemories() {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null) return List.of();
        ensureWorld(client);
        String dimension = client.level.dimension().identifier().toString();
        return store.containers(worldId).stream().filter(c -> dimension.equals(c.dimension())).toList();
    }
    private void findItem(String itemId) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null) return;
        ensureWorld(client);
        try { performFind(new ItemFinder.Request(worldId, null, itemId.contains(":") ? itemId : "minecraft:" + itemId, true), true); }
        catch (IllegalArgumentException e) { client.player.sendSystemMessage(Component.literal("[Stockpile] Use /stockpile find minecraft:stone (an item ID).")); }
    }
    private static <T> CompletableFuture<T> onClient(Supplier<T> action) {
        var result = new CompletableFuture<T>();
        Minecraft.getInstance().execute(() -> {
            if (result.isDone()) return;
            try { result.complete(action.get()); }
            catch (RuntimeException e) { result.completeExceptionally(e); }
        });
        return result;
    }

    private CompletableFuture<HighlightOptions> highlightSettings(HighlightOptions options) {
        return onClient(() -> {
            if (options != null) {
                config.highlight = options;
                findHighlight.clear();
                saveConfig();
            }
            return config.highlight;
        });
    }

    private TerrainSnapshot.Result captureTerrain(TerrainSnapshot.Request request) {
        var client = Minecraft.getInstance();
        if (client.player == null || client.level == null) {
            return new TerrainSnapshot.Result(409, "Join this world in Minecraft to capture terrain.", null);
        }
        ensureWorld(client);
        if (!isCurrentWorkspace(request.worldId())) {
            return new TerrainSnapshot.Result(409, "Join the selected world in Minecraft first.", null);
        }
        if (!request.dimension().equals(client.level.dimension().identifier().toString())) {
            return new TerrainSnapshot.Result(409, "Enter the selected dimension in Minecraft to capture its terrain.", null);
        }
        return sessionTerrain.capture(new TerrainSnapshot.Request(worldId, request.dimension()), client.level, client.player.blockPosition());
    }

    private boolean isCurrentWorkspace(String id) {
        if (id.equals(worldId)) return true;
        return store.isLiveSharedWorkspace(id, worldId);
    }

    private CompletableFuture<SchematicFunnel.Result> fillSchematicChest(SchematicFunnel.Request request) {
        var client = Minecraft.getInstance();
        var result = new CompletableFuture<SchematicFunnel.Result>();
        client.execute(() -> {
            if (result.isDone()) return;
            if (!store.schematicChestEnabled()) {
                result.complete(SchematicFunnel.Result.unavailable(409, "Schematic chest is disabled in Stockpile settings."));
                return;
            }
            var server = client.getSingleplayerServer();
            if (server == null || client.player == null || client.level == null || !store.canFunnel(request.worldId(), request.session())) {
                result.complete(SchematicFunnel.Result.unavailable(409,
                        "Join the selected single-player world to fill a schematic chest."));
                return;
            }
            var dimension = client.level.dimension();
            var player = client.player.getUUID();
            String name = worldName;
            server.submit(() -> {
                if (result.isDone()) return null;
                var owner = server.getPlayerList().getPlayer(player);
                if (server.isPublished() || owner == null || !owner.level().dimension().equals(dimension)) {
                    return SchematicFunnel.Result.unavailable(409, "Stay in the destination dimension in an unshared single-player world.");
                }
                return SchematicFunnel.fill(owner.level(), store, name, request);
            }).whenComplete((reply, failure) -> {
                if (failure != null) result.completeExceptionally(failure);
                else result.complete(reply);
            });
        });
        return result;
    }

    private CompletableFuture<SchematicFunnel.Settings> schematicChestSettings(SchematicFunnel.SettingsUpdate update) {
        return onClient(() -> updateSchematicSettings(update));
    }

    private SchematicFunnel.Settings updateSchematicSettings(SchematicFunnel.SettingsUpdate update) {
        var settings = store.schematicSettings(update);
        if (update != null) {
            config.schematicChestEnabled = settings.enabled();
            config.schematicChestAutoClear = settings.autoClear();
            saveConfig();
        }
        return settings;
    }

    void setSchematicChest(boolean enabled) {
        updateSchematicSettings(new SchematicFunnel.SettingsUpdate(enabled, null));
    }

    void setSchematicChestAutoClear(boolean autoClear) {
        updateSchematicSettings(new SchematicFunnel.SettingsUpdate(null, autoClear));
    }

    private ItemFinder.Reply performFind(ItemFinder.Request request, boolean sendChat) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null) return new ItemFinder.Reply(409, "Join the saved world in Minecraft first.");
        ensureWorld(client);
        if (!isCurrentWorkspace(request.worldId())) return new ItemFinder.Reply(409, "This inventory belongs to another world or player. Join that world first.");
        findHighlight.clear();
        String dimension = client.level.dimension().identifier().toString();
        var matches = ItemFinder.search(store, request, request.worldId(), dimension, client.player.getX(), client.player.getY(), client.player.getZ());
        if (matches.isEmpty()) {
            String message = "No remembered supplies match this item and location. Reopen the container to refresh it.";
            if (sendChat) client.player.sendSystemMessage(Component.literal("[Stockpile] " + message));
            return new ItemFinder.Reply(404, message);
        }
        findHighlight.show(client, matches, config.highlight);
        var highlighted = matches.stream().filter(c -> dimension.equals(c.dimension())).limit(FindHighlight.LIMIT)
                .map(InventoryStore.Container::id).collect(java.util.stream.Collectors.toSet());
        int visible = 0, unloaded = 0, stale = 0, elsewhere = 0;
        for (var c : matches) {
            BlockPos pos = FindHighlight.positions(c).getFirst();
            if (!dimension.equals(c.dimension())) elsewhere++;
            else if (!client.level.hasChunkAt(pos)) unloaded++;
            else if (!FindHighlight.present(client, c, pos)) stale++;
            else if (highlighted.contains(c.id())) visible++;
        }
        String message = matches.size() + " remembered container(s). " + Math.min(visible, FindHighlight.LIMIT) + " ready to highlight for " + config.highlight.seconds() + "s" +
                (unloaded > 0 ? "; " + unloaded + " in unloaded chunks (approach to highlight)" : "") +
                (elsewhere > 0 ? "; " + elsewhere + " in another dimension" : "") +
                (stale > 0 ? "; " + stale + " storage block(s) missing; saved contents may be stale" : "") +
                (matches.size() > FindHighlight.LIMIT ? "; highlighting limited to the nearest " + FindHighlight.LIMIT + " in this dimension" : "") + ".";
        if (!sendChat) {
            return new ItemFinder.Reply(200, message + " Return to Minecraft to see the outline.");
        }
        client.player.sendSystemMessage(Component.literal("[Stockpile] " + (request.itemId()==null?"Storage location":request.itemId()) + ": " + message));
        for (var c : matches.stream().limit(10).toList()) {
            var packedGroups=request.includePacked()?ItemFinder.packedLocations(c,request.itemId()):List.<ItemFinder.PackedLocation>of();
            String packed=packedGroups.stream().limit(3).map(i->" | "+i.count()+" inside "+String.join(" / ",i.path())).reduce("",String::concat);
            if(packedGroups.size()>3)packed+=" | +"+(packedGroups.size()-3)+" more packed locations";
            client.player.sendSystemMessage(Component.literal("[Stockpile] " + c.name() + " · " + c.dimension() + " · " +
                    c.position().get(0) + " / " + c.position().get(1) + " / " + c.position().get(2) +
                    (ItemFinder.ender(c) ? " (last Ender Chest access point; any Ender Chest has these contents)" : c.type().endsWith("shulker_box") ? " (placed shulker)" : "") + packed));
        }
        if (matches.size() > 10) client.player.sendSystemMessage(Component.literal("[Stockpile] Showing the first 10 locations in chat. Use the dashboard for all locations."));
        return new ItemFinder.Reply(200, message);
    }
    InventoryStore.Container currentChest(int menuId) {
        var client = Minecraft.getInstance();
        if (worldId == null || active == null || activeMenu != menuId || client.player == null ||
                client.player.containerMenu.containerId != menuId) return null;
        return store.getContainer(worldId, active.id());
    }

    boolean chestExcluded(int menuId) {
        var chest = currentChest(menuId);
        return chest != null && store.excluded(worldId, chest.id());
    }

    void toggleChestExclusion(int menuId) {
        var chest = currentChest(menuId);
        if (chest != null) store.exclude(worldId, chest.id(), !store.excluded(worldId, chest.id()));
    }

    boolean offerSchematicChest(int menuId) {
        var server = Minecraft.getInstance().getSingleplayerServer();
        if (server == null || server.isPublished() || !config.schematicChestEnabled) return false;
        var candidate = activeMenu == menuId && active != null ? active : pending;
        return candidate != null && InventoryStore.schematicChestKind(candidate.type());
    }

    boolean canChooseSchematicChest(int menuId) {
        var chest = currentChest(menuId);
        return offerSchematicChest(menuId) && chest != null &&
                (store.isSchematicChest(worldId, chest.id()) || !store.excluded(worldId, chest.id()));
    }

    boolean isSchematicChest(int menuId) {
        var chest = currentChest(menuId);
        return chest != null && store.isSchematicChest(worldId, chest.id());
    }

    void toggleSchematicChest(int menuId) {
        if (!canChooseSchematicChest(menuId)) return;
        store.toggleSchematicChest(worldId, currentChest(menuId).id());
    }

    void openStorageMap() {
        openBrowser("&view=map");
    }

    public void saveConfig() { config.save(configPath, LOGGER); }
    public Component shortcutLabel() {
        return !config.keybindEnabled ? Component.literal("Disabled") : open.isUnbound() ? Component.translatable("stockpile.settings.unbound") : open.getTranslatedKeyMessage();
    }
    public void unbindShortcut() {
        open.setKey(InputConstants.UNKNOWN); KeyMapping.resetMapping(); Minecraft.getInstance().options.save();
    }
    public void openSettings(net.minecraft.client.gui.screens.Screen parent) {
        settingsParent = parent instanceof net.minecraft.client.gui.screens.ChatScreen ? null : parent;
        settingsRequested = true;
    }
    public void setTracking(boolean enabled) {
        if (!enabled && config.trackingEnabled) closingContainer();
        config.trackingEnabled = enabled; store.tracking(enabled);
        if (!enabled) { pending = null; active = null; activeMenu = -1; }
        saveConfig();
    }
    private boolean ensureDashboard() {
        if (dashboard != null) return true;
        try {
            dashboard = new DashboardServer(store, request -> onClient(() -> performFind(request, false)),
                    data -> store.shared(data.get("worldId").getAsString()) ? sharedStorage.reserve(data) :
                            CompletableFuture.completedFuture(store.reservation(data, "local")),
                    request -> onClient(() -> captureTerrain(request)), this::highlightSettings,
                    sessionTerrain, this::fillSchematicChest, this::schematicChestSettings);
            dashboard.registry(registryJson);
            return true;
        }
        catch (Exception e) { LOGGER.error("Could not start Stockpile dashboard", e); return false; }
    }
    public void showDashboardLink(boolean respectAutoOpen) {
        Minecraft client = Minecraft.getInstance();
        if (!ensureDashboard()) {
            if (client.player != null) client.player.sendSystemMessage(Component.literal("[Stockpile] Dashboard unavailable. Check the game log."));
            return;
        }
        if (client.player != null) client.player.sendSystemMessage(StockpileMessages.dashboard(dashboard.url(), config.trackingEnabled));
        if (respectAutoOpen && config.autoOpenBrowser) openBrowser();
    }
    public void openDashboard() {
        showDashboardLink(false);
        openBrowser();
    }
    private void openBrowser() { openBrowser(""); }

    private void openBrowser(String fragmentOptions) {
        Minecraft client = Minecraft.getInstance();
        if (!ensureDashboard()) {
            if (client.player != null) client.player.sendSystemMessage(Component.literal("Stockpile could not start its browser dashboard. Check the game log."));
            return;
        }
        String url = dashboard.url() + fragmentOptions;
        try {
            String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
            if (os.contains("mac")) new ProcessBuilder("open", url).start();
            else if (os.contains("win")) new ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", url).start();
            else new ProcessBuilder("xdg-open", url).start();
        } catch (Exception e) {
            LOGGER.warn("Could not open browser", e);
            if (client.player != null) client.player.sendSystemMessage(Component.literal("Open your browser: " + url));
        }
    }
}
