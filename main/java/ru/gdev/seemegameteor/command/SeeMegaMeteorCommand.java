package ru.gdev.seemegameteor.command;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import ru.gdev.seemegameteor.SeeMegaMeteor;
import ru.gdev.seemegameteor.menu.ChanceEditMenu;
import ru.gdev.seemegameteor.menu.LootEditMenu;
import ru.gdev.seemegameteor.util.TimeUtil;

import java.util.*;

public class SeeMegaMeteorCommand implements CommandExecutor, TabCompleter {
    private final SeeMegaMeteor plugin;
    private final List<String> subCommands = Arrays.asList(
            "edit", "start", "stop", "tp", "reload", "disable", "enable"
    );
    private final List<String> editSubCommands = Arrays.asList("loot", "chance");

    public SeeMegaMeteorCommand(SeeMegaMeteor plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (cmd.getName().equalsIgnoreCase("megameteor")) {
            handleMegaMeteorCommand(sender);
            return true;
        }

        if (!sender.hasPermission("seemegameteor.admin")) {
            return true;
        }

        if (args.length == 0) {
            showUsage(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "edit":
                handleEditCommand(sender, args);
                break;
            case "start":
                handleStartCommand(sender);
                break;
            case "stop":
                handleStopCommand(sender);
                break;
            case "tp":
                handleTpCommand(sender);
                break;
            case "reload":
                handleReloadCommand(sender);
                break;
            case "disable":
                handleDisableCommand(sender);
                break;
            case "enable":
                handleEnableCommand(sender);
                break;
            default:
                showUsage(sender);
        }
        return true;
    }

    private void handleMegaMeteorCommand(CommandSender sender) {
        String time = plugin.getEventManager().getTimeUntilNextLabel();
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                "&cДо падения МЕГА-Метеора осталось: &e" + time));
    }

    private void handleEditCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Эта команда только для игроков");
            return;
        }

        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Использование: /seemegameteor edit <loot/chance>");
            return;
        }

        Player player = (Player) sender;
        switch (args[1].toLowerCase()) {
            case "loot":
                new LootEditMenu(plugin, player).open();
                break;
            case "chance":
                new ChanceEditMenu(plugin, player).open();
                break;
            default:
                sender.sendMessage(ChatColor.RED + "Неизвестный параметр: " + args[1]);
        }
    }

    private void handleStartCommand(CommandSender sender) {
        plugin.getEventManager().adminStart();
        plugin.getConfig().getStringList("messages.external.start").forEach(msg ->
                Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&', msg)));
    }

    private void handleStopCommand(CommandSender sender) {
        plugin.getEventManager().adminStop();
        plugin.getConfig().getStringList("messages.external.end").forEach(msg ->
                Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&', msg)));
    }

    private void handleTpCommand(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Эта команда только для игроков");
            return;
        }
        plugin.getEventManager().teleportToEvent((Player) sender);
    }

    private void handleReloadCommand(CommandSender sender) {
        plugin.reload();
        int seconds = plugin.getEventManager().secondsUntilPlannedStart();
        sender.sendMessage(ChatColor.GREEN + "Конфигурация перезагружена. Следующее окно запуска: " +
                TimeUtil.format(seconds));
    }

    private void handleDisableCommand(CommandSender sender) {
        plugin.setEventsEnabled(false);
        sender.sendMessage(ChatColor.RED + "Ивент отключён до команды enable");
    }

    private void handleEnableCommand(CommandSender sender) {
        plugin.setEventsEnabled(true);
        sender.sendMessage(ChatColor.GREEN + "Ивент включён");
    }

    private void showUsage(CommandSender sender) {
        sender.sendMessage(ChatColor.RED + "Использование: /seemegameteor edit <loot/chance> | start | stop | tp | reload | disable | enable");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (!cmd.getName().equalsIgnoreCase("seemegameteor")) return null;

        if (args.length == 1) {
            return filterCompletions(args[0], subCommands);
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("edit")) {
            return filterCompletions(args[1], editSubCommands);
        }

        return Collections.emptyList();
    }

    private List<String> filterCompletions(String input, List<String> options) {
        List<String> completions = new ArrayList<>();
        String lowerInput = input.toLowerCase();

        for (String option : options) {
            if (option.toLowerCase().startsWith(lowerInput)) {
                completions.add(option);
            }
        }

        return completions;
    }
}