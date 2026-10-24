package dev.worldevents.events;

import dev.worldevents.Category;
import dev.worldevents.WorldEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerExpChangeEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.function.Predicate;
import java.util.concurrent.ThreadLocalRandom;

/** Mining, farming, XP and mob-loot events. All bonuses skip creative mode and player-placed blocks. */
public final class ResourceEvents {
    private ResourceEvents() {}

    private static boolean roll(double chance) {
        return ThreadLocalRandom.current().nextDouble() < chance;
    }

    private static Location center(Block b) {
        return b.getLocation().add(0.5, 0.5, 0.5);
    }

    // ------------------------------------------------------------------ Miner's Haven
    public static final class MinersHaven extends WorldEvent {
        public MinersHaven() {
            super("miners_haven", "Miner's Haven", Category.MINING,
                    "Ores drop double. Silk Touch and player-placed ores don't count, so there's no duplication trick.");
        }

        @Override
        public void onBlockDrops(BlockDropItemEvent e) {
            Material m = e.getBlockState().getType();
            if (!isOre(m)) return;
            if (!eligibleBreak(e.getPlayer(), e.getBlock(), true, cfgB("allow-with-fortune", false))) return;
            multiplyDrops(e, 2);
        }
    }

    // ------------------------------------------------------------------ Diamond Fever
    public static final class DiamondFever extends WorldEvent {
        public DiamondFever() {
            super("diamond_fever", "Diamond Fever", Category.MINING,
                    "Diamond ore has a chance to drop an extra diamond.");
        }

        @Override
        public void onBlockBreak(BlockBreakEvent e) {
            Block b = e.getBlock();
            if (!Tag.DIAMOND_ORES.isTagged(b.getType()) || !e.isDropItems()) return;
            if (!eligibleBreak(e.getPlayer(), b, true, cfgB("allow-with-fortune", false))) return;
            if (!roll(cfgD("chance", 0.15))) return;
            b.getWorld().dropItemNaturally(center(b), new ItemStack(Material.DIAMOND));
            e.getPlayer().sendActionBar(Component.text("Diamond Fever! +1 diamond", NamedTextColor.AQUA));
        }
    }

    // ------------------------------------------------------------------ Lucky Ores
    public static final class LuckyOres extends WorldEvent {
        public LuckyOres() {
            super("lucky_ores", "Lucky Ores", Category.MINING,
                    "Mining ores has a chance to produce a random bonus resource.");
        }

        @Override
        public void onBlockBreak(BlockBreakEvent e) {
            Block b = e.getBlock();
            if (!isOre(b.getType()) || !e.isDropItems()) return;
            if (!eligibleBreak(e.getPlayer(), b, true, cfgB("allow-with-fortune", true))) return;
            if (!roll(cfgD("chance", 0.08))) return;
            ItemStack bonus = rollReward("bonus-rewards",
                    List.of("DIAMOND:1", "EMERALD:1-2", "GOLD_INGOT:2-4", "IRON_INGOT:3-6", "LAPIS_LAZULI:4-8"));
            if (bonus == null) return;
            b.getWorld().dropItemNaturally(center(b), bonus);
            e.getPlayer().sendActionBar(Component.text("Lucky Ores! Bonus drop!", NamedTextColor.GOLD));
        }
    }

    // ------------------------------------------------------------------ Lumberjack's Dream
    public static final class LumberjacksDream extends WorldEvent {
        public LumberjacksDream() {
            super("lumberjacks_dream", "Lumberjack's Dream", Category.MINING,
                    "Natural logs drop extra wood. Logs you placed yourself don't count.");
        }

        @Override
        public void onBlockDrops(BlockDropItemEvent e) {
            if (!Tag.LOGS.isTagged(e.getBlockState().getType())) return;
            if (!eligibleBreak(e.getPlayer(), e.getBlock(), false, true)) return;
            multiplyDrops(e, Math.max(1, cfgI("multiplier", 2)));
        }
    }

    // ------------------------------------------------------------------ Farming Frenzy
    public static final class FarmingFrenzy extends WorldEvent {
        public FarmingFrenzy() {
            super("farming_frenzy", "Farming Frenzy", Category.FARMING,
                    "Fully grown crops have a chance to drop double when harvested.");
        }

        @Override
        public void onBlockDrops(BlockDropItemEvent e) {
            Material m = e.getBlockState().getType();
            if (m != Material.WHEAT && m != Material.CARROTS && m != Material.POTATOES
                    && m != Material.BEETROOTS && m != Material.NETHER_WART) return;
            if (!(e.getBlockState().getBlockData() instanceof Ageable a) || a.getAge() < a.getMaximumAge()) return;
            if (!eligibleBreak(e.getPlayer(), e.getBlock(), false, true)) return;
            if (roll(cfgD("chance", 0.5))) multiplyDrops(e, 2);
        }
    }

    // ------------------------------------------------------------------ Forbidden Fruit
    public static final class ForbiddenFruit extends WorldEvent {
        public ForbiddenFruit() {
            super("forbidden_fruit", "Forbidden Fruit", Category.FARMING,
                    "Breaking oak and dark oak leaves has a chance to drop a golden apple.");
        }

        @Override
        public void onBlockBreak(BlockBreakEvent e) {
            Material m = e.getBlock().getType();
            if (m != Material.OAK_LEAVES && m != Material.DARK_OAK_LEAVES) return;
            if (!e.isDropItems()) return;
            if (!eligibleBreak(e.getPlayer(), e.getBlock(), false, true)) return; // Fortune never boosts this
            if (!roll(cfgD("chance", 0.05))) return;
            e.getBlock().getWorld().dropItemNaturally(center(e.getBlock()), new ItemStack(Material.GOLDEN_APPLE));
        }
    }

    // ------------------------------------------------------------------ XP Rush
    public static final class XpRush extends WorldEvent {
        public XpRush() {
            super("xp_rush", "XP Rush", Category.UTILITY,
                    "All experience you collect is doubled.");
        }

        @Override
        public void onExp(PlayerExpChangeEvent e) {
            int amount = e.getAmount();
            if (amount <= 0) return;
            e.setAmount((int) Math.max(1, Math.round(amount * cfgD("multiplier", 2.0))));
        }
    }

    // ------------------------------------------------------------------ Mob Loot Mania
    public static final class MobLootMania extends WorldEvent {
        public MobLootMania() {
            super("mob_loot_mania", "Mob Loot Mania", Category.LOOT,
                    "Hostile mobs drop double loot. Mobs from spawners and event spawns are excluded.");
        }

        @Override
        public void onMobDeath(EntityDeathEvent e) {
            Entity en = e.getEntity();
            if (!(en instanceof Monster)) return;
            if (plugin.isSpawnerMob(en)) return;
            if (cfgB("require-player-kill", true) && e.getEntity().getKiller() == null) return;
            int mult = Math.max(1, cfgI("multiplier", 2));
            for (ItemStack drop : e.getDrops()) {
                if (drop == null) continue;
                drop.setAmount(Math.min(drop.getMaxStackSize(), drop.getAmount() * mult));
            }
        }
    }

    // ------------------------------------------------------------------ Treasure Fishing
    public static final class TreasureFishing extends WorldEvent {
        public TreasureFishing() {
            super("treasure_fishing", "Treasure Fishing", Category.LOOT,
                    "Every catch has a chance to come with a bonus treasure.");
        }

        @Override
        public void onFish(PlayerFishEvent e) {
            if (e.getState() != PlayerFishEvent.State.CAUGHT_FISH) return;
            if (!roll(cfgD("chance", 0.35))) return;
            ItemStack bonus = rollReward("rewards", List.of("GOLD_INGOT:2-5", "EMERALD:1-3", "DIAMOND:1",
                    "NAME_TAG:1", "SADDLE:1", "EXPERIENCE_BOTTLE:2-4", "NAUTILUS_SHELL:1"));
            if (bonus == null) return;
            Player p = e.getPlayer();
            giveOrDrop(p, bonus);
            p.sendActionBar(Component.text("Treasure Fishing! You found " + bonus.getAmount() + "x "
                    + bonus.getType().name().toLowerCase().replace('_', ' '), NamedTextColor.AQUA));
        }
    }

    // ------------------------------------------------------------------ Generic "bonus drop from a block" event
    public static final class OreBonus extends WorldEvent {
        private final Predicate<Material> blocks;
        private final Material bonus;
        private final double defaultChance;

        public OreBonus(String id, String name, Category category, String desc,
                        Predicate<Material> blocks, Material bonus, double defaultChance) {
            super(id, name, category, desc);
            this.blocks = blocks;
            this.bonus = bonus;
            this.defaultChance = defaultChance;
        }

        @Override
        public void onBlockBreak(BlockBreakEvent e) {
            Block b = e.getBlock();
            if (!e.isDropItems() || !blocks.test(b.getType())) return;
            if (!eligibleBreak(e.getPlayer(), b, true, cfgB("allow-with-fortune", false))) return;
            if (!roll(cfgD("chance", defaultChance))) return;
            int amount = Math.max(1, cfgI("amount", 1));
            b.getWorld().dropItemNaturally(center(b), new ItemStack(bonus, amount));
            e.getPlayer().sendActionBar(Component.text(getName() + "! Bonus drop!", NamedTextColor.GOLD));
        }
    }

    // ------------------------------------------------------------------ Rich Harvest
    public static final class RichHarvest extends WorldEvent {
        public RichHarvest() {
            super("rich_harvest", "Rich Harvest", Category.FARMING,
                    "Harvesting fully grown crops has a small chance to drop bonus golden food and other treats.");
        }

        @Override
        public void onBlockBreak(BlockBreakEvent e) {
            Block b = e.getBlock();
            Material m = b.getType();
            if (m != Material.WHEAT && m != Material.CARROTS && m != Material.POTATOES
                    && m != Material.BEETROOTS && m != Material.NETHER_WART) return;
            if (!e.isDropItems()) return;
            if (!(b.getBlockData() instanceof Ageable a) || a.getAge() < a.getMaximumAge()) return;
            if (!eligibleBreak(e.getPlayer(), b, false, true)) return;
            if (!roll(cfgD("chance", 0.12))) return;
            ItemStack bonus = rollReward("rewards",
                    List.of("GOLDEN_CARROT:1-2", "GOLDEN_APPLE:1", "BREAD:2-3", "APPLE:2-4", "COOKED_BEEF:2-4"));
            if (bonus != null) b.getWorld().dropItemNaturally(center(b), bonus);
        }
    }

    // ------------------------------------------------------------------ Mob Treasure
    public static final class MobTreasure extends WorldEvent {
        public MobTreasure() {
            super("mob_treasure", "Mob Treasure", Category.LOOT,
                    "Hostile mobs killed by players have a chance to drop bonus loot. Spawner mobs are excluded.");
        }

        @Override
        public void onMobDeath(EntityDeathEvent e) {
            Entity en = e.getEntity();
            if (!(en instanceof Monster) || plugin.isSpawnerMob(en) || e.getEntity().getKiller() == null) return;
            if (!roll(cfgD("chance", 0.25))) return;
            ItemStack bonus = rollReward("loot", List.of("EXPERIENCE_BOTTLE:1-3", "GOLDEN_CARROT:1-2",
                    "IRON_NUGGET:3-6", "GOLD_NUGGET:3-6", "COOKED_BEEF:2-4", "EMERALD:1"));
            if (bonus != null) e.getDrops().add(bonus);
        }
    }
}
