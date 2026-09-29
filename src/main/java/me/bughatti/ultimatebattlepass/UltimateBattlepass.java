package me.bughatti.ultimatebattlepass;

import me.bughatti.ultimatebattlepass.commands.BattlepassCommand;
import me.bughatti.ultimatebattlepass.data.DataManager;
import me.bughatti.ultimatebattlepass.listeners.ConnectionListener;
import me.bughatti.ultimatebattlepass.listeners.MissionListener;
import me.bughatti.ultimatebattlepass.managers.MenuManager;
import me.bughatti.ultimatebattlepass.managers.MissionManager;
import me.bughatti.ultimatebattlepass.managers.PassManager;
import me.bughatti.ultimatebattlepass.managers.SeasonManager;
import me.bughatti.ultimatebattlepass.utils.Colors;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.util.List;

public class UltimateBattlepass extends JavaPlugin {

    private static UltimateBattlepass instance;

    private FileConfiguration messagesConfig;
    private FileConfiguration menusConfig;
    private FileConfiguration missionsConfig;
    private FileConfiguration rewardsConfig;

    private SeasonManager seasonManager;
    private DataManager dataManager;
    private PassManager passManager;
    private MissionManager missionManager;
    private MenuManager menuManager;

    private BukkitTask autosaveTask;

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();
        loadFiles();

        seasonManager = new SeasonManager(this);
        dataManager = new DataManager(this);
        passManager = new PassManager(this);
        missionManager = new MissionManager(this);
        menuManager = new MenuManager(this);

        getServer().getPluginManager().registerEvents(new ConnectionListener(this), this);
        getServer().getPluginManager().registerEvents(new MissionListener(this), this);
        getServer().getPluginManager().registerEvents(menuManager, this);

        BattlepassCommand command = new BattlepassCommand(this);
        PluginCommand pluginCommand = getCommand("battlepass");
        if (pluginCommand != null) {
            pluginCommand.setExecutor(command);
            pluginCommand.setTabCompleter(command);
        }

        // Carga a los jugadores que ya estén conectados (por ejemplo, tras un /reload)
        for (Player player : Bukkit.getOnlinePlayers()) {
            dataManager.load(player.getUniqueId());
        }

        missionManager.startTasks();
        startAutosave();

        getLogger().info("UltimateBattlepass " + getDescription().getVersion() + " activado.");
    }

    @Override
    public void onDisable() {
        if (autosaveTask != null) {
            autosaveTask.cancel();
        }
        if (missionManager != null) {
            missionManager.stopTasks();
        }
        if (dataManager != null) {
            dataManager.saveAll();
        }
        getLogger().info("UltimateBattlepass desactivado.");
    }

    /**
     * Recarga config.yml, messages.yml, menus.yml, missions.yml y rewards.yml.
     */
    public void reloadAll() {
        reloadConfig();
        loadFiles();

        seasonManager.reload();
        dataManager.validateAll();
        passManager.reload();
        missionManager.reload();
        menuManager.reload();

        startAutosave();
    }

    private void loadFiles() {
        messagesConfig = loadYaml("messages.yml");
        menusConfig = loadYaml("menus.yml");
        missionsConfig = loadYaml("missions.yml");
        rewardsConfig = loadYaml("rewards.yml");
    }

    private FileConfiguration loadYaml(String name) {
        File file = new File(getDataFolder(), name);
        if (!file.exists()) {
            saveResource(name, false);
        }
        return YamlConfiguration.loadConfiguration(file);
    }

    private void startAutosave() {
        if (autosaveTask != null) {
            autosaveTask.cancel();
        }
        long ticks = Math.max(30, getConfig().getInt("save-interval-seconds", 300)) * 20L;
        autosaveTask = Bukkit.getScheduler().runTaskTimer(this, () -> dataManager.saveAll(), ticks, ticks);
    }

    // ------------------------------------------------------------------
    // Mensajes
    // ------------------------------------------------------------------

    public String prefix() {
        return Colors.colorize(messagesConfig.getString("prefix", ""));
    }

    /**
     * Devuelve un mensaje de messages.yml con colores y reemplazos aplicados.
     * Los reemplazos van de a pares: "%player%", "Bughatti", "%points%", "50"...
     */
    public String format(String path, String... replacements) {
        String raw = messagesConfig.getString(path);
        if (raw == null) {
            raw = "&cMensaje faltante en messages.yml: " + path;
        }
        return Colors.colorize(replace(raw, replacements));
    }

    public void send(CommandSender sender, String path, String... replacements) {
        sender.sendMessage(prefix() + format(path, replacements));
    }

    public void sendList(CommandSender sender, String path) {
        List<String> lines = messagesConfig.getStringList(path);
        for (String line : lines) {
            sender.sendMessage(Colors.colorize(line));
        }
    }

    /**
     * Muestra un título y subtítulo configurados en messages.yml (sección "titles").
     * Los reemplazos van de a pares, igual que en format(...).
     */
    public void sendTitle(Player player, String path, String... replacements) {
        ConfigurationSection section = messagesConfig.getConfigurationSection("titles." + path);
        if (section == null || !section.getBoolean("enabled", true)) {
            return;
        }

        String title = Colors.colorize(replace(section.getString("title", ""), replacements));
        String subtitle = Colors.colorize(replace(section.getString("subtitle", ""), replacements));
        int fadeIn = section.getInt("fade-in", 10);
        int stay = section.getInt("stay", 50);
        int fadeOut = section.getInt("fade-out", 20);
        int delay = Math.max(0, section.getInt("delay", 0));

        if (delay <= 0) {
            player.sendTitle(title, subtitle, fadeIn, stay, fadeOut);
            return;
        }

        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (player.isOnline()) {
                player.sendTitle(title, subtitle, fadeIn, stay, fadeOut);
            }
        }, delay);
    }

    private String replace(String text, String... replacements) {
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            text = text.replace(replacements[i], replacements[i + 1]);
        }
        return text;
    }

    // ------------------------------------------------------------------
    // Getters
    // ------------------------------------------------------------------

    public static UltimateBattlepass getInstance() {
        return instance;
    }

    public FileConfiguration getMessagesConfig() {
        return messagesConfig;
    }

    public FileConfiguration getMenusConfig() {
        return menusConfig;
    }

    public FileConfiguration getMissionsConfig() {
        return missionsConfig;
    }

    public FileConfiguration getRewardsConfig() {
        return rewardsConfig;
    }

    public SeasonManager getSeasonManager() {
        return seasonManager;
    }

    public DataManager getDataManager() {
        return dataManager;
    }

    public PassManager getPassManager() {
        return passManager;
    }

    public MissionManager getMissionManager() {
        return missionManager;
    }

    public MenuManager getMenuManager() {
        return menuManager;
    }
            }
