package ru.gdev.seemegameteor.event;

import com.fastasyncworldedit.core.FaweAPI;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.function.operation.Operation;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.session.ClipboardHolder;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import ru.gdev.seemegameteor.SeeMegaMeteor;
import ru.gdev.seemegameteor.holo.Hologram;
import ru.gdev.seemegameteor.loot.LootEntry;
import ru.gdev.seemegameteor.loot.LootManager;
import ru.gdev.seemegameteor.util.OptimizedWorldEditBridge;
import ru.gdev.seemegameteor.util.TimeUtil;

import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class MegaMeteorEventManager {
    private final SeeMegaMeteor plugin;
    private final LootManager lootManager;
    private final OptimizedWorldEditBridge worldEdit;

    private EventState state = EventState.IDLE;
    private Location eventCenter;
    private UUID activator;
    private Hologram activatorHolo;
    private Block magnetiteBlock;
    private Block anchorBlock;

    private final Map<Location, Material> changedBlocks = new ConcurrentHashMap<>();
    private final Queue<Runnable> blockUpdateQueue = new ConcurrentLinkedQueue<>();
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();

    private BukkitTask meteorTask;
    private BukkitTask countdownTask;
    private BukkitTask lootTask;

    private long nextPlannedEpochSec;
    private long phaseEndsAtEpochSec;

    private final List<Item> spawnedLoot = new ArrayList<>();
    private final AtomicInteger activeTasks = new AtomicInteger(0);

    public MegaMeteorEventManager(SeeMegaMeteor plugin, LootManager lootManager, OptimizedWorldEditBridge we) {
        this.plugin = plugin;
        this.lootManager = lootManager;
        this.worldEdit = we;
        startBlockUpdateProcessor();
        scheduleNextEvent();
    }

    private void startBlockUpdateProcessor() {
        executor.scheduleAtFixedRate(() -> {
            if (activeTasks.get() > 3 || blockUpdateQueue.isEmpty()) return;

            activeTasks.incrementAndGet();
            List<Runnable> tasks = new ArrayList<>();
            while (!blockUpdateQueue.isEmpty() && tasks.size() < 100) {
                tasks.add(blockUpdateQueue.poll());
            }

            Bukkit.getScheduler().runTask(plugin, () -> {
                tasks.forEach(Runnable::run);
                activeTasks.decrementAndGet();
            });
        }, 0, 50, TimeUnit.MILLISECONDS);
    }

    public void tickSecond() {
        if (!plugin.isEventsEnabled()) return;

        long now = System.currentTimeMillis() / 1000L;
        if (state == EventState.IDLE) {
            handleIdleState(now);
            return;
        }

        if (phaseEndsAtEpochSec > 0 && now >= phaseEndsAtEpochSec) {
            advanceEventPhase();
        }
    }

    private void handleIdleState(long now) {
        int preAnnounceSec = plugin.getConfig().getInt("timings.preannounce_seconds", 60);
        if (now >= nextPlannedEpochSec - preAnnounceSec) {
            spawnStage1();
        }

        int warnTime = plugin.getConfig().getInt("messages.before.time", 60);
        if (now == nextPlannedEpochSec - warnTime) {
            broadcastMessages("messages.before.text");
        }
    }

    private void advanceEventPhase() {
        switch (state) {
            case SPAWNED:
                state = EventState.WAITING_ACTIVATION;
                phaseEndsAtEpochSec = 0;
                break;

            case ACTIVATED:
                startMeteorFall();
                break;

            case METEOR_FALLING:
                createCrater();
                break;

            case CRATER_READY:
                spawnAnchor();
                break;

            case ANCHOR_SPAWNED:
                startLootBurst();
                break;

            case LOOT_BURST:
                createGlowEffect();
                break;

            case GLOW_BURST:
                spawnFinalBeacon();
                break;

            case BEACON_FINISH:
                finishEvent();
                break;
        }
    }

    public void adminStart() {
        if (state != EventState.IDLE) return;
        spawnStage1();
    }

    public void adminStop() {
        forceFinishEvent();
    }

    private void scheduleNextEvent() {
        FileConfiguration c = plugin.getConfig();
        String timeZone = c.getString("event_settings.start.time_zone", "UTC+3");
        int h = c.getInt("event_settings.start.time.hours", 15);
        int m = c.getInt("event_settings.start.time.minutes", 0);

        ZonedDateTime now = ZonedDateTime.now(parseZone(timeZone));
        ZonedDateTime next = now.withHour(h).withMinute(m).withSecond(0);
        if (!next.isAfter(now)) next = next.plusDays(1);

        nextPlannedEpochSec = next.toEpochSecond();
    }

    private void spawnStage1() {
        World w = Bukkit.getWorld(plugin.getConfig().getString("event_settings.spawn_settings.world", "world"));
        if (w == null) {
            scheduleNextEvent();
            return;
        }

        eventCenter = findSuitableLocation(w);
        worldEdit.pasteSchematic(plugin.getStageSchematic(1), eventCenter);

        magnetiteBlock = eventCenter.getBlock();
        queueBlockUpdate(magnetiteBlock, Material.LODESTONE);

        spawnActivatorHologram();
        broadcastMessages("messages.stared");

        state = EventState.SPAWNED;
        phaseEndsAtEpochSec = System.currentTimeMillis() / 1000L + 300; // 5 минут на активацию
    }

    private void spawnActivatorHologram() {
        Location holoLoc = eventCenter.clone().add(0.5,
                plugin.getConfig().getDouble("event_settings.holo.height", 1.5), 0.5);

        activatorHolo = new Hologram(holoLoc, Arrays.asList(
                ChatColor.translateAlternateColorCodes('&',
                        plugin.getConfig().getString("event_settings.holo.lines.0", "&cМега-Метеор")),
                " ",
                ChatColor.translateAlternateColorCodes('&',
                        plugin.getConfig().getString("event_settings.holo.lines.2", "&fСтатус: &r{status}")),
                " "
        ));

        activatorHolo.appendLine(ChatColor.translateAlternateColorCodes('&',
                plugin.getConfig().getString("event_settings.loot.hologram_activate_text", "&eНажмите, чтобы активировать")));

        activatorHolo.onInteract(this::handleHoloActivation);
        activatorHolo.spawn();
    }

    private void handleHoloActivation(Player player) {
        if (state != EventState.SPAWNED && state != EventState.WAITING_ACTIVATION) return;

        activator = player.getUniqueId();
        broadcastMessages("messages.activated");

        state = EventState.ACTIVATED;
        phaseEndsAtEpochSec = System.currentTimeMillis() / 1000L +
                plugin.getConfig().getInt("timings.activation_to_meteor_seconds", 20);
    }

    private void startMeteorFall() {
        state = EventState.METEOR_FALLING;
        int spawnY = plugin.getConfig().getInt("heights.meteor_spawn_y", 255);

        Location start = new Location(eventCenter.getWorld(),
                eventCenter.getX(), spawnY, eventCenter.getZ());

        FallingBlock meteor = spawnFallingMeteor(start);
        meteorTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (meteor.isDead()) {
                    cancel();
                    return;
                }
                meteor.setVelocity(new Vector(0, -0.9, 0));
            }
        }.runTaskTimer(plugin, 0L, 1L);

        phaseEndsAtEpochSec = System.currentTimeMillis() / 1000L + 5;
    }

    private FallingBlock spawnFallingMeteor(Location loc) {
        loc.getWorld().playSound(loc, Sound.ENTITY_ENDER_DRAGON_GROWL, 3f, 0.8f);
        return loc.getWorld().spawnFallingBlock(loc,
                plugin.getStageMaterial("falling"), (byte) 0);
    }

    private void createCrater() {
        if (meteorTask != null) meteorTask.cancel();

        World w = eventCenter.getWorld();
        w.playSound(eventCenter, Sound.ENTITY_GENERIC_EXPLODE, 3f, 1f);
        w.spawnParticle(Particle.EXPLOSION_HUGE, eventCenter, 3);

        int radius = plugin.getConfig().getInt("crater.radius", 6);
        carveSphere(eventCenter, radius, Material.AIR);

        state = EventState.CRATER_READY;
        phaseEndsAtEpochSec = System.currentTimeMillis() / 1000L +
                plugin.getConfig().getInt("timings.anchor_delay_seconds", 10);
    }

    private void spawnAnchor() {
        worldEdit.pasteSchematic(plugin.getStageSchematic(2), eventCenter);
        anchorBlock = eventCenter.getBlock();
        queueBlockUpdate(anchorBlock, Material.RESPAWN_ANCHOR);

        World w = eventCenter.getWorld();
        for (int i = 0; i < 4; i++) {
            w.playSound(eventCenter, Sound.BLOCK_BEACON_ACTIVATE, 1.6f, 0.7f + i * 0.1f);
        }

        w.spawnParticle(Particle.FLAME, eventCenter, 150, 3, 1, 3, 0.02);
        spawnLootCrate();

        state = EventState.ANCHOR_SPAWNED;
        phaseEndsAtEpochSec = System.currentTimeMillis() / 1000L +
                ThreadLocalRandom.current().nextInt(
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
        state = EventState.LOOT_BURST;
        int duration = plugin.getConfig().getInt("timings.loot_burst_max_seconds", 30);

        lootTask = new BukkitRunnable() {
            int ticks = duration * 20;

            @Override
            public void run() {
                if (ticks-- <= 0 || state != EventState.LOOT_BURST) {
                    cancel();
                    return;
                }

                LootEntry entry = lootManager.roll();
                if (entry == null) return;

                spawnLootItem(entry.getItem());
            }
        }.runTaskTimer(plugin, 0L, 1L);

        phaseEndsAtEpochSec = System.currentTimeMillis() / 1000L + duration;
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
                ThreadLocalRandom.current().nextFloat() * 0.5f + 0.5f);
    }

    private void createGlowEffect() {
        if (lootTask != null) lootTask.cancel();

        World w = eventCenter.getWorld();
        int radius = plugin.getConfig().getInt("timings.glow_burst_radius", 16);
        int duration = plugin.getConfig().getInt("timings.glow_burst_duration_seconds", 10);

        w.spawnParticle(Particle.FLAME, eventCenter, 300, 3, 1, 3, 0.02);
        for (Player p : w.getPlayers()) {
            if (p.getLocation().distanceSquared(eventCenter) <= radius * radius) {
                p.addPotionEffect(new PotionEffect(
                        PotionEffectType.GLOWING, duration * 20, 0, true, false, false));
            }
        }

        state = EventState.GLOW_BURST;
        phaseEndsAtEpochSec = System.currentTimeMillis() / 1000L +
                plugin.getConfig().getInt("timings.beacon_finish_delay_seconds", 120);
    }

    private void spawnFinalBeacon() {
        int extraHeight = plugin.getConfig().getInt("heights.beacon_spawn_extra_height", 30);
        Location start = eventCenter.clone().add(0, extraHeight, 0);

        FallingBlock beacon = spawnFallingBeacon(start);
        meteorTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (beacon.isDead()) {
                    cancel();
                    return;
                }
                beacon.setVelocity(new Vector(0, -0.9, 0));
            }
        }.runTaskTimer(plugin, 0L, 1L);

        state = EventState.BEACON_FINISH;
        phaseEndsAtEpochSec = System.currentTimeMillis() / 1000L + 5;
    }

    private FallingBlock spawnFallingBeacon(Location loc) {
        return loc.getWorld().spawnFallingBlock(loc,
                plugin.getStageMaterial("final"), (byte) 0);
    }

    private void finishEvent() {
        if (meteorTask != null) meteorTask.cancel();

        World w = eventCenter.getWorld();
        queueBlockUpdate(eventCenter.getBlock(), Material.BEACON);

        w.playSound(eventCenter, Sound.BLOCK_BEACON_POWER_SELECT, 1.5f, 1.2f);
        w.spawnParticle(Particle.CLOUD, eventCenter, 120, 2, 0.5, 2, 0.02);

        int radius = plugin.getConfig().getInt("crater.radius", 6);
        fillSphere(eventCenter, radius, Material.DIRT);

        if (magnetiteBlock != null) queueBlockUpdate(magnetiteBlock, Material.AIR);
        if (anchorBlock != null) queueBlockUpdate(anchorBlock, Material.AIR);
        if (activatorHolo != null) activatorHolo.remove();

        broadcastMessages("messages.ended");
        cleanupLoot();

        state = EventState.ENDED;
        Bukkit.getScheduler().runTaskLater(plugin, this::resetEvent, 20L * 5);
    }

    private void forceFinishEvent() {
        if (meteorTask != null) meteorTask.cancel();
        if (lootTask != null) lootTask.cancel();
        if (countdownTask != null) countdownTask.cancel();

        cleanupLoot();
        resetEvent();
    }

    private void cleanupLoot() {
        spawnedLoot.forEach(item -> {
            if (!item.isDead()) {
                item.setGlowing(false);
                item.remove();
            }
        });
        spawnedLoot.clear();
    }

    private void resetEvent() {
        state = EventState.IDLE;
        eventCenter = null;
        activator = null;
        scheduleNextEvent();
    }

    private void queueBlockUpdate(Block block, Material material) {
        blockUpdateQueue.add(() -> {
            block.setType(material, false);
            changedBlocks.put(block.getLocation(), material);
        });
    }

    private void carveSphere(Location center, int radius, Material material) {
        worldEdit.eraseAreaAround(center, radius);
    }

    private void fillSphere(Location center, int radius, Material material) {
        carveSphere(center, radius, material);
        queueBlockUpdate(center.getBlock().getRelative(BlockFace.UP), Material.AIR);
    }

    private Location findSuitableLocation(World world) {
        Random r = ThreadLocalRandom.current();
        int x = r.nextInt(2000) - 1000;
        int z = r.nextInt(2000) - 1000;
        int y = world.getHighestBlockYAt(x, z);
        return new Location(world, x + 0.5, y, z + 0.5);
    }

    private void broadcastMessages(String path) {
        plugin.getConfig().getStringList(path).forEach(msg ->
                Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&', msg)));
    }

    private ZoneId parseZone(String id) {
        id = id.replace("GMT", "UTC");
        if (id.startsWith("UTC") && id.length() > 3) {
            return ZoneId.ofOffset("UTC", ZoneOffset.of(id.substring(3)));
        }
        return ZoneId.of(id);
    }

    public void shutdown() {
        forceFinishEvent();
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
        }
    }

    public void teleportToEvent(Player player) {
        if (eventCenter != null) {
            player.teleport(eventCenter.clone().add(0, 1, 0));
        }
    }

    public int getSecondsUntilEvent() {
        return (int) Math.max(0, nextPlannedEpochSec - System.currentTimeMillis() / 1000L);
    }

    public String getTimeUntilEventFormatted() {
        return TimeUtil.format(getSecondsUntilEvent());
    }

    public EventState getEventState() {
        return state;
    }

    public String getTimeUntilNextLabel() {
        return TimeUtil.format(secondsUntilPlannedStart());
    }

    public int secondsUntilPlannedStart() {
        long now = System.currentTimeMillis() / 1000L;
        return (int) Math.max(0, nextPlannedEpochSec - now);
    }
}