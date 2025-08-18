package ru.gdev.seemegameteor.holo;

import net.md_5.bungee.api.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.util.Consumer;
import ru.gdev.seemegameteor.SeeMegaMeteor;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import ru.gdev.seemegameteor.util.ColorUtil;

import java.util.ArrayList;
import java.util.List;

public class Hologram implements Listener {
    private final List<String> lines = new ArrayList<>();
    private final List<ArmorStand> stands = new ArrayList<>();
    private final Location origin;
    private Consumer<Player> click;
    private final SeeMegaMeteor plugin;

    public void appendLine(String text) {
        if (text == null) return;

        this.lines.add(text);
        if (stands != null && !stands.isEmpty()) {
            Location loc = origin.clone().subtract(0, 0.25 * (stands.size() - 1), 0);
            ArmorStand as = (ArmorStand) origin.getWorld().spawnEntity(loc, EntityType.ARMOR_STAND);
            as.setCustomName(ChatColor.translateAlternateColorCodes('&', text));
            as.setCustomNameVisible(true);
            as.setGravity(false);
            as.setVisible(false);
            as.setInvulnerable(true);
            stands.add(as);
        }
    }

    public void onInteract(Consumer<Player> consumer) {
        this.click = player -> {
            if (plugin.getEventManager().isEventRunning()) {
                consumer.accept(player);
            }
        };
    }

    public Hologram(SeeMegaMeteor plugin, Location origin, List<String> initial) {
        this.plugin = plugin;
        this.origin = origin.clone().add(0.5,
                plugin.getConfig().getDouble("event_settings.holo.height", 1.8),
                0.5);
        this.lines.addAll(initial);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void spawn() {
        remove();
        Location loc = origin.clone();
        for (String line : lines) {
            ArmorStand as = (ArmorStand) origin.getWorld().spawnEntity(loc, EntityType.ARMOR_STAND);
            as.setCustomName(ColorUtil.translate(line));
            as.setCustomNameVisible(true);
            as.setGravity(false);
            as.setVisible(false);
            as.setInvulnerable(true);
            stands.add(as);
            loc.subtract(0, 0.28, 0);
        }
    }

    @EventHandler
    public void onClick(PlayerInteractEvent e) {
        if (click != null && e.getAction().name().contains("RIGHT") &&
                e.getClickedBlock() != null &&
                e.getClickedBlock().getLocation().distanceSquared(origin) <= 9) {
            e.setCancelled(true);
            click.accept(e.getPlayer());
        }
    }

    public void remove() {
        stands.forEach(stand -> {
            if (!stand.isDead()) stand.remove();
        });
        stands.clear();
    }
}