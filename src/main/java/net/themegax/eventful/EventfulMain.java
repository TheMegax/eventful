package net.themegax.eventful;

import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.event.CitizensEnableEvent;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.util.NMS;
import net.themegax.eventful.Database.DatabaseManager;
import org.apache.commons.lang3.StringUtils;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class EventfulMain extends JavaPlugin {

    private static Logger logger;
    private static DatabaseManager databaseManager;
    private static Boolean isCitizensEnabled = false;
    private static final Dictionary<NPC, PatrolProperties> PatrolDict = new Hashtable<>();
    FileConfiguration config = getConfig();

    private static class PatrolProperties {
        public long lastAttackTimer = 0;
        public long lastTimeSincePlayerWasSeen = 0;
        public boolean isChasingPlayer = false;
        public Location lastKnownPlayerLocation = null;
        public Location closestTargetLocation = null;
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
            if (!npc.isSpawned()) continue;
            var npcEntity = (LivingEntity) npc.getEntity();
            if (npcEntity == null) continue;

            // We control NPCs with the "patrol" tag
            // If the NPC is not a villager, auto convert it to one
            if (!npcEntity.getScoreboardTags().contains("patrol")) continue;

            if (npcEntity.getScoreboardTags().contains("guard")) {
                if (npcEntity.getType() != EntityType.VINDICATOR) {
                    npc.setBukkitEntityType(EntityType.VINDICATOR);
                    npcEntity = (LivingEntity) npc.getEntity();
                    npcEntity.addScoreboardTag("patrol");
                    npcEntity.addScoreboardTag("guard");
                }
            }
            else if (npcEntity.getType() != EntityType.VILLAGER) {
                npc.setBukkitEntityType(EntityType.VILLAGER);
                npcEntity = (LivingEntity) npc.getEntity();
                npcEntity.addScoreboardTag("patrol");
            }

            // Traits
            npcEntity.setSilent(true);

            // Get patrol NPC properties
            PatrolProperties properties = PatrolDict.get(npc);
            if (properties == null) { // Setup if needed
                properties = new PatrolProperties();
                PatrolDict.put(npc, properties);
            }

            // Play corresponding animation
            npc.data().set(NPC.Metadata.AGGRESSIVE, properties.isChasingPlayer);
            NMS.setAggressive(npcEntity, properties.isChasingPlayer);

            // If we lost track of the player, return back to normal
            var hasLostPlayer = (System.currentTimeMillis() - properties.lastTimeSincePlayerWasSeen) > 5000;
            if (hasLostPlayer) {
                if (properties.lastKnownPlayerLocation != null) {
                    properties.isChasingPlayer = false;
                    GetLogger().info("lost the player :[");
                }
                properties.lastKnownPlayerLocation = null;
            }

            // << Here is the meat of the chase routine >> //
            var closestPlayer = (Player) getClosestEntity(npcEntity, EntityType.PLAYER, "");

            // Check if the NPC can see the player
            if (closestPlayer != null && closestPlayer.getGameMode().equals(GameMode.SURVIVAL) && npcEntity.hasLineOfSight(closestPlayer)) {
                var playerDistance = npcEntity.getLocation().distance(closestPlayer.getLocation());
                var playerBlock = closestPlayer.getWorld().getBlockAt(closestPlayer.getLocation().toBlockLocation());
                GetLogger().info(String.valueOf(playerBlock.getLightLevel()));

                if (playerDistance < 10) {
                    var npcDirection = npcEntity.getLocation().getDirection().normalize();
                    var playerVector = closestPlayer.getLocation().toVector().subtract(npcEntity.getLocation().toVector()).normalize();
                    var angleDegrees = Math.acos(npcDirection.dot(playerVector)) * 180 / Math.PI;

                    if (angleDegrees <= 60) {
                        properties.lastKnownPlayerLocation = closestPlayer.getLocation();
                        properties.lastTimeSincePlayerWasSeen = System.currentTimeMillis();
                        properties.isChasingPlayer = true;
                    }
                    else if (properties.isChasingPlayer) {
                        properties.lastKnownPlayerLocation = closestPlayer.getLocation();
                    }

                    // Do damage to the player (if its close enough)
                    if (playerDistance < 1.5 && (System.currentTimeMillis() - properties.lastAttackTimer) > 1000) {
                        npcEntity.swingMainHand();
                        npcEntity.attack(closestPlayer);
                        properties.lastAttackTimer = System.currentTimeMillis();
                    }
                }
            }

            // Check for sound entities near the npc
            properties.closestSoundLocation = null;
            Entity closestSoundEntity = getClosestEntity(npcEntity, EntityType.ARMOR_STAND, "sound");
            if (closestSoundEntity != null && closestSoundEntity.getScoreboardTags().size() > 1) {
                var maxDistanceBlocks = (String) closestSoundEntity.getScoreboardTags().toArray()[0];
                var soundDistance = npcEntity.getLocation().distance(closestSoundEntity.getLocation());
                if (StringUtils.isNumeric(maxDistanceBlocks) && Integer.parseInt(maxDistanceBlocks) <= soundDistance) {
                    properties.closestSoundLocation = closestSoundEntity.getLocation();
                }
            }

            // Check for target entities near the npc
            Entity closestTargetEntity = getClosestEntity(npcEntity, EntityType.ARMOR_STAND, npc.getName() + ".target");
            properties.closestTargetLocation = (closestTargetEntity == null) ? null : closestTargetEntity.getLocation();


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
            // Else if there's a target present
            else if (properties.closestTargetLocation != null) {
                speedModifier = 1f;
                targetLocation = properties.closestTargetLocation;
            }

            // Move towards the target location
            if (targetLocation != null) {
                npc.getNavigator().setTarget(targetLocation);
                npc.getNavigator().getLocalParameters().range(500);
                npc.getNavigator().getLocalParameters().speedModifier(speedModifier);
            }
            else { // Else, we are following waypoints
                npc.getNavigator().getLocalParameters().speedModifier(speedModifier);
            }
        }
    }

    public static Entity getClosestEntity(Entity entity, EntityType entityType, String tag) {
        var minDistance = Double.MAX_VALUE;
        Entity closestEntity = null;
        var nearbyEntities = entity.getNearbyEntities(50, 50, 50);
        for (Entity e : nearbyEntities) {
            if (!tag.isEmpty() && !e.getScoreboardTags().contains(tag)) {
                continue;
            }
            if (e.getType() != entityType) {
                continue;
            }
            var curDistance = e.getLocation().distance(entity.getLocation());
            if (curDistance < minDistance) {
                minDistance = curDistance;
                closestEntity = e;
            }
        }
        return closestEntity;
    }
}
