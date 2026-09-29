package me.bughatti.ultimatebattlepass.managers;

import me.bughatti.ultimatebattlepass.UltimateBattlepass;
import me.bughatti.ultimatebattlepass.data.PlayerData;
import me.bughatti.ultimatebattlepass.utils.Colors;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PassManager {

    public static class Reward {

        private final List<String> display;
        private final List<String> commands;

        public Reward(List<String> display, List<String> commands) {
            this.display = display;
            this.commands = commands;
        }

        public List<String> getDisplay() {
            return display;
        }

        public List<String> getCommands() {
            return commands;
        }
    }

    public enum ClaimResult {
        SUCCESS,
        NO_REWARD,
        ALREADY_CLAIMED,
        NEED_PREMIUM,
        LEVEL_LOCKED
    }

    private final UltimateBattlepass plugin;

    private final Map<Integer, Reward> freeRewards = new HashMap<>();
    private final Map<Integer, Reward> premiumRewards = new HashMap<>();

    private int pointsPerLevel = 120;
    private int maxLevel = 50;
    private String premiumPermission = "ultimatebattlepass.premium";

    public PassManager(UltimateBattlepass plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        FileConfiguration config = plugin.getConfig();
        pointsPerLevel = Math.max(1, config.getInt("pass.points-per-level", 120));
        maxLevel = Math.max(1, config.getInt("pass.max-level", 50));
        premiumPermission = config.getString("pass.premium-permission", "ultimatebattlepass.premium");

        freeRewards.clear();
        premiumRewards.clear();

        ConfigurationSection levels = plugin.getRewardsConfig().getConfigurationSection("levels");
        if (levels == null) {
            plugin.getLogger().warning("rewards.yml no tiene la sección 'levels'.");
            return;
        }

        for (String key : levels.getKeys(false)) {
            int level;
            try {
                level = Integer.parseInt(key);
            } catch (NumberFormatException e) {
                plugin.getLogger().warning("Nivel inválido en rewards.yml: " + key);
                continue;
            }
            loadReward(levels.getConfigurationSection(key + ".free"), level, freeRewards);
            loadReward(levels.getConfigurationSection(key + ".premium"), level, premiumRewards);
        }
    }

    private void loadReward(ConfigurationSection section, int level, Map<Integer, Reward> target) {
        if (section == null) {
            return;
        }
        List<String> display = section.getStringList("display");
        List<String> commands = section.getStringList("commands");
        if (display.isEmpty() && commands.isEmpty()) {
            return;
        }
        target.put(level, new Reward(new ArrayList<>(display), new ArrayList<>(commands)));
    }

    // ------------------------------------------------------------------
    // Puntos y niveles
    // ------------------------------------------------------------------

    public int getPointsPerLevel() {
        return pointsPerLevel;
    }

    public int getMaxLevel() {
        return maxLevel;
    }

    public int getMaxPoints() {
        return maxLevel * pointsPerLevel;
    }

    public int getLevel(PlayerData data) {
        return Math.min(maxLevel, data.getPoints() / pointsPerLevel);
    }

    /**
     * Puntos que lleva el jugador dentro de su nivel actual (ej: 55 de 120).
     */
    public int getPointsInLevel(PlayerData data) {
        if (getLevel(data) >= maxLevel) {
            return pointsPerLevel;
        }
        return data.getPoints() % pointsPerLevel;
    }

    public void addPoints(Player player, int amount) {
        if (amount <= 0) {
            return;
        }

        PlayerData data = plugin.getDataManager().get(player.getUniqueId());
        int before = getLevel(data);

        data.setPoints(Math.min(getMaxPoints(), data.getPoints() + amount));

        int after = getLevel(data);
        for (int level = before + 1; level <= after; level++) {
            plugin.send(player, "level-up", "%level%", String.valueOf(level));
        }

        if (after > before) {
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
            // Un solo título con el nivel final, aunque haya subido varios a la vez
            plugin.sendTitle(player, "level-up", "%level%", String.valueOf(after));
        }
    }

    public void setPoints(Player player, int points) {
        PlayerData data = plugin.getDataManager().get(player.getUniqueId());
        data.setPoints(Math.min(getMaxPoints(), Math.max(0, points)));
    }

    // ------------------------------------------------------------------
    // Premium y recompensas
    // ------------------------------------------------------------------

    public String getPremiumPermission() {
        return premiumPermission;
    }

    public boolean hasPremium(Player player) {
        return player.hasPermission(premiumPermission);
    }

    public Reward getReward(int level, boolean premium) {
        return premium ? premiumRewards.get(level) : freeRewards.get(level);
    }

    public boolean isClaimed(PlayerData data, int level, boolean premium) {
        return premium ? data.getClaimedPremium().contains(level) : data.getClaimedFree().contains(level);
    }

    public ClaimResult claim(Player player, int level, boolean premium) {
        PlayerData data = plugin.getDataManager().get(player.getUniqueId());

        Reward reward = getReward(level, premium);
        if (reward == null) {
            return ClaimResult.NO_REWARD;
        }

        if (isClaimed(data, level, premium)) {
            plugin.send(player, "reward-already-claimed");
            return ClaimResult.ALREADY_CLAIMED;
        }

        if (premium && !hasPremium(player)) {
            plugin.send(player, "need-premium");
            return ClaimResult.NEED_PREMIUM;
        }

        if (getLevel(data) < level) {
            plugin.send(player, "reward-locked-level", "%level%", String.valueOf(level));
            return ClaimResult.LEVEL_LOCKED;
        }

        if (premium) {
            data.getClaimedPremium().add(level);
        } else {
            data.getClaimedFree().add(level);
        }

        for (String command : reward.getCommands()) {
            runCommand(player, command.replace("%level%", String.valueOf(level)));
        }

        plugin.send(player, "reward-claimed", "%level%", String.valueOf(level));
        plugin.sendTitle(player, "reward-claimed",
                "%level%", String.valueOf(level),
                "%type%", plugin.format(premium ? "pass-type.premium" : "pass-type.free"));
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
        return ClaimResult.SUCCESS;
    }

    /**
     * Ejecuta un comando de recompensa. Por defecto sale desde consola.
     * Prefijos opcionales: [player] comando, [console] comando, [message] texto.
     */
    public void runCommand(Player player, String raw) {
        String command = raw.replace("%player%", player.getName());
        String lower = command.toLowerCase();

        if (lower.startsWith("[message] ")) {
            player.sendMessage(Colors.colorize(command.substring(10)));
            return;
        }

        if (lower.startsWith("[player] ")) {
            player.performCommand(stripSlash(command.substring(9)));
            return;
        }

        if (lower.startsWith("[console] ")) {
            command = command.substring(10);
        }

        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), stripSlash(command));
    }

    private String stripSlash(String command) {
        return command.startsWith("/") ? command.substring(1) : command;
    }
    }
