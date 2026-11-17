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
        super(Component.literal("Stockpile settings"));
        this.parent = parent; this.owner = owner;
    }

    @Override protected void init() {
        int total = Math.min(332, width - 20), left = (width - total) / 2, half = (total - 8) / 2;
        top = Math.max(44, height / 2 - 80);
        StockpileConfig config = owner.config();
        toggle("Tracking", () -> config.trackingEnabled, owner::setTracking, left, top, total,
                "Pause or resume container tracking. Saved contents remain available while paused.");
        toggle("Startup message", () -> config.showStartupMessage, value -> config.showStartupMessage = value,
                left, top + 24, half, "Show a chat hint when joining. The hint uses your current key binding.");
        toggle("Auto-open browser", () -> config.autoOpenBrowser, value -> config.autoOpenBrowser = value,
                left + half + 8, top + 24, half, "When off, commands and the shortcut print a clickable link without opening a browser.");
        toggle("Shortcut", () -> config.keybindEnabled, value -> config.keybindEnabled = value,
                left, top + 48, half, "Disable the Stockpile shortcut while keeping commands available.");
        addRenderableWidget(Button.builder(Component.literal("Key bindings..."), b ->
                minecraft.gui.setScreen(new KeyBindsScreen(this, minecraft.options))).bounds(left + half + 8, top + 48, half, 20).build());
        toggle("Chest Tracker button", () -> config.chestTrackerButton, value -> config.chestTrackerButton = value,
                left, top + 72, half, "Add an S button to Chest Tracker's main GUI when Chest Tracker is installed.");
        addRenderableWidget(Button.builder(Component.literal("Unbind shortcut"), b -> {
            owner.unbindShortcut(); rebuildWidgets();
        }).bounds(left + half + 8, top + 72, half, 20).build());
        Button link = Button.builder(Component.literal("Show dashboard link"), b -> owner.showDashboardLink(false))
                .bounds(left, top + 96, half, 20).build();
        link.active = minecraft.player != null;
        link.setTooltip(Tooltip.create(Component.literal("Send the dashboard link to chat. Join a world to use this action.")));
        addRenderableWidget(link);
        addRenderableWidget(Button.builder(Component.literal("Open browser"), b -> owner.openDashboard())
                .bounds(left + half + 8, top + 96, half, 20).build());
        Button support = Button.builder(Component.literal("Issues, support & suggestions"),
                ConfirmLinkScreen.confirmLink(this, URI.create("https://github.com/danzaa/StockPile/issues/new/choose")))
                .bounds(left, top + 120, total, 20).build();
        support.setTooltip(Tooltip.create(Component.literal("Open GitHub to report a bug, ask for help or suggest a feature.")));
        addRenderableWidget(support);
        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
                .bounds(left, height - 28, total, 20).build());
    }

    private void toggle(String label, BooleanSupplier get, Consumer<Boolean> set, int x, int y, int width, String hint) {
        Button button = Button.builder(Component.literal(label + ": " + (get.getAsBoolean() ? "ON" : "OFF")), b -> {
            set.accept(!get.getAsBoolean()); owner.saveConfig();
            b.setMessage(Component.literal(label + ": " + (get.getAsBoolean() ? "ON" : "OFF")));
        }).bounds(x, y, width, 20).build();
        button.setTooltip(Tooltip.create(Component.literal(hint)));
        addRenderableWidget(button);
    }

    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        graphics.centeredText(font, title, width / 2, 16, 0xffc4dda2);
        graphics.centeredText(font, "Local container tracking and browser access", width / 2, 29, 0xffa3aaa0);
        graphics.centeredText(font, Component.literal("Current shortcut: ").append(owner.shortcutLabel()),
                width / 2, Math.min(top + 148, height - 44), 0xffb6c4a7);
    }

    @Override public void onClose() { owner.saveConfig(); minecraft.gui.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
