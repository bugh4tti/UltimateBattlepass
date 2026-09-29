package me.bughatti.ultimatebattlepass.listeners;

import me.bughatti.ultimatebattlepass.UltimateBattlepass;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class ConnectionListener implements Listener {

    private final UltimateBattlepass plugin;

    public ConnectionListener(UltimateBattlepass plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        plugin.getDataManager().load(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.getProgressDisplayManager().remove(event.getPlayer());
        plugin.getDataManager().unload(event.getPlayer().getUniqueId());
    }
}
