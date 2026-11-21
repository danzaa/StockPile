package dev.danzaa.stockpile;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;

final class SessionTerrain {
    private static final int HISTORY_LIMIT = 8;
    private record Location(String worldId, String dimension) {}
    private final LinkedHashMap<Location, TerrainSnapshot.Result> recent = new LinkedHashMap<>();
    private final Set<String> capturedDimensions = new HashSet<>();
    private String dimension;
    private int ticksHere;
    private long revision;

    void resetSession() {
        dimension = null;
        capturedDimensions.clear();
    }

    void tick(String worldId, ClientLevel level, BlockPos player) {
        String current = level.dimension().identifier().toString();
        if (!current.equals(dimension)) {
            dimension = current;
            ticksHere = 0;
        }
        if (capturedDimensions.contains(current) || ++ticksHere < 20 || ticksHere % 10 != 0) return;
        if (!level.hasChunkAt(player)) return;
        if (ticksHere < 140 && !surroundingsReady(level, player)) return;
        capture(new TerrainSnapshot.Request(worldId, current), level, player);
    }

    private static boolean surroundingsReady(ClientLevel level, BlockPos player) {
        for (int z = -16; z <= 16; z += 16) {
            for (int x = -16; x <= 16; x += 16) {
                if (!level.hasChunkAt(player.offset(x, 0, z))) return false;
            }
        }
        return true;
    }

    TerrainSnapshot.Result capture(TerrainSnapshot.Request request, ClientLevel level, BlockPos player) {
        var result = TerrainSnapshot.capture(level, player);
        if (result.status() == 200) {
            capturedDimensions.add(request.dimension());
            remember(request, result);
        }
        return result;
    }

    private synchronized void remember(TerrainSnapshot.Request request, TerrainSnapshot.Result result) {
        var location = new Location(request.worldId(), request.dimension());
        recent.remove(location);
        recent.put(location, result);
        if (recent.size() > HISTORY_LIMIT) recent.pollFirstEntry();
        revision++;
    }

    synchronized long revision() {
        return revision;
    }

    JsonArray json() throws IOException {
        List<java.util.Map.Entry<Location, TerrainSnapshot.Result>> entries;
        synchronized (this) {
            entries = List.copyOf(recent.reversed().entrySet());
        }
        var captures = new JsonArray();
        for (var entry : entries) {
            JsonObject json = entry.getValue().json();
            json.addProperty("worldId", entry.getKey().worldId());
            json.addProperty("dimension", entry.getKey().dimension());
            captures.add(json);
        }
        return captures;
    }
}
