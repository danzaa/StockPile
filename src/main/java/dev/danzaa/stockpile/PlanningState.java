package dev.danzaa.stockpile;

import com.google.gson.*;
import java.util.*;

/** Persistent planning data. Callers serialize mutations under InventoryStore's lock. */
public final class PlanningState {
    public record Claim(String containerId, String itemId, boolean packed, long count) {
        public Claim {
            if (containerId == null || containerId.isBlank() || containerId.length()>500 || itemId == null ||
                    !itemId.matches("[a-z0-9_.-]+:[a-z0-9_./-]+") || itemId.length()>256 || count<1 || count>Integer.MAX_VALUE)
                throw new IllegalArgumentException("Invalid material claim");
        }
        public String key() { return containerId + "\n" + itemId + "\n" + packed; }
    }
    public record Reservation(String projectId, String name, String owner, long updatedAt, String status, List<Claim> claims) {}
    public record Change(String itemId, long delta) {}
    public record Observation(long at, String containerId, String type, String dimension, String reason, List<Change> changes, boolean truncated) {}
    public record Result(int status, String message) {}
    public final Map<String,Reservation> reservations = new LinkedHashMap<>();
    public final List<Observation> history = new ArrayList<>();
    public long revision;

    public static Map<String,Long> stock(Collection<InventoryStore.Container> containers) {
        Map<String,Long> result=new LinkedHashMap<>();
        for(var c:containers) {
            for(var i:c.items()) if(i.count()>0) result.merge(new Claim(c.id(),i.id(),false,i.count()).key(),(long)i.count(),Long::sum);
            if(c.packedItems()!=null) for(var i:c.packedItems()) if(i.count()>0) result.merge(new Claim(c.id(),i.id(),true,i.count()).key(),(long)i.count(),Long::sum);
        }
        return result;
    }
    public Result reserve(String projectId,String name,String owner,long expected,List<Claim> claims,Collection<InventoryStore.Container> containers) {
        if(projectId==null || !projectId.matches("[a-zA-Z0-9_.:-]{1,160}") || name==null || name.isBlank() || name.length()>150 || claims==null || claims.isEmpty() || claims.size()>5000)
            return new Result(400,"Invalid project or claims. Reserve a nonempty feasible material plan.");
        if(expected!=revision) return new Result(409,"Inventory or reservations changed. Refresh and retry.");
        var previous=reservations.get(projectId);
        if(previous!=null && !Objects.equals(previous.owner(),owner)) return new Result(403,"This reservation belongs to another teammate.");
        if(previous==null && reservations.size()>=200) return new Result(409,"Reservation limit reached. Remove completed entries first.");
        Map<String,Long> free=stock(containers);
        for(var r:reservations.values()) if(r.status().equals("active")&&!r.projectId().equals(projectId))
            for(var c:r.claims()) free.merge(c.key(),-c.count(),Long::sum);
        Map<String,Long> wanted=new LinkedHashMap<>();
        for(var c:claims) wanted.merge(c.key(),c.count(),Long::sum);
        for(var entry:wanted.entrySet()) if(entry.getValue()>Math.max(0,free.getOrDefault(entry.getKey(),0L)))
            return new Result(409,"These materials are no longer free. Refresh the inventory and adjust the plan.");
        reservations.put(projectId,new Reservation(projectId,name,owner,System.currentTimeMillis(),"active",List.copyOf(claims)));
        revision++;
        return new Result(200,"Materials reserved for " + name + ".");
    }
    public Result release(String projectId,String owner,boolean complete,long expected) {
        if(expected!=revision) return new Result(409,"Reservations changed. Refresh and retry.");
        var r=reservations.get(projectId);
        if(r==null) return new Result(404,"Reservation not found.");
        if(!Objects.equals(owner,r.owner())) return new Result(403,"This reservation belongs to another teammate.");
        if(complete) reservations.put(projectId,new Reservation(r.projectId(),r.name(),r.owner(),System.currentTimeMillis(),"completed",List.of()));
        else reservations.remove(projectId);
        revision++;
        return new Result(200,complete?"Project completed; its material claims are released.":"Material reservation released.");
    }
    public void remap(Set<String> oldIds,String newId) {
        reservations.replaceAll((key,r)->new Reservation(r.projectId(),r.name(),r.owner(),r.updatedAt(),r.status(),r.claims().stream()
                .map(c->oldIds.contains(c.containerId())?new Claim(newId,c.itemId(),c.packed(),c.count()):c).toList()));
    }
    public void observe(Collection<InventoryStore.Container> before,Collection<InventoryStore.Container> after,InventoryStore.Container at,String reason) {
        Map<String,Long> changes=new TreeMap<>();
        for(var c:before) totals(c).forEach((id,n)->changes.merge(id,-n,Long::sum));
        for(var c:after) totals(c).forEach((id,n)->changes.merge(id,n,Long::sum));
        var deltas=changes.entrySet().stream().filter(e->e.getValue()!=0).map(e->new Change(e.getKey(),e.getValue())).toList();
        if(deltas.isEmpty() && !reason.equals("forgotten")) return;
        history.add(new Observation(System.currentTimeMillis(),at.id(),at.type(),at.dimension(),reason,deltas.stream().limit(512).toList(),deltas.size()>512));
        revision++;
        prune();
    }
    private static Map<String,Long> totals(InventoryStore.Container c) {
        Map<String,Long> result=new LinkedHashMap<>();
        c.items().forEach(i->result.merge(i.id(),(long)i.count(),Long::sum));
        if(c.packedItems()!=null)c.packedItems().forEach(i->result.merge(i.id(),(long)i.count(),Long::sum));
        return result;
    }
    public void prune() {
        long cutoff=System.currentTimeMillis()-30L*86400000;
        history.removeIf(e->e.at()<cutoff);
        while(history.size()>1000 || history.stream().mapToInt(e->Math.max(1,e.changes().size())).sum()>10000) history.removeFirst();
    }
    public void validate(){
        if(revision<0||revision>9007199254740991L||reservations==null||reservations.size()>200||history==null||history.size()>1000)throw new IllegalArgumentException("Invalid planning state");
        for(var e:reservations.entrySet()){var r=e.getValue();if(r==null||!e.getKey().equals(r.projectId())||!e.getKey().matches("[a-zA-Z0-9_.:-]{1,160}")||r.name()==null||r.name().length()>150||r.owner()==null||r.owner().length()>200||!List.of("active","completed").contains(r.status())||r.claims()==null||r.claims().size()>5000)throw new IllegalArgumentException("Invalid reservation");}
        for(var e:history){if(e.at()<0||e.containerId()==null||e.type()==null||e.dimension()==null||!List.of("observed","discovered","forgotten","replacement","imported","transfer").contains(e.reason())||e.changes()==null||e.changes().size()>512)throw new IllegalArgumentException("Invalid history");
            for(var change:e.changes())if(change.itemId()==null||!change.itemId().matches("[a-z0-9_.-]+:[a-z0-9_./-]+"))throw new IllegalArgumentException("Invalid stock change");}
    }
    public static List<Claim> claims(JsonArray array) {
        if(array==null || array.size()>5000) throw new IllegalArgumentException("Too many claims");
        List<Claim> claims=new ArrayList<>();
        for(var value:array){var o=value.getAsJsonObject();
            if(!o.get("packed").isJsonPrimitive() || !o.get("packed").getAsJsonPrimitive().isBoolean() || !o.get("count").isJsonPrimitive() || !o.get("count").getAsJsonPrimitive().isNumber())throw new IllegalArgumentException();
            long n=o.get("count").getAsBigDecimal().longValueExact();
            claims.add(new Claim(o.get("containerId").getAsString(),o.get("itemId").getAsString(),o.get("packed").getAsBoolean(),n));}
        return claims;
    }
}
