package me.bughatti.ultimatebattlepass.hooks;

import me.bughatti.ultimatebattlepass.UltimateBattlepass;
import me.bughatti.ultimatebattlepass.data.PlayerData;
import me.bughatti.ultimatebattlepass.managers.MissionManager;
import me.bughatti.ultimatebattlepass.managers.PassManager;
import me.bughatti.ultimatebattlepass.managers.SeasonManager;
import me.bughatti.ultimatebattlepass.managers.TopManager;
import me.bughatti.ultimatebattlepass.utils.Colors;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

public class BattlepassExpansion extends PlaceholderExpansion {

    private final UltimateBattlepass plugin;

    public BattlepassExpansion(UltimateBattlepass plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "bp";
    }

    @Override
    public String getAuthor() {
        return "Bughatti";
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer offline, String params) {
        String key = params.toLowerCase();

        // Placeholders del top (no dependen de quién los mire, sirven para hologramas)
        if (key.startsWith("top_")) {
            String result = top(offline, key.substring(4));
            if (result != null) {
                return result;
            }
        }

        if (offline == null || !offline.isOnline()) {
            return "";
        }
        Player player = offline.getPlayer();
        if (player == null) {
            return "";
        }

        PlayerData data = plugin.getDataManager().get(player.getUniqueId());
        plugin.getDataManager().validate(data);

        PassManager pass = plugin.getPassManager();
        SeasonManager season = plugin.getSeasonManager();
        MissionManager missions = plugin.getMissionManager();

        switch (key) {
            // Pase
            case "level":
                return String.valueOf(pass.getLevel(data));
            case "next_level":
                return String.valueOf(Math.min(pass.getMaxLevel(), pass.getLevel(data) + 1));
            case "max_level":
                return String.valueOf(pass.getMaxLevel());
            case "points":
                return String.valueOf(pass.getPointsInLevel(data));
            case "points_needed":
                return String.valueOf(pass.getPointsPerLevel());
            case "points_total":
                return String.valueOf(data.getPoints());
            case "premium":
                return pass.hasPremium(player) ? text("placeholders.yes", "Sí") : text("placeholders.no", "No");

            // Temporada
            case "season":
                return season.getSeasonName();
            case "week": {
                int week = season.getCurrentWeek();
                return week >= 1 ? String.valueOf(week) : text("placeholders.empty", "-");
            }
            case "week_state": {
                int week = season.getCurrentWeek();
                String state = week >= 1 ? "ACTIVE" : (week == 0 ? "UPCOMING" : "ENDED");
                return Colors.colorize(plugin.getMenusConfig().getString("texts.week-state." + state, state));
            }
            case "season_time_left":
                return SeasonManager.formatDuration(season.secondsUntil(season.seasonEnd()));

            // Misiones diarias
            case "daily_reset":
                return SeasonManager.formatDuration(season.secondsUntilDailyReset());
            case "daily_reset_seconds":
                return String.valueOf(season.secondsUntilDailyReset());
            case "daily_completed":
                return String.valueOf(missions.countCompleted(data, missions.getDailyMissions()));
            case "daily_total":
                return String.valueOf(missions.getDailyMissions().size());

            // Booster
            case "booster":
                return data.hasActiveBooster()
                        ? Colors.colorize(data.getBoosterName())
                        : text("placeholders.no-booster", "Ninguno");
            case "booster_time":
                return data.hasActiveBooster()
                        ? SeasonManager.formatDuration(data.getBoosterRemainingSeconds())
                        : text("placeholders.empty", "-");
            case "booster_multiplier":
                return data.hasActiveBooster()
                        ? me.bughatti.ultimatebattlepass.managers.BoosterManager.formatNumber(data.getBoosterMultiplier())
                        : "1";
            case "booster_chance":
                return data.hasActiveBooster()
                        ? me.bughatti.ultimatebattlepass.managers.BoosterManager.formatNumber(data.getBoosterChance())
                        : "0";

            default:
                return null;
        }
    }

    /**
     * %bp_top_1_name%, %bp_top_1_points%, %bp_top_1_level%, %bp_top_position%, %bp_top_size%
     */
    private String top(OfflinePlayer offline, String rest) {
        TopManager top = plugin.getTopManager();
        String empty = text("placeholders.empty", "-");

        if (rest.equals("position")) {
            if (offline == null) {
                return empty;
            }
            int position = top.getPosition(offline.getUniqueId());
            return position > 0 ? String.valueOf(position) : empty;
        }

        if (rest.equals("size")) {
            return String.valueOf(top.getSize());
        }

        String[] parts = rest.split("_");
        if (parts.length != 2) {
            return null;
        }

        int position;
        try {
            position = Integer.parseInt(parts[0]);
        } catch (NumberFormatException e) {
            return null;
        }

        TopManager.Entry entry = top.getEntry(position);

        switch (parts[1]) {
            case "name":
                return entry == null ? empty : entry.getName();
            case "points":
                return entry == null ? "0" : String.valueOf(entry.getPoints());
            case "level":
                return entry == null ? "0" : String.valueOf(entry.getLevel());
            default:
                return null;
        }
    }

    private String text(String path, String fallback) {
        return plugin.getMessagesConfig().getString(path, fallback);
    }
    }
