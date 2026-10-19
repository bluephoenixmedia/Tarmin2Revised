package com.bpm.minotaur.gamedata.arts;

import com.bpm.minotaur.gamedata.item.WeaponFamily;
import com.bpm.minotaur.gamedata.player.Mastery.Rank;

/**
 * The thirty Combat Arts: three per Weapon Family, taught at Adept, Expert and Master (#227, #228;
 * docs/FEatures/combat-arts-spec.md). The numbers are starting values, tuned in the duel simulator.
 *
 * <p>An Adept Art is a Passive that fires during ordinary attacks; an Expert Art is an Active on a
 * cooldown; a Master Art is a capstone that makes the Adept Passive certain for a few turns.
 */
public enum CombatArt {
    // Blade
    ANSWERING_STEEL(WeaponFamily.BLADE, Rank.ADEPT, 0.30f, "A melee attack that misses you may draw a free counter-cut."),
    CLOSING_THRUST(WeaponFamily.BLADE, Rank.EXPERT, 0f, "Step in and strike a foe two tiles ahead, at +2 to-hit."),
    WHIRLING_EDGE(WeaponFamily.BLADE, Rank.MASTER, 0f, "For 5 turns, every missed blow at you is answered."),
    // Dagger
    CUT_THE_SINEW(WeaponFamily.DAGGER, Rank.ADEPT, 0.25f, "A full hit may make the target bleed and slow it."),
    GUTTING_STAB(WeaponFamily.DAGGER, Rank.EXPERT, 0f, "A stab that deals more for every status on the target."),
    RED_HARVEST(WeaponFamily.DAGGER, Rank.MASTER, 0f, "For 5 turns, every full hit cuts the sinew."),
    // Axe
    WIDE_CHOP(WeaponFamily.AXE, Rank.ADEPT, 0.30f, "A full hit may also strike every other foe beside you."),
    HEW_THROUGH(WeaponFamily.AXE, Rank.EXPERT, 0f, "A heavy chop that strips armour and draws blood."),
    BUTCHERS_FRENZY(WeaponFamily.AXE, Rank.MASTER, 0f, "For 5 turns, every full hit chops wide."),
    // Blunt
    RINGING_BLOW(WeaponFamily.BLUNT, Rank.ADEPT, 0.25f, "A full hit may daze; a dazed foe is stunned instead."),
    BATTERING_RAM(WeaponFamily.BLUNT, Rank.EXPERT, 0f, "Knock a foe back a tile and daze it; into a wall, stun it."),
    TOLL_THE_BELL(WeaponFamily.BLUNT, Rank.MASTER, 0f, "For 5 turns, every full hit rings."),
    // Polearm
    HOLD_THE_LINE(WeaponFamily.POLEARM, Rank.ADEPT, 1f, "The first time a foe steps next to you, you strike first."),
    REEL_IN(WeaponFamily.POLEARM, Rank.EXPERT, 0f, "Pull a foe from two tiles into reach and daze it."),
    THICKET_OF_POINTS(WeaponFamily.POLEARM, Rank.MASTER, 0f, "For 5 turns, every foe that closes is struck first."),
    // Unarmed
    QUICK_KNUCKLES(WeaponFamily.UNARMED, Rank.ADEPT, 0.30f, "A full hit may land a second, lighter blow."),
    IRON_GRIP(WeaponFamily.UNARMED, Rank.EXPERT, 0f, "Seize a foe: it cannot move and fights at -2 for 3 turns."),
    STORM_OF_FISTS(WeaponFamily.UNARMED, Rank.MASTER, 0f, "For 5 turns, every full hit lands a second blow."),
    // Bow
    SWIFT_NOCK(WeaponFamily.BOW, Rank.ADEPT, 0.25f, "A full hit may let you loose a second arrow at once."),
    HELD_ARROW(WeaponFamily.BOW, Rank.EXPERT, 0f, "Hold one arrow drawn for the first foe to move or strike in your line."),
    RAIN_OF_SHAFTS(WeaponFamily.BOW, Rank.MASTER, 0f, "For 5 turns, every full hit looses a second arrow."),
    // Crossbow
    STAGGERING_BOLT(WeaponFamily.CROSSBOW, Rank.ADEPT, 0.25f, "A full hit may daze the target."),
    NAIL_IN_PLACE(WeaponFamily.CROSSBOW, Rank.EXPERT, 0f, "A bolt that pins its target in place for 3 turns."),
    SIEGE_RHYTHM(WeaponFamily.CROSSBOW, Rank.MASTER, 0f, "For 5 turns, every full hit dazes."),
    // Firearm
    PRACTISED_HANDS(WeaponFamily.FIREARM, Rank.ADEPT, 0.25f, "A full hit may shorten your next reload by a turn."),
    MARKED_SKULL(WeaponFamily.FIREARM, Rank.EXPERT, 0f, "Aim for a turn; the shot that follows takes the head."),
    POWDER_FURY(WeaponFamily.FIREARM, Rank.MASTER, 0f, "For 5 turns, every full hit hastens the reload."),
    // Thrown
    TANGLE(WeaponFamily.THROWN, Rank.ADEPT, 0.25f, "A thrown full hit may slow the target."),
    SHATTERING_THROW(WeaponFamily.THROWN, Rank.EXPERT, 0f, "A throw that bursts on everything beside its target, you included."),
    HAIL_OF_IRON(WeaponFamily.THROWN, Rank.MASTER, 0f, "For 5 turns, every thrown full hit slows.");

    /** Passive, Active or capstone: fixed by the rank that teaches the Art. */
    public enum Kind { PASSIVE, ACTIVE, CAPSTONE }

    /** Starting cooldowns (#227): an Expert Active 15 turns, a capstone 50. */
    public static final int ACTIVE_COOLDOWN = 15;
    public static final int CAPSTONE_COOLDOWN = 50;
    /** How long a capstone keeps its Family's Adept Passive certain. */
    public static final int CAPSTONE_TURNS = 5;

    public final WeaponFamily family;
    public final Rank rank;
    public final Kind kind;
    /** A Passive's chance to fire on a full hit at the rank that taught it; 0 for the rest. */
    public final float baseChance;
    public final int cooldownTurns;
    public final int durationTurns;
    /** The one line the Art-learned card shows. */
    public final String summary;

    CombatArt(WeaponFamily family, Rank rank, float baseChance, String summary) {
        this.family = family;
        this.rank = rank;
        this.kind = rank == Rank.ADEPT ? Kind.PASSIVE : rank == Rank.EXPERT ? Kind.ACTIVE : Kind.CAPSTONE;
        this.baseChance = baseChance;
        this.cooldownTurns = kind == Kind.ACTIVE ? ACTIVE_COOLDOWN : kind == Kind.CAPSTONE ? CAPSTONE_COOLDOWN : 0;
        this.durationTurns = kind == Kind.CAPSTONE ? CAPSTONE_TURNS : 0;
        this.summary = summary;
    }

    /** The Art {@code family} teaches at {@code rank}, or null: Novice and below teach nothing. */
    public static CombatArt of(WeaponFamily family, Rank rank) {
        for (CombatArt art : values()) {
            if (art.family == family && art.rank == rank) return art;
        }
        return null;
    }
}
