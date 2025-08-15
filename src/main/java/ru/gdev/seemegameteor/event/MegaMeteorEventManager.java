package ru.gdev.seemegameteor.event;

import com.sk89q.worldedit.math.BlockVector3;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import ru.gdev.seemegameteor.SeeMegaMeteor;
import ru.gdev.seemegameteor.holo.Hologram;
import ru.gdev.seemegameteor.loot.LootEntry;
import ru.gdev.seemegameteor.loot.LootManager;
import ru.gdev.seemegameteor.util.Placeholders;
import ru.gdev.seemegameteor.util.TimeUtil;
import ru.gdev.seemegameteor.util.WorldEditBridge;
import ru.gdev.seemegameteor.virtual.MeteorVisual;

import java.time.*;
import java.time.format.TextStyle;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class MegaMeteorEventManager {
    private final SeeMegaMeteor plugin;
    private final LootManager loot;
    private final WorldEditBridge worldEdit;
    private EventState state = EventState.IDLE;
    private Location eventCenter;
    private UUID activator;
    private Hologram activatorHolo;
    private Block magnetiteBlock;
    private Block anchorBlock;
    private String statusCached = "";
    private long nextPlannedEpochSec;
    private long phaseEndsAtEpochSec;
    private MeteorVisual meteorVisual;
    private List<Item> spawnedLoot = new ArrayList<>();

    public MegaMeteorEventManager(SeeMegaMeteor plugin, LootManager loot, WorldEditBridge we) {
        this.plugin = plugin;
        this.loot = loot;
        this.worldEdit = we;
        scheduleNext();
    }

    public void shutdown() {
        if (activatorHolo != null) activatorHolo.remove();
        if (meteorVisual != null) meteorVisual.remove();
    }

    public void tickSecond() {
        if (!plugin.isEventsEnabled()) return;
        long now = System.currentTimeMillis() / 1000L;
        if (state == EventState.IDLE) {
            if (now >= nextPlannedEpochSec - getCfg().getInt("timings.preannounce_seconds", 60)) {
                spawnStage1();
                return;
            }
            if (now == nextPlannedEpochSec - getCfg().getInt("messages.before.time", 60)) {
                List<String> list = getCfg().getStringList("messages.before.text");
                for (String m : list) Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&', Placeholders.apply(m, this)));
            }
            return;
        }
        if (phaseEndsAtEpochSec > 0 && now >= phaseEndsAtEpochSec) advance();
    }

    private void advance() {
        if (state == EventState.SPAWNED) {
            state = EventState.SPAWNED;
        } else if (state == EventState.ACTIVATED) {
            startMeteorFall();
        } else if (state == EventState.METEOR_FALLING) {
            craterExplosion();
        } else if (state == EventState.CRATER_READY) {
            spawnAnchorSecondStage();
        } else if (state == EventState.ANCHOR_SPAWNED) {
            startLootBurst();
        } else if (state == EventState.LOOT_BURST) {
            glowBurst();
        } else if (state == EventState.GLOW_BURST) {
            scheduleBeaconFinish();
        } else if (state == EventState.BEACON_FINISH) {
            finishBeaconCleanup();
        }
    }

    public void adminStart() {
        if (state != EventState.IDLE) return;
        spawnStage1();
    }

    public void adminStop() {
        finishNow();
    }

    private void scheduleNext() {
        FileConfiguration c = getCfg();
        ZoneId zone = parseZone(c.getString("event_settings.start.time_zone", "UTC"));
        int h = c.getInt("event_settings.start.time.hours", 15);
        int m = c.getInt("event_settings.start.time.minutes", 0);
        int s = c.getInt("event_settings.start.time.seconds", 0);
        ZonedDateTime now = ZonedDateTime.now(zone);
        ZonedDateTime next = now.withHour(h).withMinute(m).withSecond(s).withNano(0);
        if (!next.isAfter(now)) next = next.plusDays(1);
        nextPlannedEpochSec = next.toEpochSecond();
    }

    public int secondsUntilPlannedStart() {
        long now = System.currentTimeMillis() / 1000L;
        return (int) Math.max(0, nextPlannedEpochSec - now);
    }

    public String getTimeUntilNextLabel() {
        return TimeUtil.format(secondsUntilPlannedStart());
    }

    private void spawnStage1() {
        World w = Bukkit.getWorld(getCfg().getString("event_settings.spawn_settings.world", "world"));
        if (w == null) {
            scheduleNext();
            return;
        }
        Location center = pickLocation(w);
        eventCenter = center;
        worldEdit.pasteSchematic(getCfg().getString("event_settings.schematics.initial"), center);
        magnetiteBlock = center.getBlock();
        magnetiteBlock.setType(Material.LODESTONE, false);
        spawnActivatorHologram(center.clone().add(0.5, getCfg().getDouble("event_settings.holo.height", 1.5), 0.5));
        List<String> s = getCfg().getStringList("messages.stared");
        for (String m : s) Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&', Placeholders.apply(m, this)));
        state = EventState.SPAWNED;
        phaseEndsAtEpochSec = 0;
    }

    private Location pickLocation(World w) {
        Random r = ThreadLocalRandom.current();
        int x = r.nextInt(2000) - 1000;
        int z = r.nextInt(2000) - 1000;
        int y = w.getHighestBlockYAt(x, z);
        return new Location(w, x + 0.5, y, z + 0.5);
    }

    private void spawnActivatorHologram(Location loc) {
        if (activatorHolo != null) activatorHolo.remove();
        activatorHolo = new Hologram(loc, Arrays.asList(
                ChatColor.translateAlternateColorCodes('&', getCfg().getString("event_settings.holo.lines.0", "&cМега-Метеор")),
                ChatColor.translateAlternateColorCodes('&', getCfg().getString("event_settings.holo.lines.1", "&f ")),
                ChatColor.translateAlternateColorCodes('&', Placeholders.apply(getCfg().getString("event_settings.holo.lines.2", "&fСтатус: &r{status}"), this)),
                ChatColor.translateAlternateColorCodes('&', getCfg().getString("event_settings.holo.lines.3", "&f "))
        ));
        String tip = ChatColor.translateAlternateColorCodes('&', getCfg().getString("event_settings.loot.hologram_activate_text", "&eНажмите, чтобы активировать"));
        activatorHolo.appendLine(tip);
        activatorHolo.onInteract(p -> {
            if (state != EventState.SPAWNED) return;
            activator = p.getUniqueId();
            Bukkit.getOnlinePlayers().forEach(pl -> getCfg().getStringList("messages.activated").forEach(m -> pl.sendMessage(ChatColor.translateAlternateColorCodes('&', Placeholders.apply(m, this)))));
            state = EventState.ACTIVATED;
            phaseEndsAtEpochSec = System.currentTimeMillis() / 1000L + getCfg().getInt("timings.activation_to_meteor_seconds", 20);
        });
        activatorHolo.spawn();
    }

    private void startMeteorFall() {
        state = EventState.METEOR_FALLING;
        int y = getCfg().getInt("heights.meteor_spawn_y", 255);
        Location start = new Location(eventCenter.getWorld(), eventCenter.getX(), y, eventCenter.getZ());
        meteorVisual = new MeteorVisual(start, eventCenter, Material.MAGMA_BLOCK);
        meteorVisual.spawn();
        phaseEndsAtEpochSec = System.currentTimeMillis() / 1000L + 5;
    }

    private void craterExplosion() {
        if (meteorVisual != null) meteorVisual.remove();
        World w = eventCenter.getWorld();
        w.playSound(eventCenter, Sound.ENTITY_GENERIC_EXPLODE, 2f, 1f);
        w.spawnParticle(Particle.EXPLOSION_HUGE, eventCenter, 3, 0.5, 0.3, 0.5, 0.01);
        int radius = getCfg().getInt("crater.radius", 6);
        carveSphere(eventCenter, radius, Material.AIR);
        state = EventState.CRATER_READY;
        phaseEndsAtEpochSec = System.currentTimeMillis() / 1000L + getCfg().getInt("timings.anchor_delay_seconds", 10);
    }

    private void spawnAnchorSecondStage() {
        worldEdit.pasteSchematic(getCfg().getString("event_settings.schematics.second"), eventCenter);
        anchorBlock = eventCenter.getBlock();
        anchorBlock.setType(Material.RESPAWN_ANCHOR, false);
        World w = eventCenter.getWorld();
        for (int i = 0; i < 4; i++) w.playSound(eventCenter, Sound.BLOCK_BEACON_ACTIVATE, 1.6f, 0.7f + i * 0.1f);
        w.spawnParticle(Particle.FLAME, eventCenter, 150, 3, 1, 3, 0.02);
        w.playSound(eventCenter, Sound.ENTITY_GENERIC_EXPLODE, 2f, 0.6f);
        org.bukkit.util.Vector v = new org.bukkit.util.Vector(0, 0.6, 0);
        ItemStack lootCrate = new ItemStack(Material.BARREL);
        Item item = w.dropItem(eventCenter.clone().add(0.5, 1, 0.5), lootCrate);
        item.setGlowing(true);
        item.setVelocity(v);
        new BukkitRunnable() {
            @Override public void run() { if (!item.isDead()) item.remove(); }
        }.runTaskLater(plugin, 20L * 5);
        state = EventState.ANCHOR_SPAWNED;
        phaseEndsAtEpochSec = System.currentTimeMillis() / 1000L + ThreadLocalRandom.current().nextInt(getCfg().getInt("timings.loot_burst_min_seconds", 15), getCfg().getInt("timings.loot_burst_max_seconds", 30) + 1);
    }

    private void startLootBurst() {
        World w = eventCenter.getWorld();
        int seconds = getCfg().getInt("timings.loot_burst_max_seconds", 30);
        state = EventState.LOOT_BURST;
        phaseEndsAtEpochSec = System.currentTimeMillis() / 1000L + seconds;
        new BukkitRunnable() {
            int t = seconds;
            @Override public void run() {
                if (state != EventState.LOOT_BURST) { cancel(); return; }
                if (t-- <= 0) { cancel(); return; }
                LootEntry entry = loot.roll();
                if (entry == null) return;
                ItemStack is = entry.getItem().clone();
                Item dropped = w.dropItem(eventCenter.clone().add(0.5, 1.0, 0.5), is);
                dropped.setGlowing(true);
                dropped.setVelocity(new org.bukkit.util.Vector(
                        (ThreadLocalRandom.current().nextDouble() - 0.5) * 0.4,
                        0.6,
                        (ThreadLocalRandom.current().nextDouble() - 0.5) * 0.4
                ));
                spawnedLoot.add(dropped);
                w.playSound(eventCenter, Sound.ENTITY_ITEM_PICKUP, 0.6f, 0.5f + ThreadLocalRandom.current().nextFloat()*0.5f);
                w.spawnParticle(Particle.FLAME, eventCenter, 120, 2.5, 0.5, 2.5, 0.02);
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private void glowBurst() {
        World w = eventCenter.getWorld();
        int radius = getCfg().getInt("timings.glow_burst_radius", 16);
        int dur = getCfg().getInt("timings.glow_burst_duration_seconds", 10);
        w.spawnParticle(Particle.FLAME, eventCenter, 300, 3, 1, 3, 0.02);
        for (Player p : w.getPlayers()) {
            if (p.getLocation().distanceSquared(eventCenter) <= radius * radius) {
                p.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, dur * 20, 0, true, false, false));
            }
        }
        state = EventState.GLOW_BURST;
        phaseEndsAtEpochSec = System.currentTimeMillis() / 1000L + getCfg().getInt("timings.beacon_finish_delay_seconds", 120);
    }

    private void scheduleBeaconFinish() {
        int extra = getCfg().getInt("heights.beacon_spawn_extra_height", 30);
        Location start = eventCenter.clone().add(0, extra, 0);
        meteorVisual = new MeteorVisual(start, eventCenter, Material.BEACON);
        meteorVisual.spawn();
        state = EventState.BEACON_FINISH;
        phaseEndsAtEpochSec = System.currentTimeMillis() / 1000L + 5;
    }

    private void finishBeaconCleanup() {
        if (meteorVisual != null) meteorVisual.remove();
        World w = eventCenter.getWorld();
        Block b = eventCenter.getBlock();
        b.setType(Material.BEACON, false);
        w.playSound(eventCenter, Sound.BLOCK_BEACON_POWER_SELECT, 1.5f, 1.2f);
        w.spawnParticle(Particle.CLOUD, eventCenter, 120, 2, 0.5, 2, 0.02);
        fillSphere(eventCenter, getCfg().getInt("crater.radius", 6), Material.DIRT);
        worldEdit.eraseAreaAround(eventCenter, getCfg().getInt("crater.radius", 6));
        if (magnetiteBlock != null) magnetiteBlock.setType(Material.AIR, false);
        if (anchorBlock != null) anchorBlock.setType(Material.AIR, false);
        if (activatorHolo != null) activatorHolo.remove();
        Bukkit.getOnlinePlayers().forEach(pl -> getCfg().getStringList("messages.ended").forEach(m -> pl.sendMessage(ChatColor.translateAlternateColorCodes('&', Placeholders.apply(m, this)))));
        state = EventState.ENDED;
        phaseEndsAtEpochSec = 0;
        new BukkitRunnable(){@Override public void run(){finishNow();}}.runTaskLater(plugin, 20L*5);
    }

    private void finishNow() {
        if (activatorHolo != null) { activatorHolo.remove(); activatorHolo = null; }
        if (meteorVisual != null) { meteorVisual.remove(); meteorVisual = null; }
        for (Item i : spawnedLoot) if (!i.isDead()) i.setGlowing(false);
        spawnedLoot.clear();
        state = EventState.IDLE;
        eventCenter = null;
        activator = null;
        scheduleNext();
    }

    private void carveSphere(Location center, int radius, Material mat) {
        World w = center.getWorld();
        int cx = center.getBlockX(), cy = center.getBlockY(), cz = center.getBlockZ();
        int r2 = radius * radius;
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    if (x*x + y*y + z*z <= r2) {
                        Block b = w.getBlockAt(cx + x, cy + y, cz + z);
                        b.setType(mat, false);
                    }
                }
            }
        }
    }

    private void fillSphere(Location center, int radius, Material mat) {
        carveSphere(center, radius, mat);
        Block top = center.getBlock().getRelative(BlockFace.UP);
        top.setType(Material.AIR, false);
    }

    public void teleportToEvent(Player p) {
        if (eventCenter == null) return;
        p.teleport(eventCenter.clone().add(0, 1, 0));
    }

    public String statusLabel() {
        if (state == EventState.IDLE) return plugin.getConfig().getString("placeholders_settings.status.not_activated", "Не активирован");
        if (state == EventState.SPAWNED) return plugin.getConfig().getString("placeholders_settings.status.not_opened", String.valueOf(timeBeforeOpen()));
        if (state == EventState.ACTIVATED || state == EventState.METEOR_FALLING || state == EventState.CRATER_READY || state == EventState.ANCHOR_SPAWNED || state == EventState.LOOT_BURST || state == EventState.GLOW_BURST || state == EventState.BEACON_FINISH) return plugin.getConfig().getString("placeholders_settings.status.opened", String.valueOf(timeBeforeEnd()));
        return plugin.getConfig().getString("placeholders_settings.status.not_activated", "Не активирован");
    }

    private int timeBeforeOpen() {
        if (state == EventState.SPAWNED && phaseEndsAtEpochSec > 0) return (int) Math.max(0, phaseEndsAtEpochSec - System.currentTimeMillis()/1000L);
        return 0;
    }

    private int timeBeforeEnd() {
        if (phaseEndsAtEpochSec > 0) return (int) Math.max(0, phaseEndsAtEpochSec - System.currentTimeMillis()/1000L);
        return 0;
    }

    public String placeholder(String key) {
        if (key.equals("{status}")) return statusLabel();
        if (key.equals("{time_befote_start}") || key.equals("{time_before_start}")) return TimeUtil.format(secondsUntilPlannedStart());
        if (key.equals("{time_before_open}")) return TimeUtil.format(timeBeforeOpen());
        if (key.equals("{time_before_end}")) return TimeUtil.format(timeBeforeEnd());
        if (eventCenter != null) {
            if (key.equals("{x}")) return String.valueOf(eventCenter.getBlockX());
            if (key.equals("{y}")) return String.valueOf(eventCenter.getBlockY());
            if (key.equals("{z}")) return String.valueOf(eventCenter.getBlockZ());
        }
        if (key.equals("{activator}")) {
            if (activator == null) return "-";
            OfflinePlayer p = Bukkit.getOfflinePlayer(activator);
            return p != null && p.getName() != null ? p.getName() : "-";
        }
        return "";
    }

    private ZoneId parseZone(String id) {
        if (id == null) return ZoneId.of("UTC");
        id = id.replace("GMT", "UTC");
        if (id.startsWith("UTC") && id.length() > 3) {
            return ZoneId.ofOffset("UTC", ZoneOffset.of(id.substring(3)));
        }
        try { return ZoneId.of(id); } catch (Exception e) { return ZoneId.of("UTC"); }
    }

    private FileConfiguration getCfg() {
        return plugin.getConfig();
    }
}
