package dev.worldevents.events;

import dev.worldevents.Category;
import dev.worldevents.WorldEvent;
import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.block.Action;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.World;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Firework;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** Rule-changing, PvP and utility events. */
public final class RuleEvents {
    private RuleEvents() {}

    // ------------------------------------------------------------------ Ultra Hardcore
    public static final class UltraHardcore extends WorldEvent {
        public UltraHardcore() {
            super("ultra_hardcore", "Ultra Hardcore", Category.PVP,
                    "Natural health regeneration is disabled. Golden apples, potions and other healing still work.");
        }

        @Override
        public void onRegen(EntityRegainHealthEvent e) {
            if (!(e.getEntity() instanceof Player)) return;
            EntityRegainHealthEvent.RegainReason r = e.getRegainReason();
            if (r == EntityRegainHealthEvent.RegainReason.REGEN || r == EntityRegainHealthEvent.RegainReason.SATIATED) {
                e.setCancelled(true);
            }
        }
    }

    // ------------------------------------------------------------------ Shieldless
    public static final class Shieldless extends WorldEvent {
        public Shieldless() {
            super("shieldless", "Shieldless", Category.PVP,
                    "Shields can't be raised. Nobody's shield is removed - it just won't work until the event ends.");
        }

        @Override
        protected void onStart() {
            lock();
            repeat(10L, 10L, this::lock);
        }

        private void lock() {
            for (Player p : Bukkit.getOnlinePlayers()) p.setCooldown(Material.SHIELD, 40);
        }

        @Override
        protected void onStop() {
            for (Player p : Bukkit.getOnlinePlayers()) p.setCooldown(Material.SHIELD, 0);
        }
    }

    // ------------------------------------------------------------------ Starvation
    public static final class Starvation extends WorldEvent {
        public Starvation() {
            super("starvation", "Starvation", Category.PLAYER_EFFECTS,
                    "Hunger drains much faster than usual. Bring plenty of food!");
        }

        @Override
        protected void onStart() {
            repeat(20L, 20L, () -> {
                float extra = (float) cfgD("exhaustion-per-second", 0.35);
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.getGameMode() == GameMode.SURVIVAL || p.getGameMode() == GameMode.ADVENTURE) {
                        p.setExhaustion(p.getExhaustion() + extra);
                    }
                }
            });
        }
    }

    // ------------------------------------------------------------------ Paranoia
    public static final class Paranoia extends WorldEvent {
        private static final Sound[] SOUNDS = {
                Sound.AMBIENT_CAVE, Sound.ENTITY_CREEPER_PRIMED, Sound.ENTITY_ENDERMAN_STARE,
                Sound.BLOCK_WOODEN_DOOR_OPEN, Sound.ENTITY_ZOMBIE_AMBIENT, Sound.ENTITY_SKELETON_AMBIENT,
                Sound.BLOCK_STONE_STEP, Sound.ENTITY_PHANTOM_AMBIENT
        };

        public Paranoia() {
            super("paranoia", "Paranoia", Category.PLAYER_EFFECTS,
                    "Eerie sounds and wisps of smoke appear around you. Harmless - but are you sure?");
        }

        @Override
        protected void onStart() {
            repeat(100L, 100L, () -> {
                ThreadLocalRandom r = ThreadLocalRandom.current();
                double chance = cfgD("chance", 0.35);
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (r.nextDouble() >= chance) continue;
                    Location l = p.getLocation().add(r.nextDouble(-8, 8), 0, r.nextDouble(-8, 8));
                    p.playSound(l, SOUNDS[r.nextInt(SOUNDS.length)], 0.8f, 0.8f + r.nextFloat() * 0.4f);
                    if (r.nextDouble() < 0.4) {
                        p.spawnParticle(Particle.SMOKE, l.add(0, 1, 0), 12, 0.3, 0.5, 0.3, 0.01);
                    }
                }
            });
        }
    }

    // ------------------------------------------------------------------ No Escape
    public static final class NoEscape extends WorldEvent {
        public NoEscape() {
            super("no_escape", "No Escape", Category.PVP,
                    "Ender pearls fail to work. Rejected pearls are not used up.");
        }

        @Override
        public void onInteract(PlayerInteractEvent e) {
            ItemStack item = e.getItem();
            if (item == null || item.getType() != Material.ENDER_PEARL) return;
            if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
            if (ThreadLocalRandom.current().nextDouble() < cfgD("fail-chance", 1.0)) {
                e.setUseItemInHand(Event.Result.DENY); // pearl is not thrown, so not consumed
                e.getPlayer().sendActionBar(Component.text("No Escape! The ender pearl fizzles.", NamedTextColor.RED));
            }
        }
    }

    // ------------------------------------------------------------------ Hotbar Havoc
    public static final class HotbarHavoc extends WorldEvent {
        public HotbarHavoc() {
            super("hotbar_havoc", "Hotbar Havoc", Category.UTILITY,
                    "Your hotbar is shuffled every so often. Items are never lost - just moved around.");
        }

        @Override
        protected void onStart() {
            long period = 20L * Math.max(5, cfgI("interval-seconds", 60));
            repeat(period, period, this::shuffle);
        }

        private void shuffle() {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getGameMode() == GameMode.SPECTATOR) continue;
                PlayerInventory inv = p.getInventory();
                List<ItemStack> items = new ArrayList<>();
                for (int i = 0; i < 9; i++) {
                    ItemStack it = inv.getItem(i);
                    items.add(it == null ? null : it.clone());
                }
                Collections.shuffle(items);
                for (int i = 0; i < 9; i++) inv.setItem(i, items.get(i));
                p.sendActionBar(Component.text("Hotbar Havoc! Your hotbar was shuffled.", NamedTextColor.YELLOW));
            }
        }
    }

    // ------------------------------------------------------------------ Anonymous
    public static final class Anonymous extends WorldEvent {
        private static final String TEAM = "we_anonymous";
        private Team team;
        private final Map<UUID, String> oldTeams = new HashMap<>();

        public Anonymous() {
            super("anonymous", "Anonymous", Category.UTILITY,
                    "Names are hidden above heads, in the tab list, in chat and in death messages. Skins are still visible!");
        }

        @Override
        protected void onStart() {
            Scoreboard sb = Bukkit.getScoreboardManager().getMainScoreboard();
            Team old = sb.getTeam(TEAM);
            if (old != null) old.unregister();
            team = sb.registerNewTeam(TEAM);
            team.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.NEVER);
            hideAll();
            repeat(20L, 20L, this::hideAll);
        }

        private void hideAll() {
            for (Player p : Bukkit.getOnlinePlayers()) hide(p);
        }

        private void hide(Player p) {
            if (team == null || team.hasEntry(p.getName())) return;
            Scoreboard sb = Bukkit.getScoreboardManager().getMainScoreboard();
            Team prev = sb.getEntryTeam(p.getName());
            if (prev != null) oldTeams.put(p.getUniqueId(), prev.getName());
            team.addEntry(p.getName());
            p.playerListName(Component.text("Anonymous", NamedTextColor.GRAY));
            p.displayName(Component.text("Anonymous", NamedTextColor.GRAY));
        }

        @Override
        protected void onStop() {
            Scoreboard sb = Bukkit.getScoreboardManager().getMainScoreboard();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (team != null) team.removeEntry(p.getName());
                String prev = oldTeams.get(p.getUniqueId());
                if (prev != null) {
                    Team t = sb.getTeam(prev);
                    if (t != null) t.addEntry(p.getName());
                }
                p.playerListName(null);
                p.displayName(null);
            }
            if (team != null) {
                try { team.unregister(); } catch (IllegalStateException ignored) { }
                team = null;
            }
            oldTeams.clear();
        }

        @Override
        public void onJoin(Player p) {
            hide(p);
        }

        @Override
        public void onJoinEvent(PlayerJoinEvent e) {
            e.joinMessage(null);
        }

        @Override
        public void onQuitEvent(PlayerQuitEvent e) {
            e.quitMessage(null);
            if (team != null) team.removeEntry(e.getPlayer().getName());
            e.getPlayer().playerListName(null);
            e.getPlayer().displayName(null);
        }

        @Override
        public void onPlayerDeath(PlayerDeathEvent e) {
            e.deathMessage(Component.text("An anonymous player has died.", NamedTextColor.GRAY));
        }

        @Override
        public void onChat(AsyncChatEvent e) {
            e.renderer(ChatRenderer.viewerUnaware((source, name, message) ->
                    Component.text("Anonymous", NamedTextColor.GRAY)
                            .append(Component.text(": ", NamedTextColor.DARK_GRAY))
                            .append(message)));
        }
    }

    // ------------------------------------------------------------------ PvP damage scaling
    public static final class PvpDamage extends WorldEvent {
        private final double defaultMultiplier;

        public PvpDamage(String id, String name, String desc, double defaultMultiplier) {
            super(id, name, Category.PVP, desc);
            this.defaultMultiplier = defaultMultiplier;
        }

        @Override
        public void onDamage(EntityDamageByEntityEvent e) {
            // melee only: the damager must be the player themselves, hitting another player
            if (!(e.getDamager() instanceof Player attacker) || !(e.getEntity() instanceof Player victim)) return;
            if (attacker.equals(victim)) return;
            EntityDamageEvent.DamageCause c = e.getCause();
            if (c != EntityDamageEvent.DamageCause.ENTITY_ATTACK && c != EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK) return;
            e.setDamage(e.getDamage() * cfgD("multiplier", defaultMultiplier));
        }
    }

    // ------------------------------------------------------------------ PvP Protection
    public static final class PvpProtection extends WorldEvent {
        public PvpProtection() {
            super("pvp_protection", "PvP Protection", Category.PVP,
                    "Players can't damage each other. Mobs and the environment still can. A peaceful break!");
        }

        @Override
        public void onDamage(EntityDamageByEntityEvent e) {
            if (!(e.getEntity() instanceof Player victim)) return;
            Player attacker = attackerOf(e);
            if (attacker == null || attacker.equals(victim)) return;
            e.setCancelled(true);
            attacker.sendActionBar(Component.text("PvP Protection is active - no player damage.", NamedTextColor.GREEN));
        }
    }

    // ------------------------------------------------------------------ Bounty Hour
    public static final class BountyHour extends WorldEvent {
        private final Map<String, Long> recent = new HashMap<>();

        public BountyHour() {
            super("bounty_hour", "Bounty Hour", Category.PVP,
                    "Eliminating another player earns a reward. Farming the same player repeatedly pays nothing.");
        }

        @Override
        protected void resetState() {
            recent.clear();
        }

        @Override
        public void onPlayerDeath(PlayerDeathEvent e) {
            Player victim = e.getEntity();
            Player killer = victim.getKiller();
            if (killer == null || killer.equals(victim)) return;
            String key = killer.getUniqueId() + ":" + victim.getUniqueId();
            long now = System.currentTimeMillis();
            Long last = recent.get(key);
            long cooldown = cfgI("pair-cooldown-minutes", 30) * 60_000L;
            if (last != null && now - last < cooldown) {
                killer.sendMessage(Component.text("No bounty - you already collected on that player recently.", NamedTextColor.GRAY));
                return;
            }
            recent.put(key, now);
            ItemStack reward = rollReward("rewards", List.of("DIAMOND:1-2", "EMERALD:3-6", "GOLDEN_APPLE:1-2"));
            if (reward == null) return;
            giveOrDrop(killer, reward);
            killer.sendMessage(Component.text("Bounty collected: " + reward.getAmount() + "x "
                    + reward.getType().name().toLowerCase().replace('_', ' '), NamedTextColor.GOLD));
            plugin.getLogger().info("Bounty: " + killer.getName() + " -> " + reward.getAmount() + "x " + reward.getType());
        }

        @Override
        protected void onStop() {
            recent.clear();
        }
    }

    // ------------------------------------------------------------------ Fog of War
    public static final class FogOfWar extends WorldEvent {
        public FogOfWar() {
            super("fog_of_war", "Fog of War", Category.WORLD,
                    "Darkness creeps in and out around every player at intervals. It's the vanilla Darkness effect, so it only affects vision.");
        }

        @Override
        protected void onStart() {
            long period = 20L * Math.max(10, cfgI("interval-seconds", 45));
            repeat(100L, period, () -> {
                int ticks = 20 * Math.max(3, cfgI("effect-seconds", 10));
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.getGameMode() == GameMode.SPECTATOR) continue;
                    p.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, ticks, 0, true, false, true));
                }
            });
        }
    }

    // ------------------------------------------------------------------ Knockback Madness
    public static final class KnockbackMadness extends WorldEvent {
        public KnockbackMadness() {
            super("knockback_madness", "Knockback Madness", Category.PVP,
                    "Melee hits between players knock the target back much further. Fall damage still applies, so mind the cliffs!");
        }

        @Override
        public void onDamage(EntityDamageByEntityEvent e) {
            if (!(e.getDamager() instanceof Player attacker) || !(e.getEntity() instanceof Player victim)) return;
            if (attacker.equals(victim)) return;
            if (e.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK) return;
            double strength = Math.min(2.0, Math.max(0.0, cfgD("strength", 0.7)));
            Vector dir = victim.getLocation().toVector().subtract(attacker.getLocation().toVector());
            dir.setY(0);
            if (dir.lengthSquared() < 1.0E-4) return;
            dir.normalize().multiply(strength);
            dir.setY(0.25);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (victim.isOnline() && !victim.isDead()) victim.setVelocity(victim.getVelocity().add(dir));
            });
        }
    }

    // ------------------------------------------------------------------ Bow Frenzy
    public static final class BowFrenzy extends WorldEvent {
        public BowFrenzy() {
            super("bow_frenzy", "Bow Frenzy", Category.PVP,
                    "Arrows fired by players deal extra damage. Bows and crossbows rule the battlefield.");
        }

        @Override
        public void onDamage(EntityDamageByEntityEvent e) {
            if (!(e.getDamager() instanceof AbstractArrow arrow) || !(arrow.getShooter() instanceof Player)) return;
            e.setDamage(e.getDamage() * cfgD("multiplier", 1.5));
        }
    }

    // ------------------------------------------------------------------ Last Stand
    public static final class LastStand extends WorldEvent {
        private final Map<UUID, Long> cooldown = new HashMap<>();

        public LastStand() {
            super("last_stand", "Last Stand", Category.PVP,
                    "Players on low health get a short burst of Strength and Resistance. It can only trigger once per cooldown, so don't rely on it.");
        }

        @Override
        protected void resetState() {
            cooldown.clear();
        }

        @Override
        protected void onStart() {
            repeat(10L, 10L, this::check);
        }

        private void check() {
            double threshold = cfgD("health-threshold", 6.0);
            long cooldownMs = cfgI("cooldown-seconds", 60) * 1000L;
            int seconds = Math.max(1, cfgI("effect-seconds", 10));
            long now = System.currentTimeMillis();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getGameMode() != GameMode.SURVIVAL && p.getGameMode() != GameMode.ADVENTURE) continue;
                if (p.isDead() || p.getHealth() > threshold) continue;
                Long last = cooldown.get(p.getUniqueId());
                if (last != null && now - last < cooldownMs) continue;
                cooldown.put(p.getUniqueId(), now);
                p.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, seconds * 20, 1));
                p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, seconds * 20, 1));
                p.sendActionBar(Component.text("Last Stand! Strength and Resistance for " + seconds + "s", NamedTextColor.RED));
            }
        }

        @Override
        protected void onStop() {
            cooldown.clear();
        }
    }

    // ------------------------------------------------------------------ Fireworks Festival
    public static final class FireworksFestival extends WorldEvent {
        private static final FireworkEffect.Type[] TYPES = {
                FireworkEffect.Type.BALL_LARGE, FireworkEffect.Type.STAR, FireworkEffect.Type.BURST, FireworkEffect.Type.BALL
        };
        private static final Color[] COLORS = {
                Color.RED, Color.ORANGE, Color.YELLOW, Color.LIME, Color.AQUA, Color.FUCHSIA, Color.WHITE
        };

        public FireworksFestival() {
            super("fireworks_festival", "Fireworks Festival", Category.UTILITY,
                    "Fireworks burst high above every player in the Overworld. Purely cosmetic - they go off well out of reach.");
        }

        @Override
        protected void onStart() {
            long period = 20L * Math.max(5, cfgI("interval-seconds", 12));
            repeat(40L, period, this::show);
        }

        private void show() {
            ThreadLocalRandom r = ThreadLocalRandom.current();
            for (Player p : Bukkit.getOnlinePlayers()) {
                World w = p.getWorld();
                if (w.getEnvironment() != World.Environment.NORMAL || p.getGameMode() == GameMode.SPECTATOR) continue;
                if (w.getHighestBlockYAt(p.getLocation()) > p.getLocation().getBlockY() + 1) continue; // only if under open sky
                Location l = p.getLocation().add(r.nextDouble(-12, 12), 16 + r.nextDouble(8), r.nextDouble(-12, 12));
                if (l.getY() > w.getMaxHeight() - 2) l.setY(w.getMaxHeight() - 2);
                Firework fw = w.spawn(l, Firework.class);
                FireworkMeta meta = fw.getFireworkMeta();
                meta.addEffect(FireworkEffect.builder()
                        .with(TYPES[r.nextInt(TYPES.length)])
                        .withColor(COLORS[r.nextInt(COLORS.length)], COLORS[r.nextInt(COLORS.length)])
                        .withFade(Color.WHITE)
                        .trail(true).flicker(true).build());
                fw.setFireworkMeta(meta);
                fw.detonate();
            }
        }
    }
}
