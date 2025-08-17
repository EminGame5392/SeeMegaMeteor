package ru.gdev.seemegameteor;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import ru.gdev.seemegameteor.command.SeeMegaMeteorCommand;
import ru.gdev.seemegameteor.event.MegaMeteorEventManager;
import ru.gdev.seemegameteor.loot.LootManager;
import ru.gdev.seemegameteor.storage.DataStore;
import ru.gdev.seemegameteor.storage.JsonDataStore;
import ru.gdev.seemegameteor.storage.SQLiteDataStore;
import ru.gdev.seemegameteor.storage.YamlDataStore;
import ru.gdev.seemegameteor.util.OptimizedWorldEditBridge;
import ru.gdev.seemegameteor.util.WorldEditBridge;

import java.io.File;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

public class SeeMegaMeteor extends JavaPlugin {
    private static SeeMegaMeteor instance;
    private MegaMeteorEventManager eventManager;
    private LootManager lootManager;
    private DataStore dataStore;
    private OptimizedWorldEditBridge worldEditBridge;
    private boolean enabledFlag = true;
    private final Map<String, Material> stageMaterials = new HashMap<>();
    private final Map<Integer, String> stageSchematics = new HashMap<>();

    @Override
    public void onLoad() {
        instance = this;
        saveDefaultConfig();
    }

    @Override
    public void onEnable() {
        setupDataStore();
        lootManager = new LootManager(this, dataStore);
        eventManager = new MegaMeteorEventManager(this, lootManager, new WorldEditBridge());
        getCommand("seemegameteor").setExecutor(new SeeMegaMeteorCommand(this));
        getCommand("megameteor").setExecutor(new SeeMegaMeteorCommand(this));
        Bukkit.getScheduler().runTaskTimer(this, () -> eventManager.tickSecond(), 20L, 20L);
    }

    private void setupDataStore() {
        String storageType = getConfig().getString("event_settings.data_type", "SQLite");
        File dataFile = new File(getDataFolder(), "data." + storageType.toLowerCase());

        try {
            switch (storageType.toUpperCase()) {
                case "YML":
                case "YAML":
                    dataStore = new YamlDataStore(dataFile);
                    break;
                case "JSON":
                    dataStore = new JsonDataStore(dataFile);
                    break;
                case "SQLITE":
                default:
                    dataStore = new SQLiteDataStore(dataFile);
                    break;
            }
            dataStore.init(success -> {
                if (!success) {
                    getLogger().severe("Failed to initialize data store");
                }
            });
        } catch (Exception e) {
            getLogger().severe("Failed to initialize data store: " + e.getMessage());
            Bukkit.getPluginManager().disablePlugin(this);
        }
    }

    public void reload() {
        reloadConfig();
        if (dataStore != null) dataStore.close();
        setupDataStore();
        lootManager = new LootManager(this, dataStore);
        eventManager = new MegaMeteorEventManager(this, lootManager, new WorldEditBridge());
    }

    public Material getStageMaterial(String stage) {
        return stageMaterials.getOrDefault(stage, Material.LODESTONE);
    }

    public String getStageSchematic(int stage) {
        return stageSchematics.getOrDefault(stage, "plugins/SeeMegaMeteor/schematics/meteor_stage1.schem");
    }

    public static SeeMegaMeteor get() {
        return instance;
    }

    public MegaMeteorEventManager getEventManager() {
        return eventManager;
    }

    public LootManager getLootManager() {
        return lootManager;
    }

    public boolean isEventsEnabled() {
        return enabledFlag;
    }

    public void setEventsEnabled(boolean enabled) {
        this.enabledFlag = enabled;
    }
}