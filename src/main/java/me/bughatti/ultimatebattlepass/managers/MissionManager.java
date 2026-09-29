package me.bughatti.ultimatebattlepass.managers;

import me.bughatti.ultimatebattlepass.UltimateBattlepass;
import me.bughatti.ultimatebattlepass.data.PlayerData;
import me.bughatti.ultimatebattlepass.utils.Colors;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MissionManager {

    public enum MissionType {
        BLOCK_BREAK,
        BLOCK_PLACE,
        MOB_KILL,
        PLAYER_KILL,
        PLAY_TIME,
        COMMAND
    }

    public static class Mission {

        private final String key;
        private final String id;
        private final int week;
        private final String name;
        private final List<String> description;
        private final MissionType type;
        private final String target;
        private final int amount;
        private final int points;
        private final List<String> commands;

        public Mission(String key, String id, int week, String name, List<String> description,
                       MissionType type, String target, int amount, int points, List<String> commands) {
            this.key = key;
            this.id = id;
            this.week = week;
            this.name = name;
            this.description = description;
            this.type = type;
            this.target = target;
            this.amount = amount;
            this.points = points;
            this.commands = commands;
        }

        /** Clave con la que se guarda el progreso (daily_id o week1_id). */
        public String getKey() {
            return key;
        }

        public String getId() {
            return id;
        }

        /** 0 si es diaria, 1 a 4 si es semanal. */
        public int getWeek() {
            return week;
        }

        public boolean isDaily() {
            return week == 0;
        }

        public String getName() {
            return name;
        }

        public List<String> getDescription() {
            return description;
        }

        public MissionType getType() {
            return type;
        }

        public String getTarget() {
            return target;
        }

        public int getAmount() {
            return amount;
        }

        public int getPoints() {
            return points;
        }

        public List<String> getCommands() {
            return commands;
        }

        public boolean matches(MissionType eventType, String eventTarget) {
            if (type != eventType) {
                return false;
            }
            if (target == null || target.equalsIgnoreCase("ANY")) {
                return true;
            }
            return eventTarget != null && target.equalsIgnoreCase(eventTarget);
        }
    }

    private final UltimateBattlepass plugin;

    private final List<Mission> daily = new ArrayList<>();
    private final Map<Integer, List<Mission>> weekly = new HashMap<>();
    private final List<Mission> all = new ArrayList<>();

    private BukkitTask checkTask;
    private BukkitTask playTimeTask;
    private String lastDailyKey = "";

    public MissionManager(UltimateBattlepass plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        daily.clear();
        weekly.clear();
        all.clear();

        FileConfiguration config = plugin.getMissionsConfig();

        loadSection(config.getConfigurationSection("daily.missions"), 0, daily);
        all.addAll(daily);

        for (int week = 1; week <= 4; week++) {
            List<Mission> list = new ArrayList<>();
            loadSection(config.getConfigurationSection("weeks." + week + ".missions"), week, list);
            weekly.put(week, list);
            all.addAll(list);
        }
    }

    private void loadSection(ConfigurationSection section, int week, List<Mission> target) {
        if (section == null) {
            return;
        }

        String prefix = week == 0 ? "daily_" : "week" + week + "_";

        for (String id : section.getKeys(false)) {
            ConfigurationSection mission = section.getConfigurationSection(id);
            if (mission == null) {
                continue;
            }

            String typeName = mission.getString("type", "");
            MissionType type;
            try {
                type = MissionType.valueOf(typeName.toUpperCase());
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Tipo de misión inválido '" + typeName + "' en " + prefix + id);
                continue;
            }

            String targetName = mission.getString("target");
            int amount = Math.max(1, mission.getInt("amount", 1));
            int points = Math.max(0, mission.getInt("points", 0));
            String name = mission.getString("name", id);

            List<String> description = new ArrayList<>();
            if (mission.isList("description")) {
                description.addAll(mission.getStringList("description"));
            } else if (mission.isString("description")) {
                description.add(mission.getString("description"));
            }

            List<String> commands = new ArrayList<>(mission.getStringList("commands"));

            target.add(new Mission(prefix + id, id, week, name, description,
                    type, targetName, amount, points, commands));
        }
    }

    // ------------------------------------------------------------------
    // Consultas (las usan los menús)
    // ------------------------------------------------------------------

    public List<Mission> getDailyMissions() {
        return daily;
    }

    public List<Mission> getWeekMissions(int week) {
        return weekly.getOrDefault(week, new ArrayList<>());
    }

    public int getProgress(PlayerData data, Mission mission) {
        return Math.min(mission.getAmount(), data.getProgress(mission.getKey()));
    }

    public boolean isCompleted(PlayerData data, Mission mission) {
        return data.isCompleted(mission.getKey());
    }

    public int countCompleted(PlayerData data, List<Mission> missions) {
        int count = 0;
        for (Mission mission : missions) {
            if (isCompleted(data, mission)) {
                count++;
            }
        }
        return count;
    }

    /**
     * Una misión solo suma progreso si su semana está activa (o si es diaria
     * y la temporada está corriendo).
     */
    public boolean isActive(Mission mission) {
        SeasonManager season = plugin.getSeasonManager();
        if (mission.isDaily()) {
            return season.isSeasonRunning();
        }
        return season.isWeekEnabled(mission.getWeek())
                && season.getWeekState(mission.getWeek()) == SeasonManager.WeekState.ACTIVE;
    }

    // ------------------------------------------------------------------
    // Progreso
    // ------------------------------------------------------------------

    public void progress(Player player, MissionType type, String target, int amount) {
        if (amount <= 0 || !plugin.getSeasonManager().isSeasonRunning()) {
            return;
        }

        PlayerData data = plugin.getDataManager().get(player.getUniqueId());
        plugin.getDataManager().validate(data);

        for (Mission mission : all) {
            if (!mission.matches(type, target)) {
                continue;
            }
            if (!isActive(mission) || data.isCompleted(mission.getKey())) {
                continue;
            }

            int updated = Math.min(mission.getAmount(), data.getProgress(mission.getKey()) + amount);
            data.setProgress(mission.getKey(), updated);

            if (updated >= mission.getAmount()) {
                complete(player, data, mission);
            }
        }
    }

    private void complete(Player player, PlayerData data, Mission mission) {
        data.markCompleted(mission.getKey());

        plugin.send(player, "mission-completed",
                "%mission%", Colors.strip(mission.getName()),
                "%points%", String.valueOf(mission.getPoints()));
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.5f);

        for (String command : mission.getCommands()) {
            plugin.getPassManager().runCommand(player, command);
        }

        plugin.getPassManager().addPoints(player, mission.getPoints());
    }

    // ------------------------------------------------------------------
    // Tareas programadas
    // ------------------------------------------------------------------

    public void startTasks() {
        stopTasks();

        lastDailyKey = plugin.getSeasonManager().getDailyKey();

        // Revisa cada 10 segundos si ya llegó la hora del reinicio diario
        checkTask = Bukkit.getScheduler().runTaskTimer(plugin, this::checkDailyReset, 200L, 200L);

        // Suma 1 minuto de tiempo jugado cada 60 segundos
        playTimeTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                progress(player, MissionType.PLAY_TIME, null, 1);
            }
        }, 1200L, 1200L);
    }

    public void stopTasks() {
        if (checkTask != null) {
            checkTask.cancel();
            checkTask = null;
        }
        if (playTimeTask != null) {
            playTimeTask.cancel();
            playTimeTask = null;
        }
    }

    private void checkDailyReset() {
        String key = plugin.getSeasonManager().getDailyKey();
        if (key.equals(lastDailyKey)) {
            return;
        }

        lastDailyKey = key;
        plugin.getDataManager().validateAll();

        for (Player player : Bukkit.getOnlinePlayers()) {
            plugin.send(player, "daily-reset");
        }
    }
              }
