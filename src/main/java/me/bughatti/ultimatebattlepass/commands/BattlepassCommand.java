package me.bughatti.ultimatebattlepass.commands;

import me.bughatti.ultimatebattlepass.UltimateBattlepass;
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
            case "addpoints" -> points(sender, args, false);
            case "setpoints" -> points(sender, args, true);
            case "premium" -> premium(sender, args);
            case "season" -> season(sender, args);
            default -> plugin.send(sender, "unknown-command");
        }
        return true;
    }

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
            if (admin) {
                options.add("reload");
                options.add("addpoints");
                options.add("setpoints");
                options.add("premium");
                options.add("season");
            }
            return filter(options, args[0]);
        }

        if (!admin) {
            return new ArrayList<>();
        }

        String sub = args[0].toLowerCase();

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
