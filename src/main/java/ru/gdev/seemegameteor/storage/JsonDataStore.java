package ru.gdev.seemegameteor.storage;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import ru.gdev.seemegameteor.SeeMegaMeteor;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class JsonDataStore implements DataStore {
    private final File file;
    private final Gson gson = new Gson();
    private final Map<Integer, Map<String, Object>> cache = new ConcurrentHashMap<>();
    private int lastId = 0;

    public JsonDataStore(File file) {
        this.file = file;
    }

    @Override
    public void init(Consumer<Boolean> callback) {
        Bukkit.getScheduler().runTaskAsynchronously(SeeMegaMeteor.get(), () -> {
            if (!file.getParentFile().exists()) file.getParentFile().mkdirs();

            if (!file.exists()) {
                saveLoot(new ArrayList<>(), success -> {
                    if (callback != null) Bukkit.getScheduler().runTask(SeeMegaMeteor.get(), () -> callback.accept(success));
                });
            } else {
                loadLoot(result -> {
                    cache.clear();
                    for (int i = 0; i < result.size(); i++) {
                        cache.put(i, result.get(i));
                        lastId = i;
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
            try (Reader r = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
                Type type = new TypeToken<List<Map<String, Object>>>(){}.getType();
                List<Map<String, Object>> list = gson.fromJson(r, type);
                List<Map<String, Object>> result = list == null ? new ArrayList<>() : verifyData(list);
                Bukkit.getScheduler().runTask(SeeMegaMeteor.get(), () -> callback.accept(result));
            } catch (Exception e) {
                Bukkit.getScheduler().runTask(SeeMegaMeteor.get(), () -> callback.accept(new ArrayList<>()));
            }
        });
    }

    @Override
    public void saveLoot(List<Map<String, Object>> raw, Consumer<Boolean> callback) {
        Bukkit.getScheduler().runTaskAsynchronously(SeeMegaMeteor.get(), () -> {
            List<Map<String, Object>> verified = verifyData(raw);
            try (Writer w = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
                gson.toJson(verified, w);
                updateCache(verified);
                if (callback != null) Bukkit.getScheduler().runTask(SeeMegaMeteor.get(), () -> callback.accept(true));
            } catch (Exception e) {
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

            String backupName = "loot_" + System.currentTimeMillis() + ".json";
            File backupFile = new File(backupDir, backupName);

            try (InputStream in = new FileInputStream(file);
                 OutputStream out = new FileOutputStream(backupFile)) {
                byte[] buffer = new byte[1024];
                int length;
                while ((length = in.read(buffer)) > 0) {
                    out.write(buffer, 0, length);
                }
                cleanupOldBackups(backupDir);
                if (callback != null) Bukkit.getScheduler().runTask(SeeMegaMeteor.get(), () -> callback.accept(true));
            } catch (IOException e) {
                if (callback != null) Bukkit.getScheduler().runTask(SeeMegaMeteor.get(), () -> callback.accept(false));
            }
        });
    }

    private List<Map<String, Object>> verifyData(List<Map<String, Object>> data) {
        List<Map<String, Object>> verified = new ArrayList<>();
        Set<String> uniqueCheck = new HashSet<>();

        for (Map<String, Object> entry : data) {
            try {
                Map<String, Object> itemMap = (Map<String, Object>) entry.get("item");
                ItemStack item = ItemStack.deserialize(itemMap);
                String uniqueKey = item.getType() + ":" + (item.hasItemMeta() ? item.getItemMeta().hashCode() : 0);

                if (!uniqueCheck.contains(uniqueKey)) {
                    verified.add(entry);
                    uniqueCheck.add(uniqueKey);
                }
            } catch (Exception ignored) {}
        }
        return verified;
    }

    private void updateCache(List<Map<String, Object>> data) {
        cache.clear();
        for (int i = 0; i < data.size(); i++) {
            cache.put(i, data.get(i));
            lastId = i;
        }
    }

    private void cleanupOldBackups(File backupDir) {
        File[] backups = backupDir.listFiles((dir, name) -> name.startsWith("loot_") && name.endsWith(".json"));
        if (backups != null && backups.length > 5) {
            Arrays.sort(backups, Comparator.comparingLong(File::lastModified));
            for (int i = 0; i < backups.length - 5; i++) {
                backups[i].delete();
            }
        }
    }
}