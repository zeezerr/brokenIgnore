package dev.worldevents.events;

import dev.worldevents.Category;
import dev.worldevents.WorldEvent;
import dev.worldevents.WorldEventsPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** Reward-based events: Mystery Gift, Jackpot and Supply Drop. */
public final class LootEvents {
    private LootEvents() {}

    // ------------------------------------------------------------------ Mystery Gift
    public static final class MysteryGift extends WorldEvent {
        private final Set<UUID> given = new HashSet<>();

        public MysteryGift() {
            super("mystery_gift", "Mystery Gift", Category.LOOT,
                    "Every player receives a random gift - once per event, even if they log in late.");
        }

        @Override
        protected void resetState() {
            given.clear();
        }

        @Override
        protected void loadState(ConfigurationSection s) {
            for (String u : s.getStringList("given")) {
                try { given.add(UUID.fromString(u)); } catch (IllegalArgumentException ignored) { }
            }
        }

        @Override
        public void saveState(ConfigurationSection s) {
            List<String> ids = new ArrayList<>();
            for (UUID u : given) ids.add(u.toString());
            s.set("given", ids);
        }

        @Override
        protected void onStart() {
            for (Player p : Bukkit.getOnlinePlayers()) gift(p);
        }

        @Override
        public void onJoin(Player p) {
            gift(p);
        }

        private void gift(Player p) {
            if (!given.add(p.getUniqueId())) return; // relogging never repeats the reward
            int rolls = Math.max(1, cfgI("rolls", 2));
            List<String> names = new ArrayList<>();
            for (int i = 0; i < rolls; i++) {
                ItemStack it = rollReward("gifts", List.of("DIAMOND:1-3", "EMERALD:3-8", "GOLDEN_APPLE:1-3",
                        "COOKED_BEEF:8-16", "IRON_INGOT:6-12", "EXPERIENCE_BOTTLE:8-16", "ENDER_PEARL:1-3"));
                if (it == null) continue;
                giveOrDrop(p, it);
                names.add(it.getAmount() + "x " + it.getType().name().toLowerCase().replace('_', ' '));
            }
            if (!names.isEmpty()) {
                p.sendMessage(Component.text("Mystery Gift! You received: " + String.join(", ", names), NamedTextColor.LIGHT_PURPLE));
            }
        }
    }

    // ------------------------------------------------------------------ Jackpot
    public static final class Jackpot extends WorldEvent {
        private boolean done;

        public Jackpot() {
            super("jackpot", "Jackpot", Category.LOOT,
                    "One randomly chosen online player wins a rare jackpot reward. Could it be you?");
        }

        @Override
        protected void resetState() {
            done = false;
        }

        @Override
        protected void loadState(ConfigurationSection s) {
            done = s.getBoolean("done", false);
        }

        @Override
        public void saveState(ConfigurationSection s) {
            s.set("done", done);
        }

        @Override
        protected void onStart() {
            if (!done) later(100L, this::draw); // short pause for suspense
        }

        @Override
        public void onJoin(Player p) {
            if (!done && Bukkit.getOnlinePlayers().size() == 1) draw();
        }

        private void draw() {
            if (done) return;
            List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
            if (players.isEmpty()) return;
            Player winner = players.get(ThreadLocalRandom.current().nextInt(players.size()));
            ItemStack prize = rollReward("rewards", List.of("DIAMOND_BLOCK:1-2", "NETHERITE_INGOT:1",
                    "ENCHANTED_GOLDEN_APPLE:1-2", "TOTEM_OF_UNDYING:1"));
            if (prize == null) return;
            done = true;
            giveOrDrop(winner, prize);
            Bukkit.broadcast(plugin.mm("{prefix}<gold><bold>JACKPOT!</bold> <yellow>" + winner.getName()
                    + "</yellow> <gray>won <white>" + prize.getAmount() + "x "
                    + prize.getType().name().toLowerCase().replace('_', ' ') + "</white>!"));
            plugin.getLogger().info("Jackpot winner: " + winner.getName() + " -> " + prize.getAmount() + "x " + prize.getType());
        }
    }

    // ------------------------------------------------------------------ Supply Drop
    public static final class SupplyDrop extends WorldEvent {
        private static final String TITLE = "Supply Drop";
        private Block chest;

        public SupplyDrop() {
            super("supply_drop", "Supply Drop", Category.LOOT,
                    "A supply chest lands somewhere in the Overworld and its coordinates are announced. First come, first served!");
        }

        @Override
        protected void onStart() {
            if (!tryPlace()) repeat(200L, 200L, () -> { if (chest == null) tryPlace(); });
            repeat(40L, 40L, this::beacon);
        }

        private boolean tryPlace() {
            List<Player> candidates = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getWorld().getEnvironment() == World.Environment.NORMAL) candidates.add(p);
            }
            if (candidates.isEmpty()) return false;
            ThreadLocalRandom r = ThreadLocalRandom.current();
            Player anchor = candidates.get(r.nextInt(candidates.size()));
            World w = anchor.getWorld();
            int min = Math.max(10, cfgI("min-distance", 60));
            int max = Math.max(min + 1, cfgI("max-distance", 160));

            for (int i = 0; i < 30; i++) {
                double ang = r.nextDouble() * Math.PI * 2;
                int d = r.nextInt(min, max + 1);
                int x = anchor.getLocation().getBlockX() + (int) (Math.cos(ang) * d);
                int z = anchor.getLocation().getBlockZ() + (int) (Math.sin(ang) * d);
                Block spot = WorldEnvEvents.safeSurface(w, x, z);
                if (spot == null) continue;

                spot.setType(Material.CHEST);
                BlockState st = spot.getState();
                if (!(st instanceof Chest c)) {
                    spot.setType(Material.AIR);
                    continue;
                }
                c.customName(Component.text(TITLE, NamedTextColor.GOLD));
                c.update();
                fill(c.getBlockInventory());
                chest = spot;
                remember(spot);

                plugin.broadcast(plugin.mm("{prefix}<gold><bold>Supply Drop!</bold> <gray>A chest landed near <yellow>X "
                        + spot.getX() + ", Z " + spot.getZ() + "</yellow> in the Overworld. Find it before anyone else!"));
                plugin.getLogger().info("Supply drop placed at " + w.getName() + " " + spot.getX() + " " + spot.getY() + " " + spot.getZ());
                return true;
            }
            return false;
        }

        private void fill(Inventory inv) {
            ThreadLocalRandom r = ThreadLocalRandom.current();
            Set<Integer> used = new HashSet<>();
            int stacks = Math.max(1, Math.min(inv.getSize(), cfgI("stacks", 6)));
            for (int i = 0; i < stacks; i++) {
                ItemStack it = rollReward("loot", List.of("DIAMOND:2-4", "GOLDEN_APPLE:2-5", "IRON_INGOT:8-16",
                        "GOLD_INGOT:6-12", "EMERALD:4-10", "COOKED_BEEF:16-32", "EXPERIENCE_BOTTLE:8-16",
                        "ARROW:16-32", "OBSIDIAN:4-8", "ENDER_PEARL:2-4"));
                if (it == null) continue;
                int slot;
                do { slot = r.nextInt(inv.getSize()); } while (!used.add(slot));
                inv.setItem(slot, it);
            }
        }

        private void beacon() {
            if (chest == null || chest.getType() != Material.CHEST) return;
            World w = chest.getWorld();
            Location base = chest.getLocation().add(0.5, 1.0, 0.5);
            for (int h = 0; h < 40; h += 2) {
                w.spawnParticle(Particle.END_ROD, base.getX(), base.getY() + h, base.getZ(), 1, 0.1, 0.1, 0.1, 0.0);
            }
        }

        @Override
        protected void onStop() {
            if (chest != null) {
                removeIfSupplyChest(chest);
                forget(chest);
                chest = null;
            }
        }

        // ---- leftovers cleanup (crash safety) ----
        private void remember(Block b) {
            List<String> list = plugin.data().getStringList("cleanup.chests");
            list.add(key(b));
            plugin.data().set("cleanup.chests", list);
            plugin.saveData();
        }

        private void forget(Block b) {
            List<String> list = plugin.data().getStringList("cleanup.chests");
            list.remove(key(b));
            plugin.data().set("cleanup.chests", list);
            plugin.saveData();
        }

        private static String key(Block b) {
            return b.getWorld().getName() + ";" + b.getX() + ";" + b.getY() + ";" + b.getZ();
        }

        private static boolean isSupplyChest(Block b) {
            if (b.getType() != Material.CHEST) return false;
            if (!(b.getState() instanceof Chest c) || c.customName() == null) return false;
            return TITLE.equals(PlainTextComponentSerializer.plainText().serialize(c.customName()));
        }

        private static void removeIfSupplyChest(Block b) {
            if (!isSupplyChest(b)) return;
            if (b.getState() instanceof Chest c) c.getBlockInventory().clear();
            b.setType(Material.AIR);
        }

        /** Removes chests left behind by a crash. Called once at startup. */
        public static void cleanupStale(WorldEventsPlugin plugin) {
            List<String> list = plugin.data().getStringList("cleanup.chests");
            if (list.isEmpty()) return;
            for (String s : list) {
                try {
                    String[] p = s.split(";");
                    World w = Bukkit.getWorld(p[0]);
                    if (w == null) continue;
                    removeIfSupplyChest(w.getBlockAt(Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3])));
                } catch (Exception ex) {
                    plugin.getLogger().warning("Could not clean stale supply chest entry: " + s);
                }
            }
            plugin.data().set("cleanup.chests", new ArrayList<String>());
            plugin.saveData();
        }
    }
}
