package ru.gdev.seemegameteor.menu;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import ru.gdev.seemegameteor.SeeMegaMeteor;
import ru.gdev.seemegameteor.loot.LootEntry;
import ru.gdev.seemegameteor.loot.LootManager;

public class LootEditMenu implements Listener {
    private final SeeMegaMeteor plugin;
    private final Player player;
    private final Inventory inv;

    public LootEditMenu(SeeMegaMeteor plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
        this.inv = Bukkit.createInventory(null, 54, "§6Редактор лута");
    }

    public void open() {
        LootManager lm = plugin.getLootManager();
        for (int i = 0; i < Math.min(inv.getSize(), lm.getEntries().size()); i++) {
            ItemStack is = lm.getEntries().get(i).getItem().clone();
            if (is.getType() == Material.AIR) is = new ItemStack(Material.BARRIER);
            inv.setItem(i, is);
        }
        player.openInventory(inv);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (e.getView().getTitle().equals("§6Редактор лута") &&
                e.getWhoClicked().getUniqueId().equals(player.getUniqueId())) {
            e.setCancelled(false);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTitle().equals("§6Редактор лута") &&
                e.getWhoClicked().getUniqueId().equals(player.getUniqueId())) {
            e.setCancelled(false);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (e.getView().getTitle().equals("§6Редактор лута")) {
            LootManager lm = plugin.getLootManager();
            lm.getEntries().clear();

            for (int i = 0; i < inv.getSize(); i++) {
                ItemStack it = inv.getItem(i);
                if (it != null && it.getType() != Material.AIR) {
                    lm.getEntries().add(new LootEntry(it.clone(), 1.0));
                }
            }

            lm.save();
            HandlerList.unregisterAll(this);
        }
    }
}