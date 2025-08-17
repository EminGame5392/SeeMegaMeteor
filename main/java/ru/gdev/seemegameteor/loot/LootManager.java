package ru.gdev.seemegameteor.loot;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.bukkit.Material;
import org.bukkit.configuration.serialization.ConfigurationSerialization;
import org.bukkit.inventory.ItemStack;
import ru.gdev.seemegameteor.SeeMegaMeteor;
import ru.gdev.seemegameteor.storage.DataStore;

import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class LootManager {
    private final SeeMegaMeteor plugin;
    private final DataStore store;
    private final List<LootEntry> entries = new ArrayList<>();

    public LootManager(SeeMegaMeteor plugin, DataStore store) {
        this.plugin = plugin;
        this.store = store;
        load();
    }

    public void load() {
        entries.clear();
        store.loadLoot(result -> {
            if (result != null) {
                for (Map<String, Object> e : result) {
                    ItemStack item = ItemStack.deserialize((Map<String, Object>) e.get("item"));
                    double chance = ((Number) e.get("chance")).doubleValue();
                    entries.add(new LootEntry(item, chance));
                }
            }
        });
    }

    public void save() {
        List<Map<String, Object>> raw = new ArrayList<>();
        for (LootEntry e : entries) {
            Map<String, Object> map = new HashMap<>();
            map.put("item", e.getItem().serialize());
            map.put("chance", e.getChance());
            raw.add(map);
        }
        store.saveLoot(raw, success -> {
            if (!success) {
                plugin.getLogger().warning("Failed to save loot data");
            }
        });
    }

    public List<LootEntry> getEntries() {
        return entries;
    }

    public LootEntry roll() {
        double sum = entries.stream().mapToDouble(LootEntry::getChance).sum();
        if (sum <= 0) return null;
        double r = ThreadLocalRandom.current().nextDouble() * sum;
        double acc = 0;
        for (LootEntry e : entries) {
            acc += e.getChance();
            if (r <= acc) return e;
        }
        return entries.isEmpty() ? null : entries.get(entries.size()-1);
    }

    public void setEntry(int slot, LootEntry entry) {
        while (entries.size() <= slot) entries.add(new LootEntry(new ItemStack(Material.AIR), 0));
        entries.set(slot, entry);
        save();
    }
}