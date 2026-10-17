package com.bpm.minotaur.ui;

import com.bpm.minotaur.gamedata.DamageType;
import com.bpm.minotaur.gamedata.monster.Monster;

/**
 * The words for War vs Spiritual affinity, shared by the combat log and the HUD's monster card.
 *
 * <p>One place, so every damage path says it the same way: the log line used to appear only on a
 * plain hit, which meant a crit, a glance, a combo or a spell on a resistant monster told the
 * player nothing about why the number was what it was (#172).
 */
public final class AffinityText {

    private AffinityText() {
    }

    /** "War" for physical damage, "Spiritual" for spiritual, otherwise the damage type's own name. */
    public static String schoolOf(DamageType type) {
        if (type == null) return "";
        if (type == DamageType.PHYSICAL) return "War";
        if (type == DamageType.SPIRITUAL) return "Spiritual";
        return UiNames.of(type);
    }

    /** What the player calls this monster: its own name if it has one, else its type's. */
    public static String nameOf(Monster monster) {
        if (monster == null) return "";
        if (monster.getDisplayName() != null && !monster.getDisplayName().isEmpty()) {
            return UiGlyphs.sanitize(monster.getDisplayName());
        }
        return UiNames.of(monster.getType());
    }

    /**
     * The log line for one hit, or null when the monster takes this damage normally.
     *
     * <p>A critical hit pierces resistance (see {@link Monster#takeDamage(int, DamageType, boolean)}),
     * so a crit on a resistant monster says so instead of claiming it was resisted.
     */
    public static String hitLine(String monsterName, Monster.Affinity affinity, DamageType type, boolean crit) {
        if (affinity == null || affinity == Monster.Affinity.NEUTRAL || type == null) return null;
        String school = schoolOf(type);
        String line;
        if (affinity == Monster.Affinity.RESISTANT) {
            line = crit
                    ? "Critical hit pierces the " + monsterName + "'s resistance to " + school + "!"
                    : monsterName + " resists " + school + " attacks!";
        } else {
            line = monsterName + " is weak to " + school + "!";
        }
        return UiGlyphs.sanitize(line);
    }

    /** {@link #hitLine(String, Monster.Affinity, DamageType, boolean)} for a monster in hand. */
    public static String hitLine(Monster monster, DamageType type, boolean crit) {
        if (monster == null || type == null) return null;
        return hitLine(nameOf(monster), monster.getAffinity(type), type, crit);
    }

    /**
     * The badge for a monster category: "Resists War / Weak to Spiritual", and so on.
     * Horrible monsters take both alike.
     */
    public static String badge(Monster.Category category) {
        if (category == null) return "";
        switch (category) {
            case BAD:
                return "Resists War / Weak to Spiritual";
            case NASTY:
                return "Resists Spiritual / Weak to War";
            case HORRIBLE:
            default:
                return "No resistance";
        }
    }

    /** {@link #badge(Monster.Category)} plus any extra weakness this one monster carries. */
    public static String badge(Monster monster) {
        if (monster == null || monster.getType() == null) return "";
        String base = badge(monster.getType().getCategory());
        DamageType extra = monster.getExtraWeakness();
        if (extra == null || extra == DamageType.PHYSICAL || extra == DamageType.SPIRITUAL) {
            return base;
        }
        String weak = "Weak to " + schoolOf(extra);
        return UiGlyphs.sanitize(base.equals("No resistance") ? weak : base + " / " + weak);
    }
}
