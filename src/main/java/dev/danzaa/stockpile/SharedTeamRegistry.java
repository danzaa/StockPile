package dev.danzaa.stockpile;

import com.google.gson.*;
import java.nio.file.*;
import java.util.*;

/** Explicit server administrator grants. No team exists until an operator creates it. */
public final class SharedTeamRegistry {
    public static final class Team {
        public String name;
        public Set<String> members=new LinkedHashSet<>();
        public Set<String> locations=new LinkedHashSet<>();
        Team(String name){this.name=name;}
    }
    public String authority=UUID.randomUUID().toString();
    public Map<String,Team> teams=new LinkedHashMap<>();
    public Team create(String id){
        if(!id.matches("[a-zA-Z0-9_-]{1,32}")||teams.containsKey(id)||teams.size()>=32)throw new IllegalArgumentException("Use a new team name (1–32 letters, digits, _ or -); maximum 32 teams.");
        var team=new Team(id);teams.put(id,team);return team;
    }
    public Team team(String id){var team=teams.get(id);if(team==null)throw new IllegalArgumentException("Unknown Stockpile team.");return team;}
    public boolean allowed(String team,String player){return teams.containsKey(team)&&teams.get(team).members.contains(player);}
    public String worldId(String team){return "shared:"+authority+":"+team;}
    public static SharedTeamRegistry load(Path file)throws java.io.IOException{
        if(!Files.exists(file))return new SharedTeamRegistry();
        if(Files.size(file)>1024*1024)throw new java.io.IOException("Team configuration exceeds 1 MB");
        var registry=new Gson().fromJson(Files.readString(file),SharedTeamRegistry.class);
        UUID.fromString(registry.authority);
        if(registry.teams.size()>32)throw new IllegalArgumentException("Too many teams");
        for(var e:registry.teams.entrySet()){
            if(!e.getKey().matches("[a-zA-Z0-9_-]{1,32}")||e.getValue().members.size()>128||e.getValue().locations.size()>128)throw new IllegalArgumentException("Invalid team grants");
            e.getValue().members.forEach(UUID::fromString);
        }
        return registry;
    }
    public void save(Path file)throws java.io.IOException{
        Files.createDirectories(file.getParent());var temp=file.resolveSibling(file.getFileName()+".tmp");
        Files.writeString(temp,new GsonBuilder().setPrettyPrinting().create().toJson(this));
        try{Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}catch(AtomicMoveNotSupportedException e){Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING);}
    }
}
