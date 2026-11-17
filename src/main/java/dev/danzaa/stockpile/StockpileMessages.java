package dev.danzaa.stockpile;

import java.net.URI;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;

public final class StockpileMessages {
    private static MutableComponent prefix() {
        return Component.empty().append(Component.literal("[Stockpile] ").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
    }

    public static Component startup(boolean running, boolean keyEnabled, Component key) {
        MutableComponent message = prefix().append(Component.literal(running ? "Tracking active" : "Tracking paused")
                .withStyle(running ? ChatFormatting.GRAY : ChatFormatting.GOLD));
        message.append(Component.literal("  |  ").withStyle(ChatFormatting.DARK_GRAY));
        if (keyEnabled) message.append(Component.literal("[").withStyle(ChatFormatting.GRAY))
                .append(key.copy().withStyle(ChatFormatting.YELLOW))
                .append(Component.literal("] dashboard link").withStyle(ChatFormatting.GRAY));
        else message.append(Component.literal("/stockpile start").withStyle(ChatFormatting.YELLOW));
        return message.append(Component.literal("  [Settings]").withStyle(style -> style.withColor(ChatFormatting.AQUA)
                .withClickEvent(new ClickEvent.RunCommand("/stockpile settings"))
                .withHoverEvent(new HoverEvent.ShowText(Component.literal("Open Stockpile settings")))));
    }

    public static Component dashboard(String url, boolean running) {
        return prefix().append(Component.literal(running ? "Tracking active. " : "Tracking paused. ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal("[Open dashboard]").withStyle(style -> style.withColor(ChatFormatting.AQUA)
                        .withUnderlined(true).withClickEvent(new ClickEvent.OpenUrl(URI.create(url)))
                        .withHoverEvent(new HoverEvent.ShowText(Component.literal("Open your local Stockpile dashboard")))))
                .append(Component.literal("  [Copy link]").withStyle(style -> style.withColor(ChatFormatting.AQUA)
                        .withClickEvent(new ClickEvent.CopyToClipboard(url))
                        .withHoverEvent(new HoverEvent.ShowText(Component.literal("Copy the dashboard address to paste into your browser")))));
    }

    public static Component paused() {
        return prefix().append(Component.literal("Tracking paused. ").withStyle(ChatFormatting.GOLD))
                .append(Component.literal("[/stockpile start]").withStyle(style -> style.withColor(ChatFormatting.AQUA)
                        .withClickEvent(new ClickEvent.RunCommand("/stockpile start"))
                        .withHoverEvent(new HoverEvent.ShowText(Component.literal("Resume Stockpile tracking")))));
    }
}
