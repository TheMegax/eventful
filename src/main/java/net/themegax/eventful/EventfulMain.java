package net.themegax.eventful;

import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.event.CitizensEnableEvent;
import net.citizensnpcs.api.npc.NPC;
import net.themegax.eventful.Database.DatabaseManager;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;
import java.util.logging.Logger;

public final class EventfulMain extends JavaPlugin {

    private static Logger logger;
    private static DatabaseManager databaseManager;
    private static NPC citizenNPC;
    FileConfiguration config = getConfig();

    @Override
    public void onEnable() {
        databaseManager = new DatabaseManager();
        databaseManager.Initialize();
        logger = this.getLogger();

        var pluginManager = getServer().getPluginManager();
        pluginManager.registerEvents(new EventfulListener(), this);

        config.addDefault("thingy", 1);
        config.options().copyDefaults(true);
        saveConfig();

        // check if Citizens is present and enabled.
        var citizensPlugin = getServer().getPluginManager().getPlugin("Citizens");

        if (citizensPlugin == null || !citizensPlugin.isEnabled()) {
            GetLogger().log(Level.SEVERE, "Citizens 2.0 not found or not enabled");
        }
        else {
            Bukkit.getServer().getPluginManager().registerEvents(new Listener() {
                @EventHandler
                public void onCitizensEnable(CitizensEnableEvent ev) {
                    citizenNPC = CitizensAPI.getNPCRegistry().getById(1);
                }
            }, this);
        }
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }

    public static Logger GetLogger() {
        return logger;
    }

    public static DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public static void onNPCTick() {
        // Find the closest player and chase them.
        var minDist = 99999.0;
        Player closestPlayer = null;
        var npcLoc = citizenNPC.getEntity().getLocation();
        for (var player : Bukkit.getOnlinePlayers()) {
            var dist = player.getPlayer().getLocation().distance(npcLoc);
            if (dist > minDist) continue;
            
            minDist = dist;
            closestPlayer = player;
        }
        
        if (closestPlayer != null) {
            citizenNPC.getEntity().setSilent(true);
            citizenNPC.getNavigator().setTarget(closestPlayer.getLocation());
            citizenNPC.getNavigator().getLocalParameters().range(1000);
            citizenNPC.getNavigator().getLocalParameters().speedModifier(2);
        }
    }
}
