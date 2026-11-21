package dev.danzaa.stockpile;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.slf4j.Logger;

public final class StockpileConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    public boolean trackingEnabled = true;
    public boolean showStartupMessage = true;
    public boolean keybindEnabled = true;
    public boolean autoOpenBrowser = false;
    public boolean chestTrackerButton = true;
    public boolean schematicChestEnabled = true;
    public boolean schematicChestAutoClear = true;
    public HighlightOptions highlight = HighlightOptions.DEFAULT;

    public static StockpileConfig load(Path path, Logger logger) {
        if (!Files.exists(path)) return new StockpileConfig();
        try {
            StockpileConfig config = GSON.fromJson(Files.readString(path), StockpileConfig.class);
            if (config == null) throw new IOException("Settings are empty");
            if (config.highlight == null) config.highlight = HighlightOptions.DEFAULT;
            return config;
        } catch (Exception e) {
            logger.warn("Could not read Stockpile settings; keeping a backup and using defaults", e);
            try { Files.copy(path, path.resolveSibling("settings.json.backup-" + System.currentTimeMillis())); }
            catch (IOException backupFailure) { throw new IllegalStateException("Cannot back up Stockpile settings", backupFailure); }
            return new StockpileConfig();
        }
    }

    public void save(Path path, Logger logger) {
        try {
            Files.createDirectories(path.getParent());
            Path temporary = path.resolveSibling("settings.json.tmp");
            Files.writeString(temporary, GSON.toJson(this));
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) { logger.error("Could not save Stockpile settings", e); }
    }
}
