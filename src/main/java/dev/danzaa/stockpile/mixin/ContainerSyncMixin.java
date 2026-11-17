package dev.danzaa.stockpile.mixin;

import dev.danzaa.stockpile.StockpileClient;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class ContainerSyncMixin {
    @Inject(method = "handleOpenScreen", at = @At("TAIL"))
    private void stockpile$open(ClientboundOpenScreenPacket packet, CallbackInfo ci) {
        if (StockpileClient.INSTANCE != null) StockpileClient.INSTANCE.openingContainer();
    }

    @Inject(method = "handleContainerContent", at = @At("TAIL"))
    private void stockpile$contents(ClientboundContainerSetContentPacket packet, CallbackInfo ci) {
        if (StockpileClient.INSTANCE != null) StockpileClient.INSTANCE.fullSync(packet.containerId());
    }

    @Inject(method = "handleContainerSetSlot", at = @At("TAIL"))
    private void stockpile$slot(ClientboundContainerSetSlotPacket packet, CallbackInfo ci) {
        if (StockpileClient.INSTANCE != null) StockpileClient.INSTANCE.slotSync(packet.getContainerId());
    }
}
