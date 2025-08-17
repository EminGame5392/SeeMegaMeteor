package ru.gdev.seemegameteor.util;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class ResourceManager {
    private final JavaPlugin plugin;
    private final ConcurrentHashMap<String, Object> resourceCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> lastModifiedMap = new ConcurrentHashMap<>();

    public ResourceManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public CompletableFuture<YamlConfiguration> loadConfigAsync(String fileName) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                File configFile = new File(plugin.getDataFolder(), fileName);
                Long lastModified = configFile.lastModified();
                String cacheKey = "config_" + fileName;

                if (resourceCache.containsKey(cacheKey)) {
                    Long cachedModified = lastModifiedMap.get(cacheKey);
                    if (cachedModified != null && cachedModified.equals(lastModified)) {
                        return (YamlConfiguration) resourceCache.get(cacheKey);
                    }
                }

                YamlConfiguration config = YamlConfiguration.loadConfiguration(configFile);
                resourceCache.put(cacheKey, config);
                lastModifiedMap.put(cacheKey, lastModified);
                return config;
            } catch (Exception e) {
                return new YamlConfiguration();
            }
        });
    }

    public void clearCache() {
        resourceCache.clear();
        lastModifiedMap.clear();
    }

    public void cleanup() {
        resourceCache.entrySet().removeIf(entry -> {
            if (entry.getKey().startsWith("temp_")) {
                return true;
            }
            return false;
        });
    }
}