package com.bpm.minotaur.rendering.animation;

import com.badlogic.gdx.math.MathUtils;
import com.bpm.minotaur.gamedata.item.Item;

import java.util.HashMap;
import java.util.Map;

/**
 * Registry of expanded, thematic special moves mapped across weapon animation archetypes.
 * Used for dynamic combat toast notifications, floating damage indicators, and combat logs.
 */
public class SpecialMoveRegistry {

    private static final Map<AnimationArchetype, String[]> REGULAR_MOVES = new HashMap<>();
    private static final Map<AnimationArchetype, String[]> FINISHER_MOVES = new HashMap<>();
    private static final Map<AnimationArchetype, String[]> CRIT_MOVES = new HashMap<>();

    static {
        // 1. SLASHING_1H
        REGULAR_MOVES.put(AnimationArchetype.SLASHING_1H, new String[]{
                "DIAGONAL CLEAVE", "RISING BACKSLASH", "CRESCENT CARVE", "FEINT AND SLASH",
                "HORIZON CUT", "SIDESTEP GASH", "SWIFT POMMEL STRIKE", "SABRE RIPOSTE", "FLURRY CUT"
        });
        FINISHER_MOVES.put(AnimationArchetype.SLASHING_1H, new String[]{
                "EXECUTIONER'S SLAM", "BLADE DANCE", "CRIMSON CRESCENT", "SEVERING VORTEX",
                "WHIRLWIND CUT", "BLOODFEAST SLASH", "EVISCERATING RIP", "TEMPEST FLURRY"
        });
        CRIT_MOVES.put(AnimationArchetype.SLASHING_1H, new String[]{
                "JUGULAR SEVER", "HEART CARVER", "PHANTOM DRAKE CUT", "SUNDERING ARC", "PERFECT RIPOSTE"
        });

        // 2. SLASHING_2H
        REGULAR_MOVES.put(AnimationArchetype.SLASHING_2H, new String[]{
                "COLOSSAL SWEEP", "CRESTING LOP", "LOW SWEEP", "HEAVY CLEAVE",
                "GRAVITY DROP", "OVERHEAD REAP", "IRON BIND", "SUNDERING SWEEP"
        });
        FINISHER_MOVES.put(AnimationArchetype.SLASHING_2H, new String[]{
                "TITAN'S CLEAVE", "SUNDERING HEFT", "EARTH BREAKER", "COLOSSAL SPLIT",
                "OBLIVION CHOP", "CATACLYSM SWING", "HEAVEN'S FALL", "ABYSSAL CLEAVE"
        });
        CRIT_MOVES.put(AnimationArchetype.SLASHING_2H, new String[]{
                "SPINE SEVER", "MOUNTAIN CRUSHER", "WORLD DIVIDER", "DECAPITATING CLEAVE", "EXTINCTION EDGE"
        });

        // 3. AXE_CHOPPING
        REGULAR_MOVES.put(AnimationArchetype.AXE_CHOPPING, new String[]{
                "OVERHEAD CHOP", "DIAGONAL HACK", "HOOKING CLEAVE", "WOODSMAN'S NOTCH",
                "TIMBER HEW", "BRUTAL LOP", "SIDE CHOP", "HEAVY FELL"
        });
        FINISHER_MOVES.put(AnimationArchetype.AXE_CHOPPING, new String[]{
                "CLEAVING HOOK", "SPLITTING MAUL", "BERSERKER LOP", "SKULL SPLITTER",
                "GORE HOOK", "SAVAGE LOP", "DECAPITATION BIND", "TIMBER FELLER"
        });
        CRIT_MOVES.put(AnimationArchetype.AXE_CHOPPING, new String[]{
                "RIB SPLITTER", "VERTEBRAE SNAP", "BLOODAXE EXECUTION", "CARNAGE CLEAVE", "BEHEADING GORE"
        });

        // 4. BLUNT_CRUSHING
        REGULAR_MOVES.put(AnimationArchetype.BLUNT_CRUSHING, new String[]{
                "HEAVY BATTER", "HORIZONTAL CRUSH", "RISING KINETIC THUMP", "ANKLE SMASH",
                "SHIELD CRUSH", "CONCUSSION TAP", "WEIGHTED SLAM", "KINETIC HAMMER"
        });
        FINISHER_MOVES.put(AnimationArchetype.BLUNT_CRUSHING, new String[]{
                "SKULL CRUSHER", "CONCUSSIVE SLAM", "BONE SHATTER", "BRAIN STUN",
                "PULVERIZING DROP", "METEORIC SMASH", "IRON QUAKE", "ANVIL DROP"
        });
        CRIT_MOVES.put(AnimationArchetype.BLUNT_CRUSHING, new String[]{
                "CRANIAL COLLAPSE", "MARROW SHATTER", "OBLITERATING SMASH", "THUNDER HAMMER", "SEISMIC IMPACT"
        });

        // 5. POLEARM_SWEEP
        REGULAR_MOVES.put(AnimationArchetype.POLEARM_SWEEP, new String[]{
                "WIDE REAP", "OVERHEAD HEFT", "SHAFT LEVERAGE", "GROUND SWEEP",
                "CIRCULAR DEFLECTION", "PROBING THRUST", "HOOKING TRIP", "REACHING JAB"
        });
        FINISHER_MOVES.put(AnimationArchetype.POLEARM_SWEEP, new String[]{
                "VAULTING THRUST", "HALBERD GUILLOTINE", "SWEEPING CRESCENT", "SERPENT STRIKE",
                "REACHING GORE", "VORTEX SWEEP", "SKEWERING CHARGE", "DRAGON'S TOOTH"
        });
        CRIT_MOVES.put(AnimationArchetype.POLEARM_SWEEP, new String[]{
                "PINPOINT EYE PIERCE", "NECK RUPTURE", "THORAX SKEWER", "IMPALING SKEWER", "VALKYRIE HARVEST"
        });

        // 6. FLAIL_WHIP
        REGULAR_MOVES.put(AnimationArchetype.FLAIL_WHIP, new String[]{
                "MOMENTUM SWIRL", "SIDE SNAP", "ORBITING ARC", "WHIPCRACK FLASH",
                "ENTANGLING COIL", "MACE SPIN", "RICOCHET BEAT", "CYCLONE WHIP"
        });
        FINISHER_MOVES.put(AnimationArchetype.FLAIL_WHIP, new String[]{
                "OVERHEAD CRUSH", "METEOR WRAP", "SCOURGE OF BONES", "THUNDER SNAP",
                "CHAIN WHIPLASH", "FLAIL OF WRATH", "CONSTRICTING BIND", "SPINED CAROUSEL"
        });
        CRIT_MOVES.put(AnimationArchetype.FLAIL_WHIP, new String[]{
                "COIL THROAT CRUSH", "SPINED WRAP", "BONECRACKER CRUNCH", "CENTRIFUGAL SHATTER", "VIPER BIND"
        });

        // 7. THRUSTING_PIERCE
        REGULAR_MOVES.put(AnimationArchetype.THRUSTING_PIERCE, new String[]{
                "LUNGING STAB", "RISING PUNCTURE", "THROAT PIERCE", "KIDNEY PIERCE",
                "PROBING DART", "SWIFT POINT", "FENCER'S TOUCH", "RAPID NEEDLE"
        });
        FINISHER_MOVES.put(AnimationArchetype.THRUSTING_PIERCE, new String[]{
                "HEARTSEEKER FLURRY", "IMPALING SKEWER", "LUNGING PUNCTURE", "THROAT SLIT",
                "VIPER FANG", "ORGAN TEAR", "STILETTO DRILL", "SHADOW PRICK"
        });
        CRIT_MOVES.put(AnimationArchetype.THRUSTING_PIERCE, new String[]{
                "AORTIC PIERCE", "BRAIN STEM PUNCTURE", "ASSASSIN'S EMBRACE", "LETHAL NEEDLE", "EYE SOCKET PLUNGE"
        });

        // 8. BRAWLING
        REGULAR_MOVES.put(AnimationArchetype.BRAWLING, new String[]{
                "LEFT JAB", "RIGHT CROSS", "LEFT HOOK", "LEAD BODY BLOW",
                "KINETIC PALM", "ELBOW SMASH", "SNAP JAB", "SOLAR PLEXUS STRIKE"
        });
        FINISHER_MOVES.put(AnimationArchetype.BRAWLING, new String[]{
                "HAYMAKER UPPERCUT", "TEMPLE BREAKER", "LIVER BLOW", "KNEE CRUSH",
                "SEISMIC SLAM", "DRAGON JAW", "BRUTAL HOOK", "IRON KNUCKLE"
        });
        CRIT_MOVES.put(AnimationArchetype.BRAWLING, new String[]{
                "JAW SHATTER", "SPINAL SHOCK", "STERNUM COLLAPSE", "KNOCKOUT DROP", "THUNDER PUNCH"
        });

        // 9. RANGED_BOW
        REGULAR_MOVES.put(AnimationArchetype.RANGED_BOW, new String[]{
                "PRECISION RELEASE", "SNAPSHOT", "WING DRAW", "CORRIDOR VOLLEY",
                "QUICK DRAW", "ARCHER'S SIGHT", "POINT BLANK RELEASE"
        });
        FINISHER_MOVES.put(AnimationArchetype.RANGED_BOW, new String[]{
                "HEART SHOT", "EYE PIERCE", "DEADEYE VOLLEY", "PINNING DRAW",
                "SPLITTING SHAFTS", "SILENT ARROW", "PIERCING GALE"
        });
        CRIT_MOVES.put(AnimationArchetype.RANGED_BOW, new String[]{
                "THROAT ARROW", "BULLSEYE CRIT", "PIERCING SHAFT", "LUNG PUNCTURE", "LETHAL FLIGHT"
        });

        // 10. RANGED_FIREARM
        REGULAR_MOVES.put(AnimationArchetype.RANGED_FIREARM, new String[]{
                "POWDER BLAST", "SMOKING CRACK", "LEAD VOLLEY", "FLASH SHOT",
                "POINT BLANK CRACK", "SULPHUR DISCHARGE"
        });
        FINISHER_MOVES.put(AnimationArchetype.RANGED_FIREARM, new String[]{
                "POINT BLANK RUPTURE", "THUNDER SHOT", "LEAD CONCUSSION", "DEVASTATING VOLLEY",
                "GUNPOWDER HAVOC", "FLASH CONCUSSION"
        });
        CRIT_MOVES.put(AnimationArchetype.RANGED_FIREARM, new String[]{
                "GORE EXPLOSION", "POWDER CONCUSSION", "CRANIAL SHATTER", "OBLITERATING BALL", "MUZZLE FLASH CRIT"
        });

        // 11. SHIELD
        REGULAR_MOVES.put(AnimationArchetype.SHIELD, new String[]{
                "SHIELD BASH", "EDGE STRIKE", "DEFLECTING PUSH", "HEAVY COVER",
                "RIM CRUSH", "IRON CHECK"
        });
        FINISHER_MOVES.put(AnimationArchetype.SHIELD, new String[]{
                "SHIELD SLAM", "IRON WALL", "BULL CHARGE", "AEGIS IMPACT",
                "BULWARK CRUSH", "TITAN'S WARD"
        });
        CRIT_MOVES.put(AnimationArchetype.SHIELD, new String[]{
                "CONCUSSION FLATTEN", "FACE BREAKER", "FORTRESS DROP", "RAMPART SLAM", "SPARTAN CLEAVE"
        });
    }

    public static String resolveMoveName(CombatMotionProfile profile, Item weapon, boolean isCrit) {
        AnimationArchetype arch = (profile != null) ? AnimationArchetype.fromItem(weapon) : AnimationArchetype.SLASHING_1H;
        if (profile != null && profile.isShieldBash) {
            arch = AnimationArchetype.SHIELD;
        }

        if (isCrit) {
            String[] pool = CRIT_MOVES.get(arch);
            if (pool != null && pool.length > 0) {
                return pool[MathUtils.random(pool.length - 1)];
            }
        }

        if (profile != null && profile.isFinisher) {
            String[] pool = FINISHER_MOVES.get(arch);
            if (pool != null && pool.length > 0) {
                return pool[MathUtils.random(pool.length - 1)];
            }
        }

        if (profile != null && profile.comboName != null && !profile.comboName.trim().isEmpty() && !profile.comboName.equals("STRIKE")) {
            String[] pool = REGULAR_MOVES.get(arch);
            if (pool != null && pool.length > 0) {
                return pool[MathUtils.random(pool.length - 1)];
            }
            return profile.comboName;
        }

        String[] pool = REGULAR_MOVES.get(arch);
        if (pool != null && pool.length > 0) {
            return pool[MathUtils.random(pool.length - 1)];
        }

        return "STRIKE";
    }

    public static String[] getMovesForArchetype(AnimationArchetype archetype) {
        return REGULAR_MOVES.getOrDefault(archetype, new String[0]);
    }

    public static String[] getFinishersForArchetype(AnimationArchetype archetype) {
        return FINISHER_MOVES.getOrDefault(archetype, new String[0]);
    }

    public static String[] getCritsForArchetype(AnimationArchetype archetype) {
        return CRIT_MOVES.getOrDefault(archetype, new String[0]);
    }
}
