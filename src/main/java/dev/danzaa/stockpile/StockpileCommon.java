package dev.danzaa.stockpile;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

public final class StockpileCommon implements ModInitializer {
    @Override public void onInitialize(){
        PayloadTypeRegistry.clientboundPlay().register(SharedPayload.TYPE,SharedPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(SharedPayload.TYPE,SharedPayload.SERVERBOUND_CODEC);
        SharedStorageServer.register();
    }
}
