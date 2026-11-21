package dev.danzaa.stockpile;

import com.google.gson.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;

public final class SharedStorageClient {
    private final InventoryStore store;
    private final Supplier<String> source;
    private final Map<String,CompletableFuture<PlanningState.Result>> pending=new HashMap<>();
    private final Map<String,Long> received=new HashMap<>();
    public SharedStorageClient(InventoryStore store,Supplier<String> source){this.store=store;this.source=source;}
    public void register(){ClientPlayNetworking.registerGlobalReceiver(SharedPayload.TYPE,(packet,context)->context.client().execute(()->{
        try{
            String scope=source.get();if(scope==null)return;
            var data=JsonParser.parseString(packet.json()).getAsJsonObject();String kind=data.get("kind").getAsString();
            if(kind.equals("result")){var f=pending.remove(data.get("requestId").getAsString());if(f!=null)f.complete(new PlanningState.Result(data.get("status").getAsInt(),data.get("message").getAsString()));return;}
            String authority=UUID.fromString(data.get("authority").getAsString()).toString();
            if(kind.equals("roster")){
                Set<String> allowed=new HashSet<>();for(var id:data.getAsJsonArray("worlds")){String remote=id.getAsString();if(remote.matches("shared:"+authority+":[a-zA-Z0-9_-]{1,32}"))allowed.add(localId(scope,remote));}
                store.revokeShared(allowed,scope);received.keySet().removeIf(id->!allowed.contains(id));
            }else if(kind.equals("world")){
                var world=data.getAsJsonObject("world");String remote=world.get("id").getAsString();if(!remote.matches("shared:"+authority+":[a-zA-Z0-9_-]{1,32}"))throw new IllegalArgumentException();
                var list=world.getAsJsonArray("containers");if(list.size()>256)throw new IllegalArgumentException();
                var containers=new ArrayList<InventoryStore.Container>();
                for(var value:list)containers.add(SharedSnapshots.container(value));
                PlanningState planning=new Gson().fromJson(world.get("planning"),PlanningState.class);
                if(planning==null||planning.revision<0||planning.reservations.size()>200||planning.history.size()>1000)throw new IllegalArgumentException();
                planning.validate();
                String id=localId(scope,remote);String viewer=context.player().getUUID().toString();
                store.sharedSnapshot(id,world.get("name").getAsString(),scope,containers,planning,viewer,remote);received.put(id,System.currentTimeMillis());
            }
        }catch(RuntimeException e){StockpileClient.LOGGER.warn("Ignored invalid shared storage update",e);}
    }));}
    private static String localId(String scope,String remote){return "shared:"+scope+":"+remote.substring("shared:".length());}
    public CompletableFuture<PlanningState.Result> reserve(JsonObject data){
        var future=new CompletableFuture<PlanningState.Result>();
        Minecraft.getInstance().execute(()->{
            if(future.isDone())return;
            try{
                String id=data.get("worldId").getAsString();var world=store.worldSnapshot(id);String scope=source.get();
                if(scope==null||world==null||!world.get("live").getAsBoolean()||!scope.equals(world.get("authority").getAsString())||!ClientPlayNetworking.canSend(SharedPayload.TYPE)){future.complete(new PlanningState.Result(409,"Join the team server and wait for a fresh shared snapshot."));return;}
                pending.values().removeIf(CompletableFuture::isDone);
                if(pending.size()>=4){future.complete(new PlanningState.Result(429,"Wait for the current team request to finish."));return;}
                var query=data.deepCopy();String request=UUID.randomUUID().toString();query.addProperty("requestId",request);query.addProperty("worldId",world.get("remoteWorld").getAsString());
                String json=query.toString();if(json.length()>256000||SharedPayload.compress(json).length>28000){future.complete(new PlanningState.Result(413,"This reservation spans too many locations. Reserve a smaller build."));return;}
                pending.put(request,future);ClientPlayNetworking.send(new SharedPayload(json));
                future.orTimeout(8,TimeUnit.SECONDS);
            }catch(RuntimeException e){future.complete(new PlanningState.Result(503,"Shared storage is unavailable. Reconnect and retry."));}
        });
        return future;
    }
    public void tick(){long now=System.currentTimeMillis();for(var e:received.entrySet())if(now-e.getValue()>15000)store.sharedLive(e.getKey(),false);}
    public void disconnect(){store.sharedOffline();pending.values().forEach(f->f.complete(new PlanningState.Result(409,"Disconnected from the team server.")));pending.clear();received.clear();}
}
