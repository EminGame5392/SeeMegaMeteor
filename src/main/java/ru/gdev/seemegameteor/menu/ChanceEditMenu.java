package ru.gdev.seemegameteor.menu;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
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
        this.inv = Bukkit.createInventory(null, 54, "Chance Editor");
    }

    public void open() {
        int i = 0;
        for (LootEntry e : plugin.loot().getEntries()) {
            if (i >= inv.getSize()) break;
            ItemStack is = e.getItem().clone();
            ItemMeta meta = is.getItemMeta();
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.YELLOW + "Шанс: " + ChatColor.GOLD + String.format(java.util.Locale.US, "%.2f", e.getChance()));
            lore.add(ChatColor.GRAY + "ЛКМ: +0.1, ПКМ: -0.1");
            lore.add(ChatColor.GRAY + "Shift+ЛКМ: +1.0, Shift+ПКМ: -1.0");
            meta.setLore(lore);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES);
            is.setItemMeta(meta);
            inv.setItem(i++, is);
        }
        player.openInventory(inv);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!e.getView().getTitle().equals("expectedTitle")) return;
        if (!e.getWhoClicked().getUniqueId().equals(player.getUniqueId())) return;
        if (e.getClickedInventory() == null || !e.getClickedInventory().equals(inv)) return;
        e.setCancelled(true);
        int slot = e.getSlot();
        if (slot < 0 || slot >= plugin.loot().getEntries().size()) return;
        LootEntry entry = plugin.loot().getEntries().get(slot);
        double delta = 0;
        if (e.getClick() == ClickType.LEFT) delta = 0.1;
        else if (e.getClick() == ClickType.RIGHT) delta = -0.1;
        else if (e.getClick() == ClickType.SHIFT_LEFT) delta = 1.0;
        else if (e.getClick() == ClickType.SHIFT_RIGHT) delta = -1.0;
        if (delta != 0) {
            entry.setChance(Math.max(0, entry.getChance() + delta));
            ItemStack is = inv.getItem(slot);
            if (is == null) return;
            ItemMeta meta = is.getItemMeta();
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.YELLOW + "Шанс: " + ChatColor.GOLD + String.format(java.util.Locale.US, "%.2f", entry.getChance()));
            lore.add(ChatColor.GRAY + "ЛКМ: +0.1, ПКМ: -0.1");
            lore.add(ChatColor.GRAY + "Shift+ЛКМ: +1.0, Shift+ПКМ: -1.0");
            meta.setLore(lore);
            is.setItemMeta(meta);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (!e.getView().getTitle().equals("expectedTitle")) return;
        if (!e.getPlayer().getUniqueId().equals(player.getUniqueId())) return;
        plugin.loot().save();
        HandlerList.unregisterAll(this);
    }
}
