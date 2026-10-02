package com.bpm.minotaur.gamedata.monster;

/**
 * Who can be charmed, for how long, and how many the player can hold. Pure rules: the spell engine
 * rolls Magic Resistance and applies the result.
 *
 * <p>There are four spells. Charm Person is short and weak; the two Dominate spells are the
 * ordinary timed charm, one for beasts and one for people; Dominate Monster is permanent.
 */
public final class CharmRules {

    /** Duration marker for an ally that never reverts. */
    public static final int PERMANENT = -1;

    public enum Kind {
        CHARM_PERSON, DOMINATE_BEAST, DOMINATE_PERSON, DOMINATE_MONSTER;

        /** The kind a spell id casts, or null if it is not a charm spell. */
        public static Kind forSpell(String spellId) {
            if (spellId == null) {
                return null;
            }
            try {
                return valueOf(spellId.toUpperCase(java.util.Locale.ROOT));
            } catch (IllegalArgumentException e) {
                return null;
            }
        }

        public boolean isPermanent() {
            return this == DOMINATE_MONSTER;
        }
    }

    /** Turns of a Charm Person, before the level bonus. */
    static final int CHARM_PERSON_TURNS = 40;
    /** Turns of Dominate Beast / Dominate Person, before the level bonus. */
    static final int DOMINATE_TURNS = 120;
    static final int TURNS_PER_CASTER_LEVEL = 5;

    private CharmRules() {
    }

    /** Turns the charm lasts, or {@link #PERMANENT}. */
    public static int duration(Kind kind, int casterLevel) {
        int level = Math.max(1, casterLevel);
        switch (kind) {
            case CHARM_PERSON:    return CHARM_PERSON_TURNS + TURNS_PER_CASTER_LEVEL * level;
            case DOMINATE_BEAST:
            case DOMINATE_PERSON: return DOMINATE_TURNS + TURNS_PER_CASTER_LEVEL * level;
            default:              return PERMANENT;
        }
    }

    /** How many permanent allies the player can hold: Charisma modifier plus one, at least one. */
    public static int permanentCap(int charismaModifier) {
        return Math.max(1, charismaModifier + 1);
    }

    /**
     * Why this monster cannot be charmed by this spell, or null if it can. The text finishes the
     * sentence "The X cannot be charmed: ...".
     */
    public static String refusal(Kind kind, Monster target, int casterLevel) {
        if (target.isAlly()) {
            return "it already follows you";
        }
        if (target.isBridgeBoss()) {
            return "it is beyond your will";
        }
        if (!kind.isPermanent() && isMindless(target)) {
            return "there is no mind there to move";
        }
        switch (kind) {
            case CHARM_PERSON:
                if (target.getLevel() > Math.max(1, casterLevel)) {
                    return "it is too strong for a mere charm";
                }
                break;
            case DOMINATE_BEAST:
                if (target.getFamily() != MonsterFamily.BEAST) {
                    return "it is not a beast";
                }
                break;
            case DOMINATE_PERSON:
                if (target.getFamily() != MonsterFamily.HUMANOID) {
                    return "it is not a person";
                }
                break;
            default:
                break;
        }
        return null;
    }

    /** Undead, constructs and mindless things: a mind spell has nothing to take hold of. */
    public static boolean isMindless(Monster target) {
        if (target.getFamily() == MonsterFamily.UNDEAD) {
            return true;
        }
        String name = target.getType().name();
        return name.contains("GOLEM") || name.contains("SLIME") || name.contains("OOZE")
                || name.contains("JELLY") || name.contains("PUDDING") || name.contains("MIMIC");
    }
}
