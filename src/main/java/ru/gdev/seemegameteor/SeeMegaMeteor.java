package ru.gdev.seemegameteor;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import ru.gdev.seemegameteor.command.SeeMegaMeteorCommand;
import ru.gdev.seemegameteor.event.MegaMeteorEventManager;
import ru.gdev.seemegameteor.loot.LootManager;
import ru.gdev.seemegameteor.storage.*;
import ru.gdev.seemegameteor.util.OptimizedWorldEditBridge;
import ru.gdev.seemegameteor.util.WorldEditBridge;
import ru.gdev.seemegameteor.util.WorldEditBridgeFactory;

import java.io.File;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class SeeMegaMeteor extends JavaPlugin {
    private static SeeMegaMeteor instance;
    private MegaMeteorEventManager eventManager;
    private LootManager lootManager;
    private DataStore dataStore;
    private boolean enabledFlag = true;
    private final Map<String, Material> stageMaterials = new HashMap<>();
    private final Map<Integer, String> stageSchematics = new HashMap<>();

    public static SeeMegaMeteor get() {
        return instance;
    }

    @Override
    public void onLoad() {
        instance = this;
        saveDefaultConfig();
    }

    @Override
    public void onEnable() {
        loadStageConfigs();
        reload();
        getCommand("seemegameteor").setExecutor(new SeeMegaMeteorCommand(this));
        getCommand("megameteor").setExecutor(new SeeMegaMeteorCommand(this));
        Bukkit.getScheduler().runTaskTimer(this, () -> eventManager.tickSecond(), 20L, 20L);
        scheduleBackups();
    }

    private void loadStageConfigs() {
        FileConfiguration config = getConfig();
        stageMaterials.put("initial", Material.valueOf(config.getString("meteor_stages.materials.initial", "LODESTONE")));
        stageMaterials.put("falling", Material.valueOf(config.getString("meteor_stages.materials.falling", "MAGMA_BLOCK")));
        stageMaterials.put("final", Material.valueOf(config.getString("meteor_stages.materials.final", "BEACON")));

        for (String stage : config.getConfigurationSection("meteor_stages.schematics").getKeys(false)) {
            int stageNum = config.getInt("meteor_stages.schematics." + stage + ".stage");
            String file = config.getString("meteor_stages.schematics." + stage + ".file");
            stageSchematics.put(stageNum, file);
        }
    }

    public Material getStageMaterial(String stage) {
        return stageMaterials.getOrDefault(stage, Material.LODESTONE);
    }

    public String getStageSchematic(int stage) {
        return stageSchematics.getOrDefault(stage, "plugins/SeeMegaMeteor/schematics/meteor_stage1.schem");
    }

    private void scheduleBackups() {
        if (getConfig().getBoolean("storage.backup.enabled", true)) {
            long interval = getConfig().getLong("storage.backup.interval_minutes", 60) * 1200L;
            Bukkit.getScheduler().runTaskTimerAsynchronously(this, () -> {
                if (dataStore != null) {
                    dataStore.backup(success -> {
                        if (success) getLogger().info("Automatic backup completed successfully");
                        else getLogger().warning("Failed to complete automatic backup");
                    });
                }
            }, interval, interval);
        }
    }

    @Override
    public void onDisable() {
        if (eventManager != null) eventManager.shutdown();
        if (dataStore != null) dataStore.close();
    }

    public void reload() {
        reloadConfig();
        loadStageConfigs();
        setupDataStore(success -> {
            if (success) {
                lootManager = new LootManager(this, dataStore);
                Supplier<WorldEditBridge> supplier = () -> new OptimizedWorldEditBridge();
                eventManager = new MegaMeteorEventManager(this, lootManager, supplier.get());
                getLogger().info("Reload completed successfully");
            } else {
                getLogger().severe("Failed to reload data store");
            }
        });
    }

    private WorldEditBridge getWorldEditBridge() {
        return new OptimizedWorldEditBridge();
    }

    private void setupDataStore(Consumer<Boolean> callback) {
        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }

        String storageType = getConfig().getString("storage.type", "SQLite").toUpperCase();
        DataStore newStore;

        try {
            switch (storageType) {
                case "YAML":
                    newStore = new YamlDataStore(new File(getDataFolder(), "loot.yml"));
                    break;
                case "JSON":
                    newStore = new JsonDataStore(new File(getDataFolder(), "loot.json"));
                    break;
                case "SQLITE":
                default:
                    newStore = new SQLiteDataStore(new File(getDataFolder(), "data.db"));
                    break;
            }

            if (dataStore != null && !dataStore.getClass().equals(newStore.getClass())) {
                dataStore.convertTo(newStore, success -> {
                    if (success) {
                        dataStore.close();
                        dataStore = newStore;
                        dataStore.init(callback);
                    } else {
                        callback.accept(false);
                    }
                });
            } else {
                if (dataStore != null) dataStore.close();
                dataStore = newStore;
                dataStore.init(callback);
            }
        } catch (Exception e) {
            getLogger().severe("Failed to initialize data store: " + e.getMessage());
            callback.accept(false);
        }
    }

    public MegaMeteorEventManager events() {
        return eventManager;
    }

    public LootManager loot() {
        return lootManager;
    }

    public DataStore store() {
        return dataStore;
    }

    public boolean isEventsEnabled() {
        return enabledFlag;
    }

    public void setEventsEnabled(boolean enabled) {
        this.enabledFlag = enabled;
    }
}