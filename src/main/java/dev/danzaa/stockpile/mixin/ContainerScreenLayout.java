package dev.danzaa.stockpile.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AbstractContainerScreen.class)
public interface ContainerScreenLayout {
    @Accessor("leftPos") int stockpile$getLeft();
    @Accessor("topPos") int stockpile$getTop();
    @Accessor("imageWidth") int stockpile$getWidth();
    @Accessor("imageHeight") int stockpile$getHeight();
}
