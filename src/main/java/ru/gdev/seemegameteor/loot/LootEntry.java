package ru.gdev.seemegameteor.loot;

import org.bukkit.inventory.ItemStack;

public class LootEntry {
    private final ItemStack item;
    private double chance;

    public LootEntry(ItemStack item, double chance) {
        this.item = item;
        this.chance = chance;
    }

    public ItemStack getItem() {
        return item;
    }

    public double getChance() {
        return chance;
    }

    public void setChance(double chance) {
        this.chance = Math.max(0d, chance);
    }
}
