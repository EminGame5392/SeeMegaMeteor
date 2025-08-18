package ru.gdev.seemegameteor.holo;

import net.md_5.bungee.api.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.util.Consumer;
import ru.gdev.seemegameteor.SeeMegaMeteor;
import ru.gdev.seemegameteor.event.MegaMeteorEventManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

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
        this.origin = origin.clone().add(
                plugin.getConfig().getDouble("event_settings.holo.offset_x", 0.5),
                plugin.getConfig().getDouble("event_settings.holo.height", 1.5),
                plugin.getConfig().getDouble("event_settings.holo.offset_z", 0.5)
        );
        this.lines.addAll(initial);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void spawn() {
        remove();
        Location loc = origin.clone();
        for (int i = 0; i < lines.size(); i++) {
            ArmorStand as = (ArmorStand) origin.getWorld().spawnEntity(loc, EntityType.ARMOR_STAND);
            as.setCustomName(plugin.getEventManager().replacePlaceholders(lines.get(i)));
            as.setCustomNameVisible(true);
            as.setGravity(false);
            as.setVisible(false);
            as.setInvulnerable(true);
            stands.add(as);
            loc.subtract(0, 0.25, 0);
        }
    }

    @EventHandler
    public void onClick(PlayerInteractAtEntityEvent e) {
        if (click != null && stands.contains(e.getRightClicked())) {
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