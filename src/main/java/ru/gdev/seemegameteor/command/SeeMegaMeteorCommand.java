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
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] args) {
        if (cmd.getName().equalsIgnoreCase("megameteor")) {
            MegaMeteorEventManager mgr = plugin.events();
            String t = mgr.getTimeUntilNextLabel();
            s.sendMessage(ChatColor.translateAlternateColorCodes('&', "&cДо начала: &e" + t));
            return true;
        }
        if (!s.hasPermission("seemegameteor.admin")) return true;
        if (args.length == 0) {
            s.sendMessage("/seemegameteor edit <loot/chance> | start | stop | tp | reload | disable | enable");
            return true;
        }
        String sub = args[0].toLowerCase();
        if (sub.equals("edit") && args.length >= 2) {
            if (!(s instanceof Player)) return true;
            Player p = (Player) s;
            if (args[1].equalsIgnoreCase("loot")) {
                new LootEditMenu(plugin, p).open();
                return true;
            }
            if (args[1].equalsIgnoreCase("chance")) {
                new ChanceEditMenu(plugin, p).open();
                return true;
            }
            return true;
        }
        if (sub.equals("start")) {
            plugin.events().adminStart();
            plugin.getConfig().getStringList("messages.external.start").forEach(m -> Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&', m)));
            return true;
        }
        if (sub.equals("stop")) {
            plugin.events().adminStop();
            plugin.getConfig().getStringList("messages.external.end").forEach(m -> Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&', m)));
            return true;
        }
        if (sub.equals("tp")) {
            if (s instanceof Player) plugin.events().teleportToEvent((Player) s);
            return true;
        }
        if (sub.equals("reload")) {
            plugin.reload();
            s.sendMessage(ChatColor.GREEN + "Конфигурация перезагружена. Следующее окно запуска: " + TimeUtil.format(plugin.events().secondsUntilPlannedStart()));
            return true;
        }
        if (sub.equals("disable")) {
            plugin.setEventsEnabled(false);
            s.sendMessage(ChatColor.RED + "Ивент отключён до команды enable");
            return true;
        }
        if (sub.equals("enable")) {
            plugin.setEventsEnabled(true);
            s.sendMessage(ChatColor.GREEN + "Ивент включён");
            return true;
        }
        s.sendMessage("/seemegameteor edit <loot/chance> | start | stop | tp | reload | disable | enable");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String alias, String[] args) {
        if (!cmd.getName().equalsIgnoreCase("seemegameteor")) return null;
        if (args.length == 1) return Arrays.asList("edit", "start", "stop", "tp", "reload", "disable", "enable");
        if (args.length == 2 && args[0].equalsIgnoreCase("edit")) return Arrays.asList("loot", "chance");
        return null;
    }
}
