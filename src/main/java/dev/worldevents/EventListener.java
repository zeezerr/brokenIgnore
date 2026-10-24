package dev.worldevents;

import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerExpChangeEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.List;
import java.util.logging.Level;

/** Single central listener: forwards Bukkit events to every currently running event (max 2). */
public final class EventListener implements Listener {
    private final WorldEventsPlugin plugin;

    public EventListener(WorldEventsPlugin plugin) {
        this.plugin = plugin;
    }

    private List<WorldEvent> act() {
        return plugin.manager().getActive();
    }

    private void safe(WorldEvent ev, Runnable r) {
        try {
            r.run();
        } catch (Throwable t) {
            plugin.getLogger().log(Level.WARNING, "Event hook failed in " + ev.getId(), t);
        }
    }

    private static boolean tracked(Material m) {
        return Tag.LOGS.isTagged(m)
                || Tag.COAL_ORES.isTagged(m) || Tag.COPPER_ORES.isTagged(m) || Tag.IRON_ORES.isTagged(m)
                || Tag.GOLD_ORES.isTagged(m) || Tag.DIAMOND_ORES.isTagged(m) || Tag.EMERALD_ORES.isTagged(m)
                || Tag.LAPIS_ORES.isTagged(m) || Tag.REDSTONE_ORES.isTagged(m)
                || m == Material.NETHER_QUARTZ_ORE || m == Material.NETHER_GOLD_ORE || m == Material.ANCIENT_DEBRIS;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        List<WorldEvent> evs = act();
        if (evs.isEmpty()) return;
        Player p = e.getPlayer();
        plugin.manager().showBar(p);
        for (WorldEvent ev : evs) safe(ev, () -> ev.onJoinEvent(e));
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline()) return;
            for (WorldEvent ev : act()) safe(ev, () -> ev.onJoin(p));
        }, 5L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        for (WorldEvent ev : act()) safe(ev, () -> ev.onQuitEvent(e));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (tracked(e.getBlockPlaced().getType())) plugin.tracker().track(e.getBlockPlaced());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        for (WorldEvent ev : act()) safe(ev, () -> ev.onBlockBreak(e));
        Block b = e.getBlock();
        // forget after the drop event for this same break has fired
        Bukkit.getScheduler().runTask(plugin, () -> plugin.tracker().forget(b));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrops(BlockDropItemEvent e) {
        for (WorldEvent ev : act()) safe(ev, () -> ev.onBlockDrops(e));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent e) {
        for (WorldEvent ev : act()) safe(ev, () -> ev.onDamage(e));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onRegen(EntityRegainHealthEvent e) {
        for (WorldEvent ev : act()) safe(ev, () -> ev.onRegen(e));
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onMobDeath(EntityDeathEvent e) {
        if (e.getEntity() instanceof Player) return;
        for (WorldEvent ev : act()) safe(ev, () -> ev.onMobDeath(e));
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerDeath(PlayerDeathEvent e) {
        for (WorldEvent ev : act()) safe(ev, () -> ev.onPlayerDeath(e));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent e) {
        if (e.getSpawnReason() == CreatureSpawnEvent.SpawnReason.SPAWNER) {
            plugin.tagSpawnerMob(e.getEntity());
        }
        for (WorldEvent ev : act()) safe(ev, () -> ev.onSpawn(e));
    }

    @EventHandler(ignoreCancelled = true)
    public void onFish(PlayerFishEvent e) {
        for (WorldEvent ev : act()) safe(ev, () -> ev.onFish(e));
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent e) {
        for (WorldEvent ev : act()) safe(ev, () -> ev.onInteract(e));
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onExp(PlayerExpChangeEvent e) {
        for (WorldEvent ev : act()) safe(ev, () -> ev.onExp(e));
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onChat(AsyncChatEvent e) {
        for (WorldEvent ev : act()) safe(ev, () -> ev.onChat(e));
    }
}
