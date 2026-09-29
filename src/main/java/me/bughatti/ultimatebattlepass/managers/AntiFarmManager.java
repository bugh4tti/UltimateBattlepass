package me.bughatti.ultimatebattlepass.managers;

import me.bughatti.ultimatebattlepass.UltimateBattlepass;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.persistence.PersistentDataType;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class AntiFarmManager {

    private record Key(UUID world, int x, int y, int z) {
    }

    private final UltimateBattlepass plugin;
    private final NamespacedKey ignoredMobKey;

    private boolean enabled = true;
    private boolean ignorePlaced = true;
    private boolean ignoreSpawnerMobs = true;
    private long cooldownMillis = 30_000L;

    private final Set<Material> cropTypes = new HashSet<>();
    private final Set<CreatureSpawnEvent.SpawnReason> ignoredReasons =
            EnumSet.noneOf(CreatureSpawnEvent.SpawnReason.class);

    // Bloques puestos por jugadores que siguen en su lugar
    private Map<Key, Boolean> placed = lru(200_000);
    // Hora de la última vez que se colocó un bloque en cada lugar
    private Map<Key, Long> lastPlaced = lru(200_000);

    public AntiFarmManager(UltimateBattlepass plugin) {
        this.plugin = plugin;
        this.ignoredMobKey = new NamespacedKey(plugin, "ignored_mob");
        reload();
    }

    public void reload() {
        FileConfiguration config = plugin.getConfig();

        enabled = config.getBoolean("anti-farm.enabled", true);
        ignorePlaced = config.getBoolean("anti-farm.ignore-player-placed-blocks", true);
        ignoreSpawnerMobs = config.getBoolean("anti-farm.ignore-spawner-mobs", true);
        cooldownMillis = Math.max(0, config.getInt("anti-farm.same-location-place-cooldown-seconds", 30)) * 1000L;

        int max = Math.max(1000, config.getInt("anti-farm.max-tracked-blocks", 200_000));
        placed = lru(max);
        lastPlaced = lru(max);

        cropTypes.clear();
        for (String name : config.getStringList("anti-farm.crop-types")) {
            Material material = Material.matchMaterial(name);
            if (material != null) {
                cropTypes.add(material);
            } else {
                plugin.getLogger().warning("anti-farm.crop-types: material inválido '" + name + "'");
            }
        }

        ignoredReasons.clear();
        for (String name : config.getStringList("anti-farm.ignored-spawn-reasons")) {
            try {
                ignoredReasons.add(CreatureSpawnEvent.SpawnReason.valueOf(name.toUpperCase()));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("anti-farm.ignored-spawn-reasons: razón inválida '" + name + "'");
            }
        }
    }

    // ------------------------------------------------------------------
    // Bloques
    // ------------------------------------------------------------------

    /**
     * Dice si romper este bloque debe contar para las misiones.
     * - Cultivos: solo cuentan si están completamente crecidos.
     * - Otros bloques: no cuentan si los colocó un jugador.
     * Llamarlo antes de forget(block).
     */
    public boolean countBreak(Block block) {
        if (!enabled) {
            return true;
        }

        if (cropTypes.contains(block.getType())) {
            if (block.getBlockData() instanceof Ageable ageable) {
                return ageable.getAge() >= ageable.getMaximumAge();
            }
            return true;
        }

        return !(ignorePlaced && placed.containsKey(keyOf(block)));
    }

    /**
     * Saca el bloque de la lista de bloques colocados (cuando se rompe).
     */
    public void forget(Block block) {
        placed.remove(keyOf(block));
    }

    /**
     * Registra un bloque colocado. Devuelve true si la colocación debe contar
     * para las misiones (false si se está repitiendo en el mismo lugar muy rápido).
     */
    public boolean countPlace(Block block) {
        if (!enabled) {
            return true;
        }

        Key key = keyOf(block);
        long now = System.currentTimeMillis();

        Long last = lastPlaced.get(key);
        boolean allowed = last == null || cooldownMillis <= 0 || now - last >= cooldownMillis;

        lastPlaced.put(key, now);
        placed.put(key, Boolean.TRUE);
        return allowed;
    }

    // ------------------------------------------------------------------
    // Mobs
    // ------------------------------------------------------------------

    /**
     * Marca los mobs que nacen de spawners, huevos, cría, etc.
     */
    public void onSpawn(CreatureSpawnEvent event) {
        if (!enabled || !ignoreSpawnerMobs) {
            return;
        }
        if (ignoredReasons.contains(event.getSpawnReason())) {
            event.getEntity().getPersistentDataContainer()
                    .set(ignoredMobKey, PersistentDataType.BYTE, (byte) 1);
        }
    }

    public boolean isIgnoredMob(Entity entity) {
        if (!enabled || !ignoreSpawnerMobs) {
            return false;
        }
        return entity.getPersistentDataContainer().has(ignoredMobKey, PersistentDataType.BYTE);
    }

    // ------------------------------------------------------------------
    // Utilidades
    // ------------------------------------------------------------------

    private Key keyOf(Block block) {
        return new Key(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
    }

    private static <V> Map<Key, V> lru(int max) {
        return new LinkedHashMap<Key, V>(1024, 0.75f, false) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Key, V> eldest) {
                return size() > max;
            }
        };
    }
  }
