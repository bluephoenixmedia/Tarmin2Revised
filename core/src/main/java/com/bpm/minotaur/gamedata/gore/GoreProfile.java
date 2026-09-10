package com.bpm.minotaur.gamedata.gore;

import com.badlogic.gdx.graphics.Color;
import com.bpm.minotaur.gamedata.monster.Monster;

/**
 * Defines creature physiology and gore behavior for the 3D Viscera system.
 */
public enum GoreProfile {
    FLESH(
            new Color(0.77f, 0.12f, 0.12f, 1.0f), // Crimson blood
            true,  // hasBlood
            true,  // hasGibs
            true   // createsFloorStains
    ),
    SKELETAL(
            new Color(0.92f, 0.90f, 0.82f, 0.85f), // Bone dust / ivory
            false, // no blood
            true,  // bone fragments only
            false  // no liquid puddles
    ),
    SLIME(
            new Color(0.22f, 0.88f, 0.28f, 0.95f), // Acidic toxic green goo
            true,  // fluid spray
            false, // no meat chunks
            true   // slime puddles
    ),
    INCORPOREAL(
            new Color(0.45f, 0.55f, 0.85f, 0.60f), // Ethereal soul mist
            false, // no physical blood
            false, // no gibs
            false  // no stains
    );

    public final Color primaryColor;
    public final boolean hasBlood;
    public final boolean hasGibs;
    public final boolean createsFloorStains;

    GoreProfile(Color primaryColor, boolean hasBlood, boolean hasGibs, boolean createsFloorStains) {
        this.primaryColor = primaryColor;
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
