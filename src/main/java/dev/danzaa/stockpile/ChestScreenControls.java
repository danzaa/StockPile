package dev.danzaa.stockpile;

import dev.danzaa.stockpile.mixin.ContainerScreenLayout;
import java.lang.ref.WeakReference;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ShulkerBoxMenu;

final class ChestScreenControls {
    private static final int GAP = 4;
    private static final int BUTTON_WIDTH = 83;
    private static final int BUTTON_HEIGHT = 18;
    private static final Component EXCLUDE = Component.translatable("stockpile.chest.exclude");
    private static final Component INCLUDE = Component.translatable("stockpile.chest.include");
    private static final Component SCHEMATIC = Component.translatable("stockpile.chest.schematic");
    private static final Component SCHEMATIC_SELECTED = Component.translatable("stockpile.chest.schematic_selected");
    private static final Tooltip WAITING = Tooltip.create(Component.translatable("stockpile.chest.wait"));
    private static final Tooltip EXCLUDE_HINT = Tooltip.create(Component.translatable("stockpile.chest.exclude_hint"));
    private static final Tooltip INCLUDE_HINT = Tooltip.create(Component.translatable("stockpile.chest.include_hint"));
    private static final Tooltip SCHEMATIC_HINT = Tooltip.create(Component.translatable("stockpile.chest.schematic_hint"));
    private static final Tooltip SCHEMATIC_CLEAR = Tooltip.create(Component.translatable("stockpile.chest.schematic_clear"));
    private static final Tooltip SCHEMATIC_WAITING = Tooltip.create(Component.translatable("stockpile.chest.schematic_wait"));
    private enum Kind { MAP, EXCLUDE, SCHEMATIC }
    private record Placement(int x, int y, int width, boolean stacked) {
        int x(int index) { return x + (stacked ? 0 : index * (width + GAP)); }
        int y(int index) { return y + (stacked ? index * (BUTTON_HEIGHT + GAP) : 0); }
    }

    static void register(StockpileClient stockpile) {
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (!(screen instanceof AbstractContainerScreen<?> container)) return;
            var menu = container.getMenu();
            if (!(menu instanceof ChestMenu || menu instanceof ShulkerBoxMenu)) return;

            var layout = (ContainerScreenLayout) container;
            boolean schematic = menu instanceof ChestMenu && stockpile.offerSchematicChest(menu.containerId);
            Placement placement = place(width, height, layout, schematic ? 3 : 2);
            if (placement == null && schematic) {
                schematic = false;
                placement = place(width, height, layout, 2);
            }
            if (placement == null) return;
            var dashboard = new StorageButton(placement, 0, Component.literal("Stockpile"), Kind.MAP,
                    b -> stockpile.openStorageMap());
            dashboard.setTooltip(Tooltip.create(Component.translatable("stockpile.chest.map_hint")));
            var exclusion = new StorageButton(placement, 1, EXCLUDE, Kind.EXCLUDE, b -> {
                stockpile.toggleChestExclusion(menu.containerId);
                updateButton(stockpile, menu.containerId, b);
            });

            Screens.getWidgets(screen).add(dashboard);
            Screens.getWidgets(screen).add(exclusion);
            updateButton(stockpile, menu.containerId, exclusion);
            Button target = null;
            if (schematic) {
                target = new StorageButton(placement, 2, SCHEMATIC, Kind.SCHEMATIC, button -> {
                    stockpile.toggleSchematicChest(menu.containerId);
                    updateTarget(stockpile, menu.containerId, button);
                });
                Screens.getWidgets(screen).add(target);
                updateTarget(stockpile, menu.containerId, target);
            }
            // The full slot synchronization can arrive after the screen's initial layout.
            var widget = new WeakReference<>(exclusion);
            var targetWidget = new WeakReference<>(target);
            ScreenEvents.afterTick(screen).register(ticked -> {
                var button = widget.get();
                if (button != null) updateButton(stockpile, menu.containerId, button);
                var destination = targetWidget.get();
                if (destination != null) updateTarget(stockpile, menu.containerId, destination);
            });
        });
    }

    private static void updateTarget(StockpileClient stockpile, int menuId, Button button) {
        button.active = stockpile.canChooseSchematicChest(menuId);
        boolean selected = stockpile.isSchematicChest(menuId);
        button.setMessage(selected ? SCHEMATIC_SELECTED : SCHEMATIC);
        button.setTooltip(!button.active ? SCHEMATIC_WAITING : selected ? SCHEMATIC_CLEAR : SCHEMATIC_HINT);
    }

    private static void updateButton(StockpileClient stockpile, int menuId, Button button) {
        button.active = stockpile.currentChest(menuId) != null;
        boolean excluded = button.active && stockpile.chestExcluded(menuId);
        button.setMessage(excluded ? INCLUDE : EXCLUDE);
        button.setTooltip(button.active ? (excluded ? INCLUDE_HINT : EXCLUDE_HINT) : WAITING);
    }

    private static Placement place(int width, int height, ContainerScreenLayout layout, int count) {
        int left = layout.stockpile$getLeft();
        int top = layout.stockpile$getTop();
        int right = left + layout.stockpile$getWidth();
        int bottom = top + layout.stockpile$getHeight();
        int rowWidth = Math.min(BUTTON_WIDTH, (width - GAP * (count + 1)) / count);
        int center = left + (layout.stockpile$getWidth() - count * rowWidth - (count - 1) * GAP) / 2;
        if (rowWidth >= 50 && bottom + GAP + BUTTON_HEIGHT < height) return new Placement(center, bottom + GAP, rowWidth, false);
        if (rowWidth >= 50 && top >= BUTTON_HEIGHT + GAP) return new Placement(center, top - BUTTON_HEIGHT - GAP, rowWidth, false);
        int sideY = Math.max(GAP, top);
        if (sideY + count * (BUTTON_HEIGHT + GAP) > height) return null;
        if (right + GAP + BUTTON_WIDTH <= width) return new Placement(right + GAP, sideY, BUTTON_WIDTH, true);
        if (left >= BUTTON_WIDTH + GAP) return new Placement(left - BUTTON_WIDTH - GAP, sideY, BUTTON_WIDTH, true);
        return null;
    }

    private static final class StorageButton extends Button {
        private final Kind kind;

        StorageButton(Placement placement, int index, Component label, Kind kind, OnPress press) {
            super(placement.x(index), placement.y(index), placement.width(), BUTTON_HEIGHT, label, press, DEFAULT_NARRATION);
            this.kind = kind;
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
            int x = getX();
            int y = getY();
            boolean hover = active && isHoveredOrFocused();
            boolean selected = getMessage().equals(SCHEMATIC_SELECTED);
            int border = hover || selected ? 0xffb7d584 : active ? 0xff748c54 : 0xff5b6256;
            int background = hover || selected ? 0xff465e36 : kind == Kind.MAP ? 0xff314729 : 0xff262e25;
            int text = active ? 0xffe7f0d8 : 0xff9da697;
            graphics.fill(x, y + 2, x + getWidth(), y + getHeight() + 1, 0xff151a14);
            graphics.fill(x + 1, y, x + getWidth() - 1, y + getHeight() - 1, border);
            graphics.fill(x, y + 1, x + getWidth(), y + getHeight() - 2, border);
            graphics.fill(x + 1, y + 1, x + getWidth() - 1, y + getHeight() - 2, background);
            if (kind == Kind.MAP) {
                graphics.fill(x + 7, y + 5, x + 16, y + 8, 0xffc38b4e);
                graphics.fill(x + 7, y + 9, x + 16, y + 12, 0xffa96d39);
                graphics.fill(x + 11, y + 7, x + 13, y + 10, 0xffffd184);
            } else if (kind == Kind.EXCLUDE) {
                graphics.fill(x + 8, y + 5, x + 15, y + 12, border);
                graphics.fill(x + 9, y + 6, x + 14, y + 11, background);
                graphics.fill(x + 10, y + 8, x + 13, y + 9, text);
                if (getMessage().equals(INCLUDE)) graphics.fill(x + 11, y + 7, x + 12, y + 10, text);
            } else {
                graphics.fill(x + 7, y + 4, x + 16, y + 13, border);
                graphics.fill(x + 8, y + 5, x + 15, y + 12, background);
                if (selected) {
                    graphics.fill(x + 8, y + 8, x + 9, y + 10, text);
                    graphics.fill(x + 9, y + 9, x + 10, y + 11, text);
                    graphics.fill(x + 10, y + 10, x + 11, y + 12, text);
                    for (int i = 0; i < 4; i++) graphics.fill(x + 11 + i, y + 9 - i, x + 12 + i, y + 11 - i, text);
                }
            }
            var font = Minecraft.getInstance().font;
            int center = x + (getWidth() + 15) / 2;
            var label = kind == Kind.SCHEMATIC ? SCHEMATIC : getMessage();
            if (font.width(label) <= getWidth() - 21) {
                graphics.centeredText(font, label, center, y + 5, text);
            } else {
                graphics.centeredText(font, font.plainSubstrByWidth(label.getString(), getWidth() - 27) + "…", center, y + 5, text);
            }
        }
    }
}
