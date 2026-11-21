package dev.danzaa.stockpile;

import com.google.gson.JsonObject;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import javax.imageio.ImageIO;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;

/** Surface colors from chunks already loaded by this client. One pixel represents one block column. */
final class TerrainSnapshot {
    static final int SIZE = 208;
    record Request(String worldId, String dimension) {
        Request {
            if (worldId == null || worldId.isBlank() || worldId.length() > 200 ||
                    dimension == null || dimension.length() > 256 || !dimension.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) {
                throw new IllegalArgumentException("Invalid world or dimension.");
            }
        }
    }

    record Result(int status, String message, TerrainSnapshot terrain) {
        JsonObject json() throws IOException {
            var json = new JsonObject();
            json.addProperty("message", message);
            if (terrain != null) {
                json.addProperty("x", terrain.x);
                json.addProperty("z", terrain.z);
                json.addProperty("size", SIZE);
                json.addProperty("capturedAt", terrain.capturedAt);
                json.addProperty("png", terrain.png());
            }
            return json;
        }
    }

    private final int x;
    private final int z;
    private final int[] pixels;
    private final long capturedAt;
    private String encoded;

    TerrainSnapshot(int x, int z, int[] pixels, long capturedAt) {
        this.x = x;
        this.z = z;
        this.pixels = pixels;
        this.capturedAt = capturedAt;
    }

    static Result capture(ClientLevel level, BlockPos player) {
        // Snap the origin to the chunk grid so a capture reads at most 13 × 13 loaded chunks.
        int firstChunkX = Math.floorDiv(player.getX(), 16) - 6;
        int firstChunkZ = Math.floorDiv(player.getZ(), 16) - 6;
        int originX = firstChunkX * 16;
        int originZ = firstChunkZ * 16;
        int[] colors = new int[SIZE * SIZE];
        int[] heights = new int[colors.length];
        var pos = new BlockPos.MutableBlockPos();
        boolean ceiling = level.dimensionType().hasCeiling();
        int ceilingTop = Math.min(level.getMaxY(), player.getY() + 1);
        int loaded = 0;
        for (int dz = 0; dz < SIZE / 16; dz++) {
            for (int dx = 0; dx < SIZE / 16; dx++) {
                var chunk = level.getChunkSource().getChunk(firstChunkX + dx, firstChunkZ + dz, ChunkStatus.FULL, false);
                if (chunk == null) continue;
                loaded++;
                for (int localZ = 0; localZ < 16; localZ++) {
                    for (int localX = 0; localX < 16; localX++) {
                        int px = dx * 16 + localX;
                        int pz = dz * 16 + localZ;
                        int y = ceiling ? ceilingTop : Math.min(level.getMaxY(),
                                chunk.getHeight(Heightmap.Types.WORLD_SURFACE, localX, localZ));
                        int bottom = Math.max(level.getMinY(), y - (ceiling ? 15 : 7));
                        for (; y >= bottom; y--) {
                            pos.set(originX + px, y, originZ + pz);
                            var block = chunk.getBlockState(pos);
                            if (block.isAir()) continue;
                            MapColor color = block.getMapColor(level, pos);
                            if (color == MapColor.NONE) continue;
                            int index = pz * SIZE + px;
                            colors[index] = 0xff000000 | color.col;
                            heights[index] = y;
                            break;
                        }
                    }
                }
            }
        }
        if (loaded == 0) return new Result(409, "Wait for nearby terrain to load in Minecraft and try again.", null);
        shade(colors, heights, SIZE);
        String message = ceiling ? "Captured terrain near your current height." : "Captured nearby terrain.";
        return new Result(200, message, new TerrainSnapshot(originX, originZ, colors, System.currentTimeMillis()));
    }

    static void shade(int[] colors, int[] heights, int width) {
        for (int i = 0; i < colors.length; i++) {
            int color = colors[i];
            if (color == 0) continue;
            int north = i - width;
            int slope = north >= 0 && colors[north] != 0 ? heights[i] - heights[north] : 0;
            double light = .88 + Math.max(-3, Math.min(3, slope)) * .045;
            int red = (int) ((color >> 16 & 255) * light);
            int green = (int) ((color >> 8 & 255) * light);
            int blue = (int) ((color & 255) * light);
            colors[i] = 0xff000000 | red << 16 | green << 8 | blue;
        }
    }

    private synchronized String png() throws IOException {
        if (encoded != null) return encoded;
        var image = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, SIZE, SIZE, pixels, 0, SIZE);
        var bytes = new ByteArrayOutputStream();
        ImageIO.write(image, "png", bytes);
        encoded = Base64.getEncoder().encodeToString(bytes.toByteArray());
        return encoded;
    }
}
