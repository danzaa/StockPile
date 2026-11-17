import dev.danzaa.stockpile.ItemStackScanner;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;

public final class ShulkerScannerSmoke {
    private static int checks;
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);checks++;}
    private static ItemStack box(int amount){
        ItemStack box=new ItemStack(Items.SHULKER_BOX);
        box.set(DataComponents.CUSTOM_NAME,Component.literal("Building supplies"));
        box.set(DataComponents.CONTAINER,ItemContainerContents.fromItems(List.of(new ItemStack(Items.IRON_INGOT,amount),new ItemStack(Items.DIAMOND,5))));
        return box;
    }
    public static void main(String[] args){
        SharedConstants.tryDetectVersion();Bootstrap.bootStrap();
        for(var item:List.of(Items.IRON_INGOT,Items.DIAMOND,Items.SHULKER_BOX,Items.CHEST)){
            item.builtInRegistryHolder().bindComponents(DataComponentMap.builder()
                    .set(DataComponents.MAX_STACK_SIZE,item==Items.SHULKER_BOX?1:64)
                    .set(DataComponents.ITEM_NAME,Component.literal(item==Items.SHULKER_BOX?"Shulker Box":"Test item"))
                    .build());
        }
        ItemStackScanner scanner=new ItemStackScanner();scanner.add(new ItemStack(Items.IRON_INGOT,20),0);scanner.add(box(64),3);
        var result=scanner.result();
        check(result.items().stream().filter(i->i.id().equals("minecraft:iron_ingot")).findFirst().orElseThrow().count()==20,"Loose counts stay separate");
        check(result.packedItems().stream().filter(i->i.id().equals("minecraft:iron_ingot")).findFirst().orElseThrow().count()==64,"Reads actual Minecraft container component");
        check(result.packedItems().getFirst().path().getFirst().equals("Building supplies (slot 4)"),"Preserves box name and parent slot");
        check(result.complete(),"Normal box scan is complete");
        ItemStackScanner changed=new ItemStackScanner();changed.add(box(32),3);
        check(changed.result().packedItems().getFirst().count()==32,"Changed packed quantities refresh");
        check(!changed.result().packedItems().equals(result.packedItems()),"Packed changes affect snapshot equality");
        ItemStackScanner duplicates=new ItemStackScanner();duplicates.add(box(64),1);duplicates.add(box(64),2);
        check(duplicates.result().packedItems().stream().filter(i->i.id().equals("minecraft:iron_ingot")).mapToInt(i->i.count()).sum()==128,"Identical boxes each contribute once");
        ItemStack outer=box(1);outer.set(DataComponents.CONTAINER,ItemContainerContents.fromItems(List.of(box(16))));
        ItemStackScanner nested=new ItemStackScanner();nested.add(outer,0);
        check(nested.result().packedItems().stream().anyMatch(i->i.id().equals("minecraft:iron_ingot")&&i.count()==16&&i.path().size()==2),"Nested locations are retained");
        ItemStack sparse=box(1);sparse.set(DataComponents.CONTAINER,ItemContainerContents.fromItems(List.of(ItemStack.EMPTY,ItemStack.EMPTY,box(16))));
        ItemStackScanner sparseScanner=new ItemStackScanner();sparseScanner.add(sparse,0);
        check(sparseScanner.result().packedItems().stream().anyMatch(i->i.id().equals("minecraft:iron_ingot")&&i.path().size()==2&&i.path().get(1).equals("Building supplies (slot 3)")),"Empty internal slots retain the nested box's real slot number");
        ItemStack deeplyNested=box(1);
        for(int depth=0;depth<10;depth++){ItemStack next=box(1);next.set(DataComponents.CONTAINER,ItemContainerContents.fromItems(List.of(deeplyNested)));deeplyNested=next;}
        ItemStackScanner bounded=new ItemStackScanner();bounded.add(deeplyNested,0);
        check(!bounded.result().complete(),"Path limit is flagged instead of silently approving counts");
        ItemStack chest=new ItemStack(Items.CHEST);chest.set(DataComponents.CONTAINER,ItemContainerContents.fromItems(List.of(new ItemStack(Items.DIAMOND,9))));
        ItemStackScanner excluded=new ItemStackScanner();excluded.add(chest,0);
        check(excluded.result().packedItems().isEmpty(),"Only shulker item contents are expanded");
        System.out.println("Minecraft "+System.getProperty("stockpile.test.minecraft","selected target")+" shulker component checks passed: "+checks);
    }
}
