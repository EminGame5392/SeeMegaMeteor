package ru.gdev.seemegameteor.util;

import com.sk89q.worldedit.*;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.function.operation.ForwardExtentCopy;
import com.sk89q.worldedit.function.operation.Operation;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.session.ClipboardHolder;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import ru.gdev.seemegameteor.SeeMegaMeteor;

import java.io.File;
import java.io.FileInputStream;
import java.util.HashSet;
import java.util.Set;

public class OptimizedWorldEditBridge extends WorldEditBridge {
    private final Set<Material> ignoredMaterials = new HashSet<>();

    public OptimizedWorldEditBridge() {
        ignoredMaterials.add(Material.AIR);
        ignoredMaterials.add(Material.CAVE_AIR);
        ignoredMaterials.add(Material.VOID_AIR);
    }

    @Override
    public void pasteSchematic(String path, Location center) {
        if (path == null || path.isEmpty()) return;

        Bukkit.getScheduler().runTaskAsynchronously(SeeMegaMeteor.get(), () -> {
            try {
                File f = new File(path);
                if (!f.exists()) return;

                Clipboard clipboard = ClipboardFormats.findByFile(f).getReader(new FileInputStream(f)).read();
                World weWorld = center.getWorld();
                com.sk89q.worldedit.world.World adapted = BukkitAdapter.adapt(weWorld);

                try (EditSession editSession = WorldEdit.getInstance().newEditSessionBuilder()
                        .world(adapted)
                        .build()) {

                    BlockVector3 origin = BlockVector3.at(center.getBlockX(), center.getBlockY(), center.getBlockZ());
                    Operation operation = new ClipboardHolder(clipboard)
                            .createPaste(editSession)
                            .to(origin)
                            .ignoreAirBlocks(true)
                            .build();

                    Operations.complete(operation);
                    editSession.commit();
                }
            } catch (Exception ignored) {}
        });
    }

    @Override
    public void eraseAreaAround(Location center, int radius) {
        World w = center.getWorld();
        int cx = center.getBlockX();
        int cy = center.getBlockY();
        int cz = center.getBlockZ();

        Bukkit.getScheduler().runTaskAsynchronously(SeeMegaMeteor.get(), () -> {
            for (int x = -radius; x <= radius; x++) {
                for (int y = -radius; y <= radius; y++) {
                    for (int z = -radius; z <= radius; z++) {
                        if (x*x + y*y + z*z <= radius*radius) {
                            Material type = w.getBlockAt(cx + x, cy + y, cz + z).getType();
                            if (!ignoredMaterials.contains(type)) {
                                int finalX = cx + x;
                                int finalY = cy + y;
                                int finalZ = cz + z;
                                Bukkit.getScheduler().runTask(SeeMegaMeteor.get(),
                                        () -> w.getBlockAt(finalX, finalY, finalZ).setType(Material.AIR, false));
                            }
                        }
                    }
                }
            }
        });
    }
}