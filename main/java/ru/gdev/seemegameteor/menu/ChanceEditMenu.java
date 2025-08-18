package ru.gdev.seemegameteor.menu;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import ru.gdev.seemegameteor.SeeMegaMeteor;
import ru.gdev.seemegameteor.loot.LootEntry;

import java.util.ArrayList;
import java.util.List;

public class ChanceEditMenu implements Listener {
    private final SeeMegaMeteor plugin;
    private final Player player;
    private final Inventory inv;

    public ChanceEditMenu(SeeMegaMeteor plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
        this.inv = Bukkit.createInventory(null, 54, "§6Редактор шансов");
    }

    public void open() {
        int i = 0;
        for (LootEntry e : plugin.getLootManager().getEntries()) {
            if (i >= inv.getSize()) break;
            ItemStack is = e.getItem().clone();
            ItemMeta meta = is.getItemMeta();
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.translateAlternateColorCodes('&', "&#FFAA00Шанс: &#FFFFFF" + String.format("%.2f%%", e.getChance())));
            lore.add(ChatColor.translateAlternateColorCodes('&', "&#AAAAAAЛКМ: -1%  |  Shift+ЛКМ: -10%"));
            lore.add(ChatColor.translateAlternateColorCodes('&', "&#AAAAAAПКМ: +1%  |  Shift+ПКМ: +10%"));
            meta.setLore(lore);
            is.setItemMeta(meta);
            inv.setItem(i++, is);
        }
        player.openInventory(inv);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!e.getView().getTitle().equals("§6Редактор шансов") ||
                !e.getWhoClicked().getUniqueId().equals(player.getUniqueId())) return;

        e.setCancelled(true);
        int slot = e.getSlot();
        if (slot < 0 || slot >= plugin.getLootManager().getEntries().size()) return;

        LootEntry entry = plugin.getLootManager().getEntries().get(slot);
        double delta = getDeltaForClick(e.getClick());

        if (delta != 0) {
            entry.setChance(Math.max(0, Math.min(100, entry.getChance() + delta)));
            updateSlot(slot, entry);
        }
    }

    private double getDeltaForClick(ClickType click) {
        switch (click) {
            case LEFT: return -1;
            case RIGHT: return 1;
            case SHIFT_LEFT: return -10;
            case SHIFT_RIGHT: return 10;
            default: return 0;
        }
    }

    private void updateSlot(int slot, LootEntry entry) {
        ItemStack is = inv.getItem(slot);
        if (is == null) return;

        ItemMeta meta = is.getItemMeta();
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.translateAlternateColorCodes('&', "&#FFAA00Шанс: &#FFFFFF" + String.format("%.2f%%", entry.getChance())));
        lore.add(ChatColor.translateAlternateColorCodes('&', "&#AAAAAAЛКМ: -1%  |  Shift+ЛКМ: -10%"));
        lore.add(ChatColor.translateAlternateColorCodes('&', "&#AAAAAAПКМ: +1%  |  Shift+ПКМ: +10%"));
        meta.setLore(lore);
        is.setItemMeta(meta);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (e.getView().getTitle().equals("§6Редактор шансов") &&
                e.getPlayer().getUniqueId().equals(player.getUniqueId())) {

            plugin.getLootManager().save();
            HandlerList.unregisterAll(this);
        }
    }
}