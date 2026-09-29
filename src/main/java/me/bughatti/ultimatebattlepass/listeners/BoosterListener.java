package me.bughatti.ultimatebattlepass.listeners;

import me.bughatti.ultimatebattlepass.UltimateBattlepass;
import me.bughatti.ultimatebattlepass.managers.BoosterManager.Booster;
import me.bughatti.ultimatebattlepass.managers.BoosterManager.BoosterType;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

public class BoosterListener implements Listener {

    private final UltimateBattlepass plugin;

    public BoosterListener(UltimateBattlepass plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        ItemStack item = event.getItem();
        Booster booster = plugin.getBoosterManager().fromItem(item);
        if (booster == null) {
            return;
        }

        // Evita que el ítem se coloque, se use o se consuma por otra cosa
        event.setCancelled(true);

        Player player = event.getPlayer();
        EquipmentSlot hand = event.getHand();
        if (hand == null) {
            return;
        }

        if (booster.getType() == BoosterType.OFFHAND) {
            plugin.send(player, "booster.offhand-hint");
            return;
        }

        if (!player.hasPermission("ultimatebattlepass.use")) {
            plugin.send(player, "no-permission");
            return;
        }

        if (plugin.getBoosterManager().activate(player, booster)) {
            consumeOne(player, hand);
        }
    }

    private void consumeOne(Player player, EquipmentSlot hand) {
        PlayerInventory inventory = player.getInventory();
        ItemStack stack = hand == EquipmentSlot.OFF_HAND
                ? inventory.getItemInOffHand().clone()
                : inventory.getItemInMainHand().clone();

        if (stack.getAmount() <= 1) {
            stack = new ItemStack(Material.AIR);
        } else {
            stack.setAmount(stack.getAmount() - 1);
        }

        if (hand == EquipmentSlot.OFF_HAND) {
            inventory.setItemInOffHand(stack);
        } else {
            inventory.setItemInMainHand(stack);
        }
    }
  }
