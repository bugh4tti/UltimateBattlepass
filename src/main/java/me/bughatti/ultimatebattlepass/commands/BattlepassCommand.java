package me.bughatti.ultimatebattlepass.commands;

import me.bughatti.ultimatebattlepass.UltimateBattlepass;
import me.bughatti.ultimatebattlepass.managers.BoosterManager;
import me.bughatti.ultimatebattlepass.managers.TopManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class BattlepassCommand implements CommandExecutor, TabCompleter {

    private static final String USE_PERMISSION = "ultimatebattlepass.use";
    private static final String ADMIN_PERMISSION = "ultimatebattlepass.admin";

    private final UltimateBattlepass plugin;

    public BattlepassCommand(UltimateBattlepass plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                plugin.send(sender, "player-only");
                return true;
            }
            if (!player.hasPermission(USE_PERMISSION)) {
                plugin.send(sender, "no-permission");
                return true;
            }
            plugin.getMenuManager().openMain(player);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "help" -> plugin.sendList(sender, "help");
            case "reload" -> reload(sender);
            case "top" -> top(sender, args);
            case "booster", "boosters" -> booster(sender, args);
            case "addpoints" -> points(sender, args, false);
            case "setpoints" -> points(sender, args, true);
            case "premium" -> premium(sender, args);
            case "season" -> season(sender, args);
            default -> plugin.send(sender, "unknown-command");
        }
        return true;
    }

    // ------------------------------------------------------------------
    // Administración básica
    // ------------------------------------------------------------------

    private void reload(CommandSender sender) {
        if (!requireAdmin(sender)) {
            return;
        }
        plugin.reloadAll();
        plugin.send(sender, "reloaded");
    }

    private void points(CommandSender sender, String[] args, boolean set) {
        if (!requireAdmin(sender)) {
            return;
        }
        if (args.length < 3) {
            plugin.send(sender, "unknown-command");
            return;
        }

        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            plugin.send(sender, "player-not-found", "%player%", args[1]);
            return;
        }

        Integer amount = parseInt(args[2]);
        if (amount == null || amount < 0) {
            plugin.send(sender, "invalid-number");
            return;
        }

        if (set) {
            plugin.getPassManager().setPoints(target, amount);
            plugin.send(sender, "admin.points-set",
                    "%player%", target.getName(), "%points%", String.valueOf(amount));
        } else {
            plugin.getPassManager().addPoints(target, amount);
            plugin.send(sender, "admin.points-added",
                    "%player%", target.getName(), "%points%", String.valueOf(amount));
        }
    }

    private void premium(CommandSender sender, String[] args) {
        if (!requireAdmin(sender)) {
            return;
        }
        if (args.length < 3) {
            plugin.send(sender, "unknown-command");
            return;
        }

        String action = args[1].toLowerCase();
        boolean give = action.equals("give");
        if (!give && !action.equals("remove")) {
            plugin.send(sender, "unknown-command");
            return;
        }

        Player target = Bukkit.getPlayerExact(args[2]);
        if (target == null) {
            plugin.send(sender, "player-not-found", "%player%", args[2]);
            return;
        }

        String key = give ? "pass.premium-give-command" : "pass.premium-remove-command";
        String fallback = give
                ? "lp user %player% permission set %permission% true"
                : "lp user %player% permission unset %permission%";

        String console = plugin.getConfig().getString(key, fallback)
                .replace("%player%", target.getName())
                .replace("%permission%", plugin.getPassManager().getPremiumPermission());

        if (console.startsWith("/")) {
            console = console.substring(1);
        }
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), console);

        plugin.send(sender, give ? "admin.premium-given" : "admin.premium-removed",
                "%player%", target.getName());
    }

    private void season(CommandSender sender, String[] args) {
        if (!requireAdmin(sender)) {
            return;
        }
        if (args.length < 2 || !args[1].equalsIgnoreCase("start")) {
            plugin.send(sender, "unknown-command");
            return;
        }

        plugin.getSeasonManager().startNewSeasonToday();
        plugin.getDataManager().validateAll();
        plugin.send(sender, "admin.season-started");
    }

    // ------------------------------------------------------------------
    // Top
    // ------------------------------------------------------------------

    private void top(CommandSender sender, String[] args) {
        boolean chat = args.length > 1 && args[1].equalsIgnoreCase("chat");

        if (sender instanceof Player player && !chat) {
            if (!player.hasPermission(USE_PERMISSION)) {
                plugin.send(sender, "no-permission");
                return;
            }
            plugin.getMenuManager().openTop(player);
            return;
        }

        sendTopChat(sender);
    }

    private void sendTopChat(CommandSender sender) {
        sender.sendMessage(plugin.format("top.header"));

        List<TopManager.Entry> entries = plugin.getTopManager().getTop();
        if (entries.isEmpty()) {
            sender.sendMessage(plugin.format("top.empty"));
        }

        for (TopManager.Entry entry : entries) {
            sender.sendMessage(plugin.format("top.line",
                    "%position%", String.valueOf(entry.getPosition()),
                    "%player%", entry.getName(),
                    "%level%", String.valueOf(entry.getLevel()),
                    "%points%", String.valueOf(entry.getPoints())));
        }

        if (sender instanceof Player player) {
            int position = plugin.getTopManager().getPosition(player.getUniqueId());
            if (position > 0) {
                sender.sendMessage(plugin.format("top.footer", "%position%", String.valueOf(position)));
            } else {
                sender.sendMessage(plugin.format("top.footer-none"));
            }
        }

        sender.sendMessage(plugin.format("top.footer-line"));
    }

    // ------------------------------------------------------------------
    // Boosters
    // ------------------------------------------------------------------

    private void booster(CommandSender sender, String[] args) {
        String sub = args.length > 1 ? args[1].toLowerCase() : "info";

        switch (sub) {
            case "info" -> {
                if (!(sender instanceof Player player)) {
                    plugin.send(sender, "player-only");
                    return;
                }
                if (!player.hasPermission(USE_PERMISSION)) {
                    plugin.send(sender, "no-permission");
                    return;
                }
                plugin.getMenuManager().openBoosters(player);
            }
            case "list" -> boosterList(sender);
            case "give" -> boosterGive(sender, args);
            case "clear" -> boosterClear(sender, args);
            default -> plugin.send(sender, "unknown-command");
        }
    }

    private void boosterList(CommandSender sender) {
        if (!requireAdmin(sender)) {
            return;
        }

        BoosterManager manager = plugin.getBoosterManager();
        List<String> ids = manager.getIds();

        sender.sendMessage(plugin.format("booster.list-header", "%count%", String.valueOf(ids.size())));
        for (String id : ids) {
            BoosterManager.Booster booster = manager.get(id);
            if (booster == null) {
                continue;
            }
            sender.sendMessage(plugin.format("booster.list-line",
                    "%id%", booster.getId(),
                    "%type%", booster.getType().name(),
                    "%multiplier%", BoosterManager.formatNumber(booster.getMultiplier()),
                    "%chance%", BoosterManager.formatNumber(booster.getChance())));
        }
    }

    private void boosterGive(CommandSender sender, String[] args) {
        if (!requireAdmin(sender)) {
            return;
        }
        if (args.length < 4) {
            plugin.send(sender, "unknown-command");
            return;
        }

        Player target = Bukkit.getPlayerExact(args[2]);
        if (target == null) {
            plugin.send(sender, "player-not-found", "%player%", args[2]);
            return;
        }

        BoosterManager.Booster booster = plugin.getBoosterManager().get(args[3]);
        if (booster == null) {
            plugin.send(sender, "booster.unknown", "%id%", args[3]);
            return;
        }

        int amount = 1;
        if (args.length > 4) {
            Integer parsed = parseInt(args[4]);
            if (parsed == null || parsed < 1) {
                plugin.send(sender, "invalid-number");
                return;
            }
            amount = Math.min(parsed, 2304);
        }

        plugin.getBoosterManager().give(target, booster, amount);

        plugin.send(sender, "booster.given",
                "%amount%", String.valueOf(amount),
                "%booster%", booster.getName(),
                "%player%", target.getName());
        plugin.send(target, "booster.received",
                "%amount%", String.valueOf(amount),
                "%booster%", booster.getName());
    }

    private void boosterClear(CommandSender sender, String[] args) {
        if (!requireAdmin(sender)) {
            return;
        }
        if (args.length < 3) {
            plugin.send(sender, "unknown-command");
            return;
        }

        Player target = Bukkit.getPlayerExact(args[2]);
        if (target == null) {
            plugin.send(sender, "player-not-found", "%player%", args[2]);
            return;
        }

        plugin.getBoosterManager().clear(target);
        plugin.send(sender, "booster.cleared", "%player%", target.getName());
    }

    // ------------------------------------------------------------------
    // Utilidades
    // ------------------------------------------------------------------

    private boolean requireAdmin(CommandSender sender) {
        if (sender.hasPermission(ADMIN_PERMISSION)) {
            return true;
        }
        plugin.send(sender, "no-permission");
        return false;
    }

    private Integer parseInt(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // ------------------------------------------------------------------
    // Tab completer
    // ------------------------------------------------------------------

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        boolean admin = sender.hasPermission(ADMIN_PERMISSION);

        if (args.length == 1) {
            List<String> options = new ArrayList<>();
            options.add("help");
            options.add("top");
            options.add("booster");
            if (admin) {
                options.add("reload");
                options.add("addpoints");
                options.add("setpoints");
                options.add("premium");
                options.add("season");
            }
            return filter(options, args[0]);
        }

        String sub = args[0].toLowerCase();

        if (sub.equals("top") && args.length == 2) {
            return filter(List.of("chat"), args[1]);
        }

        if (sub.equals("booster") || sub.equals("boosters")) {
            return boosterTab(args, admin);
        }

        if (!admin) {
            return new ArrayList<>();
        }

        if (args.length == 2) {
            if (sub.equals("addpoints") || sub.equals("setpoints")) {
                return filter(onlineNames(), args[1]);
            }
            if (sub.equals("premium")) {
                return filter(List.of("give", "remove"), args[1]);
            }
            if (sub.equals("season")) {
                return filter(List.of("start"), args[1]);
            }
        }

        if (args.length == 3) {
            if (sub.equals("premium")) {
                return filter(onlineNames(), args[2]);
            }
            if (sub.equals("addpoints") || sub.equals("setpoints")) {
                return filter(List.of("50", "120", "500"), args[2]);
            }
        }

        return new ArrayList<>();
    }

    private List<String> boosterTab(String[] args, boolean admin) {
        if (args.length == 2) {
            List<String> options = new ArrayList<>();
            options.add("info");
            if (admin) {
                options.add("list");
                options.add("give");
                options.add("clear");
            }
            return filter(options, args[1]);
        }

        if (!admin) {
            return new ArrayList<>();
        }

        String action = args[1].toLowerCase();

        if (args.length == 3 && (action.equals("give") || action.equals("clear"))) {
            return filter(onlineNames(), args[2]);
        }
        if (args.length == 4 && action.equals("give")) {
            return filter(plugin.getBoosterManager().getIds(), args[3]);
        }
        if (args.length == 5 && action.equals("give")) {
            return filter(List.of("1", "5", "10"), args[4]);
        }

        return new ArrayList<>();
    }

    private List<String> onlineNames() {
        List<String> names = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            names.add(player.getName());
        }
        return names;
    }

    private List<String> filter(List<String> options, String typed) {
        List<String> result = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase().startsWith(typed.toLowerCase())) {
                result.add(option);
            }
        }
        return result;
    }
                }
