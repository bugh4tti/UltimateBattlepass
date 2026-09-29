package me.bughatti.ultimatebattlepass.managers;

import me.bughatti.ultimatebattlepass.UltimateBattlepass;
import me.bughatti.ultimatebattlepass.data.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class TopManager {

    public static class Entry {

        private final int position;
        private final UUID uuid;
        private final String name;
        private final int points;
        private final int level;

        public Entry(int position, UUID uuid, String name, int points, int level) {
            this.position = position;
            this.uuid = uuid;
            this.name = name;
            this.points = points;
            this.level = level;
        }

        public int getPosition() {
            return position;
        }

        public UUID getUuid() {
            return uuid;
        }

        public String getName() {
            return name;
        }

        public int getPoints() {
            return points;
        }

        public int getLevel() {
            return level;
        }
    }

    private record Raw(UUID uuid, String name, int points) {
    }

    private final UltimateBattlepass plugin;

    private volatile List<Entry> top = new ArrayList<>();
    private volatile Map<UUID, Integer> positions = new HashMap<>();

    private BukkitTask task;
    private int size = 10;
    private int refreshSeconds = 60;

    public TopManager(UltimateBattlepass plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        size = Math.max(1, Math.min(45, plugin.getConfig().getInt("top.size", 10)));
        refreshSeconds = Math.max(10, plugin.getConfig().getInt("top.refresh-seconds", 60));
        start();
    }

    public void start() {
        stop();
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::refresh, 40L, refreshSeconds * 20L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    // ------------------------------------------------------------------
    // Consultas
    // ------------------------------------------------------------------

    public int getSize() {
        return size;
    }

    public List<Entry> getTop() {
        return Collections.unmodifiableList(top);
    }

    /**
     * Devuelve la entrada de una posición (empieza en 1) o null si no hay nadie.
     */
    public Entry getEntry(int position) {
        List<Entry> current = top;
        if (position < 1 || position > current.size()) {
            return null;
        }
        return current.get(position - 1);
    }

    /**
     * Posición del jugador en el ranking (0 si todavía no tiene puntos).
     */
    public int getPosition(UUID uuid) {
        return positions.getOrDefault(uuid, 0);
    }

    // ------------------------------------------------------------------
    // Actualización
    // ------------------------------------------------------------------

    /**
     * Lee los archivos de datos en un hilo aparte y arma el ranking en el hilo principal.
     */
    public void refresh() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            List<Raw> raws = scanFiles();
            Bukkit.getScheduler().runTask(plugin, () -> build(raws));
        });
    }

    private List<Raw> scanFiles() {
        List<Raw> result = new ArrayList<>();

        File[] files = plugin.getDataManager().getFolder().listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) {
            return result;
        }

        String seasonId = plugin.getSeasonManager().getSeasonId();

        for (File file : files) {
            String fileName = file.getName();
            String base = fileName.substring(0, fileName.length() - 4);

            UUID uuid;
            try {
                uuid = UUID.fromString(base);
            } catch (IllegalArgumentException e) {
                continue;
            }

            YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
            if (!seasonId.equals(yml.getString("season-id", ""))) {
                continue;
            }

            int points = yml.getInt("points", 0);
            if (points <= 0) {
                continue;
            }

            result.add(new Raw(uuid, yml.getString("name", ""), points));
        }

        return result;
    }

    private void build(List<Raw> raws) {
        String seasonId = plugin.getSeasonManager().getSeasonId();

        Map<UUID, Raw> merged = new HashMap<>();
        for (Raw raw : raws) {
            merged.put(raw.uuid(), raw);
        }

        // Los jugadores conectados tienen datos más nuevos que el archivo
        for (PlayerData data : plugin.getDataManager().getLoaded()) {
            if (!seasonId.equals(data.getSeasonId()) || data.getPoints() <= 0) {
                merged.remove(data.getUuid());
                continue;
            }
            merged.put(data.getUuid(), new Raw(data.getUuid(), data.getName(), data.getPoints()));
        }

        List<Raw> sorted = new ArrayList<>(merged.values());
        sorted.sort(Comparator.<Raw>comparingInt(Raw::points).reversed()
                .thenComparing(raw -> raw.uuid().toString()));

        List<Entry> entries = new ArrayList<>();
        Map<UUID, Integer> newPositions = new HashMap<>();

        for (int i = 0; i < sorted.size(); i++) {
            Raw raw = sorted.get(i);
            newPositions.put(raw.uuid(), i + 1);

            if (i < size) {
                entries.add(new Entry(
                        i + 1,
                        raw.uuid(),
                        resolveName(raw.uuid(), raw.name()),
                        raw.points(),
                        plugin.getPassManager().getLevel(raw.points())
                ));
            }
        }

        top = entries;
        positions = newPositions;
    }

    private String resolveName(UUID uuid, String stored) {
        if (stored != null && !stored.isEmpty()) {
            return stored;
        }
        String offline = Bukkit.getOfflinePlayer(uuid).getName();
        return offline != null ? offline : "?";
    }
          }
