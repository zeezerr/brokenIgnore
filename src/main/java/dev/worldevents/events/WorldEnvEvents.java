package dev.worldevents.events;

import dev.worldevents.Category;
import dev.worldevents.WorldEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/** World, mob and environment events. */
public final class WorldEnvEvents {
    private WorldEnvEvents() {}

    private static boolean overworld(World w) {
        return w.getEnvironment() == World.Environment.NORMAL;
    }

    /** Finds a safe surface spot (solid ground, two air blocks above, loaded chunk) near a point, or null. */
    static Block safeSurface(World w, int x, int z) {
        if (!w.isChunkLoaded(x >> 4, z >> 4)) return null;
        int y = w.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
        Block ground = w.getBlockAt(x, y, z);
        Block above = ground.getRelative(BlockFace.UP);
        if (!ground.getType().isSolid() || ground.isLiquid()) return null;
        if (!above.getType().isAir() || !above.getRelative(BlockFace.UP).getType().isAir()) return null;
        if (!w.getWorldBorder().isInside(above.getLocation())) return null;
        int protect = Bukkit.getSpawnRadius() + 16;
        Location spawn = w.getSpawnLocation();
        double dx = above.getX() - spawn.getX(), dz = above.getZ() - spawn.getZ();
        if (dx * dx + dz * dz < (double) protect * protect) return null; // keep away from spawn protection
        return above;
    }

    // ------------------------------------------------------------------ Time lock (Eternal Night / Day)
    public static class TimeLock extends WorldEvent {
        private final long time;

        public TimeLock(String id, String name, String desc, long time) {
            super(id, name, Category.WORLD, desc);
            this.time = time;
        }

        @Override
        protected void onStart() {
            lock();
            repeat(10L, 10L, this::lock);
        }

        protected void lock() {
            for (World w : Bukkit.getWorlds()) {
                if (overworld(w)) w.setTime(time);
            }
        }
    }

    // ------------------------------------------------------------------ Blood Moon
    public static final class BloodMoon extends TimeLock {
        public BloodMoon() {
            super("blood_moon", "Blood Moon", "Permanent night. Hostile mobs are stronger and tougher, and more of them spawn.", 18000L);
        }

        @Override
        public void onSpawn(CreatureSpawnEvent e) {
            if (e.getSpawnReason() != CreatureSpawnEvent.SpawnReason.NATURAL) return;
            if (!(e.getEntity() instanceof Monster m)) return;
            boost(m);
            Entity extra = duplicateSpawn(e, e.getEntityType(), cfgD("spawn-chance", 0.3), cfgI("spawn-cap", 40));
            if (extra instanceof Monster em) boost(em);
        }

        private void boost(Monster m) {
            m.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, PotionEffect.INFINITE_DURATION,
                    cfgI("strength-level", 0), true, false));
            m.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, PotionEffect.INFINITE_DURATION,
                    cfgI("resistance-level", 0), true, false));
        }
    }

    // ------------------------------------------------------------------ Mob Invasion
    public static final class MobInvasion extends WorldEvent {
        public MobInvasion() {
            super("mob_invasion", "Mob Invasion", Category.WORLD,
                    "Far more hostile mobs spawn around you. A nearby-mob cap keeps the server healthy.");
        }

        @Override
        public void onSpawn(CreatureSpawnEvent e) {
            if (e.getSpawnReason() != CreatureSpawnEvent.SpawnReason.NATURAL || !(e.getEntity() instanceof Monster)) return;
            duplicateSpawn(e, e.getEntityType(), cfgD("spawn-chance", 0.6), cfgI("spawn-cap", 40));
        }
    }

    // ------------------------------------------------------------------ Creeper Season
    public static final class CreeperSeason extends WorldEvent {
        public CreeperSeason() {
            super("creeper_season", "Creeper Season", Category.WORLD,
                    "Creepers spawn far more often in the Overworld. Keep your base well lit!");
        }

        @Override
        public void onSpawn(CreatureSpawnEvent e) {
            if (e.getSpawnReason() != CreatureSpawnEvent.SpawnReason.NATURAL || !(e.getEntity() instanceof Monster)) return;
            if (!overworld(e.getLocation().getWorld())) return;
            duplicateSpawn(e, EntityType.CREEPER, cfgD("spawn-chance", 0.5), cfgI("spawn-cap", 30));
        }
    }

    // ------------------------------------------------------------------ Peaceful Hour
    public static final class PeacefulHour extends WorldEvent {
        public PeacefulHour() {
            super("peaceful_hour", "Peaceful Hour", Category.WORLD,
                    "Hostile mobs stop spawning naturally. Mobs that already exist stay.");
        }

        @Override
        public void onSpawn(CreatureSpawnEvent e) {
            if (e.getSpawnReason() != CreatureSpawnEvent.SpawnReason.NATURAL || !(e.getEntity() instanceof Monster)) return;
            if (ThreadLocalRandom.current().nextDouble() < cfgD("block-chance", 1.0)) e.setCancelled(true);
        }
    }

    // ------------------------------------------------------------------ Treasure Rain
    public static final class TreasureRain extends WorldEvent {
        public TreasureRain() {
            super("treasure_rain", "Treasure Rain", Category.LOOT,
                    "Sparkling treasure keeps appearing on the ground near every player in the Overworld.");
        }

        @Override
        protected void onStart() {
            long period = 20L * Math.max(3, cfgI("interval-seconds", 15));
            repeat(period, period, this::rain);
        }

        private void rain() {
            ThreadLocalRandom r = ThreadLocalRandom.current();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (!overworld(p.getWorld())) continue;
                if (p.getGameMode() == GameMode.SPECTATOR) continue;
                for (int i = 0; i < 10; i++) {
                    double ang = r.nextDouble() * Math.PI * 2;
                    double dist = 5 + r.nextDouble() * 13;
                    int x = p.getLocation().getBlockX() + (int) (Math.cos(ang) * dist);
                    int z = p.getLocation().getBlockZ() + (int) (Math.sin(ang) * dist);
                    Block spot = safeSurface(p.getWorld(), x, z);
                    if (spot == null) continue;
                    ItemStack loot = rollReward("loot", List.of("IRON_INGOT:1-3", "GOLD_NUGGET:3-8", "COAL:3-6",
                            "COOKED_BEEF:2-4", "EXPERIENCE_BOTTLE:1-2", "ARROW:4-8", "DIAMOND:1"));
                    if (loot == null) break;
                    Location l = spot.getLocation().add(0.5, 0.3, 0.5);
                    Item item = p.getWorld().dropItem(l, loot);
                    item.setVelocity(new Vector(0, 0, 0));
                    item.setGlowing(true);
                    p.getWorld().spawnParticle(Particle.END_ROD, l, 25, 0.3, 0.6, 0.3, 0.02);
                    p.playSound(l, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 1f);
                    p.sendActionBar(Component.text("Treasure appeared nearby!", NamedTextColor.GOLD));
                    break;
                }
            }
        }
    }

    // ------------------------------------------------------------------ World Blessing / Curse
    public static final class PulseEffect extends WorldEvent {
        private static final PotionEffectType[] BLESSINGS = {
                PotionEffectType.SPEED, PotionEffectType.HASTE, PotionEffectType.STRENGTH,
                PotionEffectType.RESISTANCE, PotionEffectType.JUMP_BOOST, PotionEffectType.REGENERATION,
                PotionEffectType.LUCK, PotionEffectType.FIRE_RESISTANCE, PotionEffectType.WATER_BREATHING
        };
        private static final PotionEffectType[] CURSES = {
                PotionEffectType.SLOWNESS, PotionEffectType.MINING_FATIGUE, PotionEffectType.WEAKNESS,
                PotionEffectType.HUNGER, PotionEffectType.UNLUCK
        };

        private final boolean blessing;

        public PulseEffect(boolean blessing) {
            super(blessing ? "world_blessing" : "world_curse",
                    blessing ? "World Blessing" : "World Curse",
                    Category.WORLD,
                    blessing ? "Every so often each player receives a random minor positive effect."
                            : "Every so often each player receives a random minor negative effect.");
            this.blessing = blessing;
        }

        @Override
        protected void onStart() {
            long period = 20L * Math.max(10, cfgI("interval-seconds", 90));
            repeat(period, period, this::pulse);
        }

        private void pulse() {
            ThreadLocalRandom r = ThreadLocalRandom.current();
            PotionEffectType[] pool = blessing ? BLESSINGS : CURSES;
            int seconds = cfgI("effect-seconds", blessing ? 30 : 20);
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getGameMode() == GameMode.SPECTATOR) continue;
                PotionEffectType t = pool[r.nextInt(pool.length)];
                int dur = t == PotionEffectType.REGENERATION ? Math.min(seconds, 10) : seconds;
                p.addPotionEffect(new PotionEffect(t, dur * 20, 0, false, true, true));
                String label = t.getKey().getKey().replace('_', ' ');
                p.sendActionBar(Component.text((blessing ? "Blessing: " : "Curse: ") + label + " for " + dur + "s",
                        blessing ? NamedTextColor.GREEN : NamedTextColor.RED));
            }
        }
    }

    // ------------------------------------------------------------------ Mob seasons (Spider Season, Enderman Migration)
    public static final class MobSeason extends WorldEvent {
        private final EntityType type;
        private final double defaultChance;
        private final int defaultCap;

        public MobSeason(String id, String name, String desc, EntityType type, double defaultChance, int defaultCap) {
            super(id, name, Category.WORLD, desc);
            this.type = type;
            this.defaultChance = defaultChance;
            this.defaultCap = defaultCap;
        }

        @Override
        public void onSpawn(CreatureSpawnEvent e) {
            if (e.getSpawnReason() != CreatureSpawnEvent.SpawnReason.NATURAL || !(e.getEntity() instanceof Monster)) return;
            if (!overworld(e.getLocation().getWorld())) return;
            duplicateSpawn(e, type, cfgD("spawn-chance", defaultChance), cfgI("spawn-cap", defaultCap));
        }
    }
}
