package me.bughatti.ultimatebattlepass.managers;

import me.bughatti.ultimatebattlepass.UltimateBattlepass;
import me.bughatti.ultimatebattlepass.data.PlayerData;
import me.bughatti.ultimatebattlepass.utils.ItemBuilder;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public class BoosterManager {

    public enum BoosterType {
        /** Click derecho: se consume y activa el multiplicador por un tiempo. */
        ACTIVATE,
        /** Funciona mientras esté en la mano secundaria. No se consume. */
        OFFHAND
    }

    public static class Booster {

        private final String id;
        private final BoosterType type;
        private final double multiplier;
        private final double chance;
        private final long duration;
        private final String name;

        public Booster(String id, BoosterType type, double multiplier, double chance, long duration, String name) {
            this.id = id;
            this.type = type;
            this.multiplier = multiplier;
            this.chance = chance;
            this.duration = duration;
            this.name = name;
        }

        public String getId() {
            return id;
        }

        public BoosterType getType() {
            return type;
        }

        public double getMultiplier() {
            return multiplier;
        }

        /** Probabilidad (0 a 100) de que el multiplicador se aplique. */
        public double getChance() {
            return chance;
        }

        /** Duración en segundos (solo ACTIVATE). */
        public long getDuration() {
            return duration;
        }

        public String getName() {
            return name;
        }
    }

    private final UltimateBattlepass plugin;
    private final NamespacedKey boosterKey;
    private final Map<String, Booster> boosters = new LinkedHashMap<>();

    public BoosterManager(UltimateBattlepass plugin) {
        this.plugin = plugin;
        this.boosterKey = new NamespacedKey(plugin, "booster");
        reload();
    }

    public void reload() {
        boosters.clear();

        ConfigurationSection section = plugin.getBoostersConfig().getConfigurationSection("boosters");
        if (section == null) {
            return;
        }

        for (String id : section.getKeys(false)) {
            ConfigurationSection booster = section.getConfigurationSection(id);
            if (booster == null) {
                continue;
            }

            BoosterType type;
            try {
                type = BoosterType.valueOf(booster.getString("type", "ACTIVATE").toUpperCase());
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Tipo de booster inválido en boosters.yml: " + id);
                continue;
            }

            double multiplier = Math.max(1.0, booster.getDouble("multiplier", 2.0));
            double chance = Math.max(0.0, Math.min(100.0, booster.getDouble("chance", 100.0)));
            long duration = Math.max(1L, booster.getLong("duration", 1800L));
            String name = booster.getString("name", id);

            boosters.put(id.toLowerCase(), new Booster(id.toLowerCase(), type, multiplier, chance, duration, name));
        }
    }

    public Booster get(String id) {
        return id == null ? null : boosters.get(id.toLowerCase());
    }

    public List<String> getIds() {
        return new ArrayList<>(boosters.keySet());
    }

    // ------------------------------------------------------------------
    // Ítems
    // ------------------------------------------------------------------

    public ItemStack createItem(Booster booster, int amount) {
        ConfigurationSection section = plugin.getBoostersConfig().getConfigurationSection("boosters." + booster.getId());
        if (section == null) {
            // Por si el id está en otra mayúscula dentro del yml
            for (String key : plugin.getBoostersConfig().getConfigurationSection("boosters").getKeys(false)) {
                if (key.equalsIgnoreCase(booster.getId())) {
                    section = plugin.getBoostersConfig().getConfigurationSection("boosters." + key);
                    break;
                }
            }
        }

        ItemStack item = ItemBuilder.fromSection(section)
                .amount(amount)
                .replace("%multiplier%", formatNumber(booster.getMultiplier()))
                .replace("%chance%", formatNumber(booster.getChance()))
                .replace("%duration%", SeasonManager.formatDuration(booster.getDuration()))
                .build();

        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(boosterKey, PersistentDataType.STRING, booster.getId());

            if (section != null && section.getBoolean("glow", true)) {
                Enchantment glow = Enchantment.getByKey(NamespacedKey.minecraft("unbreaking"));
                if (glow != null) {
                    meta.addEnchant(glow, 1, true);
                }
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * Devuelve el booster que representa este ítem, o null si no es un booster.
     */
    public Booster fromItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        String id = item.getItemMeta().getPersistentDataContainer().get(boosterKey, PersistentDataType.STRING);
        return get(id);
    }

    public void give(Player target, Booster booster, int amount) {
        int remaining = Math.max(1, amount);

        while (remaining > 0) {
            int stack = Math.min(64, remaining);
            remaining -= stack;

            Map<Integer, ItemStack> leftover = target.getInventory().addItem(createItem(booster, stack));
            Location location = target.getLocation();
            for (ItemStack extra : leftover.values()) {
                target.getWorld().dropItemNaturally(location, extra);
            }
        }
    }

    // ------------------------------------------------------------------
    // Activación y aplicación
    // ------------------------------------------------------------------

    /**
     * Activa un booster de tipo ACTIVATE. Devuelve true si se activó
     * (o sea, si hay que consumir el ítem).
     */
    public boolean activate(Player player, Booster booster) {
        PlayerData data = plugin.getDataManager().get(player.getUniqueId());

        if (data.hasActiveBooster()) {
            plugin.send(player, "booster.already-active");
            return false;
        }

        long expires = System.currentTimeMillis() + booster.getDuration() * 1000L;
        data.setBooster(booster.getName(), booster.getMultiplier(), booster.getChance(), expires);

        String time = SeasonManager.formatDuration(booster.getDuration());
        plugin.send(player, "booster.activated",
                "%booster%", booster.getName(),
                "%multiplier%", formatNumber(booster.getMultiplier()),
                "%chance%", formatNumber(booster.getChance()),
                "%time%", time);
        plugin.sendTitle(player, "booster-activated",
                "%booster%", booster.getName(),
                "%multiplier%", formatNumber(booster.getMultiplier()),
                "%chance%", formatNumber(booster.getChance()),
                "%time%", time);
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 0.8f);
        return true;
    }

    /**
     * Aplica los boosters a unos puntos ganados por misión y devuelve el total final.
     * Cada booster tira su probabilidad; si salen varios, se usa el mayor multiplicador.
     */
    public int apply(Player player, int points) {
        if (points <= 0) {
            return points;
        }

        PlayerData data = plugin.getDataManager().get(player.getUniqueId());
        double best = 1.0;

        // Booster activado con click derecho
        if (data.getBoosterExpires() > 0) {
            if (data.hasActiveBooster()) {
                if (roll(data.getBoosterChance())) {
                    best = Math.max(best, data.getBoosterMultiplier());
                }
            } else {
                data.clearBooster();
                plugin.send(player, "booster.expired");
            }
        }

        // Booster en la mano secundaria
        Booster offhand = fromItem(player.getInventory().getItemInOffHand());
        if (offhand != null && offhand.getType() == BoosterType.OFFHAND && roll(offhand.getChance())) {
            best = Math.max(best, offhand.getMultiplier());
        }

        if (best <= 1.0) {
            return points;
        }

        int result = (int) Math.round(points * best);
        plugin.send(player, "booster.proc",
                "%multiplier%", formatNumber(best),
                "%base%", String.valueOf(points),
                "%points%", String.valueOf(result));
        return result;
    }

    public long getRemainingSeconds(PlayerData data) {
        return data.hasActiveBooster() ? data.getBoosterRemainingSeconds() : 0L;
    }

    public void clear(Player player) {
        plugin.getDataManager().get(player.getUniqueId()).clearBooster();
    }

    private boolean roll(double chance) {
        if (chance >= 100.0) {
            return true;
        }
        if (chance <= 0.0) {
            return false;
        }
        return ThreadLocalRandom.current().nextDouble(100.0) < chance;
    }

    /**
     * 2.0 -> "2", 1.5 -> "1.5"
     */
    public static String formatNumber(double value) {
        if (value == Math.floor(value)) {
            return String.valueOf((long) value);
        }
        return String.valueOf(Math.round(value * 100.0) / 100.0);
    }
              }
