package dev.danzaa.stockpile;

import com.google.gson.*;
import java.util.*;

/** Validate shared observations before they can enter persistent local state. */
public final class SharedSnapshots {
    private static String text(JsonObject o,String key,int max){var v=o.get(key);if(v==null||!v.isJsonPrimitive()||!v.getAsJsonPrimitive().isString())throw new IllegalArgumentException("Invalid "+key);String s=v.getAsString();if(s.isBlank()||s.length()>max)throw new IllegalArgumentException("Invalid "+key);return s;}
    private static String id(JsonObject o,String key){String s=text(o,key,256);if(!s.matches("[a-z0-9_.-]+:[a-z0-9_./-]+"))throw new IllegalArgumentException("Invalid item or dimension");return s;}
    private static int count(JsonObject o,String key,int fallback){if(!o.has(key))return fallback;var v=o.get(key);if(!v.isJsonPrimitive()||!v.getAsJsonPrimitive().isNumber())throw new IllegalArgumentException();int n=v.getAsBigDecimal().intValueExact();if(n<0)throw new IllegalArgumentException();return n;}
    public static InventoryStore.Container container(JsonElement value){
        var o=value.getAsJsonObject();String dimension=id(o,"dimension"),type=text(o,"type",100),name=text(o,"name",150);
        if(type.endsWith("ender_chest")||text(o,"id",500).startsWith("ender:"))throw new IllegalArgumentException("Personal Ender Chests cannot be shared");
        var xyz=o.getAsJsonArray("position");if(xyz.size()!=3)throw new IllegalArgumentException();var position=new ArrayList<Integer>();for(var n:xyz)position.add(n.getAsBigDecimal().intValueExact());
        String main=dimension+":"+position.get(0)+","+position.get(1)+","+position.get(2);var members=new TreeSet<String>((a,b)->{String[] x=a.substring(dimension.length()+1).split(","),y=b.substring(dimension.length()+1).split(",");for(int i=0;i<3;i++){int n=Integer.compare(Integer.parseInt(x[i]),Integer.parseInt(y[i]));if(n!=0)return n;}return 0;});members.add(main);
        if(o.has("members")){var array=o.getAsJsonArray("members");if(array.size()>2)throw new IllegalArgumentException();for(var m:array){String member=m.getAsString();if(member.equals(main))continue;
            if(!type.endsWith("chest")||!member.startsWith(dimension+":"))throw new IllegalArgumentException();String[] p=member.substring(dimension.length()+1).split(",");
            if(p.length!=3||Integer.parseInt(p[1])!=position.get(1)||Math.abs((long)Integer.parseInt(p[0])-position.get(0))+Math.abs((long)Integer.parseInt(p[2])-position.get(2))!=1)throw new IllegalArgumentException();members.add(member);
        }}
        var items=new ArrayList<InventoryStore.Item>();var raw=o.getAsJsonArray("items");if(raw==null||raw.size()>200)throw new IllegalArgumentException();Set<String> seen=new HashSet<>();
        for(var entry:raw){var i=entry.getAsJsonObject();String item=id(i,"id");if(!seen.add(item))throw new IllegalArgumentException();items.add(new InventoryStore.Item(item,i.has("name")?text(i,"name",150):item,count(i,"count",0),Math.max(1,count(i,"maxStack",64))));}
        var packed=new ArrayList<InventoryStore.PackedItem>();if(o.has("packedItems")){raw=o.getAsJsonArray("packedItems");if(raw.size()>10000)throw new IllegalArgumentException();for(var entry:raw){var i=entry.getAsJsonObject();var paths=i.getAsJsonArray("path");if(paths==null||paths.isEmpty()||paths.size()>8)throw new IllegalArgumentException();var path=new ArrayList<String>();for(var part:paths){String text=part.getAsString();if(text.length()>180)throw new IllegalArgumentException();path.add(text);}String item=id(i,"id");packed.add(new InventoryStore.PackedItem(item,i.has("name")?text(i,"name",150):item,count(i,"count",0),Math.max(1,count(i,"maxStack",64)),List.copyOf(path)));}}
        long at=o.has("lastSeen")?o.get("lastSeen").getAsBigDecimal().longValueExact():0;if(at<0)throw new IllegalArgumentException();at=Math.min(at,System.currentTimeMillis());
        return new InventoryStore.Container(String.join("|",members),name,type,dimension,List.copyOf(position),List.copyOf(members),at,List.copyOf(items),List.copyOf(packed),!o.has("packedScanComplete")||o.get("packedScanComplete").getAsBoolean());
    }
    private SharedSnapshots(){}
}
