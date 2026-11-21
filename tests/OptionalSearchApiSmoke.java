package dev.danzaa.stockpile;

import java.util.Collection;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.event.Event;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.ListTag;

/** Verifies the real optional binary API without initializing Fabric registries outside the game. */
public final class OptionalSearchApiSmoke {
    public static void main(String[] args) throws Exception {
        String base="red.jackf.whereisit.";
        ClassLoader loader=OptionalSearchApiSmoke.class.getClassLoader();
        var invoker=Class.forName(base+"client.api.events.SearchInvoker",false,loader);
        var request=Class.forName(base+"api.SearchRequest",false,loader);
        var result=Class.forName(base+"api.SearchResult",false,loader);
        var builder=Class.forName(base+"api.SearchResult$Builder",false,loader);
        if(!invoker.isInterface() || invoker.getMethod("search",request,Consumer.class).getReturnType()!=boolean.class ||
                invoker.getField("EVENT").getType()!=Event.class || request.getMethod("toTag").getReturnType()!=ListTag.class ||
                result.getMethod("builder",BlockPos.class).getReturnType()!=builder ||
                builder.getMethod("otherPositions",Collection.class).getReturnType()!=builder ||
                builder.getMethod("build").getReturnType()!=result)
            throw new AssertionError("Unsupported Chest Tracker / Where Is It search API");
        System.out.println("Installed Chest Tracker / Where Is It binary API signatures passed (in-game event behavior still requires playtesting)");
    }
}
