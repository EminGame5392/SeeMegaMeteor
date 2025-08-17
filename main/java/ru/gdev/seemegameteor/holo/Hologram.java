package ru.gdev.seemegameteor.holo;

import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.util.Consumer;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import ru.gdev.seemegameteor.SeeMegaMeteor;

public class Hologram implements Listener {
    private final List<String> lines = new ArrayList<>();
    private final List<ArmorStand> stands = new ArrayList<>();
    private final Location origin;
    private Consumer<Player> click;
    private UUID id;

    public Hologram(Location origin, List<String> initial) {
        this.origin = origin.clone();
        this.lines.addAll(initial);
        SeeMegaMeteor.get().getServer().getPluginManager().registerEvents(this, SeeMegaMeteor.get());
    }

    public void appendLine(String s) {
        lines.add(s);
    }

    public void onInteract(Consumer<Player> consumer) {
        this.click = consumer;
    }

    public void spawn() {
        remove();
        Location l = origin.clone();
        for (int i = 0; i < lines.size(); i++) {
            ArmorStand as = (ArmorStand) origin.getWorld().spawnEntity(l.clone().add(0, -0.25 * i, 0), EntityType.ARMOR_STAND);
            as.setMarker(true);
            as.setVisible(false);
            as.setGravity(false);
            as.setCustomNameVisible(true);
            as.setCustomName(lines.get(i));
            as.setSmall(true);
            stands.add(as);
        }
        id = UUID.randomUUID();
    }

    public void remove() {
        for (ArmorStand as : stands) if (!as.isDead()) as.remove();
        stands.clear();
    }

    @EventHandler
    public void onClick(PlayerInteractAtEntityEvent e) {
        if (click == null) return;
        if (!(e.getRightClicked() instanceof ArmorStand)) return;
        ArmorStand as = (ArmorStand) e.getRightClicked();
        if (!stands.contains(as)) return;
        e.setCancelled(true);
        click.accept(e.getPlayer());
    }
}
