package dev.worldevents;

import dev.worldevents.events.LootEvents;
import dev.worldevents.events.PotionEvents;
import dev.worldevents.events.ResourceEvents;
import dev.worldevents.events.RuleEvents;
import dev.worldevents.events.WorldEnvEvents;

import org.bukkit.Material;
import org.bukkit.Tag;

import java.util.ArrayList;
import java.util.List;

/** Add new events here. Each event is its own class extending WorldEvent. */
public final class EventRegistry {
    private EventRegistry() {}

    public static List<WorldEvent> createAll() {
        List<WorldEvent> l = new ArrayList<>();

        // Player effects
        l.add(new PotionEvents.GlowAndBehold());
        l.add(new PotionEvents.SpeedRush());
        l.add(new PotionEvents.HeavyFeet());
        l.add(new PotionEvents.SuperJump());
        l.add(new PotionEvents.LowGravity());
        l.add(new PotionEvents.NightVision());
        l.add(new PotionEvents.ShoppingSpree());
        l.add(new PotionEvents.RegenerationRush());

        l.add(new PotionEvents.CombatFrenzy());
        l.add(new PotionEvents.FragileFighters());
        l.add(new PotionEvents.MiningRush());
        l.add(new PotionEvents.ThickSkin());
        l.add(new PotionEvents.AquaticAdventure());
        l.add(new PotionEvents.Fireproof());
        l.add(new PotionEvents.LuckyDay());

        // Rules / PvP / utility
        l.add(new RuleEvents.FogOfWar());
        l.add(new RuleEvents.KnockbackMadness());
        l.add(new RuleEvents.BowFrenzy());
        l.add(new RuleEvents.LastStand());
        l.add(new RuleEvents.FireworksFestival());
        l.add(new RuleEvents.UltraHardcore());
        l.add(new RuleEvents.Shieldless());
        l.add(new RuleEvents.Starvation());
        l.add(new RuleEvents.Paranoia());
        l.add(new RuleEvents.NoEscape());
        l.add(new RuleEvents.HotbarHavoc());
        l.add(new RuleEvents.Anonymous());
        l.add(new RuleEvents.PvpDamage("double_damage", "Double Damage", "Melee attacks between players deal extra damage. Mobs and the environment are unaffected.", 1.5));
        l.add(new RuleEvents.PvpDamage("reduced_damage", "Reduced Damage", "Melee attacks between players deal less damage, allowing longer, more tactical fights.", 0.5));
        l.add(new RuleEvents.PvpProtection());
        l.add(new RuleEvents.BountyHour());

        // Mining / farming / resources
        l.add(new ResourceEvents.MinersHaven());
        l.add(new ResourceEvents.DiamondFever());
        l.add(new ResourceEvents.LuckyOres());
        l.add(new ResourceEvents.LumberjacksDream());
        l.add(new ResourceEvents.FarmingFrenzy());
        l.add(new ResourceEvents.ForbiddenFruit());
        l.add(new ResourceEvents.XpRush());
        l.add(new ResourceEvents.MobLootMania());
        l.add(new ResourceEvents.TreasureFishing());

        l.add(new ResourceEvents.RichHarvest());
        l.add(new ResourceEvents.MobTreasure());
        l.add(new ResourceEvents.OreBonus("coal_rush", "Coal Rush", Category.MINING,
                "Coal ore has a chance to drop extra coal.", m -> Tag.COAL_ORES.isTagged(m), Material.COAL, 0.5));
        l.add(new ResourceEvents.OreBonus("emerald_rush", "Emerald Rush", Category.MINING,
                "Emerald ore has a chance to drop extra emeralds.", m -> Tag.EMERALD_ORES.isTagged(m), Material.EMERALD, 0.35));
        l.add(new ResourceEvents.OreBonus("golden_hour", "Golden Hour", Category.MINING,
                "Gold ore has a chance to drop extra raw gold. Player-placed ore doesn't count.",
                m -> Tag.GOLD_ORES.isTagged(m), Material.RAW_GOLD, 0.4));
        l.add(new ResourceEvents.OreBonus("gravel_gamble", "Gravel Gamble", Category.MINING,
                "Breaking gravel has a chance to drop an extra flint, great for stocking up on arrows.",
                m -> m == Material.GRAVEL, Material.FLINT, 0.5));

        // World
        l.add(new WorldEnvEvents.BloodMoon());
        l.add(new WorldEnvEvents.TimeLock("eternal_night", "Eternal Night", "The Overworld stays at night for the whole event. The normal day cycle resumes afterwards.", 18000L));
        l.add(new WorldEnvEvents.TimeLock("eternal_day", "Eternal Day", "The Overworld stays at midday for the whole event. The normal day cycle resumes afterwards.", 6000L));
        l.add(new WorldEnvEvents.MobInvasion());
        l.add(new WorldEnvEvents.CreeperSeason());
        l.add(new WorldEnvEvents.MobSeason("spider_season", "Spider Season",
                "Spiders spawn far more often in the Overworld. Watch the cave ceilings!", org.bukkit.entity.EntityType.SPIDER, 0.5, 30));
        l.add(new WorldEnvEvents.MobSeason("enderman_migration", "Enderman Migration",
                "Endermen turn up far more often in the Overworld. Don't look them in the eye!", org.bukkit.entity.EntityType.ENDERMAN, 0.25, 15));
        l.add(new WorldEnvEvents.PeacefulHour());
        l.add(new WorldEnvEvents.TreasureRain());
        l.add(new WorldEnvEvents.PulseEffect(true));
        l.add(new WorldEnvEvents.PulseEffect(false));

        // Loot
        l.add(new LootEvents.MysteryGift());
        l.add(new LootEvents.Jackpot());
        l.add(new LootEvents.SupplyDrop());

        for (WorldEvent ev : l) EventStyles.apply(ev);
        return l;
    }
}
