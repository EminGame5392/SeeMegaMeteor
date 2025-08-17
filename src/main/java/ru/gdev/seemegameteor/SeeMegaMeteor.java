package ru.gdev.seemegameteor;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import ru.gdev.seemegameteor.command.SeeMegaMeteorCommand;
import ru.gdev.seemegameteor.event.MegaMeteorEventManager;
import ru.gdev.seemegameteor.loot.LootManager;
import ru.gdev.seemegameteor.storage.DataStore;
import ru.gdev.seemegameteor.storage.SQLiteDataStore;
import ru.gdev.seemegameteor.util.OptimizedWorldEditBridge;

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
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        setupDataStore();
        lootManager = new LootManager(this, dataStore);
        worldEditBridge = new OptimizedWorldEditBridge();
        eventManager = new MegaMeteorEventManager(this, lootManager, worldEditBridge);
        getCommand("seemegameteor").setExecutor(new SeeMegaMeteorCommand(this));
        getCommand("megameteor").setExecutor(new SeeMegaMeteorCommand(this));
        Bukkit.getScheduler().runTaskTimer(this, () -> eventManager.tickSecond(), 20L, 20L);
    }

    @Override
    public void onDisable() {
        if (eventManager != null) eventManager.shutdown();
        if (dataStore != null) dataStore.close();
    }

    private void setupDataStore() {
        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }
        try {
            File dbFile = new File(getDataFolder(), "data.db");
            dataStore = new SQLiteDataStore(dbFile);
        } catch (SQLException e) {
            getLogger().severe("Failed to initialize SQLite database!");
            e.printStackTrace();
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    public void reload() {
        reloadConfig();
        if (dataStore != null) dataStore.close();
        setupDataStore();
        lootManager = new LootManager(this, dataStore);
        eventManager = new MegaMeteorEventManager(this, lootManager, worldEditBridge);
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

    public DataStore getDataStore() {
        return dataStore;
    }

    public boolean isEventsEnabled() {
        return enabledFlag;
    }

    public void setEventsEnabled(boolean enabled) {
        this.enabledFlag = enabled;
    }

    public Material getStageMaterial(String stage) {
        return stageMaterials.getOrDefault(stage, Material.LODESTONE);
    }

    public String getStageSchematic(int stage) {
        return stageSchematics.getOrDefault(stage, "plugins/SeeMegaMeteor/schematics/meteor_stage1.schem");
    }
}