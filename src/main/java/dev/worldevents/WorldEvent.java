package dev.worldevents;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDropItemEvent;
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
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.Tag;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

/**
 * Base class for every event. Subclass it, override onStart/onStop and any hook you need,
 * then add the class to {@link EventRegistry}. Scheduled tasks created with repeat()/later()
 * are cancelled automatically when the event ends.
 */
public abstract class WorldEvent {
    private final String id;
    private final String name;
    private final String description;
    private String colorTag = "<white>";
    private BossBar.Color barColor = BossBar.Color.WHITE;
    private String tagline;
    private final Category category;
    private final List<BukkitTask> tasks = new ArrayList<>();
    private boolean active;

    protected WorldEventsPlugin plugin;

    protected WorldEvent(String id, String name, Category category, String description) {
        this.id = id;
        this.name = name;
        this.category = category;
        this.description = description;
    }

    public final String getId() { return id; }
    public final String getName() { return name; }
    public final String getDescription() { return description; }
    public final String getColorTag() { return colorTag; }
    public final BossBar.Color getBarColor() { return barColor; }

    /** Short line shown under the title on screen (the full description goes to chat). */
    public final String getTagline() {
        if (tagline != null) return tagline;
        int cut = description.indexOf('.');
        return cut > 0 ? description.substring(0, cut) : description;
    }

    final void setStyle(String colorTag, BossBar.Color barColor, String tagline) {
        this.colorTag = colorTag;
        this.barColor = barColor;
        this.tagline = tagline;
    }
    public final Category getCategory() { return category; }
    public final boolean isActive() { return active; }

    final void init(WorldEventsPlugin plugin) {
        this.plugin = plugin;
    }

    /** Called by the manager. state is non-null only when resuming after a restart. */
    public final void begin(ConfigurationSection state) {
        resetState();
        if (state != null) loadState(state);
        active = true;
        onStart();
    }

    public final void end() {
        active = false;
        try {
            onStop();
        } catch (Throwable t) {
            plugin.getLogger().log(Level.SEVERE, "Error while stopping event " + id, t);
        }
        for (BukkitTask t : tasks) t.cancel();
        tasks.clear();
    }

    // ---------------------------------------------------------------- lifecycle hooks
    protected void onStart() {}
    protected void onStop() {}
    protected void resetState() {}
    protected void loadState(ConfigurationSection s) {}
    public void saveState(ConfigurationSection s) {}

    // ---------------------------------------------------------------- event hooks
    public void onJoin(Player p) {}
    public void onJoinEvent(PlayerJoinEvent e) {}
    public void onQuitEvent(PlayerQuitEvent e) {}
    public void onBlockBreak(BlockBreakEvent e) {}
    public void onBlockDrops(BlockDropItemEvent e) {}
    public void onDamage(EntityDamageByEntityEvent e) {}
    public void onRegen(EntityRegainHealthEvent e) {}
    public void onMobDeath(EntityDeathEvent e) {}
    public void onPlayerDeath(PlayerDeathEvent e) {}
    public void onSpawn(CreatureSpawnEvent e) {}
    public void onFish(PlayerFishEvent e) {}
    public void onInteract(PlayerInteractEvent e) {}
    public void onExp(PlayerExpChangeEvent e) {}
    public void onChat(AsyncChatEvent e) {}

    // ---------------------------------------------------------------- scheduling
    protected void repeat(long delay, long period, Runnable r) {
        tasks.add(Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!active) return;
            try {
                r.run();
            } catch (Throwable t) {
                plugin.getLogger().log(Level.WARNING, "Error in event " + id, t);
            }
        }, delay, period));
    }

    protected void later(long delay, Runnable r) {
        tasks.add(Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!active) return;
            try {
                r.run();
            } catch (Throwable t) {
                plugin.getLogger().log(Level.WARNING, "Error in event " + id, t);
            }
        }, delay));
    }

    // ---------------------------------------------------------------- config helpers
    protected String path(String key) { return "events." + id + "." + key; }
    protected double cfgD(String key, double def) { return plugin.getConfig().getDouble(path(key), def); }
    protected int cfgI(String key, int def) { return plugin.getConfig().getInt(path(key), def); }
    protected boolean cfgB(String key, boolean def) { return plugin.getConfig().getBoolean(path(key), def); }

    protected List<String> cfgList(String key, List<String> def) {
        String p = path(key);
        return plugin.getConfig().isList(p) ? plugin.getConfig().getStringList(p) : def;
    }

    // ---------------------------------------------------------------- reward helpers
    /** Parses "MATERIAL", "MATERIAL:5" or "MATERIAL:2-6". Returns null if invalid. */
    protected static ItemStack parseItem(String entry) {
        try {
            String[] parts = entry.trim().split(":");
            Material m = Material.matchMaterial(parts[0].trim());
            if (m == null || !m.isItem() || m.isAir()) return null;
            int min = 1, max = 1;
            if (parts.length > 1) {
                String[] r = parts[1].trim().split("-");
                min = Integer.parseInt(r[0].trim());
                max = r.length > 1 ? Integer.parseInt(r[1].trim()) : min;
            }
            if (max < min) max = min;
            int amount = ThreadLocalRandom.current().nextInt(min, max + 1);
            return new ItemStack(m, Math.max(1, Math.min(amount, m.getMaxStackSize())));
        } catch (Exception ex) {
            return null;
        }
    }

    protected ItemStack rollReward(String key, List<String> defaults) {
        List<String> list = cfgList(key, defaults);
        if (list.isEmpty()) return null;
        for (int i = 0; i < 5; i++) {
            ItemStack it = parseItem(list.get(ThreadLocalRandom.current().nextInt(list.size())));
            if (it != null) return it;
        }
        return null;
    }

    protected static void giveOrDrop(Player p, ItemStack item) {
        if (item == null) return;
        Map<Integer, ItemStack> left = p.getInventory().addItem(item);
        for (ItemStack rest : left.values()) {
            p.getWorld().dropItemNaturally(p.getLocation(), rest);
        }
    }

    // ---------------------------------------------------------------- misc helpers
    protected static boolean isOre(Material m) {
        return Tag.COAL_ORES.isTagged(m) || Tag.COPPER_ORES.isTagged(m) || Tag.IRON_ORES.isTagged(m)
                || Tag.GOLD_ORES.isTagged(m) || Tag.DIAMOND_ORES.isTagged(m) || Tag.EMERALD_ORES.isTagged(m)
                || Tag.LAPIS_ORES.isTagged(m) || Tag.REDSTONE_ORES.isTagged(m)
                || m == Material.NETHER_QUARTZ_ORE || m == Material.NETHER_GOLD_ORE || m == Material.ANCIENT_DEBRIS;
    }

    /** The player responsible for a damage event (melee or projectile), or null. */
    protected static Player attackerOf(EntityDamageByEntityEvent e) {
        if (e.getDamager() instanceof Player p) return p;
        if (e.getDamager() instanceof Projectile proj && proj.getShooter() instanceof Player p) return p;
        return null;
    }

    /** Whether a player's block break qualifies for event bonuses (anti-exploit checks). */
    protected boolean eligibleBreak(Player p, Block b, boolean toolChecks, boolean allowFortune) {
        if (p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR) return false;
        if (plugin.tracker().isPlaced(b)) return false;
        if (toolChecks) {
            ItemStack tool = p.getInventory().getItemInMainHand();
            if (tool.containsEnchantment(Enchantment.SILK_TOUCH)) return false;
            if (!allowFortune && tool.containsEnchantment(Enchantment.FORTUNE)) return false;
        }
        return true;
    }

    protected static void multiplyDrops(BlockDropItemEvent e, int multiplier) {
        for (org.bukkit.entity.Item it : e.getItems()) {
            ItemStack s = it.getItemStack();
            s.setAmount(Math.min(s.getMaxStackSize(), s.getAmount() * multiplier));
            it.setItemStack(s);
        }
    }

    /** Spawns an extra mob next to a natural spawn, respecting a nearby-monster cap. */
    protected Entity duplicateSpawn(CreatureSpawnEvent e, EntityType type, double chance, int cap) {
        if (ThreadLocalRandom.current().nextDouble() >= chance) return null;
        Location l = e.getLocation();
        World w = l.getWorld();
        if (w == null) return null;
        if (w.getNearbyEntities(l, 32, 32, 32, en -> en instanceof Monster).size() >= cap) return null;
        Entity ent = w.spawnEntity(l, type);
        plugin.tagSpawnerMob(ent); // extra mobs never count for loot bonuses
        return ent;
    }
}
