package me.bughatti.ultimatebattlepass.managers;

import me.bughatti.ultimatebattlepass.UltimateBattlepass;
import me.bughatti.ultimatebattlepass.managers.MissionManager.Mission;
import me.bughatti.ultimatebattlepass.managers.MissionManager.MissionType;
import me.bughatti.ultimatebattlepass.utils.Colors;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Muestra el progreso de la misión en una bossbar y en el mini mensaje
 * que aparece arriba de la hotbar (actionbar).
 * La bossbar se borra cuando la misión se completa y se vuelve a crear
 * cuando el jugador avanza en otra misión.
 */
public class ProgressDisplayManager {

    private final UltimateBattlepass plugin;

    private final Map<UUID, BossBar> bars = new HashMap<>();
    private final Map<UUID, String> shownMission = new HashMap<>();
    private final Map<UUID, BukkitTask> hideTasks = new HashMap<>();

    private final Set<MissionType> ignoredTypes = EnumSet.noneOf(MissionType.class);

    private boolean bossbarEnabled = true;
    private boolean actionbarEnabled = true;
    private String bossbarFormat = "";
    private String actionbarFormat = "";
    private BarColor color = BarColor.YELLOW;
    private BarStyle style = BarStyle.SEGMENTED_10;
    private int hideAfterSeconds = 0;

    public ProgressDisplayManager(UltimateBattlepass plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        removeAll();

        ConfigurationSection section = plugin.getConfig().getConfigurationSection("progress-display");
        if (section == null) {
            bossbarEnabled = false;
            actionbarEnabled = false;
            return;
        }

        ignoredTypes.clear();
        for (String name : section.getStringList("ignored-types")) {
            try {
                ignoredTypes.add(MissionType.valueOf(name.toUpperCase()));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("progress-display.ignored-types: tipo inválido '" + name + "'");
            }
        }

        bossbarEnabled = section.getBoolean("bossbar.enabled", true);
        bossbarFormat = section.getString("bossbar.format",
                "&e&lMisión &8» &f%mission% &8- &f%progress%&7/&f%amount% %unit% &7(%percent%%)");
        hideAfterSeconds = Math.max(0, section.getInt("bossbar.hide-after-seconds", 0));

        try {
            color = BarColor.valueOf(section.getString("bossbar.color", "YELLOW").toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("progress-display.bossbar.color inválido. Usando YELLOW.");
            color = BarColor.YELLOW;
        }

        try {
            style = BarStyle.valueOf(section.getString("bossbar.style", "SEGMENTED_10").toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("progress-display.bossbar.style inválido. Usando SEGMENTED_10.");
            style = BarStyle.SEGMENTED_10;
        }

        actionbarEnabled = section.getBoolean("actionbar.enabled", true);
        actionbarFormat = section.getString("actionbar.format",
                "&6Misión de %mission% &f%progress%/%amount% %unit% &7(%percent%%)");
    }

    // ------------------------------------------------------------------
    // Mostrar y borrar
    // ------------------------------------------------------------------

    /**
     * Muestra el progreso de una misión que todavía no se completó.
     */
    public void show(Player player, Mission mission, int progress) {
        if (ignoredTypes.contains(mission.getType())) {
            return;
        }

        int amount = Math.max(1, mission.getAmount());
        int current = Math.max(0, Math.min(amount, progress));
        int percent = (int) Math.floor(current * 100.0 / amount);

        if (actionbarEnabled) {
            String text = Colors.colorize(fill(actionbarFormat, mission, current, amount, percent));
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(text));
        }

        if (!bossbarEnabled) {
            return;
        }

        UUID uuid = player.getUniqueId();
        String title = Colors.colorize(fill(bossbarFormat, mission, current, amount, percent));
        double fraction = Math.max(0.0, Math.min(1.0, current / (double) amount));

        BossBar bar = bars.get(uuid);
        if (bar == null) {
            bar = Bukkit.createBossBar(title, color, style);
            bar.addPlayer(player);
            bars.put(uuid, bar);
        }

        bar.setTitle(title);
        bar.setProgress(fraction);
        bar.setVisible(true);

        shownMission.put(uuid, mission.getKey());
        scheduleHide(player);
    }

    /**
     * Se llama cuando una misión se completa: si su bossbar estaba en pantalla, se borra.
     */
    public void onComplete(Player player, Mission mission) {
        String shown = shownMission.get(player.getUniqueId());
        if (shown != null && shown.equals(mission.getKey())) {
            remove(player);
        }
    }

    public void remove(Player player) {
        remove(player.getUniqueId());
    }

    private void remove(UUID uuid) {
        BukkitTask task = hideTasks.remove(uuid);
        if (task != null) {
            task.cancel();
        }

        shownMission.remove(uuid);

        BossBar bar = bars.remove(uuid);
        if (bar != null) {
            bar.removeAll();
        }
    }

    public void removeAll() {
        for (UUID uuid : new ArrayList<>(bars.keySet())) {
            remove(uuid);
        }
    }

    private void scheduleHide(Player player) {
        UUID uuid = player.getUniqueId();

        BukkitTask previous = hideTasks.remove(uuid);
        if (previous != null) {
            previous.cancel();
        }

        if (hideAfterSeconds <= 0) {
            return;
        }

        hideTasks.put(uuid, Bukkit.getScheduler().runTaskLater(plugin, () -> remove(uuid), hideAfterSeconds * 20L));
    }

    private String fill(String format, Mission mission, int current, int amount, int percent) {
        return format
                .replace("%mission%", Colors.strip(mission.getName()))
                .replace("%progress%", String.valueOf(current))
                .replace("%amount%", String.valueOf(amount))
                .replace("%percent%", String.valueOf(percent))
                .replace("%unit%", mission.getUnit());
    }
  }
