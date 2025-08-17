package ru.gdev.seemegameteor.util;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extension.input.ParserContext;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.function.operation.ForwardExtentCopy;
import com.sk89q.worldedit.function.operation.Operation;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.session.ClipboardHolder;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;

import java.io.File;
import java.io.FileInputStream;

public class WorldEditBridge {
    public void pasteSchematic(String path, Location center) {
        if (path == null || path.isEmpty()) return;
        try {
            File f = new File(path);
            if (!f.exists()) return;
            Clipboard clipboard = ClipboardFormats.findByFile(f).getReader(new FileInputStream(f)).read();
            World weWorld = center.getWorld();
            com.sk89q.worldedit.world.World adapted = BukkitAdapter.adapt(weWorld);
            EditSession editSession = WorldEdit.getInstance().newEditSession(adapted);
            ClipboardHolder holder = new ClipboardHolder(clipboard);
            BlockVector3 origin = BlockVector3.at(center.getBlockX(), center.getBlockY(), center.getBlockZ());
            Operation operation = holder.createPaste(editSession).to(origin).ignoreAirBlocks(false).build();
            Operations.complete(operation);
            editSession.close();
        } catch (Exception ignored) {}
    }

    public void eraseAreaAround(Location center, int radius) {
        org.bukkit.World w = center.getWorld();
        int cx = center.getBlockX(), cy = center.getBlockY(), cz = center.getBlockZ();
        int r2 = radius*radius;
        for (int x=-radius; x<=radius; x++) for (int y=-radius; y<=radius; y++) for (int z=-radius; z<=radius; z++) {
            if (x*x+y*y+z*z <= r2) {
                w.getBlockAt(cx+x, cy+y, cz+z).setType(Material.AIR, false);
            }
        }
    }
}
