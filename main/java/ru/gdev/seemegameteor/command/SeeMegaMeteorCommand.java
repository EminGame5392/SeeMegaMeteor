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
import java.util.stream.Collectors;

import static com.sk89q.commandbook.CommandBookUtil.sendMessage;

public class SeeMegaMeteorCommand implements CommandExecutor, TabCompleter {
    private final SeeMegaMeteor plugin;
    private final List<String> subCommands = Arrays.asList("edit", "start", "stop", "tp", "reload", "disable", "enable", "help");
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
            sendMessage(sender, "messages.errors.no_permission");
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            showHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "edit": handleEditCommand(sender, args); break;
            case "start": handleStartCommand(sender); break;
            case "stop": handleStopCommand(sender); break;
            case "tp": handleTpCommand(sender); break;
            case "reload": handleReloadCommand(sender); break;
            case "disable": handleDisableCommand(sender); break;
            case "enable": handleEnableCommand(sender); break;
            default: sendMessage(sender, "messages.errors.invalid_command");
        }
        return true;
    }

    private void showHelp(CommandSender sender) {
        List<String> help = Arrays.asList(
                "&#FFAA00===== &#FFFFFFSeeMegaMeteor Help &#FFAA00=====",
                "&#FFAA00/seemegameteor edit loot &#FFFFFF- Редактировать лут",
                "&#FFAA00/seemegameteor edit chance &#FFFFFF- Редактировать шансы",
                "&#FFAA00/seemegameteor start &#FFFFFF- Принудительно запустить",
                "&#FFAA00/seemegameteor stop &#FFFFFF- Принудительно остановить",
                "&#FFAA00/seemegameteor tp &#FFFFFF- Телепорт к ивенту",
                "&#FFAA00/seemegameteor reload &#FFFFFF- Перезагрузить конфиг",
                "&#FFAA00/seemegameteor disable &#FFFFFF- Отключить ивенты",
                "&#FFAA00/seemegameteor enable &#FFFFFF- Включить ивенты",
                "&#FFAA00/megameteor &#FFFFFF- Время до ивента"
        );
        help.forEach(line -> sender.sendMessage(ChatColor.translateAlternateColorCodes('&', line)));
    }

    private void handleStartCommand(CommandSender sender) {
        if (plugin.getEventManager().isEventRunning()) {
            sendMessage(sender, "messages.errors.already_running");
            return;
        }
        plugin.getEventManager().adminStart();
        sendMessages("messages.external.start");
    }

    private void handleStopCommand(CommandSender sender) {
        if (!plugin.getEventManager().isEventRunning()) {
            sendMessage(sender, "messages.errors.not_started");
            return;
        }
        plugin.getEventManager().adminStop();
        sendMessages("messages.external.end");
    }

    private void sendMessage(CommandSender sender, String path) {
        String message = plugin.getConfig().getString(path);
        if (message != null) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
        }
    }

    private void sendMessages(String path) {
        plugin.getConfig().getStringList(path).forEach(msg ->
                Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&', msg)));
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
        if (!cmd.getName().equalsIgnoreCase("seemegameteor")) return Collections.emptyList();

        if (args.length == 1) {
            return subCommands.stream()
                    .filter(c -> c.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("edit")) {
            return editSubCommands.stream()
                    .filter(c -> c.startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
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