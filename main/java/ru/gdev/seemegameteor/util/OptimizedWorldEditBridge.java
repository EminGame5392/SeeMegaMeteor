package ru.gdev.seemegameteor.util;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.function.operation.Operation;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.session.ClipboardHolder;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import ru.gdev.seemegameteor.SeeMegaMeteor;

import java.io.File;
import java.io.FileInputStream;
import java.util.concurrent.ConcurrentHashMap;

public class OptimizedWorldEditBridge {
    public void pasteSchematic(String path, Location center) {
        if (path == null || path.isEmpty()) return;

        Bukkit.getScheduler().runTaskAsynchronously(SeeMegaMeteor.get(), () -> {
            try {
                File file = new File(path);
                if (!file.exists()) return;

                Clipboard clipboard = ClipboardFormats.findByFile(file)
                        .getReader(new FileInputStream(file))
                        .read();

                try (EditSession editSession = WorldEdit.getInstance()
                        .newEditSession(BukkitAdapter.adapt(center.getWorld()))) {

                    Operation operation = new ClipboardHolder(clipboard)
                            .createPaste(editSession)
                            .to(BlockVector3.at(center.getX(), center.getY(), center.getZ()))
                            .ignoreAirBlocks(true)
                            .build();

                    Operations.complete(operation);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    public void eraseAreaAround(Location center, int radius) {
        World world = center.getWorld();
        int centerX = center.getBlockX();
        int centerY = center.getBlockY();
        int centerZ = center.getBlockZ();

        Bukkit.getScheduler().runTaskAsynchronously(SeeMegaMeteor.get(), () -> {
            for (int x = -radius; x <= radius; x++) {
                for (int y = -radius; y <= radius; y++) {
                    for (int z = -radius; z <= radius; z++) {
                        if (x*x + y*y + z*z <= radius*radius) {
                            int finalX = centerX + x;
                            int finalY = centerY + y;
                            int finalZ = centerZ + z;
                            Bukkit.getScheduler().runTask(SeeMegaMeteor.get(),
                                    () -> world.getBlockAt(finalX, finalY, finalZ).setType(Material.AIR, false));
                        }
                    }
                }
            }
        });
    }

    public void shutdown() {
        // Пустой метод для совместимости
    }
}