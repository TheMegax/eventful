package net.themegax.eventful;

import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.event.CitizensEnableEvent;
import net.citizensnpcs.api.npc.NPC;
import net.themegax.eventful.Database.DatabaseManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Dictionary;
import java.util.Hashtable;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class EventfulMain extends JavaPlugin {

    private static Logger logger;
    private static DatabaseManager databaseManager;
    private static Boolean isCitizensEnabled = false;
    private static final Dictionary<NPC, PatrolProperties> PatrolDict = new Hashtable<>();
    FileConfiguration config = getConfig();

    private static class PatrolProperties {
        public float susLevel = 0;
        public Location lastKnownPlayerLocation = null;
        public Location closestSoundLocation = null;

        public PatrolProperties() {}
    }

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
                    isCitizensEnabled = true;
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
        if (!isCitizensEnabled) return;

        for ( NPC npc : CitizensAPI.getNPCRegistry()) {
            var npcEntity = (LivingEntity) npc.getEntity();
            if (npcEntity == null) continue;

            // We control NPCs with the "patrol" tag
            // If the NPC is not a villager, auto convert it to one
            if (!npcEntity.getScoreboardTags().contains("patrol")) continue;
            if (npcEntity.getType() != EntityType.VILLAGER) {
                npc.setBukkitEntityType(EntityType.VILLAGER);
                npcEntity = (LivingEntity) npc.getEntity();
                npcEntity.addScoreboardTag("patrol");
            }
            npcEntity.setSilent(true); // Just so we don't have to deal with random villager sounds

            // Get patrol NPC properties
            PatrolProperties properties = PatrolDict.get(npc);
            if (properties == null) { // Setup if needed
                properties = new PatrolProperties();
                PatrolDict.put(npc, properties);
            }

            // Find the closest player
            var minDist = 99999.0;
            Player closestPlayer = null;
            for (var player : Bukkit.getOnlinePlayers()) {
                var dist = player.getPlayer().getLocation().distance(npcEntity.getLocation());
                if (dist > minDist) continue;
                minDist = dist;
                closestPlayer = player;
            }
            if (closestPlayer == null) continue;

            // << Here is the meat of the chase routine >> //

            // Check if the NPC can see the player
            var world = closestPlayer.getWorld();
            var playerDir = closestPlayer.getEyeLocation().subtract(npcEntity.getEyeLocation()).toVector();
            var traceResult = world.rayTraceBlocks(npcEntity.getEyeLocation(), playerDir, 100f);
            if (traceResult != null && traceResult.getHitBlock() == null) { // This means it has direct line of sight with the player
                properties.susLevel = Math.min(properties.susLevel + 0.01f, 1); // Takes 5 seconds to max out the sus meter
                GetLogger().log(Level.INFO, properties.susLevel + "");
                if (properties.susLevel >= 1) {
                    properties.lastKnownPlayerLocation = closestPlayer.getLocation();
                }
            }

            // Check for audio entities near the npc
            minDist = 99999.0;
            Entity closestSoundEntity = null;
            for (var entity : npcEntity.getNearbyEntities(16, 16, 16)) {
                if (!entity.getScoreboardTags().contains("audio")) continue; // Check for the "audio" tag
                var dist = entity.getLocation().distance(npcEntity.getLocation());
                if (dist > minDist) continue;
                minDist = dist;
                closestSoundEntity = entity;
            }
            if (closestSoundEntity != null)  {
                properties.closestSoundLocation = closestSoundEntity.getLocation();
            }

            float speedModifier = 1f;
            Location targetLocation = null;

            // If the npc can hear a sound, but not locate the player
            if (properties.closestSoundLocation != null && properties.lastKnownPlayerLocation == null) {
                speedModifier = 1f;
                targetLocation = properties.closestSoundLocation;
            }
            // Else if the player is being tracked
            else if (properties.lastKnownPlayerLocation != null) {
                speedModifier = 1.4f;
                targetLocation = properties.lastKnownPlayerLocation;
            }

            // Move towards the target location
            if (properties.closestSoundLocation != null && properties.lastKnownPlayerLocation != null) {
                npc.getNavigator().setTarget(targetLocation);
                npc.getNavigator().getLocalParameters().range(500);
                npc.getNavigator().getLocalParameters().speedModifier(speedModifier);
                npc.getNavigator().getLocalParameters().pathDistanceMargin(0.1f);
            }
        }
    }
}
