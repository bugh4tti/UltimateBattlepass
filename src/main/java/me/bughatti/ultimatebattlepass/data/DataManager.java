package me.bughatti.ultimatebattlepass.data;

import me.bughatti.ultimatebattlepass.UltimateBattlepass;
import me.bughatti.ultimatebattlepass.managers.SeasonManager;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class DataManager {

    private final UltimateBattlepass plugin;
    private final File folder;
    private final Map<UUID, PlayerData> cache = new ConcurrentHashMap<>();

    public DataManager(UltimateBattlepass plugin) {
        this.plugin = plugin;
        this.folder = new File(plugin.getDataFolder(), "data");
        if (!folder.exists()) {
            folder.mkdirs();
        }
    }

    public PlayerData get(UUID uuid) {
        PlayerData data = cache.get(uuid);
        if (data == null) {
            data = load(uuid);
        }
        return data;
    }

    public PlayerData load(UUID uuid) {
        PlayerData data = new PlayerData(uuid);
        File file = new File(folder, uuid + ".yml");

        if (file.exists()) {
            YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
            data.setSeasonId(yml.getString("season-id", ""));
            data.setDailyKey(yml.getString("daily-key", ""));
            data.setPoints(yml.getInt("points", 0));
            data.getClaimedFree().addAll(yml.getIntegerList("claimed-free"));
            data.getClaimedPremium().addAll(yml.getIntegerList("claimed-premium"));
            data.getCompleted().addAll(yml.getStringList("completed"));

            ConfigurationSection section = yml.getConfigurationSection("progress");
            if (section != null) {
                for (String key : section.getKeys(false)) {
                    data.getProgress().put(key, section.getInt(key));
                }
            }
        }

        validate(data);
        cache.put(uuid, data);
        return data;
    }

    /**
     * Revisa que los datos del jugador correspondan a la temporada y al día actual.
     * Si la temporada cambió, reinicia todo. Si el día de misiones cambió, reinicia las diarias.
     */
    public void validate(PlayerData data) {
        SeasonManager season = plugin.getSeasonManager();

        if (!season.getSeasonId().equals(data.getSeasonId())) {
            data.resetSeason();
            data.setSeasonId(season.getSeasonId());
        }

        String dailyKey = season.getDailyKey();
        if (!dailyKey.equals(data.getDailyKey())) {
            data.clearDaily();
            data.setDailyKey(dailyKey);
        }
    }

    public void validateAll() {
        for (PlayerData data : cache.values()) {
            validate(data);
        }
    }

    public void save(UUID uuid) {
        PlayerData data = cache.get(uuid);
        if (data == null) {
            return;
        }

        YamlConfiguration yml = new YamlConfiguration();
        yml.set("season-id", data.getSeasonId());
        yml.set("daily-key", data.getDailyKey());
        yml.set("points", data.getPoints());
        yml.set("claimed-free", new ArrayList<>(data.getClaimedFree()));
        yml.set("claimed-premium", new ArrayList<>(data.getClaimedPremium()));
        yml.set("completed", new ArrayList<>(data.getCompleted()));

        for (Map.Entry<String, Integer> entry : data.getProgress().entrySet()) {
            yml.set("progress." + entry.getKey(), entry.getValue());
        }

        try {
            yml.save(new File(folder, uuid + ".yml"));
        } catch (IOException e) {
            plugin.getLogger().severe("No se pudieron guardar los datos de " + uuid + ": " + e.getMessage());
        }
    }

    public void unload(UUID uuid) {
        save(uuid);
        cache.remove(uuid);
    }

    public void saveAll() {
        for (UUID uuid : new ArrayList<>(cache.keySet())) {
            save(uuid);
        }
    }

    public Collection<PlayerData> getLoaded() {
        return cache.values();
    }
    }
