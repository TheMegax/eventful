package net.themegax.eventful;

import com.destroystokyo.paper.event.server.ServerTickStartEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class EventfulListener implements Listener {
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        EventfulMain.getDatabaseManager().initializePlayer(event.getPlayer().getName());
    }

    @EventHandler
    public void onServerTick(ServerTickStartEvent event) {
        EventfulMain.onNPCTick();
    }
}
