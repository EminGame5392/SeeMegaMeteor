package ru.gdev.seemegameteor.event;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import ru.gdev.seemegameteor.SeeMegaMeteor;
import ru.gdev.seemegameteor.holo.Hologram;
import ru.gdev.seemegameteor.loot.LootEntry;
import ru.gdev.seemegameteor.loot.LootManager;
import ru.gdev.seemegameteor.util.TimeUtil;
import ru.gdev.seemegameteor.util.WorldEditBridge;

import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class MegaMeteorEventManager {
    private final SeeMegaMeteor plugin;
    private final LootManager lootManager;
    private final WorldEditBridge worldEdit;
    private EventState state = EventState.IDLE;
    private Location eventCenter;
    private UUID activator;
    private Hologram activatorHolo;
    private Block magnetiteBlock;
    private Block anchorBlock;
    private long nextPlannedEpochSec;
    private long phaseEndsAtEpochSec;
    private final List<Item> spawnedLoot = new ArrayList<>();

    public void adminStartAtLocation(Location location) {
        if (state != EventState.IDLE) {
            Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&',
                    plugin.getConfig().getString("messages.errors.already_running")));
            return;
        }
        this.eventCenter = location.clone();
        spawnStage1();
        broadcastMessages("messages.external.start");
    }

    public MegaMeteorEventManager(SeeMegaMeteor plugin, LootManager lootManager, WorldEditBridge we) {
        this.plugin = plugin;
        this.lootManager = lootManager;
        this.worldEdit = we;
        scheduleNext();
    }

    public boolean isEventRunning() {
        return state != EventState.IDLE;
    }

    public void shutdown() {
        if (activatorHolo != null) activatorHolo.remove();
        cleanupLoot();
    }

    public void tickSecond() {
        if (!plugin.isEventsEnabled()) return;
        long now = System.currentTimeMillis() / 1000L;

        if (state == EventState.IDLE) {
            if (now >= nextPlannedEpochSec - plugin.getConfig().getInt("event_settings.stages.delay_before_spawn", 10)) {
                spawnStage1();
                return;
            }
            if (now == nextPlannedEpochSec - plugin.getConfig().getInt("messages.before.time", 60)) {
                broadcastMessages("messages.before.text");
            }
            return;
        }

        if (phaseEndsAtEpochSec > 0 && now >= phaseEndsAtEpochSec) {
            advance();
        }
    }

    private void advance() {
        switch (state) {
            case SPAWNED:
                state = EventState.WAITING_ACTIVATION;
                phaseEndsAtEpochSec = 0;
                break;
            case ACTIVATED:
                startMeteorFall();
                break;
            case METEOR_FALLING:
                craterExplosion();
                break;
            case CRATER_READY:
                spawnAnchorSecondStage();
                break;
            case ANCHOR_SPAWNED:
                startLootBurst();
                break;
            case LOOT_BURST:
                glowBurst();
                break;
            case GLOW_BURST:
                scheduleBeaconFinish();
                break;
            case BEACON_FINISH:
                finishBeaconCleanup();
                break;
        }
    }

    private void spawnStage1() {
        World w = Bukkit.getWorld(plugin.getConfig().getString("event_settings.spawn_settings.world", "world"));
        if (w == null) {
            scheduleNext();
            return;
        }

        eventCenter = pickLocation(w);
        worldEdit.pasteSchematic(plugin.getConfig().getString("event_settings.schematics.initial"), eventCenter);
        magnetiteBlock = eventCenter.getBlock();
        magnetiteBlock.setType(Material.getMaterial(plugin.getConfig().getString("meteor_stages.materials.initial", "LODESTONE")), false);

        activatorHolo = new Hologram(plugin, eventCenter, plugin.getConfig().getStringList("event_settings.holo.lines"));
        activatorHolo.appendLine(plugin.getConfig().getString("event_settings.loot.hologram_activate_text", "&eНажмите, чтобы активировать"));
        activatorHolo.onInteract(this::handleActivation);
        activatorHolo.spawn();

        broadcastMessages("messages.stared");
        state = EventState.SPAWNED;
        phaseEndsAtEpochSec = System.currentTimeMillis() / 1000L + plugin.getConfig().getInt("event_settings.stages.delay_before_meteor", 20);
    }

    private void handleActivation(Player player) {
        if (state != EventState.SPAWNED) return;
        activator = player.getUniqueId();
        broadcastMessages("messages.activated");
        state = EventState.ACTIVATED;
        phaseEndsAtEpochSec = System.currentTimeMillis() / 1000L + plugin.getConfig().getInt("event_settings.stages.delay_before_anchor", 10);
    }

    private void startMeteorFall() {
        state = EventState.METEOR_FALLING;
        int y = plugin.getConfig().getInt("heights.meteor_spawn_y", 255);
        Location start = new Location(eventCenter.getWorld(), eventCenter.getX(), y, eventCenter.getZ());

        FallingBlock meteor = start.getWorld().spawnFallingBlock(start,
                Material.getMaterial(plugin.getConfig().getString("meteor_stages.materials.falling", "MAGMA_BLOCK")), (byte) 0);
        meteor.setVelocity(new Vector(0, -0.9, 0));

        phaseEndsAtEpochSec = System.currentTimeMillis() / 1000L + 5;
    }

    private void craterExplosion() {
        World w = eventCenter.getWorld();
        w.playSound(eventCenter, Sound.ENTITY_GENERIC_EXPLODE, 2f, 1f);
        w.spawnParticle(Particle.EXPLOSION_HUGE, eventCenter, 3, 0.5, 0.3, 0.5, 0.01);

        worldEdit.eraseAreaAround(eventCenter, plugin.getConfig().getInt("crater.radius", 6));

        state = EventState.CRATER_READY;
        phaseEndsAtEpochSec = System.currentTimeMillis() / 1000L + plugin.getConfig().getInt("event_settings.stages.delay_before_loot", 15);
    }

    private void spawnAnchorSecondStage() {
        worldEdit.pasteSchematic(plugin.getConfig().getString("event_settings.schematics.second"), eventCenter);
        anchorBlock = eventCenter.getBlock();
        anchorBlock.setType(Material.RESPAWN_ANCHOR, false);

        World w = eventCenter.getWorld();
        for (int i = 0; i < 4; i++) {
            w.playSound(eventCenter, Sound.BLOCK_BEACON_ACTIVATE, 1.6f, 0.7f + i * 0.1f);
        }

        w.spawnParticle(Particle.FLAME, eventCenter, 150, 3, 1, 3, 0.02);
        spawnLootCrate();

        state = EventState.ANCHOR_SPAWNED;
        phaseEndsAtEpochSec = System.currentTimeMillis() / 1000L + ThreadLocalRandom.current().nextInt(
                plugin.getConfig().getInt("timings.loot_burst_min_seconds", 15),
                plugin.getConfig().getInt("timings.loot_burst_max_seconds", 30) + 1);
    }

    private void spawnLootCrate() {
        ItemStack crate = new ItemStack(Material.BARREL);
        Item item = eventCenter.getWorld().dropItem(
                eventCenter.clone().add(0.5, 1, 0.5), crate);

        item.setGlowing(true);
        item.setVelocity(new Vector(0, 0.6, 0));
        spawnedLoot.add(item);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!item.isDead()) item.remove();
        }, 20L * 5);
    }

    private void startLootBurst() {
        World w = eventCenter.getWorld();
        int seconds = plugin.getConfig().getInt("timings.loot_burst_max_seconds", 30);

        state = EventState.LOOT_BURST;
        phaseEndsAtEpochSec = System.currentTimeMillis() / 1000L + seconds;

        new BukkitRunnable() {
            int t = seconds;

            @Override
            public void run() {
                if (state != EventState.LOOT_BURST || t-- <= 0) {
                    cancel();
                    return;
                }

                LootEntry entry = lootManager.roll();
                if (entry == null) return;

                spawnLootItem(entry.getItem());
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private void spawnLootItem(ItemStack item) {
        World w = eventCenter.getWorld();
        Item dropped = w.dropItem(eventCenter.clone().add(0.5, 1.0, 0.5), item.clone());

        dropped.setGlowing(true);
        dropped.setVelocity(new Vector(
                (ThreadLocalRandom.current().nextDouble() - 0.5) * 0.4,
                0.6,
                (ThreadLocalRandom.current().nextDouble() - 0.5) * 0.4
        ));

        spawnedLoot.add(dropped);
        w.playSound(eventCenter, Sound.ENTITY_ITEM_PICKUP, 0.6f,
                0.5f + ThreadLocalRandom.current().nextFloat() * 0.5f);
    }

    public void updateHologramStatus() {
        if (activatorHolo != null) {
            activatorHolo.spawn();
        }
    }

    private void spawnLootItems() {
        World world = eventCenter.getWorld();
        int glowDuration = plugin.getConfig().getInt("event_settings.loot.glow_duration", 40) * 20;
        double radius = plugin.getConfig().getDouble("event_settings.loot.spawn_radius", 15);
        double spread = plugin.getConfig().getDouble("event_settings.loot.item_spread", 0.5);

        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            LootEntry entry = lootManager.roll();
            if (entry != null) {
                Item item = world.dropItem(eventCenter.clone().add(0, 1, 0), entry.getItem().clone());
                item.setGlowing(true);
                item.setVelocity(new Vector(
                        (ThreadLocalRandom.current().nextDouble() - 0.5) * spread,
                        0.5,
                        (ThreadLocalRandom.current().nextDouble() - 0.5) * spread
                ));
                spawnedLoot.add(item);
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (!item.isDead()) item.remove();
                }, glowDuration);
            }
        }, 0L, 10L);
    }

    private void glowBurst() {
        World w = eventCenter.getWorld();
        int radius = plugin.getConfig().getInt("timings.glow_burst_radius", 16);
        int dur = plugin.getConfig().getInt("timings.glow_burst_duration_seconds", 10);

        w.spawnParticle(Particle.FLAME, eventCenter, 300, 3, 1, 3, 0.02);
        for (Player p : w.getPlayers()) {
            if (p.getLocation().distanceSquared(eventCenter) <= radius * radius) {
                p.addPotionEffect(new PotionEffect(
                        PotionEffectType.GLOWING, dur * 20, 0, true, false, false));
            }
        }

        state = EventState.GLOW_BURST;
        phaseEndsAtEpochSec = System.currentTimeMillis() / 1000L + plugin.getConfig().getInt("event_settings.stages.delay_before_beacon", 120);
    }

    private void scheduleBeaconFinish() {
        int extra = plugin.getConfig().getInt("heights.beacon_spawn_extra_height", 30);
        Location start = eventCenter.clone().add(0, extra, 0);

        FallingBlock beacon = start.getWorld().spawnFallingBlock(start,
                Material.getMaterial(plugin.getConfig().getString("meteor_stages.materials.final", "BEACON")), (byte) 0);
        beacon.setVelocity(new Vector(0, -0.9, 0));

        state = EventState.BEACON_FINISH;
        phaseEndsAtEpochSec = System.currentTimeMillis() / 1000L + 5;
    }

    private void finishBeaconCleanup() {
        World w = eventCenter.getWorld();
        Block b = eventCenter.getBlock();
        b.setType(Material.BEACON, false);

        w.playSound(eventCenter, Sound.BLOCK_BEACON_POWER_SELECT, 1.5f, 1.2f);
        w.spawnParticle(Particle.CLOUD, eventCenter, 120, 2, 0.5, 2, 0.02);

        worldEdit.eraseAreaAround(eventCenter, plugin.getConfig().getInt("crater.radius", 6));

        if (magnetiteBlock != null) magnetiteBlock.setType(Material.AIR, false);
        if (anchorBlock != null) anchorBlock.setType(Material.AIR, false);
        if (activatorHolo != null) activatorHolo.remove();

        broadcastMessages("messages.ended");
        cleanupLoot();
        state = EventState.ENDED;

        Bukkit.getScheduler().runTaskLater(plugin, this::finishNow, 20L * 5);
    }

    public void adminStart() {
        if (state != EventState.IDLE) {
            Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&',
                    plugin.getConfig().getString("messages.errors.already_running", "&cИвент уже запущен!")));
            return;
        }
        spawnStage1();
        broadcastMessages("messages.external.start");
    }

    public void adminStop() {
        if (state == EventState.IDLE) {
            Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&',
                    plugin.getConfig().getString("messages.errors.not_started", "&cИвент ещё не начался!")));
            return;
        }
        finishNow();
        broadcastMessages("messages.external.end");
    }

    private void finishNow() {
        if (activatorHolo != null) activatorHolo.remove();
        cleanupLoot();
        state = EventState.IDLE;
        eventCenter = null;
        activator = null;
        scheduleNext();
    }

    private void cleanupLoot() {
        spawnedLoot.forEach(item -> {
            if (!item.isDead()) item.remove();
        });
        spawnedLoot.clear();
    }

    public void teleportToEvent(Player p) {
        if (eventCenter != null) p.teleport(eventCenter.clone().add(0, 1, 0));
    }

    public String replacePlaceholders(String text) {
        return text.replace("{status}", statusLabel())
                .replace("{time_before_start}", TimeUtil.format(secondsUntilPlannedStart()))
                .replace("{time_before_open}", TimeUtil.format(secondsUntilPlannedStart()))
                .replace("{time_before_end}", TimeUtil.format((int)(phaseEndsAtEpochSec - System.currentTimeMillis()/1000L)))
                .replace("{x}", eventCenter != null ? String.valueOf(eventCenter.getBlockX()) : "0")
                .replace("{y}", eventCenter != null ? String.valueOf(eventCenter.getBlockY()) : "0")
                .replace("{z}", eventCenter != null ? String.valueOf(eventCenter.getBlockZ()) : "0")
                .replace("{activator}", activator != null ? Bukkit.getOfflinePlayer(activator).getName() : "-");
    }

    public String statusLabel() {
        switch (state) {
            case IDLE: return plugin.getConfig().getString("placeholders_settings.status.not_activated", "&cНе активирован");
            case SPAWNED: return plugin.getConfig().getString("placeholders_settings.status.not_opened", "&eОжидание активации");
            case WAITING_ACTIVATION: return plugin.getConfig().getString("placeholders_settings.status.not_opened", "&eОжидание активации");
            default: return plugin.getConfig().getString("placeholders_settings.status.opened", "&aАктивен");
        }
    }

    public int secondsUntilPlannedStart() {
        long now = System.currentTimeMillis() / 1000L;
        return (int) Math.max(0, nextPlannedEpochSec - now);
    }

    public String getTimeUntilNextLabel() {
        return TimeUtil.format(secondsUntilPlannedStart());
    }

    private void scheduleNext() {
        FileConfiguration c = plugin.getConfig();
        ZoneId zone = parseZone(c.getString("event_settings.start.time_zone", "UTC+3"));
        int h = c.getInt("event_settings.start.time.hours", 15);
        int m = c.getInt("event_settings.start.time.minutes", 0);
        int s = c.getInt("event_settings.start.time.seconds", 0);

        ZonedDateTime now = ZonedDateTime.now(zone);
        ZonedDateTime next = now.withHour(h).withMinute(m).withSecond(s);
        if (!next.isAfter(now)) next = next.plusDays(1);

        nextPlannedEpochSec = next.toEpochSecond();
    }

    private Location pickLocation(World w) {
        Random r = ThreadLocalRandom.current();
        int x = r.nextInt(2000) - 1000;
        int z = r.nextInt(2000) - 1000;
        int y = w.getHighestBlockYAt(x, z);
        return new Location(w, x + 0.5, y, z + 0.5);
    }

    private void broadcastMessages(String path) {
        plugin.getConfig().getStringList(path).forEach(msg ->
                Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&', replacePlaceholders(msg))));
    }

    private ZoneId parseZone(String id) {
        id = id.replace("GMT", "UTC");
        if (id.startsWith("UTC") && id.length() > 3) {
            return ZoneId.ofOffset("UTC", ZoneOffset.of(id.substring(3)));
        }
        return ZoneId.of(id);
    }
}