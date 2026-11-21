package dev.danzaa.stockpile;

import com.google.gson.*;
import com.mojang.brigadier.arguments.StringArgumentType;
import java.nio.file.Path;
import java.util.*;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.*;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.Container;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.LoggerFactory;

public final class SharedStorageServer {
    private static final org.slf4j.Logger LOG=LoggerFactory.getLogger("stockpile-server");
    private static final Gson GSON=new Gson();
    private static final Map<UUID,Long> requests=new HashMap<>();
    private static SharedTeamRegistry teams;
    private static InventoryStore store;
    private static Path grants;
    private static int ticks;
    public static void register(){
        ServerLifecycleEvents.SERVER_STARTED.register(server->{
            grants=server.getWorldPath(LevelResource.ROOT).resolve("stockpile/shared-teams.json");
            try{teams=SharedTeamRegistry.load(grants);store=new InventoryStore(grants.resolveSibling("shared-inventory.json"),LOG);}
            catch(Exception e){teams=null;LOG.error("Stockpile team service disabled; preserve and repair its configuration before restarting",e);}
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server->{if(store!=null)store.close();store=null;teams=null;requests.clear();});
        ServerTickEvents.END_SERVER_TICK.register(server->{if(teams!=null&&store!=null&&++ticks%100==0){scan(server);broadcast(server);}});
        ServerPlayNetworking.registerGlobalReceiver(SharedPayload.TYPE,(payload,context)->{
            if(teams==null||store==null||payload.json().length()>256000)return;
            var player=context.player();String requestId="";
            try{
                var query=JsonParser.parseString(payload.json()).getAsJsonObject();requestId=query.get("requestId").getAsString();
                if(!requestId.matches("[a-zA-Z0-9-]{1,64}"))return;
                long now=System.currentTimeMillis(),last=requests.getOrDefault(player.getUUID(),0L);
                if(now-last<500){respond(player,requestId,new PlanningState.Result(429,"Wait a moment before changing shared reservations again."));return;}
                requests.put(player.getUUID(),now);
                String world=query.get("worldId").getAsString();
                var team=teams.teams.keySet().stream().filter(id->teams.worldId(id).equals(world)).findFirst().orElse(null);
                if(team==null||!teams.allowed(team,player.getUUID().toString())){respond(player,requestId,new PlanningState.Result(403,"You are not a member of this team."));return;}
                var result=store.reservation(query,player.getUUID().toString());
                respond(player,requestId,result);broadcast(context.server());
            }catch(RuntimeException e){if(!requestId.isEmpty())respond(player,requestId,new PlanningState.Result(400,"Invalid shared reservation request."));}
        });
        CommandRegistrationCallback.EVENT.register((dispatcher,access,environment)->dispatcher.register(Commands.literal("stockpileteam")
                .requires(s->s.permissions().hasPermission(Permissions.COMMANDS_ADMIN))
                .executes(c -> {
                    c.getSource().sendSuccess(() -> Component.literal("Stockpile team commands: create <team>, add <team> <player>, share <team> <x> <y> <z>, remove <team> <player>, unshare <team> <x> <y> <z>."), false);
                    return 1;
                })
                .then(Commands.literal("create").then(Commands.argument("team",StringArgumentType.word()).executes(c->edit(c.getSource(),()->teams.create(StringArgumentType.getString(c,"team"))))))
                .then(Commands.literal("add").then(Commands.argument("team",StringArgumentType.word()).then(Commands.argument("player",StringArgumentType.word()).executes(c->member(c.getSource(),StringArgumentType.getString(c,"team"),StringArgumentType.getString(c,"player"),true)))))
                .then(Commands.literal("remove").then(Commands.argument("team",StringArgumentType.word()).then(Commands.argument("player",StringArgumentType.word()).executes(c->member(c.getSource(),StringArgumentType.getString(c,"team"),StringArgumentType.getString(c,"player"),false)))))
                .then(Commands.literal("share").then(Commands.argument("team",StringArgumentType.word()).then(Commands.argument("pos",BlockPosArgument.blockPos()).executes(c->location(c.getSource(),StringArgumentType.getString(c,"team"),BlockPosArgument.getLoadedBlockPos(c,"pos"),true)))))
                .then(Commands.literal("unshare").then(Commands.argument("team",StringArgumentType.word()).then(Commands.argument("pos",BlockPosArgument.blockPos()).executes(c->location(c.getSource(),StringArgumentType.getString(c,"team"),BlockPosArgument.getBlockPos(c,"pos"),false)))))));
    }
    private static int edit(CommandSourceStack source,Runnable action){
        if(teams==null||store==null){source.sendFailure(Component.literal("Stockpile team service is unavailable; check server logs."));return 0;}
        String before=GSON.toJson(teams);
        try{action.run();teams.save(grants);scan(source.getServer());broadcast(source.getServer());source.sendSuccess(()->Component.literal("Stockpile shared storage updated."),true);return 1;}
        catch(Exception e){teams=GSON.fromJson(before,SharedTeamRegistry.class);source.sendFailure(Component.literal(e.getMessage()==null?"Could not update team storage.":e.getMessage()));return 0;}
    }
    private static int member(CommandSourceStack source,String team,String who,boolean add){
        String[] member={null};
        int result=edit(source,()->{
            var player=source.getServer().getPlayerList().getPlayerByName(who);
            String uuid=player==null?UUID.fromString(who).toString():player.getUUID().toString();member[0]=uuid;
            var members=teams.team(team).members;
            if(add){if(members.size()>=128)throw new IllegalArgumentException("Team member limit reached.");members.add(uuid);}else members.remove(uuid);
        });
        if(result==1&&!add){store.releaseOwned(teams.worldId(team),member[0]);broadcast(source.getServer());}
        return result;
    }
    private static int location(CommandSourceStack source,String team,BlockPos pos,boolean add){return edit(source,()->{
        var selected=teams.team(team);String dimension=source.getLevel().dimension().identifier().toString();
        var c=read(source.getLevel(),pos);
        List<String> positions=c==null?List.of(key(dimension,pos)):c.members();
        if(add){for(var e:teams.teams.entrySet())if(!e.getKey().equals(team)&&positions.stream().anyMatch(e.getValue().locations::contains))throw new IllegalArgumentException("A storage block can belong to only one team.");if(c==null)throw new IllegalArgumentException("Select a loaded chest, barrel or placed shulker; personal Ender Chests are never shared.");
            if(selected.locations.size()+positions.stream().filter(p->!selected.locations.contains(p)).count()>128)throw new IllegalArgumentException("Team limit: 128 physical storage blocks.");selected.locations.addAll(positions);
        }else{selected.locations.removeAll(positions);
            for(var old:store.containers(teams.worldId(team)))if(old.members().stream().anyMatch(positions::contains))store.forget(teams.worldId(team),old.id());}
    });}
    private static String key(String dimension,BlockPos p){return dimension+":"+p.getX()+","+p.getY()+","+p.getZ();}
    private static InventoryStore.Container read(ServerLevel level,BlockPos clicked){
        if(!level.hasChunkAt(clicked))return null;
        var entity=level.getBlockEntity(clicked);
        if(!(entity instanceof ChestBlockEntity||entity instanceof BarrelBlockEntity||entity instanceof ShulkerBoxBlockEntity))return null;
        var state=level.getBlockState(clicked);Container slots=(Container)entity;
        List<BlockPos> parts=new ArrayList<>();parts.add(clicked);
        if(state.getBlock() instanceof ChestBlock chest&&state.hasProperty(ChestBlock.TYPE)&&state.getValue(ChestBlock.TYPE)!=ChestType.SINGLE){
            BlockPos other=ChestBlock.getConnectedBlockPos(clicked,state);if(!level.hasChunkAt(other))return null;
            parts.add(other);slots=ChestBlock.getContainer(chest,state,level,clicked,true);if(slots==null)return null;
        }
        parts.sort(Comparator.comparingInt((BlockPos p)->p.getX()).thenComparingInt(BlockPos::getY).thenComparingInt(BlockPos::getZ));
        String dimension=level.dimension().identifier().toString();var members=parts.stream().map(p->key(dimension,p)).toList();
        var scanner=new ItemStackScanner();for(int i=0;i<slots.getContainerSize();i++)scanner.add(slots.getItem(i),i);var scan=scanner.result();
        var p=parts.getFirst();String type=BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
        return new InventoryStore.Container(String.join("|",members),"Shared "+type,type,dimension,List.of(p.getX(),p.getY(),p.getZ()),members,System.currentTimeMillis(),scan.items(),scan.packedItems().stream().limit(256).toList(),scan.complete()&&scan.packedItems().size()<=256);
    }
    private static void scan(MinecraftServer server){
        requests.values().removeIf(at->System.currentTimeMillis()-at>60000);
        for(var entry:teams.teams.entrySet()){
            String worldId=teams.worldId(entry.getKey());var team=entry.getValue();Set<String> seen=new HashSet<>(),loaded=new HashSet<>();
            for(var level:server.getAllLevels())for(String location:team.locations){
                String prefix=level.dimension().identifier()+":";if(!location.startsWith(prefix))continue;
                try{String[] xyz=location.substring(prefix.length()).split(",");var pos=new BlockPos(Integer.parseInt(xyz[0]),Integer.parseInt(xyz[1]),Integer.parseInt(xyz[2]));
                    if(!level.hasChunkAt(pos))continue;loaded.add(location);var c=read(level,pos);
                    if(c!=null&&team.locations.containsAll(c.members())&&seen.add(c.id()))store.put(worldId,"Team · "+team.name,c);
                }catch(RuntimeException e){LOG.warn("Invalid shared container grant: {}",location);}
            }
            for(var old:store.containers(worldId))if(!seen.contains(old.id())&&(!team.locations.containsAll(old.members())||loaded.containsAll(old.members())))store.forget(worldId,old.id());
        }
    }
    private static void broadcast(MinecraftServer server){
        for(var player:server.getPlayerList().getPlayers())if(ServerPlayNetworking.canSend(player,SharedPayload.TYPE)){
            var roster=new JsonObject();roster.addProperty("kind","roster");roster.addProperty("authority",teams.authority);var allowed=new JsonArray();
            for(var e:teams.teams.entrySet())if(teams.allowed(e.getKey(),player.getUUID().toString()))allowed.add(teams.worldId(e.getKey()));
            roster.add("worlds",allowed);send(player,roster);
            for(var e:teams.teams.entrySet())if(teams.allowed(e.getKey(),player.getUUID().toString())){
                var world=store.worldSnapshot(teams.worldId(e.getKey()));if(world==null){world=new JsonObject();world.addProperty("id",teams.worldId(e.getKey()));world.addProperty("name","Team · "+e.getValue().name);world.add("containers",new JsonArray());world.add("planning",GSON.toJsonTree(new PlanningState()));}
                world.addProperty("viewer",player.getUUID().toString());var packet=new JsonObject();packet.addProperty("kind","world");packet.addProperty("authority",teams.authority);packet.add("world",world);send(player,packet);
            }
        }
    }
    private static void respond(net.minecraft.server.level.ServerPlayer player,String id,PlanningState.Result result){var o=new JsonObject();o.addProperty("kind","result");o.addProperty("requestId",id);o.addProperty("status",result.status());o.addProperty("message",result.message());send(player,o);}
    private static void send(net.minecraft.server.level.ServerPlayer player,JsonObject data){try{String json=data.toString();SharedPayload.compress(json);ServerPlayNetworking.send(player,new SharedPayload(json));}catch(RuntimeException e){LOG.warn("Shared workspace could not be sent to {}: {}",player.getUUID(),e.getMessage());}}
    private SharedStorageServer(){}
}
