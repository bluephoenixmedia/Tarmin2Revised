package com.bpm.minotaur.gamedata.monster;

import com.bpm.minotaur.gamedata.DamageType;

public class AttackDefinition {
    public String attackType; // e.g., "CLAW", "BITE", "GAZE", "BREATH"
    public DamageType damageType; // e.g., PHYSICAL, FIRE, COLD
    public String damageRoll; // e.g., "1d6", "2d8+1" - parsed at runtime or pre-parsed
    public float chance; // Multiplier or percentage chance to use this attack? Or weight?
                         // For now, let's assume it's part of a sequence or weighted choice.

    // Additional effects can be added here, akin to AD_FIRE, AD_CONF in NetHack
    public String effect; // e.g., "POISON", "CONFUSE"

    public AttackDefinition() {
    }

    public AttackDefinition(String attackType, DamageType damageType, String damageRoll) {
        this.attackType = attackType;
        this.damageType = damageType;
        this.damageRoll = damageRoll;
    }
}
