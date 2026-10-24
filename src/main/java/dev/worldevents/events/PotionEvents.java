package dev.worldevents.events;

import dev.worldevents.Category;
import dev.worldevents.WorldEvent;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/** Events that simply keep one or more potion effects on every online player. */
public final class PotionEvents {
    private PotionEvents() {}

    public record Eff(PotionEffectType type, int amplifier) {}

    private static final int DURATION = 400;      // ticks applied each refresh
    private static final int REFRESH_BELOW = 300; // refresh when this much or less remains

    public abstract static class PotionEvent extends WorldEvent {
        private final Eff[] effects;

        protected PotionEvent(String id, String name, Category c, String desc, Eff... effects) {
            super(id, name, c, desc);
            this.effects = effects;
        }

        @Override
        protected void onStart() {
            apply();
            repeat(20L, 20L, this::apply);
        }

        private void apply() {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getGameMode() == GameMode.SPECTATOR) continue;
                for (int i = 0; i < effects.length; i++) {
                    Eff e = effects[i];
                    int amp = i == 0 ? cfgI("amplifier", e.amplifier()) : e.amplifier();
                    PotionEffect cur = p.getPotionEffect(e.type());
                    if (cur != null) {
                        if (cur.isInfinite() || cur.getAmplifier() > amp) continue;
                        if (cur.getAmplifier() == amp && cur.getDuration() > REFRESH_BELOW) continue;
                    }
                    p.addPotionEffect(new PotionEffect(e.type(), DURATION, amp, true, false, true));
                }
            }
        }

        @Override
        protected void onStop() {
            for (Player p : Bukkit.getOnlinePlayers()) {
                for (Eff e : effects) {
                    PotionEffect cur = p.getPotionEffect(e.type());
                    // only remove effects that look like ours (ambient + short), not real potions
                    if (cur != null && cur.isAmbient() && !cur.isInfinite() && cur.getDuration() <= DURATION) {
                        p.removePotionEffect(e.type());
                    }
                }
            }
        }
    }

    public static final class GlowAndBehold extends PotionEvent {
        public GlowAndBehold() {
            super("glow_and_behold", "Glow & Behold", Category.PLAYER_EFFECTS,
                    "Everyone glows and can be seen through walls. Hiding is nearly impossible.",
                    new Eff(PotionEffectType.GLOWING, 0));
        }
    }

    public static final class SpeedRush extends PotionEvent {
        public SpeedRush() {
            super("speed_rush", "Speed Rush", Category.PLAYER_EFFECTS,
                    "Everyone moves much faster. Travel, explore and chase in record time.",
                    new Eff(PotionEffectType.SPEED, 1));
        }
    }

    public static final class HeavyFeet extends PotionEvent {
        public HeavyFeet() {
            super("heavy_feet", "Heavy Feet", Category.PLAYER_EFFECTS,
                    "Everyone moves slower than normal. Think carefully before you get into trouble.",
                    new Eff(PotionEffectType.SLOWNESS, 0));
        }
    }

    public static final class SuperJump extends PotionEvent {
        public SuperJump() {
            super("super_jump", "Super Jump", Category.PLAYER_EFFECTS,
                    "Everyone jumps much higher. Fall damage still applies, so watch the landing!",
                    new Eff(PotionEffectType.JUMP_BOOST, 2));
        }
    }

    public static final class LowGravity extends PotionEvent {
        public LowGravity() {
            super("low_gravity", "Low Gravity", Category.PLAYER_EFFECTS,
                    "Higher jumps and slow falling for everyone. (Slow falling also prevents fall damage.)",
                    new Eff(PotionEffectType.JUMP_BOOST, 1), new Eff(PotionEffectType.SLOW_FALLING, 0));
        }
    }

    public static final class NightVision extends PotionEvent {
        public NightVision() {
            super("night_vision", "Night Vision", Category.PLAYER_EFFECTS,
                    "Everyone can see clearly in caves and at night.",
                    new Eff(PotionEffectType.NIGHT_VISION, 0));
        }
    }

    public static final class ShoppingSpree extends PotionEvent {
        public ShoppingSpree() {
            super("shopping_spree", "Shopping Spree", Category.ECONOMY,
                    "Everyone gets Hero of the Village - villager trades are much cheaper.",
                    new Eff(PotionEffectType.HERO_OF_THE_VILLAGE, 1));
        }
    }

    public static final class RegenerationRush extends PotionEvent {
        public RegenerationRush() {
            super("regeneration_rush", "Regeneration Rush", Category.PLAYER_EFFECTS,
                    "Everyone gets a mild, constant Regeneration effect. Fights last longer.",
                    new Eff(PotionEffectType.REGENERATION, 0));
        }
    }

    public static final class CombatFrenzy extends PotionEvent {
        public CombatFrenzy() {
            super("combat_frenzy", "Combat Frenzy", Category.PVP,
                    "Everyone gets Strength, so melee attacks hit harder for the whole event.",
                    new Eff(PotionEffectType.STRENGTH, 0));
        }
    }

    public static final class FragileFighters extends PotionEvent {
        public FragileFighters() {
            super("fragile_fighters", "Fragile Fighters", Category.PVP,
                    "Everyone gets Weakness, so melee attacks deal less damage. Bows and careful play win the day.",
                    new Eff(PotionEffectType.WEAKNESS, 0));
        }
    }

    public static final class MiningRush extends PotionEvent {
        public MiningRush() {
            super("mining_rush", "Mining Rush", Category.MINING,
                    "Everyone gets Haste II. Mining and chopping are much faster.",
                    new Eff(PotionEffectType.HASTE, 1));
        }
    }

    public static final class ThickSkin extends PotionEvent {
        public ThickSkin() {
            super("thick_skin", "Thick Skin", Category.PLAYER_EFFECTS,
                    "Everyone gets Resistance, so all incoming damage is reduced.",
                    new Eff(PotionEffectType.RESISTANCE, 0));
        }
    }

    public static final class AquaticAdventure extends PotionEvent {
        public AquaticAdventure() {
            super("aquatic_adventure", "Aquatic Adventure", Category.PLAYER_EFFECTS,
                    "Everyone can breathe underwater and swim like a dolphin. Time to explore the oceans!",
                    new Eff(PotionEffectType.WATER_BREATHING, 0), new Eff(PotionEffectType.DOLPHINS_GRACE, 0));
        }
    }

    public static final class Fireproof extends PotionEvent {
        public Fireproof() {
            super("fireproof", "Fireproof", Category.PLAYER_EFFECTS,
                    "Everyone gets Fire Resistance. Fire and lava damage are blocked, so the Nether is a lot friendlier.",
                    new Eff(PotionEffectType.FIRE_RESISTANCE, 0));
        }
    }

    public static final class LuckyDay extends PotionEvent {
        public LuckyDay() {
            super("lucky_day", "Lucky Day", Category.LOOT,
                    "Everyone gets Luck IV, which improves fishing and loot-table rewards like chests.",
                    new Eff(PotionEffectType.LUCK, 3));
        }
    }
}
