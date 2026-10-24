package dev.worldevents;

import net.kyori.adventure.bossbar.BossBar;

import org.bukkit.Material;

import java.util.HashMap;
import java.util.Map;

/** Colour (MiniMessage tag), boss bar colour and short on-screen tagline for each event. */
public final class EventStyles {
    private EventStyles() {}

    private record Style(String color, BossBar.Color bar, String tagline) {}

    private static final Map<String, Style> STYLES = new HashMap<>();

    private static void put(String id, String color, BossBar.Color bar, String tagline) {
        STYLES.put(id, new Style(color, bar, tagline));
    }

    static {
        BossBar.Color Y = BossBar.Color.YELLOW, R = BossBar.Color.RED, G = BossBar.Color.GREEN,
                B = BossBar.Color.BLUE, P = BossBar.Color.PURPLE, K = BossBar.Color.PINK, W = BossBar.Color.WHITE;

        // player effects
        put("glow_and_behold", "<yellow>", Y, "Everyone glows through walls");
        put("speed_rush", "<aqua>", B, "Everyone moves much faster");
        put("heavy_feet", "<gray>", W, "Everyone moves slower");
        put("super_jump", "<green>", G, "Everyone jumps much higher");
        put("low_gravity", "<light_purple>", K, "Jump high, fall slowly");
        put("night_vision", "<#5b6ee1>", B, "See clearly in the dark");
        put("shopping_spree", "<dark_green>", G, "Villager trades are cheaper");
        put("regeneration_rush", "<#ff6b81>", K, "Constant mild regeneration");
        put("combat_frenzy", "<red>", R, "Strength for everyone");
        put("fragile_fighters", "<#9aa0a6>", W, "Everyone deals less damage");
        put("mining_rush", "<#f4c430>", Y, "Dig and chop much faster");
        put("thick_skin", "<#cd7f32>", Y, "Everyone takes less damage");
        put("aquatic_adventure", "<#00bfff>", B, "Swim like a dolphin");
        put("fireproof", "<#ff7f27>", R, "Fire and lava can't hurt you");
        put("lucky_day", "<#00e676>", G, "Better luck on loot and fishing");

        // rules / pvp / utility
        put("ultra_hardcore", "<dark_red>", R, "No natural health regeneration");
        put("shieldless", "<#a0a0a0>", W, "Shields can't be used");
        put("starvation", "<#c47f2c>", Y, "Hunger drains much faster");
        put("paranoia", "<dark_purple>", P, "Did you hear that?");
        put("no_escape", "<dark_aqua>", B, "Ender pearls don't work");
        put("hotbar_havoc", "<gold>", Y, "Your hotbar keeps shuffling");
        put("anonymous", "<#6e6e6e>", W, "Everyone's name is hidden");
        put("double_damage", "<red>", R, "Player melee hits hurt more");
        put("reduced_damage", "<#7ec8e3>", B, "Player melee hits hurt less");
        put("pvp_protection", "<green>", G, "No player-vs-player damage");
        put("bounty_hour", "<gold>", Y, "Kill players for rewards");
        put("fog_of_war", "<#4b4b6b>", P, "Darkness creeps in and out");
        put("knockback_madness", "<#ff6347>", R, "Hits send players flying");
        put("bow_frenzy", "<#d2b48c>", Y, "Arrows hit harder");
        put("last_stand", "<#ff1744>", R, "Low health? Fight back!");
        put("fireworks_festival", "<#ff69b4>", K, "Fireworks fill the sky");

        // resources
        put("miners_haven", "<#c0c0c0>", W, "Ores drop double");
        put("diamond_fever", "<aqua>", B, "Diamond ore can drop extra");
        put("lucky_ores", "<#2ecc71>", G, "Ores can drop bonus resources");
        put("lumberjacks_dream", "<#a0522d>", Y, "Logs drop extra wood");
        put("farming_frenzy", "<#9acd32>", G, "Crops can drop double");
        put("forbidden_fruit", "<gold>", Y, "Oak leaves drop golden apples");
        put("xp_rush", "<#7fff00>", G, "Double experience");
        put("mob_loot_mania", "<dark_green>", G, "Hostile mobs drop double loot");
        put("treasure_fishing", "<#1e90ff>", B, "Fishing finds bonus treasure");
        put("coal_rush", "<#555555>", W, "Coal ore drops extra coal");
        put("emerald_rush", "<#17dd62>", G, "Emerald ore drops extra emeralds");
        put("golden_hour", "<#ffd700>", Y, "Gold ore drops extra gold");
        put("gravel_gamble", "<#9e9e9e>", W, "Gravel drops extra flint");
        put("rich_harvest", "<#f5c542>", Y, "Harvests can drop golden food");
        put("mob_treasure", "<#2e8b57>", G, "Hostile mobs drop bonus loot");

        // world
        put("blood_moon", "<dark_red>", R, "Endless night, stronger mobs");
        put("eternal_night", "<#3a3aa8>", B, "It's night, all night");
        put("eternal_day", "<yellow>", Y, "It's day, all day");
        put("mob_invasion", "<red>", R, "Far more hostile mobs");
        put("creeper_season", "<green>", G, "Creepers everywhere");
        put("spider_season", "<#a52a2a>", R, "Spiders everywhere");
        put("enderman_migration", "<#8a2be2>", P, "Endermen are on the move");
        put("peaceful_hour", "<#98fb98>", G, "No natural hostile spawns");
        put("treasure_rain", "<gold>", Y, "Treasure appears around you");
        put("world_blessing", "<#fff2a8>", Y, "Random positive effects");
        put("world_curse", "<dark_purple>", P, "Random negative effects");

        // loot
        put("mystery_gift", "<light_purple>", K, "Everyone gets a free gift");
        put("jackpot", "<gold>", Y, "One lucky winner");
        put("supply_drop", "<#e67e22>", Y, "A supply chest has landed");
    }

    private static final Map<String, String> ICONS = new HashMap<>();

    static {
        String[][] icons = {
                {"glow_and_behold", "SPECTRAL_ARROW"}, {"speed_rush", "SUGAR"}, {"heavy_feet", "ANVIL"},
                {"super_jump", "SLIME_BALL"}, {"low_gravity", "FEATHER"}, {"night_vision", "SPYGLASS"},
                {"shopping_spree", "EMERALD"}, {"regeneration_rush", "GHAST_TEAR"}, {"combat_frenzy", "IRON_SWORD"},
                {"fragile_fighters", "STICK"}, {"mining_rush", "GOLDEN_PICKAXE"}, {"thick_skin", "IRON_CHESTPLATE"},
                {"aquatic_adventure", "HEART_OF_THE_SEA"}, {"fireproof", "MAGMA_CREAM"}, {"lucky_day", "RABBIT_FOOT"},
                {"ultra_hardcore", "GOLDEN_APPLE"}, {"shieldless", "SHIELD"}, {"starvation", "COOKED_BEEF"},
                {"paranoia", "WITHER_SKELETON_SKULL"}, {"no_escape", "ENDER_PEARL"}, {"hotbar_havoc", "HOPPER"},
                {"anonymous", "PLAYER_HEAD"}, {"double_damage", "DIAMOND_SWORD"}, {"reduced_damage", "WOODEN_SWORD"},
                {"pvp_protection", "TURTLE_HELMET"}, {"bounty_hour", "GOLD_INGOT"}, {"fog_of_war", "GRAY_STAINED_GLASS"},
                {"knockback_madness", "PISTON"}, {"bow_frenzy", "BOW"}, {"last_stand", "TOTEM_OF_UNDYING"},
                {"fireworks_festival", "FIREWORK_ROCKET"}, {"miners_haven", "IRON_PICKAXE"}, {"diamond_fever", "DIAMOND"},
                {"lucky_ores", "GOLD_ORE"}, {"lumberjacks_dream", "IRON_AXE"}, {"farming_frenzy", "WHEAT"},
                {"forbidden_fruit", "OAK_LEAVES"}, {"xp_rush", "EXPERIENCE_BOTTLE"}, {"mob_loot_mania", "BONE"},
                {"treasure_fishing", "FISHING_ROD"}, {"coal_rush", "COAL"}, {"emerald_rush", "EMERALD_ORE"},
                {"golden_hour", "RAW_GOLD"}, {"gravel_gamble", "FLINT"}, {"rich_harvest", "GOLDEN_CARROT"},
                {"mob_treasure", "ENDER_CHEST"}, {"blood_moon", "REDSTONE"}, {"eternal_night", "COAL_BLOCK"},
                {"eternal_day", "SUNFLOWER"}, {"mob_invasion", "ZOMBIE_HEAD"}, {"creeper_season", "CREEPER_HEAD"},
                {"spider_season", "SPIDER_EYE"}, {"enderman_migration", "ENDER_EYE"}, {"peaceful_hour", "WHITE_TULIP"},
                {"treasure_rain", "AMETHYST_SHARD"}, {"world_blessing", "NETHER_STAR"}, {"world_curse", "WITHER_ROSE"},
                {"mystery_gift", "CHEST"}, {"jackpot", "DIAMOND_BLOCK"}, {"supply_drop", "BARREL"}
        };
        for (String[] i : icons) ICONS.put(i[0], i[1]);
    }

    /** Menu icon for an event (falls back to paper if a material name is ever missing). */
    public static Material icon(String id) {
        Material m = Material.matchMaterial(ICONS.getOrDefault(id, "PAPER"));
        return m != null ? m : Material.PAPER;
    }

    public static void apply(WorldEvent ev) {
        Style s = STYLES.get(ev.getId());
        if (s != null) ev.setStyle(s.color(), s.bar(), s.tagline());
    }
}
