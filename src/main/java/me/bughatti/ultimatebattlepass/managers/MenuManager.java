package me.bughatti.ultimatebattlepass.managers;

import me.bughatti.ultimatebattlepass.UltimateBattlepass;
import me.bughatti.ultimatebattlepass.data.PlayerData;
import me.bughatti.ultimatebattlepass.managers.MissionManager.Mission;
import me.bughatti.ultimatebattlepass.utils.Colors;
import me.bughatti.ultimatebattlepass.utils.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MenuManager implements Listener {

    public enum MenuType {
        MAIN,
        MISSIONS,
        MISSION_LIST,
        REWARDS
    }

    public static class MenuHolder implements InventoryHolder {

        private final Player player;
        private final MenuType type;
        private final int week;
        private int page;
        private Inventory inventory;
        private final Map<Integer, Runnable> actions = new HashMap<>();

        public MenuHolder(Player player, MenuType type, int week, int page) {
            this.player = player;
            this.type = type;
            this.week = week;
            this.page = page;
        }

        public Player getPlayer() {
            return player;
        }

        public MenuType getType() {
            return type;
        }

        /** 0 = misiones diarias, 1 a 4 = semana. Solo se usa en MISSION_LIST. */
        public int getWeek() {
            return week;
        }

        public int getPage() {
            return page;
        }

        public void setPage(int page) {
            this.page = page;
        }

        public Map<Integer, Runnable> getActions() {
            return actions;
        }

        public void setInventory(Inventory inventory) {
            this.inventory = inventory;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private final UltimateBattlepass plugin;

    public MenuManager(UltimateBattlepass plugin) {
        this.plugin = plugin;

        // Actualiza cada segundo la cuenta regresiva del Selector de Misiones
        Bukkit.getScheduler().runTaskTimer(plugin, this::tickOpenMenus, 20L, 20L);
    }

    /**
     * Cierra los menús abiertos para que no queden con datos viejos tras un reload.
     */
    public void reload() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof MenuHolder) {
                player.closeInventory();
            }
        }
    }

    // ------------------------------------------------------------------
    // Abrir menús
    // ------------------------------------------------------------------

    public void openMain(Player player) {
        ConfigurationSection menu = menu("main");
        open(player, MenuType.MAIN,
                menu.getString("title", "Menú de Pase de Batalla"),
                menu.getInt("rows", 3), 0, 0);
    }

    public void openMissions(Player player) {
        ConfigurationSection menu = menu("missions");
        open(player, MenuType.MISSIONS,
                menu.getString("title", "Selector de Misiones"),
                menu.getInt("rows", 4), 0, 0);
    }

    public void openMissionList(Player player, int week) {
        ConfigurationSection menu = menu("mission-list");
        String title = week == 0
                ? menu.getString("title-daily", "Misiones Diarias")
                : menu.getString("title-week", "Misiones de Semana #%week%").replace("%week%", String.valueOf(week));
        open(player, MenuType.MISSION_LIST, title, menu.getInt("rows", 4), week, 0);
    }

    public void openRewards(Player player, int page) {
        ConfigurationSection menu = menu("rewards");
        open(player, MenuType.REWARDS,
                menu.getString("title", "Recompensas del Pase"),
                menu.getInt("rows", 6), 0, page);
    }

    private void open(Player player, MenuType type, String title, int rows, int week, int page) {
        MenuHolder holder = new MenuHolder(player, type, week, page);
        int size = Math.max(1, Math.min(6, rows)) * 9;
        Inventory inventory = Bukkit.createInventory(holder, size, Colors.colorize(title));
        holder.setInventory(inventory);
        render(holder);
        player.openInventory(inventory);
    }

    private void refresh(MenuHolder holder) {
        holder.getInventory().clear();
        holder.getActions().clear();
        render(holder);
    }

    private void render(MenuHolder holder) {
        switch (holder.getType()) {
            case MAIN -> renderMain(holder);
            case MISSIONS -> renderMissions(holder);
            case MISSION_LIST -> renderMissionList(holder);
            case REWARDS -> renderRewards(holder);
        }
    }

    // ------------------------------------------------------------------
    // Menú de Pase de Batalla
    // ------------------------------------------------------------------

    private void renderMain(MenuHolder holder) {
        Player player = holder.getPlayer();
        PlayerData data = data(player);
        PassManager pass = plugin.getPassManager();
        SeasonManager season = plugin.getSeasonManager();

        ConfigurationSection items = menu("main").getConfigurationSection("items");
        if (items == null) {
            return;
        }

        int currentWeek = season.getCurrentWeek();
        String weekKey = currentWeek >= 1 ? "ACTIVE" : (currentWeek == 0 ? "UPCOMING" : "ENDED");
        String passKey = pass.hasPremium(player) ? "premium" : "free";

        ConfigurationSection rewards = items.getConfigurationSection("rewards");
        if (rewards != null) {
            place(holder, rewards.getInt("slot"), ItemBuilder.fromSection(rewards),
                    () -> openRewards(player, 0));
        }

        ConfigurationSection progress = items.getConfigurationSection("progress");
        if (progress != null) {
            ItemBuilder builder = ItemBuilder.fromSection(progress)
                    .replace("%season%", season.getSeasonName())
                    .replace("%week%", currentWeek >= 1 ? String.valueOf(currentWeek) : "-")
                    .replace("%week_state%", text("week-state." + weekKey))
                    .replace("%pass_type%", text("pass-type." + passKey))
                    .replace("%points%", String.valueOf(pass.getPointsInLevel(data)))
                    .replace("%points_needed%", String.valueOf(pass.getPointsPerLevel()))
                    .replace("%total_points%", String.valueOf(data.getPoints()))
                    .replace("%level%", String.valueOf(pass.getLevel(data)))
                    .replace("%max_level%", String.valueOf(pass.getMaxLevel()));
            place(holder, progress.getInt("slot"), builder, null);
        }

        ConfigurationSection missions = items.getConfigurationSection("missions");
        if (missions != null) {
            place(holder, missions.getInt("slot"), ItemBuilder.fromSection(missions),
                    () -> openMissions(player));
        }
    }

    // ------------------------------------------------------------------
    // Selector de Misiones
    // ------------------------------------------------------------------

    private void renderMissions(MenuHolder holder) {
        Player player = holder.getPlayer();
        PlayerData data = data(player);
        MissionManager missions = plugin.getMissionManager();
        SeasonManager season = plugin.getSeasonManager();
        ConfigurationSection menu = menu("missions");

        // Semanas #1 a #4
        List<Integer> weekSlots = menu.getIntegerList("week-slots");
        ConfigurationSection weekItem = menu.getConfigurationSection("week-item");

        if (weekItem != null) {
            for (int week = 1; week <= 4 && week <= weekSlots.size(); week++) {
                if (!season.isWeekEnabled(week)) {
                    continue;
                }

                final int selected = week;
                SeasonManager.WeekState state = season.getWeekState(selected);

                String loreKey = switch (state) {
                    case ACTIVE -> "lore-active";
                    case UPCOMING -> "lore-upcoming";
                    case ENDED -> "lore-ended";
                };

                long seconds = switch (state) {
                    case ACTIVE -> season.secondsUntil(season.weekEnd(selected));
                    case UPCOMING -> season.secondsUntil(season.weekStart(selected));
                    case ENDED -> 0L;
                };

                List<Mission> list = missions.getWeekMissions(selected);

                ItemBuilder builder = ItemBuilder.fromSection(weekItem, loreKey)
                        .replace("%week%", String.valueOf(selected))
                        .replace("%state%", text("week-state." + state.name()))
                        .replace("%time%", SeasonManager.formatDuration(seconds))
                        .replace("%completed%", String.valueOf(missions.countCompleted(data, list)))
                        .replace("%total%", String.valueOf(list.size()));

                place(holder, weekSlots.get(selected - 1), builder, () -> {
                    if (season.getWeekState(selected) == SeasonManager.WeekState.UPCOMING) {
                        plugin.send(player, "week-locked");
                        return;
                    }
                    openMissionList(player, selected);
                });
            }
        }

        // Misiones diarias ("Nuevas Misiones")
        ConfigurationSection dailyItem = menu.getConfigurationSection("daily-item");
        if (dailyItem != null) {
            List<Mission> list = missions.getDailyMissions();
            ItemBuilder builder = ItemBuilder.fromSection(dailyItem)
                    .replace("%time%", SeasonManager.formatDuration(season.secondsUntilDailyReset()))
                    .replace("%completed%", String.valueOf(missions.countCompleted(data, list)))
                    .replace("%total%", String.valueOf(list.size()));
            place(holder, dailyItem.getInt("slot"), builder, () -> openMissionList(player, 0));
        }

        placeBack(holder, menu, () -> openMain(player));
    }

    // ------------------------------------------------------------------
    // Lista de misiones (diarias o de una semana)
    // ------------------------------------------------------------------

    private void renderMissionList(MenuHolder holder) {
        Player player = holder.getPlayer();
        PlayerData data = data(player);
        MissionManager manager = plugin.getMissionManager();
        ConfigurationSection menu = menu("mission-list");

        int week = holder.getWeek();
        List<Mission> missions = week == 0 ? manager.getDailyMissions() : manager.getWeekMissions(week);
        List<Integer> slots = menu.getIntegerList("slots");

        for (int i = 0; i < missions.size() && i < slots.size(); i++) {
            Mission mission = missions.get(i);
            boolean done = manager.isCompleted(data, mission);

            ConfigurationSection section = menu.getConfigurationSection(done ? "completed" : "in-progress");
            if (section == null) {
                continue;
            }

            int current = done ? mission.getAmount() : manager.getProgress(data, mission);
            int percent = (int) Math.floor(current * 100.0 / mission.getAmount());

            ItemBuilder builder = ItemBuilder.fromSection(section)
                    .replaceLines("%description%", mission.getDescription())
                    .replace("%mission_name%", mission.getName())
                    .replace("%progress%", String.valueOf(current))
                    .replace("%amount%", String.valueOf(mission.getAmount()))
                    .replace("%points%", String.valueOf(mission.getPoints()))
                    .replace("%percent%", String.valueOf(percent))
                    .replace("%bar%", bar(current, mission.getAmount(), 10));

            place(holder, slots.get(i), builder, null);
        }

        placeBack(holder, menu, () -> openMissions(player));
    }

    // ------------------------------------------------------------------
    // Recompensas del Pase
    // ------------------------------------------------------------------

    private void renderRewards(MenuHolder holder) {
        Player player = holder.getPlayer();
        PlayerData data = data(player);
        PassManager pass = plugin.getPassManager();
        ConfigurationSection menu = menu("rewards");

        List<Integer> freeSlots = menu.getIntegerList("free-slots");
        List<Integer> barSlots = menu.getIntegerList("bar-slots");
        List<Integer> premiumSlots = menu.getIntegerList("premium-slots");

        int perPage = Math.max(1, freeSlots.size());
        int pages = Math.max(1, (int) Math.ceil(pass.getMaxLevel() / (double) perPage));
        int page = Math.max(0, Math.min(pages - 1, holder.getPage()));
        holder.setPage(page);

        // Borde
        ConfigurationSection border = menu.getConfigurationSection("border");
        if (border != null) {
            for (int slot : border.getIntegerList("slots")) {
                place(holder, slot, ItemBuilder.fromSection(border), null);
            }
        }

        int playerLevel = pass.getLevel(data);

        for (int i = 0; i < perPage; i++) {
            int level = page * perPage + i + 1;
            if (level > pass.getMaxLevel()) {
                break;
            }

            // Fila de recompensas gratis
            if (i < freeSlots.size()) {
                placeReward(holder, menu, data, level, false, freeSlots.get(i));
            }

            // Fila de progreso (verde = nivel alcanzado)
            if (i < barSlots.size()) {
                boolean reached = playerLevel >= level;
                ConfigurationSection section = menu.getConfigurationSection("bar." + (reached ? "reached" : "pending"));
                if (section != null) {
                    ItemBuilder builder = ItemBuilder.fromSection(section)
                            .replace("%level%", String.valueOf(level));
                    place(holder, barSlots.get(i), builder, null);
                }
            }

            // Fila de recompensas premium
            if (i < premiumSlots.size()) {
                placeReward(holder, menu, data, level, true, premiumSlots.get(i));
            }
        }

        // Navegación
        ConfigurationSection previous = menu.getConfigurationSection("previous");
        if (page > 0 && previous != null) {
            ItemBuilder builder = ItemBuilder.fromSection(previous)
                    .replace("%page%", String.valueOf(page + 1))
                    .replace("%pages%", String.valueOf(pages));
            place(holder, previous.getInt("slot"), builder, () -> {
                holder.setPage(holder.getPage() - 1);
                refresh(holder);
            });
        }

        ConfigurationSection next = menu.getConfigurationSection("next");
        if (page < pages - 1 && next != null) {
            ItemBuilder builder = ItemBuilder.fromSection(next)
                    .replace("%page%", String.valueOf(page + 1))
                    .replace("%pages%", String.valueOf(pages));
            place(holder, next.getInt("slot"), builder, () -> {
                holder.setPage(holder.getPage() + 1);
                refresh(holder);
            });
        }

        placeBack(holder, menu, () -> openMain(player));
    }

    private void placeReward(MenuHolder holder, ConfigurationSection menu, PlayerData data,
                             int level, boolean premium, int slot) {
        PassManager pass = plugin.getPassManager();
        PassManager.Reward reward = pass.getReward(level, premium);
        if (reward == null) {
            return;
        }

        Player player = holder.getPlayer();

        String state;
        String status;

        if (pass.isClaimed(data, level, premium)) {
            state = "claimed";
            status = "claimed";
        } else if (premium && !pass.hasPremium(player)) {
            state = "locked";
            status = "need-premium";
        } else if (pass.getLevel(data) < level) {
            state = "locked";
            status = "level-locked";
        } else {
            state = "available";
            status = "available";
        }

        ConfigurationSection section = menu.getConfigurationSection((premium ? "premium." : "free.") + state);
        if (section == null) {
            return;
        }

        ItemBuilder builder = ItemBuilder.fromSection(section)
                .amount(level)
                .replaceLines("%rewards%", reward.getDisplay())
                .replace("%status%", menu.getString("status." + status, ""))
                .replace("%level%", String.valueOf(level));

        place(holder, slot, builder, () -> {
            pass.claim(player, level, premium);
            refresh(holder);
        });
    }

    // ------------------------------------------------------------------
    // Eventos
    // ------------------------------------------------------------------

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof MenuHolder holder)) {
            return;
        }

        // Nada se puede mover dentro ni hacia estos menús
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        Inventory clicked = event.getClickedInventory();
        if (clicked == null || !clicked.equals(top)) {
            return;
        }

        Runnable action = holder.getActions().get(event.getRawSlot());
        if (action != null) {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1f);
            action.run();
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof MenuHolder) {
            event.setCancelled(true);
        }
    }

    private void tickOpenMenus() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            Inventory top = player.getOpenInventory().getTopInventory();
            if (top.getHolder() instanceof MenuHolder holder && holder.getType() == MenuType.MISSIONS) {
                refresh(holder);
            }
        }
    }

    // ------------------------------------------------------------------
    // Utilidades
    // ------------------------------------------------------------------

    private void place(MenuHolder holder, int slot, ItemBuilder builder, Runnable action) {
        Inventory inventory = holder.getInventory();
        if (slot < 0 || slot >= inventory.getSize()) {
            return;
        }

        inventory.setItem(slot, builder.build());
        if (action != null) {
            holder.getActions().put(slot, action);
        }
    }

    private void placeBack(MenuHolder holder, ConfigurationSection menu, Runnable action) {
        ConfigurationSection back = menu.getConfigurationSection("back");
        if (back != null) {
            place(holder, back.getInt("slot"), ItemBuilder.fromSection(back), action);
        }
    }

    private ConfigurationSection menu(String path) {
        ConfigurationSection section = plugin.getMenusConfig().getConfigurationSection(path);
        return section != null ? section : new MemoryConfiguration();
    }

    private String text(String path) {
        return plugin.getMenusConfig().getString("texts." + path, path);
    }

    private PlayerData data(Player player) {
        PlayerData data = plugin.getDataManager().get(player.getUniqueId());
        plugin.getDataManager().validate(data);
        return data;
    }

    private String bar(int current, int total, int length) {
        int filled = (int) Math.round((double) current / total * length);
        filled = Math.max(0, Math.min(length, filled));
        return "&a" + "■".repeat(filled) + "&7" + "■".repeat(length - filled);
    }
      }
