package dev.danzaa.stockpile;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.EnderChestBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.AABB;

/** Tick-owned overlay. It cannot survive a level change, disconnect or a new search. */
public final class FindHighlight {
    public static final int LIMIT = 32;
    private List<InventoryStore.Container> targets = List.of();
    private Object level;
    private long since;
    private long until;
    private HighlightOptions options = HighlightOptions.DEFAULT;

    public void clear() { targets = List.of(); level = null; until = 0; }
    public void show(Minecraft client, List<InventoryStore.Container> containers, HighlightOptions options) {
        this.options = options;
        targets = containers.stream().filter(c -> c.dimension().equals(client.level.dimension().identifier().toString())).limit(LIMIT).toList();
        level = client.level;
        since = System.nanoTime() / 1_000_000L;
        until = since + options.seconds() * 1_000L;
    }
    public void tick(Minecraft client) {
        long now = System.nanoTime() / 1_000_000L;
        if (client.level == null || client.level != level || now >= until) { clear(); return; }
        if (targets.isEmpty()) return;
        float opacity = opacity(now - since, until - since);
        try (var ignored = client.collectPerTickGizmos()) {
            for (var c : targets) {
                for (var outline : outlines(client, c)) draw(outline, options, opacity);
            }
        }
    }

    static List<AABB> outlines(Minecraft client, InventoryStore.Container chest) {
        var loaded = positions(chest).stream().filter(pos -> present(client, chest, pos)).toList();
        if (loaded.size() == 2 && paired(client, loaded.getFirst(), loaded.getLast())) {
            return List.of(new AABB(loaded.getFirst()).minmax(new AABB(loaded.getLast())));
        }
        return loaded.stream().map(AABB::new).toList();
    }

    static float opacity(long elapsed, long duration) {
        float fraction = Math.max(0f, Math.min(1f, Math.min(elapsed / 450f, (duration - elapsed) / 550f)));
        return fraction * fraction * (3f - 2f * fraction);
    }

    private static boolean paired(Minecraft client, BlockPos first, BlockPos second) {
        var a = client.level.getBlockState(first);
        var b = client.level.getBlockState(second);
        return a.getBlock() instanceof ChestBlock && a.getBlock() == b.getBlock() &&
                a.getValue(ChestBlock.TYPE) != ChestType.SINGLE && b.getValue(ChestBlock.TYPE) != ChestType.SINGLE &&
                ChestBlock.getConnectedBlockPos(first, a).equals(second) && ChestBlock.getConnectedBlockPos(second, b).equals(first);
    }
    public static void draw(BlockPos pos) {
        draw(pos, HighlightOptions.DEFAULT);
    }
    static void draw(BlockPos pos, HighlightOptions options) {
        draw(new AABB(pos), options, 1f);
    }
    static void draw(AABB bounds, HighlightOptions options, float opacity) {
        if (opacity <= 0f) return;
        int rgb = options.rgb();
        int stroke = Math.round(255 * opacity) << 24 | rgb;
        int fill = Math.round(37 * opacity) << 24 | rgb;
        var gizmo = Gizmos.cuboid(bounds.inflate(.015), GizmoStyle.strokeAndFill(stroke, 3f, fill));
        if (options.throughWalls()) gizmo.setAlwaysOnTop();
    }
    public static List<BlockPos> positions(InventoryStore.Container c) {
        BlockPos main = new BlockPos(c.position().get(0), c.position().get(1), c.position().get(2));
        List<BlockPos> result = new ArrayList<>(); result.add(main);
        if (c.members() != null) for (String member : c.members()) {
            if (!member.startsWith(c.dimension() + ":")) continue;
            String[] coords = member.substring(c.dimension().length() + 1).split(",");
            if (coords.length != 3) continue;
            try {
                BlockPos p = new BlockPos(Integer.parseInt(coords[0]), Integer.parseInt(coords[1]), Integer.parseInt(coords[2]));
                if (p.getY() == main.getY() && p.distManhattan(main) == 1 && result.size() < 2 && c.type().endsWith("chest") && !ItemFinder.ender(c)) result.add(p);
            } catch (NumberFormatException ignored) { /* Legacy member identifiers need not be coordinates. */ }
        }
        return List.copyOf(result);
    }
    public static boolean present(Minecraft client, InventoryStore.Container c, BlockPos pos) {
        if (!client.level.hasChunkAt(pos)) return false;
        var entity = client.level.getBlockEntity(pos);
        if (ItemFinder.ender(c)) return entity instanceof EnderChestBlockEntity;
        if (c.type().endsWith("shulker_box")) return entity instanceof ShulkerBoxBlockEntity;
        if (c.type().endsWith("barrel")) return entity instanceof BarrelBlockEntity;
        return entity instanceof ChestBlockEntity;
    }
}
