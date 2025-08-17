package ru.gdev.seemegameteor.command;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import ru.gdev.seemegameteor.SeeMegaMeteor;
import ru.gdev.seemegameteor.event.MegaMeteorEventManager;
import ru.gdev.seemegameteor.menu.ChanceEditMenu;
import ru.gdev.seemegameteor.menu.LootEditMenu;
import ru.gdev.seemegameteor.util.TimeUtil;

import java.util.Arrays;
import java.util.List;

public class SeeMegaMeteorCommand implements CommandExecutor, TabCompleter {
    private final SeeMegaMeteor plugin;

    public SeeMegaMeteorCommand(SeeMegaMeteor plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (cmd.getName().equalsIgnoreCase("megameteor")) {
            MegaMeteorEventManager eventManager = plugin.getEventManager();
            String timeLeft = eventManager.getTimeUntilNextLabel();
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    "&cДо падения МЕГА-Метеора осталось: &e" + timeLeft));
            return true;
        }

        if (!sender.hasPermission("seemegameteor.admin")) {
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage("/seemegameteor edit <loot/chance> | start | stop | tp | reload | disable | enable");
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "edit":
                handleEditCommand(sender, args);
                break;
            case "start":
                plugin.getEventManager().adminStart();
                plugin.getConfig().getStringList("messages.external.start").forEach(m ->
                        Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&', m)));
                break;
            case "stop":
                plugin.getEventManager().adminStop();
                plugin.getConfig().getStringList("messages.external.end").forEach(m ->
                        Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&', m)));
                break;
            case "tp":
                if (sender instanceof Player) {
                    plugin.getEventManager().teleportToEvent((Player) sender);
                }
                break;
            case "reload":
                plugin.reload();
                int seconds = plugin.getEventManager().secondsUntilPlannedStart();
                sender.sendMessage(ChatColor.GREEN + "Конфигурация перезагружена. Следующее окно запуска: " +
                        TimeUtil.format(seconds));
                break;
            case "disable":
                plugin.setEventsEnabled(false);
                sender.sendMessage(ChatColor.RED + "Ивент отключён до команды enable");
                break;
            case "enable":
                plugin.setEventsEnabled(true);
                sender.sendMessage(ChatColor.GREEN + "Ивент включён");
                break;
            default:
                sender.sendMessage("/seemegameteor edit <loot/chance> | start | stop | tp | reload | disable | enable");
        }
        return true;
    }

    private void handleEditCommand(CommandSender s, String[] args) {
        if (args.length < 2 || !(s instanceof Player)) return;

        Player p = (Player) s;
        switch (args[1].toLowerCase()) {
            case "loot":
                new LootEditMenu(plugin, p).open();
                break;
            case "chance":
                new ChanceEditMenu(plugin, p).open();
                break;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String alias, String[] args) {
        if (!cmd.getName().equalsIgnoreCase("seemegameteor")) return null;

        if (args.length == 1) {
            return Arrays.asList("edit", "start", "stop", "tp", "reload", "disable", "enable");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("edit")) {
            return Arrays.asList("loot", "chance");
        }
        return null;
    }
}