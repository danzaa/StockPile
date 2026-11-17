package dev.danzaa.stockpile;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ShulkerBoxBlock;

public final class ItemStackScanner {
    public record Result(List<InventoryStore.Item> items, List<InventoryStore.PackedItem> packedItems, boolean complete) {}
    private final Map<String, InventoryStore.Item> loose = new LinkedHashMap<>();
    private final List<InventoryStore.PackedItem> packed = new ArrayList<>();
    private boolean complete = true;

    public void add(ItemStack stack, int slot) {
        if (stack.isEmpty()) return;
        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        InventoryStore.Item previous = loose.get(id);
        loose.put(id, new InventoryStore.Item(id, stack.getItemName().getString(),
                Math.addExact(stack.getCount(), previous == null ? 0 : previous.count()), stack.getMaxStackSize()));
        inspect(stack, List.of(label(stack) + " (slot " + (slot + 1) + ")"), stack.getCount(), 0);
    }

    private static String label(ItemStack stack) {
        var custom = stack.get(DataComponents.CUSTOM_NAME);
        return custom == null ? stack.getItemName().getString() : custom.getString();
    }

    private void inspect(ItemStack box, List<String> path, int multiplier, int depth) {
        if (!(box.getItem() instanceof BlockItem blockItem) || !(blockItem.getBlock() instanceof ShulkerBoxBlock)) return;
        var contents = box.get(DataComponents.CONTAINER);
        if (contents == null) return;
        var items = ContainerContentsReader.copy(contents);
        if (items.isEmpty()) return;
        if (depth >= 8 || items.size() > 256) { complete = false; return; }
        for (int slot = 0; slot < items.size(); slot++) {
            ItemStack child = items.get(slot);
            if (child.isEmpty()) continue;
            if (packed.size() >= 10_000) { complete = false; return; }
            int amount;
            try { amount = Math.multiplyExact(child.getCount(), multiplier); }
            catch (ArithmeticException overflow) { complete = false; continue; }
            String id = BuiltInRegistries.ITEM.getKey(child.getItem()).toString();
            packed.add(new InventoryStore.PackedItem(id, child.getItemName().getString(), amount, child.getMaxStackSize(), path));
            List<String> nested = new ArrayList<>(path);
            nested.add(label(child) + " (slot " + (slot + 1) + ")");
            inspect(child, List.copyOf(nested), amount, depth + 1);
        }
    }

    public Result result() { return new Result(List.copyOf(loose.values()), List.copyOf(packed), complete); }
}
