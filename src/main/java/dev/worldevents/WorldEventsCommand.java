package dev.worldevents;

import net.kyori.adventure.text.Component;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class WorldEventsCommand implements CommandExecutor, TabCompleter {
    private static final List<String> SUBS = List.of(
            "help", "list", "info", "gui", "start", "stop", "random", "extra", "resume", "reload", "toggle", "weights");

    private static final String BAD_TIME = "{prefix}<red>Time must look like <yellow>30</yellow> (minutes), <yellow>45m</yellow>, <yellow>2h</yellow>, <yellow>1h30m</yellow> or <yellow>90s</yellow> - between 10 seconds and 24 hours.";

    private final WorldEventsPlugin plugin;

    public WorldEventsCommand(WorldEventsPlugin plugin) {
        this.plugin = plugin;
    }

    private EventManager mgr() { return plugin.manager(); }

    private void send(CommandSender s, String raw, String... kv) {
        s.sendMessage(plugin.mm(raw, kv));
    }

    private boolean need(CommandSender s, String perm) {
        if (s.hasPermission(perm)) return true;
        s.sendMessage(plugin.msg("no-permission"));
        return false;
    }

    private static String perm(String sub) {
        return switch (sub) {
            case "gui", "start", "stop", "random", "extra", "resume", "toggle" -> "worldevents.admin";
            case "reload" -> "worldevents.reload";
            case "weights" -> "worldevents.weights";
            default -> "worldevents.use";
        };
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (need(sender, "worldevents.use")) showCurrent(sender);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (!SUBS.contains(sub)) {
            send(sender, "{prefix}<red>Unknown subcommand. Use <yellow>/worldevents help</yellow>.");
            return true;
        }
        if (!need(sender, perm(sub))) return true;

        switch (sub) {
            case "help" -> help(sender);
            case "list" -> list(sender);
            case "info" -> info(sender, args);
            case "gui" -> {
                if (sender instanceof Player p) plugin.gui().open(p, 0);
                else send(sender, "{prefix}<red>Only players can open the menu.");
            }
            case "start" -> start(sender, args);
            case "stop" -> stop(sender, args);
            case "random" -> {
                Long duration = null;
                if (args.length >= 2) {
                    duration = EventManager.parseDuration(args[1]);
                    if (duration == null) {
                        send(sender, BAD_TIME);
                        return true;
                    }
                }
                EventManager.StartResult r = mgr().startRandom(duration);
                plugin.getLogger().info(sender.getName() + " started a random event (" + r + ").");
                Component m = mgr().resultMessage(r, null);
                if (m != null) sender.sendMessage(m);
            }
            case "extra" -> {
                boolean on = mgr().toggleExtra();
                plugin.getLogger().info(sender.getName() + (on ? " armed" : " disarmed") + " extra mode.");
                send(sender, on
                        ? "{prefix}<gold>Extra mode ON: the next event you start (random or chosen) runs alongside the current one. Max " + EventManager.MAX_ACTIVE + " at once."
                        : "{prefix}<gray>Extra mode OFF: the next event you start replaces the current ones.");
            }
            case "resume" -> {
                mgr().resumeAuto();
                plugin.getLogger().info(sender.getName() + " resumed automatic events.");
                send(sender, "{prefix}<gray>Automatic events resumed.");
            }
            case "reload" -> reload(sender);
            case "toggle" -> toggle(sender, args);
            case "weights" -> weights(sender);
            default -> help(sender);
        }
        return true;
    }

    // ---------------------------------------------------------------- subcommands
    private void showCurrent(CommandSender s) {
        List<WorldEvent> active = mgr().getActive();
        if (active.isEmpty()) {
            send(s, mgr().isSpinning() ? "{prefix}<gray>The wheel is spinning..." : "{prefix}<gray>No event is active right now.");
            return;
        }
        for (WorldEvent ev : active) {
            String[] ph = mgr().ph(ev, mgr().remainingMs(ev));
            send(s, "{prefix}{color}<bold>{event}</bold> <dark_gray>-</dark_gray> <yellow>{time}</yellow> <gray>left", ph);
            send(s, "<gray>{description}", ph);
        }
    }

    private void help(CommandSender s) {
        send(s, "{prefix}<gold><bold>World Events commands");
        send(s, "<yellow>/worldevents</yellow> <gray>- running events");
        send(s, "<yellow>/worldevents list</yellow> <gray>- all events");
        send(s, "<yellow>/worldevents info <event></yellow> <gray>- event details");
        if (s.hasPermission("worldevents.admin")) {
            send(s, "<yellow>/worldevents gui</yellow> <gray>- open the event menu");
            send(s, "<yellow>/worldevents start <event> [time]</yellow> <gray>- start an event (time e.g. 30m, 2h; default 1h)");
            send(s, "<yellow>/worldevents random [time]</yellow> <gray>- spin the wheel for a random event");
            send(s, "<yellow>/worldevents extra</yellow> <gray>- next event runs alongside the current one (max " + EventManager.MAX_ACTIVE + ")");
            send(s, "<yellow>/worldevents stop</yellow> <gray>- stop all events");
            send(s, "<yellow>/worldevents stop <event></yellow> <gray>- stop just that event");
            send(s, "<yellow>/worldevents resume</yellow> <gray>- resume automatic events");
            send(s, "<yellow>/worldevents toggle <event></yellow> <gray>- enable/disable in the pool");
        }
        if (s.hasPermission("worldevents.weights")) send(s, "<yellow>/worldevents weights</yellow> <gray>- selection weights");
        if (s.hasPermission("worldevents.reload")) send(s, "<yellow>/worldevents reload</yellow> <gray>- reload config");
    }

    private void list(CommandSender s) {
        boolean admin = s.hasPermission("worldevents.admin");
        send(s, "{prefix}<gold><bold>Events <gray>(" + mgr().getEvents().size() + ")");
        for (WorldEvent ev : mgr().getEvents().values()) {
            boolean enabled = mgr().isEnabled(ev);
            if (!enabled && !admin) continue;
            String line = ev.getColorTag() + "<bold>" + ev.getName() + "</bold><reset>"
                    + (mgr().isActive(ev) ? " <green>(running)</green>" : "")
                    + (enabled ? "" : " <red>(disabled)</red>") + " <gray>- " + ev.getDescription();
            send(s, line);
        }
    }

    private void info(CommandSender s, String[] args) {
        if (args.length < 2) {
            send(s, "{prefix}<red>Usage: /worldevents info <event>");
            return;
        }
        WorldEvent ev = mgr().find(args[1]);
        if (ev == null) {
            send(s, "{prefix}<red>Unknown event '" + args[1].replace("<", "") + "'. Use /worldevents list.");
            return;
        }
        String[] ph = mgr().ph(ev, 0);
        send(s, "{prefix}{color}<bold>{event}</bold>", ph);
        send(s, "<gray>{description}", ph);
        send(s, "<gray>Category: <white>" + ev.getCategory().display + " <dark_gray>|</dark_gray> <gray>Duration: <white>"
                + mgr().durationMinutes(ev) + " min <dark_gray>|</dark_gray> <gray>In pool: "
                + (mgr().isEnabled(ev) ? "<green>yes" : "<red>no"));
        if (s.hasPermission("worldevents.weights")) {
            send(s, "<gray>Weight: <white>" + mgr().weight(ev));
        }
        send(s, "<dark_gray>id: " + ev.getId());
    }

    private void start(CommandSender s, String[] args) {
        if (args.length < 2) {
            send(s, "{prefix}<red>Usage: /worldevents start <event> [time]");
            return;
        }
        WorldEvent ev = mgr().find(args[1]);
        if (ev == null) {
            send(s, "{prefix}<red>Unknown event '" + args[1].replace("<", "") + "'. Use /worldevents list.");
            return;
        }
        Long duration = null;
        if (args.length >= 3) {
            duration = EventManager.parseDuration(args[2]);
            if (duration == null) {
                send(s, BAD_TIME);
                return;
            }
        }
        EventManager.StartResult r = mgr().startManual(ev, duration);
        plugin.getLogger().info(s.getName() + " started event " + ev.getId() + " (" + r + ")");
        Component m = mgr().resultMessage(r, ev);
        if (m != null) s.sendMessage(m);
    }

    private void stop(CommandSender s, String[] args) {
        if (args.length >= 2) {
            WorldEvent ev = mgr().find(args[1]);
            if (ev == null) {
                send(s, "{prefix}<red>Unknown event '" + args[1].replace("<", "") + "'.");
                return;
            }
            if (!mgr().stopEvent(ev)) {
                send(s, "{prefix}<red>" + ev.getName() + " isn't running.");
                return;
            }
            plugin.getLogger().info(s.getName() + " stopped event " + ev.getId());
            send(s, mgr().activeCount() == 0
                    ? "{prefix}<gray>Stopped " + ev.getName() + ". Nothing is running, so automatic events are paused - use <yellow>/worldevents resume</yellow>."
                    : "{prefix}<gray>Stopped " + ev.getName() + ". The other event keeps running.");
            return;
        }
        if (mgr().getActive().isEmpty() && !mgr().isSpinning()) {
            send(s, "{prefix}<red>No event is running.");
            return;
        }
        mgr().stopAll();
        plugin.getLogger().info(s.getName() + " stopped all events.");
        send(s, "{prefix}<gray>All events stopped and effects cleaned up. Automatic events are paused - use <yellow>/worldevents resume</yellow> or <yellow>random</yellow>.");
    }

    private void reload(CommandSender s) {
        plugin.reloadConfig();
        List<String> problems = mgr().validateConfig();
        mgr().afterReload();
        plugin.getLogger().info(s.getName() + " reloaded the configuration (" + problems.size() + " problems).");
        if (problems.isEmpty()) {
            send(s, "{prefix}<green>Configuration reloaded with no problems.");
        } else {
            send(s, "{prefix}<yellow>Configuration reloaded with " + problems.size() + " problem(s):");
            for (String p : problems) {
                send(s, "<red> - " + p.replace("<", ""));
                plugin.getLogger().warning("Config problem: " + p);
            }
        }
    }

    private void toggle(CommandSender s, String[] args) {
        if (args.length < 2) {
            send(s, "{prefix}<red>Usage: /worldevents toggle <event>");
            return;
        }
        WorldEvent ev = mgr().find(args[1]);
        if (ev == null) {
            send(s, "{prefix}<red>Unknown event '" + args[1].replace("<", "") + "'.");
            return;
        }
        boolean now = mgr().toggle(ev);
        plugin.getLogger().info(s.getName() + (now ? " enabled " : " disabled ") + ev.getId());
        send(s, "{prefix}<gray>" + ev.getName() + " is now " + (now ? "<green>enabled" : "<red>disabled") + "<gray> in the random pool.");
    }

    private void weights(CommandSender s) {
        List<WorldEvent> pool = new ArrayList<>(mgr().getEvents().values());
        pool.sort(Comparator.comparingDouble((WorldEvent e) -> mgr().weight(e)).reversed());
        double total = 0;
        for (WorldEvent e : pool) if (mgr().isEnabled(e)) total += Math.max(0, mgr().weight(e));
        send(s, "{prefix}<gold><bold>Event weights");
        for (WorldEvent e : pool) {
            boolean en = mgr().isEnabled(e);
            double w = mgr().weight(e);
            String pct = en && total > 0 ? String.format("%.1f%%", Math.max(0, w) / total * 100) : "off";
            send(s, e.getColorTag() + e.getName() + "<reset> <dark_gray>- <gray>weight <white>" + w
                    + " <dark_gray>(" + pct + ")");
        }
    }

    // ---------------------------------------------------------------- tab completion
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            for (String sub : SUBS) {
                if (sender.hasPermission(perm(sub)) && sub.startsWith(args[0].toLowerCase(Locale.ROOT))) out.add(sub);
            }
        } else if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            String prefix = args[1].toLowerCase(Locale.ROOT);
            if (List.of("start", "info", "toggle").contains(sub) && sender.hasPermission(perm(sub))) {
                for (String id : mgr().getEvents().keySet()) if (id.startsWith(prefix)) out.add(id);
            } else if (sub.equals("random") && sender.hasPermission(perm(sub))) {
                for (String t : List.of("5m", "15m", "30m", "1h", "2h")) if (t.startsWith(prefix)) out.add(t);
            } else if (sub.equals("stop") && sender.hasPermission(perm(sub))) {
                for (WorldEvent ev : mgr().getActive()) if (ev.getId().startsWith(prefix)) out.add(ev.getId());
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("start") && sender.hasPermission("worldevents.admin")) {
            out.addAll(List.of("5m", "15m", "30m", "1h", "2h"));
        }
        return out;
    }
}
