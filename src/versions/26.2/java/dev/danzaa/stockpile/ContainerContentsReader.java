package dev.danzaa.stockpile;

import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;

final class ContainerContentsReader {
    static List<ItemStack> copy(ItemContainerContents contents) {
        return contents.allItemsCopyStream().limit(257).toList();
    }
}
