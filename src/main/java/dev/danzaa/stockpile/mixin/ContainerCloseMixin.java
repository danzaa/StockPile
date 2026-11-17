package dev.danzaa.stockpile.mixin;

import dev.danzaa.stockpile.StockpileClient;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LocalPlayer.class)
public abstract class ContainerCloseMixin {
    @Inject(method = "clientSideCloseContainer", at = @At("HEAD"))
    private void stockpile$close(CallbackInfo ci) {
        if (StockpileClient.INSTANCE != null) StockpileClient.INSTANCE.closingContainer();
    }
}
