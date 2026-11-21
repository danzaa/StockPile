package dev.danzaa.stockpile;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.arguments.StringArgumentType;
import java.util.function.Consumer;

public final class StockpileCommands {
    public static <S> void register(CommandDispatcher<S> dispatcher, Runnable settings, Runnable start, Runnable stop, Runnable link, Runnable open) {
        register(dispatcher, settings, start, stop, link, open, item -> {}, () -> {});
    }
    public static <S> void register(CommandDispatcher<S> dispatcher, Runnable settings, Runnable start, Runnable stop, Runnable link, Runnable open, Consumer<String> find, Runnable clearFind) {
        register(dispatcher, settings, start, stop, link, open, find, clearFind, () -> {});
    }
    public static <S> void register(CommandDispatcher<S> dispatcher, Runnable settings, Runnable start, Runnable stop, Runnable link, Runnable open, Consumer<String> find, Runnable clearFind, Runnable team) {
        dispatcher.register(LiteralArgumentBuilder.<S>literal("stockpile")
                .executes(context -> { settings.run(); return 1; })
                .then(LiteralArgumentBuilder.<S>literal("settings").executes(context -> { settings.run(); return 1; }))
                .then(LiteralArgumentBuilder.<S>literal("start").executes(context -> { start.run(); return 1; }))
                .then(LiteralArgumentBuilder.<S>literal("stop").executes(context -> { stop.run(); return 1; }))
                .then(LiteralArgumentBuilder.<S>literal("link").executes(context -> { link.run(); return 1; }))
                .then(LiteralArgumentBuilder.<S>literal("open").executes(context -> { open.run(); return 1; }))
                .then(LiteralArgumentBuilder.<S>literal("team").executes(context -> { team.run(); return 1; }))
                .then(LiteralArgumentBuilder.<S>literal("find")
                        .then(RequiredArgumentBuilder.<S, String>argument("item", StringArgumentType.greedyString())
                                .executes(context -> { find.accept(StringArgumentType.getString(context, "item")); return 1; })))
                .then(LiteralArgumentBuilder.<S>literal("clearfind").executes(context -> { clearFind.run(); return 1; })));
        dispatcher.register(LiteralArgumentBuilder.<S>literal("stop").executes(context -> { stop.run(); return 1; }));
    }
}
