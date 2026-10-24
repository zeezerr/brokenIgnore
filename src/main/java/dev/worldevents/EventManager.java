package dev.worldevents;

import dev.worldevents.events.LootEvents;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;

public final class EventManager {
    /** Never more than this many events at once. */
    public static final int MAX_ACTIVE = 2;

    public enum StartResult { OK, FAILED, FULL, CONFLICT, ALREADY_ACTIVE, NONE }

    /** Events in the same group can't run together because their effects would fight each other. */
    private static final List<Set<String>> CONFLICTS = List.of(
            Set.of("eternal_night", "eternal_day", "blood_moon"),
            Set.of("speed_rush", "heavy_feet"),
            Set.of("super_jump", "low_gravity"),
            Set.of("double_damage", "reduced_damage", "combat_frenzy", "fragile_fighters", "pvp_protection"),
            Set.of("pvp_protection", "bounty_hour", "knockback_madness"),
            Set.of("peaceful_hour", "mob_invasion", "creeper_season", "spider_season", "enderman_migration", "blood_moon"),
            Set.of("ultra_hardcore", "regeneration_rush"),
            Set.of("night_vision", "fog_of_war")
    );

    private static final class Active {
        final WorldEvent ev;
        final long endAt;
        final long totalMs;
        final BossBar bar;

        Active(WorldEvent ev, long endAt, long totalMs, BossBar bar) {
            this.ev = ev;
            this.endAt = endAt;
            this.totalMs = totalMs;
            this.bar = bar;
        }
    }

    private final WorldEventsPlugin plugin;
    private final Map<String, WorldEvent> events = new LinkedHashMap<>();
    private final List<Active> active = new CopyOnWriteArrayList<>(); // read from the async chat thread too
    private final Deque<String> history = new ArrayDeque<>();
    private final Random random = new Random();

    private WorldEvent next;
    private long nextStartAt;
    private boolean paused;
    private boolean warned;
    private boolean spinning;
    private boolean extraArmed;
    private BukkitTask spinTask;
    private BukkitTask timer;

    public EventManager(WorldEventsPlugin plugin) {
        this.plugin = plugin;
    }

    // ---------------------------------------------------------------- setup
    public void registerAll() {
        for (WorldEvent ev : EventRegistry.createAll()) {
            ev.init(plugin);
            events.put(ev.getId(), ev);
        }
    }

    public void start() {
        LootEvents.SupplyDrop.cleanupStale(plugin);
        FileConfiguration c = plugin.getConfig();
        nextStartAt = System.currentTimeMillis() + c.getLong("initial-delay-seconds", 60) * 1000L;
        paused = plugin.data().getBoolean("paused", false) || !c.getBoolean("auto-start", true);

        String savedNext = plugin.data().getString("next");
        if (savedNext != null && events.containsKey(savedNext) && isEnabled(events.get(savedNext))) {
            next = events.get(savedNext);
        } else {
            pickNext();
        }

        timer = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
        Bukkit.getScheduler().runTask(plugin, this::resume);
    }

    private void resume() {
        ConfigurationSection sec = plugin.data().getConfigurationSection("active");
        if (sec != null) {
            for (String key : sec.getKeys(false)) {
                ConfigurationSection e = sec.getConfigurationSection(key);
                if (e == null) continue;
                WorldEvent ev = events.get(e.getString("id", ""));
                long remaining = e.getLong("remaining", 0);
                if (ev == null || remaining <= 0 || active.size() >= MAX_ACTIVE) continue;
                startEvent(ev, remaining, e.getLong("total", remaining), e.getConfigurationSection("state"), true);
            }
        }
        plugin.data().set("active", null);
        plugin.data().set("current", null);
        plugin.saveData();
    }

    public void shutdown() {
        if (timer != null) timer.cancel();
        cancelSpin();
        FileConfiguration d = plugin.data();
        d.set("active", null);
        d.set("current", null);
        long now = System.currentTimeMillis();
        int i = 0;
        for (Active a : active) {
            String base = "active." + i++;
            d.set(base + ".id", a.ev.getId());
            d.set(base + ".remaining", Math.max(0, a.endAt - now));
            d.set(base + ".total", a.totalMs);
            a.ev.saveState(d.createSection(base + ".state"));
        }
        d.set("paused", paused);
        d.set("next", next == null ? null : next.getId());
        endAll(false);
        plugin.saveData();
    }

    // ---------------------------------------------------------------- queries
    public List<WorldEvent> getActive() {
        if (active.isEmpty()) return List.of();
        List<WorldEvent> out = new ArrayList<>(active.size());
        for (Active a : active) out.add(a.ev);
        return out;
    }

    private Active findActive(WorldEvent ev) {
        for (Active a : active) if (a.ev == ev) return a;
        return null;
    }

    public boolean isActive(WorldEvent ev) { return findActive(ev) != null; }
    public int activeCount() { return active.size(); }
    public WorldEvent getNext() { return next; }
    public Map<String, WorldEvent> getEvents() { return events; }
    public boolean isPaused() { return paused; }
    public boolean isSpinning() { return spinning; }
    public boolean isExtraArmed() { return extraArmed; }

    public long remainingMs(WorldEvent ev) {
        Active a = findActive(ev);
        return a == null ? 0 : Math.max(0, a.endAt - System.currentTimeMillis());
    }

    public WorldEvent find(String input) {
        if (input == null) return null;
        String key = input.toLowerCase().trim().replace('-', '_').replace(' ', '_').replace("'", "");
        return events.get(key);
    }

    public boolean isEnabled(WorldEvent ev) {
        String p = "toggles." + ev.getId();
        if (plugin.data().contains(p)) return plugin.data().getBoolean(p);
        return plugin.getConfig().getBoolean("events." + ev.getId() + ".enabled", true);
    }

    public boolean toggle(WorldEvent ev) {
        boolean now = !isEnabled(ev);
        plugin.data().set("toggles." + ev.getId(), now);
        plugin.saveData();
        if (next == ev && !now) pickNext();
        return now;
    }

    public double weight(WorldEvent ev) {
        FileConfiguration c = plugin.getConfig();
        return c.getDouble("events." + ev.getId() + ".weight", c.getDouble("default-weight", 10));
    }

    public int durationMinutes(WorldEvent ev) {
        FileConfiguration c = plugin.getConfig();
        return c.getInt("events." + ev.getId() + ".duration-minutes", c.getInt("default-duration-minutes", 60));
    }

    // ---------------------------------------------------------------- compatibility
    private static boolean conflicts(String a, String b) {
        if (a.equals(b)) return false;
        for (Set<String> group : CONFLICTS) {
            if (group.contains(a) && group.contains(b)) return true;
        }
        return false;
    }

    /** The running event that stops this one from being added alongside it, or null. */
    public WorldEvent conflictingActive(WorldEvent ev) {
        for (Active a : active) {
            if (conflicts(a.ev.getId(), ev.getId())) return a.ev;
        }
        return null;
    }

    // ---------------------------------------------------------------- selection
    public WorldEvent pickNext() {
        next = choose(false);
        return next;
    }

    /** Weighted random choice. For an extra event, running and conflicting events are excluded. */
    private WorldEvent choose(boolean extra) {
        List<WorldEvent> pool = new ArrayList<>();
        for (WorldEvent ev : events.values()) {
            if (!isEnabled(ev) || weight(ev) <= 0) continue;
            if (findActive(ev) != null) continue;
            if (extra && conflictingActive(ev) != null) continue;
            pool.add(ev);
        }
        if (pool.isEmpty()) return null;

        int protect = Math.max(0, Math.min(plugin.getConfig().getInt("repeat-protection", 3), pool.size() - 1));
        Set<String> recent = new HashSet<>();
        Iterator<String> it = history.iterator();
        for (int i = 0; i < protect && it.hasNext(); i++) recent.add(it.next());
        String last = history.peekFirst();
        if (last != null) recent.add(last); // never the same event twice in a row

        List<WorldEvent> filtered = new ArrayList<>();
        for (WorldEvent ev : pool) if (!recent.contains(ev.getId())) filtered.add(ev);
        if (filtered.isEmpty()) {
            for (WorldEvent ev : pool) if (last == null || !ev.getId().equals(last)) filtered.add(ev);
        }
        if (filtered.isEmpty()) filtered = pool;

        double total = 0;
        for (WorldEvent ev : filtered) total += weight(ev);
        double roll = random.nextDouble() * total;
        for (WorldEvent ev : filtered) {
            roll -= weight(ev);
            if (roll <= 0) return ev;
        }
        return filtered.get(filtered.size() - 1);
    }

    // ---------------------------------------------------------------- control
    public boolean toggleExtra() {
        extraArmed = !extraArmed;
        return extraArmed;
    }

    /** Admin start. Replaces the running events, or adds to them when extra mode is armed (max 2). */
    public StartResult startManual(WorldEvent ev, Long durationMs) {
        cancelSpin();
        if (findActive(ev) != null) return StartResult.ALREADY_ACTIVE;
        long total = durationMs != null ? durationMs : Math.max(1, durationMinutes(ev)) * 60_000L;
        boolean extra = extraArmed && !active.isEmpty();
        if (extra) {
            if (active.size() >= MAX_ACTIVE) return StartResult.FULL;
            if (conflictingActive(ev) != null) return StartResult.CONFLICT;
        }
        extraArmed = false;
        if (!extra) endAll(true);
        paused = false;
        return startEvent(ev, total, total, null, false) ? StartResult.OK : StartResult.FAILED;
    }

    /** Admin random start with the slot-machine animation. Honours extra mode like startManual. */
    public StartResult startRandom() {
        return startRandom(null);
    }

    /** Random start; durationMs null means the normal duration from the config. */
    public StartResult startRandom(Long durationMs) {
        cancelSpin();
        boolean extra = extraArmed && !active.isEmpty();
        if (extra && active.size() >= MAX_ACTIVE) return StartResult.FULL;
        WorldEvent pick;
        if (extra) {
            pick = choose(true);
        } else {
            endAll(true);
            pick = choose(false);
        }
        if (pick == null) return StartResult.NONE;
        extraArmed = false;
        paused = false;
        startWithSpin(pick, durationMs);
        return StartResult.OK;
    }

    /** Stops every running event and pauses automatic events. */
    public void stopAll() {
        cancelSpin();
        endAll(true);
        paused = true;
    }

    /** Stops just one event. If nothing is left running, automatic events pause too. */
    public boolean stopEvent(WorldEvent ev) {
        Active a = findActive(ev);
        if (a == null) return false;
        endActive(a, true);
        if (active.isEmpty()) paused = true;
        return true;
    }

    public void resumeAuto() {
        paused = false;
        if (active.isEmpty()) nextStartAt = System.currentTimeMillis() + 5_000L;
    }

    public Component resultMessage(StartResult r, WorldEvent ev) {
        String name = ev == null ? "That event" : ev.getName();
        return switch (r) {
            case OK -> null;
            case FAILED -> plugin.mm("{prefix}<red>That event failed to start - check the console.");
            case FULL -> plugin.mm("{prefix}<red>" + MAX_ACTIVE + " events are already running (the maximum). "
                    + "Stop one with <yellow>/worldevents stop <event></yellow>.");
            case CONFLICT -> {
                WorldEvent other = ev == null ? null : conflictingActive(ev);
                yield plugin.mm("{prefix}<red>" + name + " can't run alongside "
                        + (other == null ? "the current event" : other.getName()) + ".");
            }
            case ALREADY_ACTIVE -> plugin.mm("{prefix}<red>" + name + " is already running.");
            case NONE -> plugin.mm("{prefix}<red>No compatible events are available to start.");
        };
    }

    private boolean startEvent(WorldEvent ev, long remainingMs, long total, ConfigurationSection state, boolean resumed) {
        if (active.size() >= MAX_ACTIVE || findActive(ev) != null) return false;
        try {
            ev.begin(state);
        } catch (Throwable t) {
            plugin.getLogger().log(Level.SEVERE, "Failed to start event " + ev.getId(), t);
            try { ev.end(); } catch (Throwable ignored) { }
            nextStartAt = System.currentTimeMillis() + 30_000L;
            return false;
        }
        long now = System.currentTimeMillis();
        BossBar bar = BossBar.bossBar(Component.text(""), 1f, ev.getBarColor(), BossBar.Overlay.PROGRESS);
        Active a = new Active(ev, now + remainingMs, Math.max(1, total), bar);
        active.add(a);
        warned = false;
        history.addFirst(ev.getId());
        while (history.size() > 50) history.removeLast();
        pickNext();

        for (Player p : Bukkit.getOnlinePlayers()) p.showBossBar(bar);
        updateBar(a, remainingMs);
        plugin.getLogger().info((resumed ? "Event resumed: " : "Event started: ") + ev.getId()
                + " (" + formatTime(remainingMs) + ", " + active.size() + " active)");

        if (resumed) {
            plugin.broadcast(plugin.msg("event-resumed", ph(ev, remainingMs)));
        } else {
            announceStart(ev, remainingMs);
        }
        return true;
    }

    private void endActive(Active a, boolean announce) {
        if (!active.remove(a)) return;
        try {
            a.ev.end();
        } catch (Throwable t) {
            plugin.getLogger().log(Level.SEVERE, "Failed to end event " + a.ev.getId(), t);
        }
        for (Player p : Bukkit.getOnlinePlayers()) p.hideBossBar(a.bar);
        plugin.getLogger().info("Event ended: " + a.ev.getId());
        if (announce) {
            plugin.broadcast(plugin.msg("event-ended", ph(a.ev, 0)));
            if (plugin.getConfig().getBoolean("sounds", true)) {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.8f, 0.8f);
                }
            }
        }
        if (active.isEmpty()) {
            nextStartAt = System.currentTimeMillis()
                    + plugin.getConfig().getLong("intermission-seconds", 30) * 1000L;
        }
    }

    private void endAll(boolean announce) {
        for (Active a : new ArrayList<>(active)) endActive(a, announce);
    }

    // ---------------------------------------------------------------- ticking
    private void tick() {
        long now = System.currentTimeMillis();
        FileConfiguration c = plugin.getConfig();

        for (Active a : new ArrayList<>(active)) {
            long rem = a.endAt - now;
            if (rem <= 0) endActive(a, true);
            else updateBar(a, rem);
        }

        if (!active.isEmpty()) {
            if (active.size() == 1 && !warned && next != null && c.getBoolean("preview-next", false)) {
                long rem = active.get(0).endAt - now;
                if (rem <= c.getLong("next-warning-seconds", 60) * 1000L) {
                    warned = true;
                    plugin.broadcast(plugin.msg("next-warning", ph(next, 0)));
                }
            }
        } else if (!paused && !spinning && now >= nextStartAt) {
            if (Bukkit.getOnlinePlayers().size() < c.getInt("min-players", 1)) {
                nextStartAt = now + 15_000L;
                return;
            }
            if (next == null || !isEnabled(next) || findActive(next) != null) pickNext();
            if (next == null) {
                nextStartAt = now + 30_000L;
                return;
            }
            startWithSpin(next, null);
        }
    }

    // ---------------------------------------------------------------- slot-machine animation
    private void cancelSpin() {
        if (spinTask != null) spinTask.cancel();
        spinTask = null;
        spinning = false;
    }

    /** Spins a slot-machine style title on every screen, then starts the chosen event. */
    private void startWithSpin(WorldEvent chosen, Long durationMs) {
        cancelSpin();
        List<WorldEvent> pool = new ArrayList<>();
        for (WorldEvent ev : events.values()) {
            if (isEnabled(ev) && weight(ev) > 0) pool.add(ev);
        }
        long total = durationMs != null ? durationMs : Math.max(1, durationMinutes(chosen)) * 60_000L;
        if (!plugin.getConfig().getBoolean("spin-animation", true) || pool.size() < 2
                || Bukkit.getOnlinePlayers().isEmpty()) {
            startEvent(chosen, total, total, null, false);
            return;
        }

        // delay (ticks) after each frame: starts fast, slows down like a real reel
        List<Integer> delays = new ArrayList<>();
        for (int i = 0; i < 14; i++) delays.add(2);
        for (int i = 0; i < 6; i++) delays.add(3);
        for (int i = 0; i < 3; i++) delays.add(5);
        for (int i = 0; i < 2; i++) delays.add(8);
        delays.add(12);

        List<WorldEvent> frames = new ArrayList<>();
        WorldEvent prev = null;
        for (int i = 0; i < delays.size(); i++) {
            WorldEvent f;
            int tries = 0;
            do {
                f = pool.get(random.nextInt(pool.size()));
                tries++;
            } while ((f == prev || (i == delays.size() - 1 && f == chosen)) && tries < 30);
            frames.add(f);
            prev = f;
        }
        frames.add(chosen); // the reel lands here

        spinning = true;
        runFrame(frames, delays, 0, chosen, total);
    }

    private void runFrame(List<WorldEvent> frames, List<Integer> delays, int i, WorldEvent chosen, long total) {
        boolean last = i == frames.size() - 1;
        showSpinFrame(frames.get(i), last, i / (double) frames.size());
        if (last) {
            spinTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
                spinning = false;
                spinTask = null;
                startEvent(chosen, total, total, null, false);
            }, 30L);
            return;
        }
        spinTask = Bukkit.getScheduler().runTaskLater(plugin,
                () -> runFrame(frames, delays, i + 1, chosen, total), delays.get(i));
    }

    private void showSpinFrame(WorldEvent ev, boolean last, double progress) {
        Component main = plugin.mm("<dark_gray>[ </dark_gray>" + ev.getColorTag()
                + (last ? "<bold>" : "") + ev.getName() + "<reset><dark_gray> ]");
        Component sub = plugin.mm(last ? "<yellow>The wheel has decided!" : "<gray>Spinning the wheel...");
        Title title = Title.title(main, sub, Title.Times.times(Duration.ZERO,
                Duration.ofMillis(last ? 1500 : 700), Duration.ZERO));
        boolean sounds = plugin.getConfig().getBoolean("sounds", true);
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.showTitle(title);
            if (!sounds) continue;
            if (last) {
                p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
                p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
                p.spawnParticle(Particle.END_ROD, p.getLocation().add(0, 1, 0), 40, 0.6, 0.8, 0.6, 0.1);
            } else {
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.8f, (float) (0.8 + 0.8 * progress));
            }
        }
    }

    // ---------------------------------------------------------------- UI
    public void showBar(Player p) {
        for (Active a : active) p.showBossBar(a.bar);
    }

    private void updateBar(Active a, long remaining) {
        a.bar.name(plugin.msg("bossbar-title", ph(a.ev, remaining)));
        a.bar.color(a.ev.getBarColor());
        a.bar.progress((float) Math.max(0.0, Math.min(1.0, remaining / (double) a.totalMs)));
    }

    private void announceStart(WorldEvent ev, long durationMs) {
        String[] ph = ph(ev, durationMs);
        plugin.broadcast(plugin.msg("event-started", ph));
        plugin.broadcast(plugin.msg("event-description", ph));
        plugin.broadcast(plugin.msg("event-duration", ph));

        Title title = Title.title(plugin.msg("title-main", ph), plugin.msg("title-sub", ph),
                Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(4), Duration.ofSeconds(1)));
        boolean sounds = plugin.getConfig().getBoolean("sounds", true);
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.showTitle(title);
            if (sounds) p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
            p.spawnParticle(Particle.END_ROD, p.getLocation().add(0, 1, 0), 30, 0.6, 0.8, 0.6, 0.05);
        }
    }

    public String[] ph(WorldEvent ev, long ms) {
        return new String[]{
                "event", ev.getName(),
                "color", ev.getColorTag(),
                "tagline", ev.getTagline(),
                "description", ev.getDescription(),
                "duration", formatTime(ms),
                "time", formatTime(ms)
        };
    }

    public static final long MIN_DURATION_MS = 10_000L;
    public static final long MAX_DURATION_MS = 24L * 3_600_000L;

    /**
     * Parses "30" (minutes), "45m", "2h", "90s" or combinations like "1h30m".
     * Returns milliseconds, or null if invalid or outside 10 seconds - 24 hours.
     */
    public static Long parseDuration(String input) {
        if (input == null) return null;
        String s = input.toLowerCase().trim();
        if (s.isEmpty()) return null;
        if (s.matches("\\d+")) s += "m";
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+)([hms])").matcher(s);
        long total = 0;
        int end = 0;
        try {
            while (m.find()) {
                if (m.start() != end) return null;
                long n = Long.parseLong(m.group(1));
                switch (m.group(2)) {
                    case "h" -> total += n * 3_600_000L;
                    case "m" -> total += n * 60_000L;
                    default -> total += n * 1_000L;
                }
                end = m.end();
            }
        } catch (NumberFormatException ex) {
            return null;
        }
        if (end != s.length() || total < MIN_DURATION_MS || total > MAX_DURATION_MS) return null;
        return total;
    }

    public static String formatTime(long ms) {
        long s = Math.max(0, ms / 1000);
        long h = s / 3600, m = (s % 3600) / 60, sec = s % 60;
        return h > 0 ? String.format("%d:%02d:%02d", h, m, sec) : String.format("%02d:%02d", m, sec);
    }

    // ---------------------------------------------------------------- config validation
    public List<String> validateConfig() {
        List<String> problems = new ArrayList<>();
        FileConfiguration c = plugin.getConfig();
        ConfigurationSection evs = c.getConfigurationSection("events");
        if (evs != null) {
            for (String key : evs.getKeys(false)) {
                if (!events.containsKey(key)) problems.add("Unknown event id in config: events." + key);
            }
        }
        if (c.getInt("default-duration-minutes", 60) < 1) problems.add("default-duration-minutes must be at least 1");
        if (c.getDouble("default-weight", 10) < 0) problems.add("default-weight is negative");
        for (WorldEvent ev : events.values()) {
            if (weight(ev) < 0) problems.add("Negative weight for " + ev.getId());
            if (durationMinutes(ev) < 1) problems.add("duration-minutes < 1 for " + ev.getId());
        }
        return problems;
    }

    public void afterReload() {
        if (next == null || !isEnabled(next)) pickNext();
    }
}
