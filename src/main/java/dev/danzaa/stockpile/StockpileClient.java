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
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
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
    private DashboardServer dashboard;
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
                LOGGER.info("Stockpile dashboard is ready. Use /stockpile start for the link.");
            } catch (Exception e) { LOGGER.error("Could not start Stockpile dashboard", e); }
        });
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            if (dashboard != null) dashboard.close();
            store.close();
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            settingsRequested = false; settingsParent = null;
            pending = null; active = null; activeMenu = -1; worldId = null; wasConnected = false;
            placedShulkers.clear();
            store.connected(false);
        });
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) -> StockpileCommands.register(dispatcher,
                () -> openSettings(Minecraft.getInstance().gui.screen()),
                () -> { setTracking(true); showDashboardLink(true); },
                () -> { setTracking(false); if (Minecraft.getInstance().player != null) Minecraft.getInstance().player.sendSystemMessage(StockpileMessages.paused()); },
                () -> showDashboardLink(false), this::openDashboard));
        ChestTrackerCompat.register(this);
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
            boolean connected = client.player != null && client.level != null;
            store.connected(connected);
            if (!connected) { pending = null; active = null; activeMenu = -1; worldId = null; }
            else {
                ensureWorld(client);
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

    private void ensureWorld(Minecraft client) {
        if (worldId != null || client.player == null) return;
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
        try {
            worldId = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest((source + ":" + client.player.getUUID()).getBytes(StandardCharsets.UTF_8))).substring(0, 24);
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
        store.put(worldId, worldName, new InventoryStore.Container(active.id(), title, active.type(), active.dimension(),
                List.of(p.getX(), p.getY(), p.getZ()), active.members(), now, items, scan.packedItems(), scan.complete()));
        if (active.type().endsWith("shulker_box")) placedShulkers.put(active.id(), active);
    }

    public StockpileConfig config() { return config; }
    public void saveConfig() { config.save(configPath, LOGGER); }
    public Component shortcutLabel() {
        return !config.keybindEnabled ? Component.literal("Disabled") : open.isUnbound() ? Component.literal("Unbound") : open.getTranslatedKeyMessage();
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
        try { dashboard = new DashboardServer(store); dashboard.registry(registryJson); return true; }
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
    private void openBrowser() {
        Minecraft client = Minecraft.getInstance();
        if (!ensureDashboard()) {
            if (client.player != null) client.player.sendSystemMessage(Component.literal("Stockpile could not start its browser dashboard. Check the game log."));
            return;
        }
        String url = dashboard.url();
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
