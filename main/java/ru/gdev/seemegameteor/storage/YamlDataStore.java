package ru.gdev.seemegameteor.storage;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import ru.gdev.seemegameteor.SeeMegaMeteor;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class YamlDataStore implements DataStore {
    private final File file;
    private YamlConfiguration cfg;
    private final Map<Integer, Map<String, Object>> cache = new ConcurrentHashMap<>();

    public YamlDataStore(File file) {
        this.file = file;
    }

    @Override
    public void init(Consumer<Boolean> callback) {
        Bukkit.getScheduler().runTaskAsynchronously(SeeMegaMeteor.get(), () -> {
            if (!file.getParentFile().exists()) file.getParentFile().mkdirs();

            cfg = YamlConfiguration.loadConfiguration(file);
            if (!cfg.isSet("loot")) {
                saveLoot(new ArrayList<>(), success -> {
                    if (callback != null) Bukkit.getScheduler().runTask(SeeMegaMeteor.get(), () -> callback.accept(success));
                });
            } else {
                loadLoot(result -> {
                    cache.clear();
                    for (int i = 0; i < result.size(); i++) {
                        cache.put(i, result.get(i));
                    }
                    if (callback != null) Bukkit.getScheduler().runTask(SeeMegaMeteor.get(), () -> callback.accept(true));
                });
            }
        });
    }

    @Override
    public void close() {}

    @Override
    public void loadLoot(Consumer<List<Map<String, Object>>> callback) {
        Bukkit.getScheduler().runTaskAsynchronously(SeeMegaMeteor.get(), () -> {
            List<Map<String, Object>> list = new ArrayList<>();
            for (Object o : cfg.getList("loot", new ArrayList<>())) {
                try {
                    Map<String, Object> entry = (Map<String, Object>) o;
                    if (verifyEntry(entry)) {
                        list.add(entry);
                    }
                } catch (Exception ignored) {}
            }
            Bukkit.getScheduler().runTask(SeeMegaMeteor.get(), () -> callback.accept(list));
        });
    }

    @Override
    public void saveLoot(List<Map<String, Object>> raw, Consumer<Boolean> callback) {
        Bukkit.getScheduler().runTaskAsynchronously(SeeMegaMeteor.get(), () -> {
            List<Map<String, Object>> verified = new ArrayList<>();
            Set<String> uniqueCheck = new HashSet<>();

            for (Map<String, Object> entry : raw) {
                if (verifyEntry(entry)) {
                    String uniqueKey = entry.hashCode() + "";
                    if (!uniqueCheck.contains(uniqueKey)) {
                        verified.add(entry);
                        uniqueCheck.add(uniqueKey);
                    }
                }
            }

            cfg.set("loot", verified);
            try {
                cfg.save(file);
                updateCache(verified);
                if (callback != null) Bukkit.getScheduler().runTask(SeeMegaMeteor.get(), () -> callback.accept(true));
            } catch (IOException e) {
                if (callback != null) Bukkit.getScheduler().runTask(SeeMegaMeteor.get(), () -> callback.accept(false));
            }
        });
    }

    @Override
    public void convertTo(DataStore newStore, Consumer<Boolean> callback) {
        loadLoot(result -> newStore.saveLoot(result, callback));
    }

    @Override
    public void backup(Consumer<Boolean> callback) {
        Bukkit.getScheduler().runTaskAsynchronously(SeeMegaMeteor.get(), () -> {
            File backupDir = new File(file.getParentFile(), "backups");
            if (!backupDir.exists()) backupDir.mkdirs();

            String backupName = "loot_" + System.currentTimeMillis() + ".yml";
            File backupFile = new File(backupDir, backupName);

            try {
                YamlConfiguration backupCfg = YamlConfiguration.loadConfiguration(file);
                backupCfg.save(backupFile);
                cleanupOldBackups(backupDir);
                if (callback != null) Bukkit.getScheduler().runTask(SeeMegaMeteor.get(), () -> callback.accept(true));
            } catch (IOException e) {
                if (callback != null) Bukkit.getScheduler().runTask(SeeMegaMeteor.get(), () -> callback.accept(false));
            }
        });
    }

    private boolean verifyEntry(Map<String, Object> entry) {
        try {
            Map<String, Object> itemMap = (Map<String, Object>) entry.get("item");
            ItemStack item = ItemStack.deserialize(itemMap);
            return item != null && entry.containsKey("chance");
        } catch (Exception e) {
            return false;
        }
    }

    private void updateCache(List<Map<String, Object>> data) {
        cache.clear();
        for (int i = 0; i < data.size(); i++) {
            cache.put(i, data.get(i));
        }
    }

    private void cleanupOldBackups(File backupDir) {
        File[] backups = backupDir.listFiles((dir, name) -> name.startsWith("loot_") && name.endsWith(".yml"));
        if (backups != null && backups.length > 5) {
            Arrays.sort(backups, Comparator.comparingLong(File::lastModified));
            for (int i = 0; i < backups.length - 5; i++) {
                backups[i].delete();
            }
        }
    }
}