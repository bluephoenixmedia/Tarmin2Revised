package com.bpm.minotaur.gamedata.item;

/**
 * The kind of weapon something is, for Mastery (#224). Every weapon has exactly one home
 * Family; ammunition and magic are {@link #NONE} and train nothing.
 */
public enum WeaponFamily {
    BLADE, DAGGER, AXE, BLUNT, POLEARM, BOW, CROSSBOW, THROWN, FIREARM, UNARMED, NONE;

    /**
     * The Family a weapons.json entry names. The field is required there: a missing or unknown
     * name fails the load rather than quietly training nothing.
     */
    public static WeaponFamily require(String weaponId, String name) {
        if (name == null) {
            throw new IllegalStateException("weapons.json: " + weaponId + " has no weaponFamily");
        }
        try {
            return valueOf(name);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("weapons.json: " + weaponId + " has unknown weaponFamily " + name);
        }
    }

    /** The Family a template names; NONE when it names none or one that does not exist. */
    public static WeaponFamily parse(String name) {
        if (name == null) return NONE;
        try {
            return valueOf(name);
        } catch (IllegalArgumentException e) {
            return NONE;
        }
    }
}
