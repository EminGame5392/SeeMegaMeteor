package ru.gdev.seemegameteor.storage;

import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;
import ru.gdev.seemegameteor.SeeMegaMeteor;

import java.io.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class H2DataStore implements DataStore {
    private final Connection connection;
    private final File dbFile;
    private final Map<Integer, Map<String, Object>> cache = new ConcurrentHashMap<>();

    public H2DataStore(File dbFile) throws SQLException {
        this.dbFile = dbFile;
        this.connection = DriverManager.getConnection("jdbc:h2:" + dbFile.getAbsolutePath());
    }

    @Override
    public void init(Consumer<Boolean> callback) {
        Bukkit.getScheduler().runTaskAsynchronously(SeeMegaMeteor.get(), () -> {
            try (Statement stmt = connection.createStatement()) {
                stmt.executeUpdate("CREATE TABLE IF NOT EXISTS loot (id INT AUTO_INCREMENT PRIMARY KEY, item BLOB, chance DOUBLE)");
                loadCache();
                if (callback != null) Bukkit.getScheduler().runTask(SeeMegaMeteor.get(), () -> callback.accept(true));
            } catch (SQLException e) {
                if (callback != null) Bukkit.getScheduler().runTask(SeeMegaMeteor.get(), () -> callback.accept(false));
            }
        });
    }

    @Override
    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException ignored) {}
    }

    @Override
    public void loadLoot(Consumer<List<Map<String, Object>>> callback) {
        Bukkit.getScheduler().runTaskAsynchronously(SeeMegaMeteor.get(), () -> {
            List<Map<String, Object>> result = new ArrayList<>(cache.values());
            Bukkit.getScheduler().runTask(SeeMegaMeteor.get(), () -> callback.accept(result));
        });
    }

    @Override
    public void saveLoot(List<Map<String, Object>> raw, Consumer<Boolean> callback) {
        Bukkit.getScheduler().runTaskAsynchronously(SeeMegaMeteor.get(), () -> {
            try {
                connection.setAutoCommit(false);
                try (Statement stmt = connection.createStatement()) {
                    stmt.executeUpdate("DELETE FROM loot");
                }

                try (PreparedStatement ps = connection.prepareStatement("INSERT INTO loot (item, chance) VALUES (?, ?)")) {
                    for (Map<String, Object> entry : raw) {
                        ItemStack item = ItemStack.deserialize((Map<String, Object>) entry.get("item"));
                        ps.setBytes(1, serializeItem(item));
                        ps.setDouble(2, ((Number) entry.get("chance")).doubleValue());
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
                connection.commit();
                loadCache();
                if (callback != null) Bukkit.getScheduler().runTask(SeeMegaMeteor.get(), () -> callback.accept(true));
            } catch (Exception e) {
                try {
                    connection.rollback();
                } catch (SQLException ignored) {}
                if (callback != null) Bukkit.getScheduler().runTask(SeeMegaMeteor.get(), () -> callback.accept(false));
            } finally {
                try {
                    connection.setAutoCommit(true);
                } catch (SQLException ignored) {}
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
            File backupDir = new File(dbFile.getParentFile(), "backups");
            if (!backupDir.exists()) backupDir.mkdirs();

            String backupName = "loot_" + System.currentTimeMillis() + ".h2.db";
            File backupFile = new File(backupDir, backupName);

            try (InputStream in = new FileInputStream(dbFile);
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

    private byte[] serializeItem(ItemStack item) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (BukkitObjectOutputStream oos = new BukkitObjectOutputStream(baos)) {
            oos.writeObject(item);
        }
        return baos.toByteArray();
    }

    private void loadCache() {
        cache.clear();
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT id, item, chance FROM loot")) {
            while (rs.next()) {
                Map<String, Object> entry = new HashMap<>();
                byte[] blob = rs.getBytes("item");
                ItemStack item = deserializeItem(blob);
                entry.put("item", item.serialize());
                entry.put("chance", rs.getDouble("chance"));
                cache.put(rs.getInt("id"), entry);
            }
        } catch (Exception ignored) {}
    }

    private ItemStack deserializeItem(byte[] blob) throws IOException, ClassNotFoundException {
        ByteArrayInputStream bais = new ByteArrayInputStream(blob);
        try (BukkitObjectInputStream ois = new BukkitObjectInputStream(bais)) {
            return (ItemStack) ois.readObject();
        }
    }

    private void cleanupOldBackups(File backupDir) {
        File[] backups = backupDir.listFiles((dir, name) -> name.startsWith("loot_") && name.endsWith(".h2.db"));
        if (backups != null && backups.length > 5) {
            Arrays.sort(backups, Comparator.comparingLong(File::lastModified));
            for (int i = 0; i < backups.length - 5; i++) {
                backups[i].delete();
            }
        }
    }
}