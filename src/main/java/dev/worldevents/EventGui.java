package dev.worldevents;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/** Admin menu: click an event to start it; the bottom row has stop / random / extra / page controls. */
public final class EventGui implements Listener {
    private static final int PER_PAGE = 45;
    private static final int SLOT_PREV = 45, SLOT_STOP = 47, SLOT_AUTO = 48, SLOT_RANDOM = 49, SLOT_EXTRA = 50, SLOT_NEXT = 53;

    private final WorldEventsPlugin plugin;

    public EventGui(WorldEventsPlugin plugin) {
        this.plugin = plugin;
    }

    public static final class Holder implements InventoryHolder {
        private Inventory inventory;
        private int page;

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    // ---------------------------------------------------------------- building
    public void open(Player p, int page) {
        Holder h = new Holder();
        h.page = page;
        h.inventory = Bukkit.createInventory(h, 54,
                plugin.mm("<gradient:#ffb347:#ffe259><bold>World Events</bold></gradient> <dark_gray>- <gray>click an event"));
        render(h);
        p.openInventory(h.inventory);
    }

    private List<WorldEvent> all() {
        return new ArrayList<>(plugin.manager().getEvents().values());
    }

    private int pages() {
        return Math.max(1, (int) Math.ceil(plugin.manager().getEvents().size() / (double) PER_PAGE));
    }

    private void render(Holder h) {
        Inventory inv = h.inventory;
        inv.clear();
        h.page = Math.max(0, Math.min(h.page, pages() - 1));

        List<WorldEvent> events = all();
        int start = h.page * PER_PAGE;
        for (int i = 0; i < PER_PAGE && start + i < events.size(); i++) {
            inv.setItem(i, eventItem(events.get(start + i)));
        }

        ItemStack filler = item(Material.GRAY_STAINED_GLASS_PANE, "<dark_gray> ", List.of(), false);
        for (int s = 45; s < 54; s++) inv.setItem(s, filler);

        EventManager m = plugin.manager();
        String pageText = "Page " + (h.page + 1) + "/" + pages();
        if (h.page > 0) inv.setItem(SLOT_PREV, item(Material.ARROW, "<yellow><bold>Previous page", List.of("<gray>" + pageText), false));
        if (h.page < pages() - 1) inv.setItem(SLOT_NEXT, item(Material.ARROW, "<yellow><bold>Next page", List.of("<gray>" + pageText), false));

        inv.setItem(SLOT_STOP, item(Material.BARRIER, "<red><bold>Stop All Events", List.of(
                "<gray>Stops every running event (<white>" + m.activeCount() + "<gray> active)",
                "<gray>and pauses automatic events.",
                "",
                "<yellow>Click to stop"), false));
        inv.setItem(SLOT_AUTO, item(Material.CLOCK, "<aqua><bold>Start Automatic Events", List.of(
                "<gray>Resumes the automatic rotation.",
                "<gray>A random event starts in a few seconds.",
                "",
                "<yellow>Click to start"), false));
        inv.setItem(SLOT_RANDOM, item(Material.DISPENSER, "<green><bold>Random Event", List.of(
                "<gray>Spins the wheel and starts a random event.",
                m.isExtraArmed() ? "<gold>Extra mode is ON: it runs alongside the current one."
                        : "<gray>It replaces whatever is running.",
                "",
                "<yellow>Click to spin"), false));
        inv.setItem(SLOT_EXTRA, item(m.isExtraArmed() ? Material.LIME_DYE : Material.GRAY_DYE,
                "<gold><bold>Extra Event: " + (m.isExtraArmed() ? "<green>ON" : "<red>OFF"), List.of(
                        "<gray>When ON, the next event you start runs",
                        "<gray>alongside the current one (max " + EventManager.MAX_ACTIVE + ").",
                        "",
                        "<yellow>Click to toggle"), m.isExtraArmed()));
    }

    private ItemStack eventItem(WorldEvent ev) {
        EventManager m = plugin.manager();
        boolean running = m.isActive(ev);
        List<String> lore = new ArrayList<>();
        for (String line : wrap(ev.getDescription(), 38)) lore.add("<gray>" + line);
        lore.add("");
        if (running) {
            lore.add("<green>Running <gray>- <white>" + EventManager.formatTime(m.remainingMs(ev)) + " <gray>left");
            lore.add("<yellow>Right-click to stop this event");
        } else {
            if (!m.isEnabled(ev)) lore.add("<red>Disabled in the random pool");
            lore.add("<yellow>Click to choose a duration and start");
        }
        return item(EventStyles.icon(ev.getId()), ev.getColorTag() + "<bold>" + ev.getName(), lore, running);
    }

    private ItemStack item(Material mat, String name, List<String> lore, boolean glow) {
        ItemStack it = new ItemStack(mat);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(plugin.mm(name).decoration(TextDecoration.ITALIC, false));
        List<Component> lines = new ArrayList<>();
        for (String l : lore) lines.add(plugin.mm(l).decoration(TextDecoration.ITALIC, false));
        meta.lore(lines);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        if (glow) meta.setEnchantmentGlintOverride(true);
        it.setItemMeta(meta);
        return it;
    }

    private static List<String> wrap(String text, int width) {
        List<String> out = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            if (line.length() + word.length() + 1 > width && line.length() > 0) {
                out.add(line.toString());
                line.setLength(0);
            }
            if (line.length() > 0) line.append(' ');
            line.append(word);
        }
        if (line.length() > 0) out.add(line.toString());
        return out;
    }

    // ---------------------------------------------------------------- duration menu
    public static final class DurationHolder implements InventoryHolder {
        private Inventory inventory;
        private WorldEvent event;
        private long duration;
        private int returnPage;

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private static final long[] NOTCHES = {
            5 * 60_000L, 10 * 60_000L, 15 * 60_000L, 30 * 60_000L, 45 * 60_000L,
            60 * 60_000L, 90 * 60_000L, 120 * 60_000L, 180 * 60_000L
    };
    private static final int SLIDER_START = 9;
    private static final int SLOT_M10 = 19, SLOT_M1 = 20, SLOT_SHOW = 22, SLOT_P1 = 24, SLOT_P10 = 25;
    private static final int SLOT_BACK = 27, SLOT_START = 31, SLOT_EXTRA2 = 33;

    private static String fmt(long ms) {
        long h = ms / 3_600_000L, m = (ms % 3_600_000L) / 60_000L, sec = (ms % 60_000L) / 1000L;
        List<String> parts = new ArrayList<>();
        if (h > 0) parts.add(h + "h");
        if (m > 0) parts.add(m + "m");
        if (sec > 0) parts.add(sec + "s");
        return parts.isEmpty() ? "0s" : String.join(" ", parts);
    }

    public void openDuration(Player p, WorldEvent ev, long duration, int returnPage) {
        DurationHolder d = new DurationHolder();
        d.event = ev;
        d.duration = Math.max(EventManager.MIN_DURATION_MS, Math.min(EventManager.MAX_DURATION_MS, duration));
        d.returnPage = returnPage;
        d.inventory = Bukkit.createInventory(d, 36,
                plugin.mm(ev.getColorTag() + "<bold>" + ev.getName() + "</bold> <dark_gray>- <gray>choose a duration"));
        renderDuration(d);
        p.openInventory(d.inventory);
    }

    private void renderDuration(DurationHolder d) {
        Inventory inv = d.inventory;
        EventManager m = plugin.manager();
        WorldEvent ev = d.event;
        ItemStack filler = item(Material.GRAY_STAINED_GLASS_PANE, "<dark_gray> ", List.of(), false);
        for (int s = 0; s < 36; s++) inv.setItem(s, filler);

        List<String> info = new ArrayList<>();
        for (String line : wrap(ev.getDescription(), 38)) info.add("<gray>" + line);
        info.add("");
        info.add("<gray>Category: <white>" + ev.getCategory().display);
        inv.setItem(4, item(EventStyles.icon(ev.getId()), ev.getColorTag() + "<bold>" + ev.getName(), info, false));

        // slider: a row of notches, filled up to the chosen duration
        int handle = -1;
        for (int i = 0; i < NOTCHES.length; i++) if (NOTCHES[i] <= d.duration) handle = i;
        for (int i = 0; i < NOTCHES.length; i++) {
            boolean filled = i <= handle;
            inv.setItem(SLIDER_START + i, item(
                    filled ? Material.LIME_STAINED_GLASS_PANE : Material.GRAY_STAINED_GLASS_PANE,
                    (filled ? "<green><bold>" : "<gray>") + fmt(NOTCHES[i]),
                    List.of("<gray>Click to set the duration to <white>" + fmt(NOTCHES[i])),
                    i == handle));
        }

        inv.setItem(SLOT_M10, item(Material.RED_DYE, "<red><bold>-10 minutes", List.of("<gray>Click to subtract 10 minutes"), false));
        inv.setItem(SLOT_M1, item(Material.ORANGE_DYE, "<gold><bold>-1 minute", List.of("<gray>Click to subtract 1 minute"), false));
        inv.setItem(SLOT_SHOW, item(Material.CLOCK, "<yellow><bold>Duration: <white>" + fmt(d.duration), List.of(
                "<gray>Default length: <white>" + fmt(Math.max(1, m.durationMinutes(ev)) * 60_000L),
                "<gray>Allowed: <white>10s to 24h",
                "",
                "<gray>Use the slider or the buttons to change it"), false));
        inv.setItem(SLOT_P1, item(Material.LIME_DYE, "<green><bold>+1 minute", List.of("<gray>Click to add 1 minute"), false));
        inv.setItem(SLOT_P10, item(Material.GREEN_DYE, "<green><bold>+10 minutes", List.of("<gray>Click to add 10 minutes"), false));

        inv.setItem(SLOT_BACK, item(Material.ARROW, "<yellow><bold>Back", List.of("<gray>Return to the event list"), false));
        inv.setItem(SLOT_START, item(Material.EMERALD_BLOCK, "<green><bold>Start " + ev.getName(), List.of(
                "<gray>Runs for <white>" + fmt(d.duration),
                m.isExtraArmed() ? "<gold>Extra mode is ON: runs alongside the current event."
                        : (m.activeCount() > 0 ? "<gray>Replaces the running event(s)." : "<gray>Nothing else is running."),
                "",
                "<yellow>Click to start"), false));
        inv.setItem(SLOT_EXTRA2, item(m.isExtraArmed() ? Material.LIME_DYE : Material.GRAY_DYE,
                "<gold><bold>Extra Event: " + (m.isExtraArmed() ? "<green>ON" : "<red>OFF"), List.of(
                        "<gray>When ON, this event runs alongside",
                        "<gray>the current one (max " + EventManager.MAX_ACTIVE + ").",
                        "",
                        "<yellow>Click to toggle"), m.isExtraArmed()));
    }

    private void handleDuration(InventoryClickEvent e, DurationHolder d) {
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getClickedInventory() == null || e.getClickedInventory() != e.getInventory()) return;
        if (!p.hasPermission("worldevents.admin")) {
            p.closeInventory();
            return;
        }
        EventManager m = plugin.manager();
        int slot = e.getSlot();

        if (slot >= SLIDER_START && slot < SLIDER_START + NOTCHES.length) {
            d.duration = NOTCHES[slot - SLIDER_START];
            renderDuration(d);
        } else if (slot == SLOT_M10 || slot == SLOT_M1 || slot == SLOT_P1 || slot == SLOT_P10) {
            long delta = switch (slot) {
                case SLOT_M10 -> -600_000L;
                case SLOT_M1 -> -60_000L;
                case SLOT_P1 -> 60_000L;
                default -> 600_000L;
            };
            d.duration = Math.max(EventManager.MIN_DURATION_MS, Math.min(EventManager.MAX_DURATION_MS, d.duration + delta));
            renderDuration(d);
        } else if (slot == SLOT_EXTRA2) {
            m.toggleExtra();
            renderDuration(d);
        } else if (slot == SLOT_BACK) {
            Bukkit.getScheduler().runTask(plugin, () -> open(p, d.returnPage));
        } else if (slot == SLOT_START) {
            EventManager.StartResult r = m.startManual(d.event, d.duration);
            plugin.getLogger().info(p.getName() + " started event " + d.event.getId() + " for " + fmt(d.duration)
                    + " from the menu (" + r + ").");
            Component msg = m.resultMessage(r, d.event);
            if (msg != null) p.sendMessage(msg);
            Bukkit.getScheduler().runTask(plugin, p::closeInventory);
        }
    }

    // ---------------------------------------------------------------- clicks
    @EventHandler
    public void onClick(InventoryClickEvent e) {
        InventoryHolder holder = e.getInventory().getHolder();
        if (holder instanceof DurationHolder d) {
            handleDuration(e, d);
            return;
        }
        if (!(holder instanceof Holder h)) return;
        e.setCancelled(true); // nothing can be taken out of or moved into the menu
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getClickedInventory() == null || e.getClickedInventory() != e.getInventory()) return;
        if (!p.hasPermission("worldevents.admin")) {
            p.closeInventory();
            return;
        }

        EventManager m = plugin.manager();
        int slot = e.getSlot();

        // page buttons just flip the page and keep the menu open
        if (slot == SLOT_PREV && h.page > 0) {
            h.page--;
            render(h);
            return;
        }
        if (slot == SLOT_NEXT && h.page < pages() - 1) {
            h.page++;
            render(h);
            return;
        }

        boolean acted = false;
        if (slot >= 0 && slot < PER_PAGE) {
            int idx = h.page * PER_PAGE + slot;
            List<WorldEvent> events = all();
            if (idx < events.size()) {
                WorldEvent ev = events.get(idx);
                if (e.getClick() == ClickType.RIGHT && m.isActive(ev)) {
                    acted = true;
                    m.stopEvent(ev);
                    plugin.getLogger().info(p.getName() + " stopped event " + ev.getId() + " from the menu.");
                    p.sendMessage(plugin.mm("{prefix}<gray>Stopped " + ev.getName() + "."));
                } else if (m.isActive(ev)) {
                    acted = true;
                    p.sendMessage(plugin.mm("{prefix}<red>" + ev.getName() + " is already running. Right-click it in the menu to stop it."));
                } else {
                    // open the duration menu instead of starting straight away
                    final int returnPage = h.page;
                    final long def = Math.max(1, m.durationMinutes(ev)) * 60_000L;
                    Bukkit.getScheduler().runTask(plugin, () -> openDuration(p, ev, def, returnPage));
                    return;
                }
            }
        } else if (slot == SLOT_STOP) {
            acted = true;
            m.stopAll();
            plugin.getLogger().info(p.getName() + " stopped all events from the menu.");
            p.sendMessage(plugin.mm("{prefix}<gray>All events stopped. Automatic events are paused."));
        } else if (slot == SLOT_AUTO) {
            acted = true;
            m.resumeAuto();
            plugin.getLogger().info(p.getName() + " resumed automatic events from the menu.");
            p.sendMessage(plugin.mm("{prefix}<gray>Automatic events resumed."));
        } else if (slot == SLOT_RANDOM) {
            acted = true;
            EventManager.StartResult r = m.startRandom();
            plugin.getLogger().info(p.getName() + " started a random event from the menu (" + r + ").");
            Component msg = m.resultMessage(r, null);
            if (msg != null) p.sendMessage(msg);
        } else if (slot == SLOT_EXTRA) {
            acted = true;
            boolean on = m.toggleExtra();
            p.sendMessage(plugin.mm(on
                    ? "{prefix}<gold>Extra mode ON: the next event you start runs alongside the current one."
                    : "{prefix}<gray>Extra mode OFF."));
        }

        if (acted) Bukkit.getScheduler().runTask(plugin, p::closeInventory);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        InventoryHolder holder = e.getInventory().getHolder();
        if (holder instanceof Holder || holder instanceof DurationHolder) e.setCancelled(true);
    }
}
