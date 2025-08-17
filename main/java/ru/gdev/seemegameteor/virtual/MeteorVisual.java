package ru.gdev.seemegameteor.virtual;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.FallingBlock;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import ru.gdev.seemegameteor.SeeMegaMeteor;

public class MeteorVisual {
    private final Location start;
    private final Location target;
    private final Material material;
    private FallingBlock fb;

    public MeteorVisual(Location start, Location target, Material material) {
        this.start = start.clone();
        this.target = target.clone();
        this.material = material;
    }

    public void spawn() {
        fb = start.getWorld().spawnFallingBlock(start, material, (byte) 0);
        fb.setHurtEntities(true);
        fb.setDropItem(false);
        Vector vel = target.clone().subtract(start).toVector().normalize().multiply(0.9);
        fb.setVelocity(vel);
        new BukkitRunnable() { @Override public void run() { if (fb == null || fb.isDead() || fb.isOnGround()) cancel(); else fb.setVelocity(vel); } }.runTaskTimer(SeeMegaMeteor.get(), 1L, 1L);
    }

    public void remove() {
        if (fb != null && !fb.isDead()) fb.remove();
        fb = null;
    }
}
