package dev.danzaa.stockpile;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

public final class StockpileModMenu implements ModMenuApi {
    @Override public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> new StockpileSettingsScreen(parent, StockpileClient.INSTANCE);
    }
}
