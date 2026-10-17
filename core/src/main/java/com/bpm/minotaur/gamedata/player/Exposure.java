package com.bpm.minotaur.gamedata.player;

import com.bpm.minotaur.gamedata.liquid.LiquidType;
import com.bpm.minotaur.generation.Biome;
import com.bpm.minotaur.generation.Stratum;
import com.bpm.minotaur.weather.WeatherIntensity;
import com.bpm.minotaur.weather.WeatherType;

/**
 * Frostfall-style Exposure (#194, decided in #181): how cold the protagonist is. Body
 * temperature is the state; this class holds the rules that move it and what each band does.
 *
 * <p>The air, read from the surface biome, the weather and the hour or from the stratum
 * underground, is felt through clothing: "feels like" is the ambient plus the worn warmth.
 * Above {@link #COMFORT_FLOOR} the body holds 37 C on its own. Below it, heat is lost in
 * proportion to the shortfall, twice as fast when WET and three times when SOAKED. A field
 * fire or a shelter hearth warms past normal into Warm and dries the player out.
 *
 * <p>Pacing (#194 item 7): temperate and dry, cold never comes up. Cold or wet, a cloak or a
 * fire is needed within about 300 turns. Cold kills only through neglect: Hypothermic chips
 * 1 HP every {@link #HYPOTHERMIA_DAMAGE_EVERY} turns, and nothing else here does damage.
 */
public final class Exposure {

    private Exposure() {
    }

    // --- Bands ---------------------------------------------------------------------------

    /** Body temperature bands, hottest first. The heat side keeps the heatstroke rules. */
    public enum Band {
        HOT("Hot", 0f, false),
        WARM("Warm", 0f, false),
        COMFORTABLE("Comfortable", 0f, false),
        CHILLY("Chilly", 0f, false),
        COLD("Cold", 0.10f, false),
        FREEZING("Freezing", 0.20f, true),
        HYPOTHERMIC("Hypothermic", 0.20f, true);

        /** What the meter and the notices call it. */
        public final String label;
        /** Fraction taken off move and attack speed. */
        public final float speedPenalty;
        /** True when natural HP regeneration stops. */
        public final boolean blocksRegen;

        Band(String label, float speedPenalty, boolean blocksRegen) {
            this.label = label;
            this.speedPenalty = speedPenalty;
            this.blocksRegen = blocksRegen;
        }

        /** Cold, Freezing or Hypothermic: the bands that slow the player. */
        public boolean isCold() {
            return ordinal() >= COLD.ordinal();
        }
    }

    public static final float NORMAL = 37.0f;
    /** Where a fire or hearth holds the body: above normal, inside Warm, short of heatstroke. */
    public static final float WARM_TARGET = 37.5f;

    public static final float HOT_ABOVE = 38.0f;
    public static final float WARM_FROM = 37.25f;
    public static final float COMFORTABLE_FROM = 36.0f;
    public static final float CHILLY_FROM = 35.0f;
    public static final float COLD_FROM = 34.0f;
    public static final float FREEZING_FROM = 32.5f;

    /** The body never cools below this; Hypothermic is sustained, not instantly fatal. */
    public static final float BODY_FLOOR = 30.0f;
    public static final float BODY_CEILING = 41.0f;

    public static Band bandOf(float bodyTemp) {
        if (bodyTemp > HOT_ABOVE) return Band.HOT;
        if (bodyTemp >= WARM_FROM) return Band.WARM;
        if (bodyTemp >= COMFORTABLE_FROM) return Band.COMFORTABLE;
        if (bodyTemp >= CHILLY_FROM) return Band.CHILLY;
        if (bodyTemp >= COLD_FROM) return Band.COLD;
        if (bodyTemp >= FREEZING_FROM) return Band.FREEZING;
        return Band.HYPOTHERMIC;
    }

    /** Hypothermic: 1 HP every this many turns. */
    public static final int HYPOTHERMIA_DAMAGE_EVERY = 10;
    /** Hypothermic: chance per turn of shivering helplessly through one action. */
    public static final float HYPOTHERMIA_LOST_ACTION_CHANCE = 0.05f;

    // --- Wetness -------------------------------------------------------------------------

    /** Wetness on a 0..100 scale. */
    public static final float WETNESS_MAX = 100f;
    /** At or above this, WET. */
    public static final float WET_FROM = 10f;
    /** At or above this, SOAKED. */
    public static final float SOAKED_FROM = 60f;
    /** Where wading, and drinking slush, leave you: plainly WET. */
    public static final float WADING_WETNESS = 40f;
    /** Rain alone never soaks you through. */
    public static final float RAIN_WETNESS_CAP = SOAKED_FROM - 1f;

    /** Drying per turn in the open. SOAKED to dry takes roughly 500 turns. */
    public static final float DRY_RATE = 0.2f;
    /** Drying per turn beside a field fire or Molten Fire. */
    public static final float FIRE_DRY_RATE = 4f;
    /** Drying per turn at a shelter hearth. */
    public static final float HEARTH_DRY_RATE = 6f;

    /** Coverage never keeps out all the rain. */
    public static final float MAX_COVERAGE = 0.9f;

    public enum Wetness {
        DRY("Dry", 1f),
        WET("Wet", 2f),
        SOAKED("Soaked", 3f);

        public final String label;
        /** How much faster heat is lost. */
        public final float heatLossMult;

        Wetness(String label, float heatLossMult) {
            this.label = label;
            this.heatLossMult = heatLossMult;
        }
    }

    public static Wetness wetnessOf(float wetness) {
        if (wetness >= SOAKED_FROM) return Wetness.SOAKED;
        if (wetness >= WET_FROM) return Wetness.WET;
        return Wetness.DRY;
    }

    /** Wetness gained per turn outdoors in this weather, before coverage. */
    public static float rainPerTurn(WeatherType weather) {
        if (weather == null) return 0f;
        switch (weather) {
            case RAIN: return 0.8f;
            case STORM: return 1.5f;
            case TORNADO: return 1.0f;
            case SNOW: return 0.3f;
            case BLIZZARD: return 0.6f;
            default: return 0f;
        }
    }

    /**
     * One turn of wetness: rain (cut by coverage, never past {@link #RAIN_WETNESS_CAP}) less
     * drying, faster by a fire.
     */
    public static float wetnessAfter(float wetness, float rainPerTurn, float coverage, Heat heat, float time) {
        float cover = Math.max(0f, Math.min(MAX_COVERAGE, coverage));
        float w = wetness;
        float gain = rainPerTurn * (1f - cover) * time;
        if (gain > 0f && w < RAIN_WETNESS_CAP) {
            w = Math.min(RAIN_WETNESS_CAP, w + gain);
        }
        w -= dryRate(heat) * time;
        return Math.max(0f, Math.min(WETNESS_MAX, w));
    }

    static float dryRate(Heat heat) {
        if (heat == null) return DRY_RATE;
        switch (heat) {
            case HEARTH: return HEARTH_DRY_RATE;
            case FIELD_FIRE:
            case MOLTEN: return FIRE_DRY_RATE;
            default: return DRY_RATE;
        }
    }

    /** Wading leaves you at least WET. */
    public static float afterWading(float wetness) {
        return Math.max(wetness, WADING_WETNESS);
    }

    /** Freezing water, or falling in: SOAKED through. */
    public static float afterSoaking() {
        return WETNESS_MAX;
    }

    // --- Freezing water ------------------------------------------------------------------

    /** The sharp drop on stepping into freezing water. */
    public static final float FREEZING_WATER_PLUNGE = 1.5f;
    /** The drop from a draught of Freezing Slush. */
    public static final float SLUSH_DRINK_CHILL = 1.0f;

    /**
     * Freezing Slush always; plain water when the biome is cold (Tundra, Mountains) at night,
     * or whenever the air itself is at freezing.
     */
    public static boolean isFreezingWater(LiquidType liquid, Biome biome, boolean night, float ambient) {
        if (liquid == LiquidType.FREEZING_SLUSH) return true;
        if (liquid != LiquidType.WATER) return false;
        return (isColdBiome(biome) && night) || ambient <= 0f;
    }

    public static boolean isColdBiome(Biome biome) {
        return biome == Biome.TUNDRA || biome == Biome.MOUNTAINS;
    }

    /** Liquids you wade in and come out wet. Ice, quicksand and fire do not wet you. */
    public static boolean wets(LiquidType liquid) {
        return liquid == LiquidType.WATER || liquid == LiquidType.FREEZING_SLUSH
                || liquid == LiquidType.BLOOD || liquid == LiquidType.BLACK_MUCK;
    }

    // --- Clothing ------------------------------------------------------------------------

    /**
     * The rags the protagonist always wears (#181: he starts in rags, no armour). A little
     * warmth, no coverage. There is no rags item; it is the floor every outfit stands on.
     */
    public static final float RAGS_WARMTH = 2f;
    /** The Ring of Warmth's insulation, unchanged from before #194. */
    public static final float RING_OF_WARMTH = 20f;
    /** A lit carried lantern is a little warmth (#194 item 5). */
    public static final float LANTERN_WARMTH = 3f;

    // --- Heat sources --------------------------------------------------------------------

    /** The heat source the player stands by, strongest wins. */
    public enum Heat {
        NONE,
        /** A lit carried lantern: a little warmth. */
        LANTERN,
        /** A field fire or campfire within {@link #FIRE_RADIUS}: warms to Warm, dries fast. */
        FIELD_FIRE,
        /** A lit shelter's hearth: fully warm, dries fastest. */
        HEARTH,
        /** Beside Molten Fire: hot. */
        MOLTEN
    }

    /** Under a shelter's roof, lit or not, the air is at least this, and no rain falls. */
    public static final float SHELTER_AMBIENT_FLOOR = 14f;

    /** A fire warms, dries and cooks within this many tiles (Chebyshev). */
    public static final int FIRE_RADIUS = 2;
    /** Molten Fire is hot within this many tiles. */
    public static final int MOLTEN_RADIUS = 1;
    /** The air beside Molten Fire. */
    public static final float MOLTEN_AMBIENT = 45f;

    /** Degrees per turn a field fire warms toward {@link #WARM_TARGET}. */
    public static final float FIRE_WARM_RATE = 0.15f;
    /** Degrees per turn a hearth warms toward {@link #WARM_TARGET}. */
    public static final float HEARTH_WARM_RATE = 0.4f;

    // --- The body ------------------------------------------------------------------------

    /** Below this "feels like" temperature the body loses heat. */
    public static final float COMFORT_FLOOR = 15f;
    /** Heat lost per turn per degree of shortfall, dry. */
    public static final float LOSS_PER_DEGREE = 0.0008f;
    /** Above this ambient the body gains heat (heatstroke rules, unchanged). */
    public static final float HEAT_STRESS_ABOVE = 35f;
    public static final float HEAT_GAIN_PER_DEGREE = 0.005f;
    /** Homeostasis: degrees per turn back toward normal when nothing is pulling. */
    public static final float RECOVERY_RATE = 0.02f;

    /**
     * The body temperature after {@code time} turns.
     *
     * @param ambient the air, before clothing
     * @param warmth  worn insulation, rags included (see {@link #RAGS_WARMTH})
     */
    public static float step(float bodyTemp, float ambient, float warmth, Wetness wet, Heat heat, float time) {
        Heat h = heat == null ? Heat.NONE : heat;
        Wetness w = wet == null ? Wetness.DRY : wet;
        float t = bodyTemp;
        if (h == Heat.HEARTH || h == Heat.FIELD_FIRE) {
            float rate = h == Heat.HEARTH ? HEARTH_WARM_RATE : FIRE_WARM_RATE;
            t = t < WARM_TARGET ? Math.min(WARM_TARGET, t + rate * time) : approach(t, WARM_TARGET, RECOVERY_RATE * time);
            return clamp(t);
        }
        float air = h == Heat.MOLTEN ? Math.max(ambient, MOLTEN_AMBIENT) : ambient;
        float feels = air + warmth + (h == Heat.LANTERN ? LANTERN_WARMTH : 0f);
        if (air > HEAT_STRESS_ABOVE) {
            t += (air - HEAT_STRESS_ABOVE) * HEAT_GAIN_PER_DEGREE * time;
        } else if (feels < COMFORT_FLOOR) {
            t -= (COMFORT_FLOOR - feels) * LOSS_PER_DEGREE * w.heatLossMult * time;
        } else {
            t = approach(t, NORMAL, RECOVERY_RATE * time);
        }
        return clamp(t);
    }

    private static float approach(float value, float target, float step) {
        if (value < target) return Math.min(target, value + step);
        if (value > target) return Math.max(target, value - step);
        return value;
    }

    private static float clamp(float t) {
        return Math.max(BODY_FLOOR, Math.min(BODY_CEILING, t));
    }

    // --- Ambient temperature -------------------------------------------------------------

    /** How much colder the deepest night is than the day, every surface biome but the Desert. */
    public static final float NIGHT_DROP = 6f;

    /** A surface biome's daytime air, before weather. */
    public static float biomeBase(Biome biome) {
        if (biome == null) return 20f;
        switch (biome) {
            case LAKELANDS: return 16f;  // damp, chilly wetland
            case FOREST: return 20f;
            case MOUNTAINS: return 4f;   // cold even by day
            case DESERT: return 24f;     // dawn and dusk; see desertCurve
            case OCEAN: return 19f;
            case TUNDRA: return -12f;
            case BLIGHT: return 16f;
            default: return 20f;
        }
    }

    /** Sun elevation for a time of day: 0 at dawn (0.25) and dusk (0.75), 1 at noon, -1 at midnight. */
    public static float sunElevation(float timeOfDay) {
        return (float) Math.sin((timeOfDay - 0.25f) * 2.0f * Math.PI);
    }

    /**
     * The surface air: biome, hour and weather. Every biome is colder at night; the Desert keeps
     * its own burning-day, cold-night curve. Rain and storms are colder.
     */
    public static float surfaceAmbient(Biome biome, WeatherType weather, WeatherIntensity intensity, float timeOfDay) {
        if (biome == Biome.MAZE) {
            return 20f; // the insulated maze chunks: no sky to speak of
        }
        float sun = sunElevation(timeOfDay);
        float t;
        if (biome == Biome.DESERT) {
            t = sun > 0f ? 24f + 18f * sun : 24f + 10f * sun;
        } else {
            t = biomeBase(biome) - NIGHT_DROP * Math.max(0f, -sun);
        }
        if (weather == null) return t;
        switch (weather) {
            case CLEAR:
                t += (biome == Biome.DESERT && intensity == WeatherIntensity.EXTREME) ? 6f : 2f;
                break;
            case FOG:
                t -= 1f;
                break;
            case RAIN:
                t -= 5f;
                break;
            case STORM:
                t -= 6f;
                break;
            case TORNADO:
                t -= 4f;
                break;
            case SNOW:
                t = Math.min(t, biome == Biome.TUNDRA ? -18f : biome == Biome.MOUNTAINS ? -2f : 1f);
                break;
            case BLIZZARD:
                float b = biome == Biome.TUNDRA ? -26f : biome == Biome.MOUNTAINS ? -16f : -10f;
                if (intensity == WeatherIntensity.HEAVY) b -= 3f;
                else if (intensity == WeatherIntensity.EXTREME) b -= 6f;
                t = Math.min(t, b);
                break;
            default:
                break;
        }
        if (weather == WeatherType.RAIN || weather == WeatherType.STORM) {
            if (intensity == WeatherIntensity.HEAVY || intensity == WeatherIntensity.EXTREME) t -= 1f;
        }
        return t;
    }

    /**
     * The air of a stratum (#194 item 2), in place of the flat 20 C the strata used to share.
     * Gash interiors follow their look: forges and pyres warm, the drowned gullet cold.
     */
    public static float strataAmbient(Stratum stratum) {
        if (stratum == null) return 18f;
        switch (stratum) {
            case MAZE: return 18f;            // temperate
            case FUNGAL_FOREST: return 18f;   // mild
            case OSSUARY: return 12f;         // cool
            case FLOODED_HALLS: return 6f;    // cold and wet
            case MAGMA_DEEPS: return 36f;     // hot
            case HOOK_FOUNDRY:
            case PYRE_CLOISTERS:
            case OBSIDIAN_RIFT: return 26f;
            case GLASS_GULLET: return 8f;
            default: return 15f;
        }
    }
}
