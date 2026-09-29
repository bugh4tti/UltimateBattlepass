package me.bughatti.ultimatebattlepass.listeners;

import me.bughatti.ultimatebattlepass.UltimateBattlepass;
import me.bughatti.ultimatebattlepass.managers.MissionManager.MissionType;
import org.bukkit.block.Block;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerFishEvent;

public class MissionListener implements Listener {

    private final UltimateBattlepass plugin;

    public MissionListener(UltimateBattlepass plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();

        // Anti-farm: se decide antes de olvidar el bloque
        boolean count = plugin.getAntiFarmManager().countBreak(block);
        plugin.getAntiFarmManager().forget(block);
        if (!count) {
            return;
        }

        plugin.getMissionManager().progress(
                event.getPlayer(),
                MissionType.BLOCK_BREAK,
                block.getType().name(),
                1
        );
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        boolean count = plugin.getAntiFarmManager().countPlace(event.getBlockPlaced());
        if (!count) {
            return;
        }

        plugin.getMissionManager().progress(
                event.getPlayer(),
                MissionType.BLOCK_PLACE,
                event.getBlockPlaced().getType().name(),
                1
        );
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent event) {
        plugin.getAntiFarmManager().onSpawn(event);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        Player killer = entity.getKiller();
        if (killer == null) {
            return;
        }

        if (entity instanceof Player) {
            if (entity.getUniqueId().equals(killer.getUniqueId())) {
                return;
            }
            plugin.getMissionManager().progress(killer, MissionType.PLAYER_KILL, null, 1);
            return;
        }

        // Anti-farm: mobs de spawners, huevos o cría no cuentan
        if (plugin.getAntiFarmManager().isIgnoredMob(entity)) {
            return;
        }

        plugin.getMissionManager().progress(killer, MissionType.MOB_KILL, entity.getType().name(), 1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) {
            return;
        }

        String target = null;
        if (event.getCaught() instanceof Item item) {
            target = item.getItemStack().getType().name();
        }

        plugin.getMissionManager().progress(event.getPlayer(), MissionType.FISH, target, 1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        String message = event.getMessage();
        if (message.length() < 2) {
            return;
        }

        String label = message.substring(1).split(" ")[0].toLowerCase();
        int colon = label.indexOf(':');
        if (colon >= 0 && colon < label.length() - 1) {
            label = label.substring(colon + 1);
        }

        plugin.getMissionManager().progress(event.getPlayer(), MissionType.COMMAND, label, 1);
    }
            }
