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
    private static final int SIZE = 14;
    private record Position(int x, int y) {}

    public static void register(StockpileClient owner) {
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (!owner.config().chestTrackerButton || !FabricLoader.getInstance().isModLoaded("chesttracker") ||
                    !screen.getClass().getName().equals(SCREEN)) return;
            List<AbstractWidget> widgets = Screens.getWidgets(screen);
            var header = widgets.stream().filter(w -> w.visible && w.getWidth() == SIZE && w.getHeight() == SIZE)
                    .min(Comparator.comparingInt(AbstractWidget::getY));
            if (header.isEmpty()) return;
            int y = header.get().getY();
            var row = widgets.stream().filter(w -> w.visible && w.getY() == y && w.getWidth() == SIZE && w.getHeight() == SIZE).toList();
            int x = row.stream().mapToInt(AbstractWidget::getX).min().orElse(width) - 17;
            int right = row.stream().mapToInt(w -> w.getX() + w.getWidth()).max().orElse(width);
            int panelRight = right + 3;
            int panelLeft = width - panelRight;
            int titleRight = panelLeft + 8 + Screens.getFont(screen).width(screen.getTitle());
            // The port's longer title can fill the header. Use an outer margin in that layout.
            var choices = List.of(new Position(x >= titleRight + 3 ? x : -1, y),
                    new Position(panelRight + 4, y), new Position(panelLeft - SIZE - 4, y),
                    new Position(panelRight - SIZE, y - SIZE - 8), new Position(width - SIZE - 4, height - SIZE - 4));
            var spot = choices.stream().filter(p -> fits(p, width, height, widgets)).findFirst();
            if (spot.isEmpty()) return;
            Button button = Button.builder(Component.literal("S"), b -> client.gui.setScreen(new StockpileSettingsScreen(screen, owner)))
                    .bounds(spot.get().x(), spot.get().y(), SIZE, SIZE).build();
            button.setTooltip(Tooltip.create(Component.literal("Stockpile settings and dashboard")));
            widgets.add(button);
        });
    }

    private static boolean fits(Position pos, int width, int height, List<AbstractWidget> widgets) {
        return pos.x() >= 0 && pos.y() >= 0 && pos.x() + SIZE <= width && pos.y() + SIZE <= height &&
                widgets.stream().noneMatch(w -> w.visible &&
                        pos.x() < w.getX() + w.getWidth() && pos.x() + SIZE > w.getX() &&
                        pos.y() < w.getY() + w.getHeight() && pos.y() + SIZE > w.getY());
    }
}
