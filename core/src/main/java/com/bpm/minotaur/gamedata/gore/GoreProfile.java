package com.bpm.minotaur.gamedata.gore;

import com.badlogic.gdx.graphics.Color;
import com.bpm.minotaur.gamedata.monster.Monster;

/**
 * Defines creature physiology and gore behavior for the 3D Viscera system.
 */
public enum GoreProfile {
    FLESH(
            new Color(0.77f, 0.12f, 0.12f, 1.0f), // Crimson blood
            new Color(0.65f, 0.06f, 0.06f, 1.0f), // Deep meat seam
            new Color(0.77f, 0.12f, 0.12f, 0.85f), // Wound tint
            true,  // hasBlood
            true,  // hasGibs
            true   // createsFloorStains
    ),
    SKELETAL(
            new Color(0.60f, 0.08f, 0.08f, 1.0f), // Dark necrotic blood & marrow
            new Color(0.50f, 0.06f, 0.06f, 1.0f), // Dark marrow cut seam
            new Color(0.60f, 0.08f, 0.08f, 0.90f), // Necrotic wound tint
            true,  // hasBlood (undead monsters have blood)
            true,  // bone fragments and skull/rib gibs
            true   // createsFloorStains
    ),
    SLIME(
            new Color(0.22f, 0.88f, 0.28f, 0.95f), // Acidic toxic green goo
            new Color(0.20f, 0.90f, 0.25f, 1.0f),  // Oozing slime seam
            new Color(0.18f, 0.80f, 0.22f, 0.90f), // Slime rupture tint
            true,  // fluid spray
            false, // no meat chunks
            true   // slime puddles
    ),
    INCORPOREAL(
            new Color(0.45f, 0.55f, 0.85f, 0.60f), // Ethereal soul mist
            new Color(0.50f, 0.40f, 0.90f, 0.70f), // Soul seam
            new Color(0.45f, 0.35f, 0.85f, 0.60f), // Spectral rip tint
            false, // no physical blood
            false, // no gibs
            false  // no stains
    );

    public final Color primaryColor;
    public final Color seamColor;
    public final Color woundColor;
    public final boolean hasBlood;
    public final boolean hasGibs;
    public final boolean createsFloorStains;

    GoreProfile(Color primaryColor, Color seamColor, Color woundColor, boolean hasBlood, boolean hasGibs, boolean createsFloorStains) {
        this.primaryColor = primaryColor;
        this.seamColor = seamColor;
        this.woundColor = woundColor;
        this.hasBlood = hasBlood;
        this.hasGibs = hasGibs;
        this.createsFloorStains = createsFloorStains;
    }

    /**
     * Resolves a Monster's physiology to a GoreProfile.
     */
    public static GoreProfile fromMonster(Monster monster) {
        if (monster == null) return FLESH;
        return fromMonsterType(monster.getType());
    }

    /**
     * Resolves MonsterType to a GoreProfile.
     */
    public static GoreProfile fromMonsterType(Monster.MonsterType type) {
        if (type == null) return FLESH;

        switch (type) {
            case SKELETON:
            case CLOAKED_SKELETON:
            case LICH:
                return SKELETAL;

            case GELATINOUS_CUBE:
                return SLIME;

            case WRAITH:
                return INCORPOREAL;

            default:
                return FLESH;
        }
    }
}
