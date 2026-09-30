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
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class MenuManager implements Listener {

    public enum MenuType {
        MAIN,
        MISSIONS,
        MISSION_LIST,
        REWARDS,
        TOP,
        BOOSTERS
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

    /**
     * Estadísticas del jugador para la cabeza del menú Top.
     */
    private record Stats(int dailyCompleted, int dailyTotal,
                         int weeklyCompleted, int weeklyTotal,
                         int weeksCompleted, int weeksTotal) {

        int missionsCompleted() {
            return dailyCompleted + weeklyCompleted;
        }

        int missionsTotal() {
            return dailyTotal + weeklyTotal;
        }
    }

    private final UltimateBattlepass plugin;

    public MenuManager(UltimateBattlepass plugin) {
        this.plugin = plugin;

        // Actualiza cada segundo los menús que tienen cuenta regresiva
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
                menu.getInt("rows", 4), 0, 0);
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

    public void openTop(Player player) {
        ConfigurationSection menu = menu("top");
        open(player, MenuType.TOP,
                menu.getString("title", "Top del Pase de Batalla"),
                menu.getInt("rows", 6), 0, 0);
    }

    public void openBoosters(Player player) {
        ConfigurationSection menu = menu("boosters");
        open(player, MenuType.BOOSTERS,
                menu.getString("title", "Boosters de Puntos"),
                menu.getInt("rows", 3), 0, 0);
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
        // La decoración va primero para que los ítems reales queden encima
        renderDecoration(holder, menu(configKey(holder.getType())));

        switch (holder.getType()) {
            case MAIN -> renderMain(holder);
            case MISSIONS -> renderMissions(holder);
            case MISSION_LIST -> renderMissionList(holder);
            case REWARDS -> renderRewards(holder);
            case TOP -> renderTop(holder);
            case BOOSTERS -> renderBoosters(holder);
        }
    }

    private String configKey(MenuType type) {
        return switch (type) {
            case MAIN -> "main";
            case MISSIONS -> "missions";
            case MISSION_LIST -> "mission-list";
            case REWARDS -> "rewards";
            case TOP -> "top";
            case BOOSTERS -> "boosters";
        };
    }

    /**
     * Decoración opcional. En el yml, cada menú puede tener:
     * decoration:
     *   cualquier-nombre:
     *     material: GRAY_STAINED_GLASS_PANE
     *     name: " "
     *     slots: [0, 1, 2]
     */
    private void renderDecoration(MenuHolder holder, ConfigurationSection menu) {
        ConfigurationSection decoration = menu.getConfigurationSection("decoration");
        if (decoration == null) {
            return;
        }

        for (String key : decoration.getKeys(false)) {
            ConfigurationSection section = decoration.getConfigurationSection(key);
            if (section == null) {
                continue;
            }

            ItemBuilder builder = ItemBuilder.fromSection(section);
            for (int slot : section.getIntegerList("slots")) {
                place(holder, slot, builder, null);
            }
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
        String boosterText = boosterText(data);

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
                    .replace("%max_level%", String.valueOf(pass.getMaxLevel()))
                    .replace("%booster%", boosterText);
            place(holder, progress.getInt("slot"), builder, null);
        }

        ConfigurationSection missions = items.getConfigurationSection("missions");
        if (missions != null) {
            place(holder, missions.getInt("slot"), ItemBuilder.fromSection(missions),
                    () -> openMissions(player));
        }

        ConfigurationSection top = items.getConfigurationSection("top");
        if (top != null) {
            place(holder, top.getInt("slot"), ItemBuilder.fromSection(top),
                    () -> openTop(player));
        }

        ConfigurationSection boosters = items.getConfigurationSection("boosters");
        if (boosters != null) {
            ItemBuilder builder = ItemBuilder.fromSection(boosters)
                    .replace("%booster%", boosterText);
            place(holder, boosters.getInt("slot"), builder, () -> openBoosters(player));
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
                    .replace("%unit%", mission.getUnit())
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
    // Top del Pase
    // ------------------------------------------------------------------

    private void renderTop(MenuHolder holder) {
        Player player = holder.getPlayer();
        ConfigurationSection menu = menu("top");
        TopManager top = plugin.getTopManager();

        // Posiciones 1, 2, 3... según la lista de slots
        List<Integer> slots = menu.getIntegerList("slots");

        for (int i = 0; i < slots.size(); i++) {
            int position = i + 1;
            TopManager.Entry entry = top.getEntry(position);

            if (entry == null) {
                ConfigurationSection empty = menu.getConfigurationSection("empty");
                if (empty != null) {
                    place(holder, slots.get(i),
                            ItemBuilder.fromSection(empty).replace("%position%", String.valueOf(position)), null);
                }
                continue;
            }

            ConfigurationSection section = menu.getConfigurationSection("entry." + position);
            if (section == null) {
                section = menu.getConfigurationSection("entry.default");
            }
            if (section == null) {
                continue;
            }

            ItemStack item = ItemBuilder.fromSection(section)
                    .replace("%position%", String.valueOf(position))
                    .replace("%player%", entry.getName())
                    .replace("%level%", String.valueOf(entry.getLevel()))
                    .replace("%points%", String.valueOf(entry.getPoints()))
                    .build();

            placeItem(holder, slots.get(i), withOwner(item, entry.getUuid()), null);
        }

        // Cabeza con las estadísticas del jugador
        ConfigurationSection stats = menu.getConfigurationSection("stats");
        if (stats != null) {
            PlayerData data = data(player);
            PassManager pass = plugin.getPassManager();
            Stats s = stats(data);

            int position = top.getPosition(player.getUniqueId());
            String passKey = pass.hasPremium(player) ? "premium" : "free";

            ItemStack item = ItemBuilder.fromSection(stats)
                    .replace("%player%", player.getName())
                    .replace("%position%", position > 0 ? "#" + position : "-")
                    .replace("%level%", String.valueOf(pass.getLevel(data)))
                    .replace("%max_level%", String.valueOf(pass.getMaxLevel()))
                    .replace("%total_points%", String.valueOf(data.getPoints()))
                    .replace("%missions_completed%", String.valueOf(s.missionsCompleted()))
                    .replace("%missions_total%", String.valueOf(s.missionsTotal()))
                    .replace("%daily_completed%", String.valueOf(s.dailyCompleted()))
                    .replace("%daily_total%", String.valueOf(s.dailyTotal()))
                    .replace("%weekly_completed%", String.valueOf(s.weeklyCompleted()))
                    .replace("%weekly_total%", String.valueOf(s.weeklyTotal()))
                    .replace("%weeks_completed%", String.valueOf(s.weeksCompleted()))
                    .replace("%weeks_total%", String.valueOf(s.weeksTotal()))
                    .replace("%pass_type%", text("pass-type." + passKey))
                    .replace("%booster%", boosterText(data))
                    .build();

            placeItem(holder, stats.getInt("slot"), withOwner(item, player.getUniqueId()), null);
        }

        placeBack(holder, menu, () -> openMain(player));
    }

    /**
     * Calcula las estadísticas de misiones de la temporada actual.
     * Las diarias son las de hoy; las semanales acumulan toda la temporada.
     */
    private Stats stats(PlayerData data) {
        MissionManager missions = plugin.getMissionManager();
        SeasonManager season = plugin.getSeasonManager();

        int dailyTotal = missions.getDailyMissions().size();
        int dailyCompleted = missions.countCompleted(data, missions.getDailyMissions());

        int weeklyTotal = 0;
        int weeklyCompleted = 0;
        int weeksCompleted = 0;
        int weeksTotal = season.getWeeks();

        for (int week = 1; week <= weeksTotal; week++) {
            List<Mission> list = missions.getWeekMissions(week);
            int done = missions.countCompleted(data, list);

            weeklyTotal += list.size();
            weeklyCompleted += done;

            if (!list.isEmpty() && done == list.size()) {
                weeksCompleted++;
            }
        }

        return new Stats(dailyCompleted, dailyTotal, weeklyCompleted, weeklyTotal, weeksCompleted, weeksTotal);
    }

    // ------------------------------------------------------------------
    // Boosters
    // ------------------------------------------------------------------

    private void renderBoosters(MenuHolder holder) {
        Player player = holder.getPlayer();
        PlayerData data = data(player);
        BoosterManager boosters = plugin.getBoosterManager();
        ConfigurationSection menu = menu("boosters");

        // Booster activado con click derecho
        if (data.hasActiveBooster()) {
            ConfigurationSection active = menu.getConfigurationSection("active");
            if (active != null) {
                ItemBuilder builder = ItemBuilder.fromSection(active)
                        .replace("%booster%", data.getBoosterName())
                        .replace("%multiplier%", BoosterManager.formatNumber(data.getBoosterMultiplier()))
                        .replace("%chance%", BoosterManager.formatNumber(data.getBoosterChance()))
                        .replace("%time%", SeasonManager.formatDuration(data.getBoosterRemainingSeconds()));
                place(holder, active.getInt("slot"), builder, null);
            }
        } else {
            ConfigurationSection inactive = menu.getConfigurationSection("inactive");
            if (inactive != null) {
                place(holder, inactive.getInt("slot"), ItemBuilder.fromSection(inactive), null);
            }
        }

        // Booster de la mano secundaria
        BoosterManager.Booster offhand = boosters.fromItem(player.getInventory().getItemInOffHand());
        if (offhand != null && offhand.getType() == BoosterManager.BoosterType.OFFHAND) {
            ConfigurationSection section = menu.getConfigurationSection("offhand-active");
            if (section != null) {
                ItemBuilder builder = ItemBuilder.fromSection(section)
                        .replace("%booster%", offhand.getName())
                        .replace("%multiplier%", BoosterManager.formatNumber(offhand.getMultiplier()))
                        .replace("%chance%", BoosterManager.formatNumber(offhand.getChance()));
                place(holder, section.getInt("slot"), builder, null);
            }
        } else {
            ConfigurationSection section = menu.getConfigurationSection("offhand-empty");
            if (section != null) {
                place(holder, section.getInt("slot"), ItemBuilder.fromSection(section), null);
            }
        }

        ConfigurationSection info = menu.getConfigurationSection("info");
        if (info != null) {
            place(holder, info.getInt("slot"), ItemBuilder.fromSection(info), null);
        }

        placeBack(holder, menu, () -> openMain(player));
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
            if (top.getHolder() instanceof MenuHolder holder
                    && (holder.getType() == MenuType.MISSIONS || holder.getType() == MenuType.BOOSTERS)) {
                refresh(holder);
            }
        }
    }

    // ------------------------------------------------------------------
    // Utilidades
    // ------------------------------------------------------------------

    private void place(MenuHolder holder, int slot, ItemBuilder builder, Runnable action) {
        placeItem(holder, slot, builder.build(), action);
    }

    private void placeItem(MenuHolder holder, int slot, ItemStack item, Runnable action) {
        Inventory inventory = holder.getInventory();
        if (slot < 0 || slot >= inventory.getSize()) {
            return;
        }

        inventory.setItem(slot, item);
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

    /**
     * Pone la cabeza de un jugador en un ítem PLAYER_HEAD.
     */
    private ItemStack withOwner(ItemStack item, UUID uuid) {
        ItemMeta meta = item.getItemMeta();
        if (meta instanceof SkullMeta skull) {
            skull.setOwningPlayer(Bukkit.getOfflinePlayer(uuid));
            item.setItemMeta(skull);
        }
        return item;
    }

    private String boosterText(PlayerData data) {
        if (!data.hasActiveBooster()) {
            return text("booster.none");
        }
        return text("booster.active")
                .replace("%booster%", data.getBoosterName())
                .replace("%time%", SeasonManager.formatDuration(data.getBoosterRemainingSeconds()));
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
