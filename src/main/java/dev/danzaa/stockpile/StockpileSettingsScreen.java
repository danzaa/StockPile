package dev.danzaa.stockpile;

import java.net.URI;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.minecraft.network.chat.Component;

public final class StockpileSettingsScreen extends Screen {
    private final Screen parent;
    private final StockpileClient owner;
    private int top;
    public StockpileSettingsScreen(Screen parent, StockpileClient owner) {
        super(Component.translatable("stockpile.settings.title"));
        this.parent = parent; this.owner = owner;
    }

    @Override protected void init() {
        int total = Math.min(332, width - 20), left = (width - total) / 2, half = (total - 8) / 2;
        top = Math.max(44, height / 2 - 80);
        StockpileConfig config = owner.config();
        toggle("stockpile.settings.tracking", () -> config.trackingEnabled, owner::setTracking, left, top, total,
                "stockpile.settings.tracking_hint");
        toggle("stockpile.settings.startup", () -> config.showStartupMessage, value -> config.showStartupMessage = value,
                left, top + 24, half, "stockpile.settings.startup_hint");
        toggle("stockpile.settings.browser", () -> config.autoOpenBrowser, value -> config.autoOpenBrowser = value,
                left + half + 8, top + 24, half, "stockpile.settings.browser_hint");
        toggle("stockpile.settings.shortcut", () -> config.keybindEnabled, value -> config.keybindEnabled = value,
                left, top + 48, half, "stockpile.settings.shortcut_hint");
        addRenderableWidget(Button.builder(Component.translatable("controls.keybinds.title"), b ->
                minecraft.gui.setScreen(new KeyBindsScreen(this, minecraft.options))).bounds(left + half + 8, top + 48, half, 20).build());
        toggle("stockpile.settings.tracker", () -> config.chestTrackerButton, value -> config.chestTrackerButton = value,
                left, top + 72, half, "stockpile.settings.tracker_hint");
        addRenderableWidget(Button.builder(Component.translatable("stockpile.settings.unbind"), b -> {
            owner.unbindShortcut(); rebuildWidgets();
        }).bounds(left + half + 8, top + 72, half, 20).build());
        Button link = Button.builder(Component.translatable("stockpile.settings.link"), b -> owner.showDashboardLink(false))
                .bounds(left, top + 96, half, 20).build();
        link.active = minecraft.player != null;
        link.setTooltip(Tooltip.create(Component.translatable("stockpile.settings.link_hint")));
        addRenderableWidget(link);
        addRenderableWidget(Button.builder(Component.translatable("stockpile.settings.open"), b -> owner.openDashboard())
                .bounds(left + half + 8, top + 96, half, 20).build());
        Button support = Button.builder(Component.translatable("stockpile.settings.support"),
                ConfirmLinkScreen.confirmLink(this, URI.create("https://github.com/danzaa/StockPile/issues/new/choose")))
                .bounds(left + half + 8, height - 28, half, 20).build();
        support.setTooltip(Tooltip.create(Component.translatable("stockpile.settings.support_hint")));
        addRenderableWidget(support);
        addRenderableWidget(Button.builder(schematicLabel(config), b -> {
            owner.setSchematicChest(!config.schematicChestEnabled);
            b.setMessage(schematicLabel(config));
        }).bounds(left, top + 120, half, 20).build());
        var autoClear = Button.builder(autoClearLabel(config), b -> {
            owner.setSchematicChestAutoClear(!config.schematicChestAutoClear);
            b.setMessage(autoClearLabel(config));
        }).bounds(left + half + 8, top + 120, half, 20).build();
        autoClear.setTooltip(Tooltip.create(Component.translatable("stockpile.schematic.auto_clear_hint")));
        addRenderableWidget(autoClear);
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(left, height - 28, half, 20).build());
    }

    private Component schematicLabel(StockpileConfig config) {
        return Component.translatable(config.schematicChestEnabled ? "stockpile.schematic.on" : "stockpile.schematic.off");
    }

    private Component autoClearLabel(StockpileConfig config) {
        return Component.translatable("stockpile.schematic.auto_clear",
                Component.translatable(config.schematicChestAutoClear ? "options.on" : "options.off"));
    }

    private void toggle(String label, BooleanSupplier get, Consumer<Boolean> set, int x, int y, int width, String hint) {
        Button button = Button.builder(Component.translatable(label, Component.translatable(get.getAsBoolean() ? "options.on" : "options.off")), b -> {
            set.accept(!get.getAsBoolean()); owner.saveConfig();
            b.setMessage(Component.translatable(label, Component.translatable(get.getAsBoolean() ? "options.on" : "options.off")));
        }).bounds(x, y, width, 20).build();
        button.setTooltip(Tooltip.create(Component.translatable(hint)));
        addRenderableWidget(button);
    }

    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        graphics.centeredText(font, title, width / 2, 16, 0xffc4dda2);
        graphics.centeredText(font, Component.translatable("stockpile.settings.subtitle"), width / 2, 29, 0xffa3aaa0);
        graphics.centeredText(font, Component.translatable("stockpile.settings.current", owner.shortcutLabel()),
                width / 2, Math.min(top + 148, height - 44), 0xffb6c4a7);
    }

    @Override public void onClose() { owner.saveConfig(); minecraft.gui.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
