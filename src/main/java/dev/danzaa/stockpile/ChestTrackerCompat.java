package dev.danzaa.stockpile;

import java.util.Comparator;
import java.util.List;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

public final class ChestTrackerCompat {
    public static final String SCREEN = "red.jackf.chesttracker.impl.gui.screen.ChestTrackerScreen";
    public static void register(StockpileClient owner) {
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (!owner.config().chestTrackerButton || !FabricLoader.getInstance().isModLoaded("chesttracker") ||
                    !screen.getClass().getName().equals(SCREEN)) return;
            List<AbstractWidget> widgets = Screens.getWidgets(screen);
            var header = widgets.stream().filter(w -> w.visible && w.getWidth() == 14 && w.getHeight() == 14)
                    .min(Comparator.comparingInt(AbstractWidget::getY));
            if (header.isEmpty()) return;
            int y = header.get().getY();
            var row = widgets.stream().filter(w -> w.visible && w.getY() == y && w.getWidth() == 14 && w.getHeight() == 14).toList();
            int x = row.stream().mapToInt(AbstractWidget::getX).min().orElse(width) - 17;
            int right = row.stream().mapToInt(w -> w.getX() + w.getWidth()).max().orElse(width);
            int titleRight = width - (right + 3) + 8 + Screens.getFont(screen).width(screen.getTitle());
            if (x < titleRight + 3) return;
            Button button = Button.builder(Component.literal("S"), b -> client.gui.setScreen(new StockpileSettingsScreen(screen, owner)))
                    .bounds(x, y, 14, 14).build();
            button.setTooltip(Tooltip.create(Component.literal("Stockpile settings and dashboard")));
            widgets.add(button);
        });
    }
}
