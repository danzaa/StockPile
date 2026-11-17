package dev.danzaa.stockpile;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;

public final class StockpileCommands {
    public static <S> void register(CommandDispatcher<S> dispatcher, Runnable settings, Runnable start, Runnable stop, Runnable link) {
        dispatcher.register(LiteralArgumentBuilder.<S>literal("stockpile")
                .executes(context -> { settings.run(); return 1; })
                .then(LiteralArgumentBuilder.<S>literal("settings").executes(context -> { settings.run(); return 1; }))
                .then(LiteralArgumentBuilder.<S>literal("start").executes(context -> { start.run(); return 1; }))
                .then(LiteralArgumentBuilder.<S>literal("stop").executes(context -> { stop.run(); return 1; }))
                .then(LiteralArgumentBuilder.<S>literal("link").executes(context -> { link.run(); return 1; })));
        dispatcher.register(LiteralArgumentBuilder.<S>literal("stop").executes(context -> { stop.run(); return 1; }));
    }
}
