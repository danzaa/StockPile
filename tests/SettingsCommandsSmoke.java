import com.mojang.brigadier.CommandDispatcher;
import dev.danzaa.stockpile.StockpileCommands;
import dev.danzaa.stockpile.StockpileConfig;
import dev.danzaa.stockpile.StockpileMessages;
import dev.danzaa.stockpile.HighlightOptions;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import org.slf4j.LoggerFactory;

public final class SettingsCommandsSmoke {
    private static int checks;
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);checks++;}
    public static void main(String[] args)throws Exception{
        var logger=LoggerFactory.getLogger("stockpile-settings-test");
        var file=Files.createTempDirectory("stockpile-settings-").resolve("settings.json");
        StockpileConfig config=StockpileConfig.load(file,logger);
        check(!config.autoOpenBrowser,"Browser opening is opt-in");
        check(config.schematicChestEnabled,"Schematic chest stays enabled for existing users");
        check(config.schematicChestAutoClear,"Build chests auto-clear by default");
        check(config.showStartupMessage&&config.keybindEnabled&&config.trackingEnabled,"Expected initial controls");
        config.showStartupMessage=false;config.keybindEnabled=false;config.trackingEnabled=false;config.autoOpenBrowser=true;
        config.highlight=new HighlightOptions("#83db71",12,false);config.schematicChestEnabled=false;
        config.schematicChestAutoClear=false;config.save(file,logger);
        var restored=StockpileConfig.load(file,logger);
        check(!restored.showStartupMessage&&!restored.keybindEnabled&&!restored.trackingEnabled&&restored.autoOpenBrowser,"Persists all preferences");
        check(restored.highlight.equals(new HighlightOptions("#83db71",12,false)),"Highlight settings persist across client restarts");
        check(!restored.schematicChestEnabled,"Schematic chest preference survives restart");
        check(!restored.schematicChestAutoClear,"Schematic chest auto-clear preference survives restart");
        Files.writeString(file,"{\"showStartupMessage\":false}");
        var partial=StockpileConfig.load(file,logger);
        check(!partial.showStartupMessage&&partial.keybindEnabled&&!partial.autoOpenBrowser,"Missing settings retain defaults");
        check(partial.highlight.equals(HighlightOptions.DEFAULT)&&partial.schematicChestEnabled&&partial.schematicChestAutoClear,"Older files retain defaults for new preferences");
        AtomicInteger starts=new AtomicInteger(),stops=new AtomicInteger(),settings=new AtomicInteger(),links=new AtomicInteger(),opens=new AtomicInteger();
        var foundItem=new java.util.concurrent.atomic.AtomicReference<String>();
        AtomicInteger clears=new AtomicInteger(), team=new AtomicInteger();
        CommandDispatcher<Object> dispatcher=new CommandDispatcher<>();
        StockpileCommands.register(dispatcher,settings::incrementAndGet,starts::incrementAndGet,stops::incrementAndGet,links::incrementAndGet,opens::incrementAndGet,foundItem::set,clears::incrementAndGet,team::incrementAndGet);
        Object source=new Object();
        dispatcher.execute("stockpile start",source);check(starts.get()==1&&links.get()==0,"Start routes to the start action");
        dispatcher.execute("stockpile stop",source);dispatcher.execute("stop",source);check(stops.get()==2,"Both local stop aliases pause the mod");
        dispatcher.execute("stockpile settings",source);dispatcher.execute("stockpile",source);check(settings.get()==2,"Root command and settings open controls");
        dispatcher.execute("stockpile link",source);check(links.get()==1,"Link action works while paused");
        dispatcher.execute("stockpile open",source);check(opens.get()==1&&starts.get()==1,"Explicit browser opening is separate from tracking and auto-open preferences");
        dispatcher.execute("stockpile find minecraft:diamond",source);check("minecraft:diamond".equals(foundItem.get()),"Find accepts a namespaced item ID");
        dispatcher.execute("stockpile clearfind",source);check(clears.get()==1&&stops.get()==2,"Clearing find does not pause tracking");
        dispatcher.execute("stockpile team",source);check(team.get()==1,"The local team help command works without server permission");
        var first=StockpileMessages.startup(true,true,Component.literal("B"));
        var rebound=StockpileMessages.startup(true,true,Component.literal("Mouse 4"));
        check(first.getString().contains("[B]")&&!first.getString().contains("Mouse 4"),"Uses supplied key name");
        check(rebound.getString().contains("Mouse 4")&&!rebound.getString().contains("[B]"),"Rebinding changes the hint");
        var disabled=StockpileMessages.startup(false,false,Component.literal("B"));
        check(disabled.getString().contains("/stockpile start")&&!disabled.getString().contains("[B]"),"Disabled shortcut gets command fallback");
        var dashboard=StockpileMessages.dashboard("http://127.0.0.1:19482/#token=test",true);
        var link=dashboard.getSiblings().get(2);
        check(link.getStyle().getClickEvent() instanceof ClickEvent.OpenUrl,"Chat link is clickable");
        check(((ClickEvent.OpenUrl)link.getStyle().getClickEvent()).uri().getHost().equals("127.0.0.1"),"Link targets the local dashboard");
        var copy=dashboard.getSiblings().get(3).getStyle().getClickEvent();
        check(copy instanceof ClickEvent.CopyToClipboard&&((ClickEvent.CopyToClipboard)copy).value().equals("http://127.0.0.1:19482/#token=test"),"Chat can copy the complete dashboard link when URL opening is unavailable");
        check(StockpileMessages.paused().getString().contains("/stockpile start"),"Pause message provides resume action");
        System.out.println("Settings, commands and chat checks passed: "+checks);
    }
}
