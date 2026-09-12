package com.bpm.minotaur.gamedata.spells;

/**
 * LibGDX-deserializable template for spells loaded from assets/data/spells.json.
 */
public class SpellTemplate {
    public String id;
    public String name;
    public int level;
    public String school;
    public int mpCost;
    public int range = 1;
    public String targetType = "PROJECTILE"; // SELF, PROJECTILE, BURST, CONE, TOUCH, BEAM
    public String damageDice = "1d8";
    public String damageType = "FORCE";
    public String domain = "SPIRITUAL";
    public String duration = "Instantaneous";
    public String statusEffect;
    public String visualArchetype = "PROJECTILE"; // PROJECTILE, BURST, BEAM, SELF_BUFF, MELEE_TOUCH
    public String description;

    public SpellTemplate() {
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name != null ? name : id;
    }

    public int getLevel() {
        return level;
    }

    public String getSchool() {
        return school;
    }

    public int getMpCost() {
        return mpCost;
    }

    public int getRange() {
        return range;
    }

    public String getTargetType() {
        return targetType;
    }

    public String getDamageDice() {
        return damageDice;
    }

    public String getDamageType() {
        return damageType;
    }

    public String getDomain() {
        return domain;
    }

    public String getDuration() {
        return duration;
    }

    public String getStatusEffect() {
        return statusEffect;
    }

    public String getVisualArchetype() {
        return visualArchetype;
    }

    public String getDescription() {
        return description;
    }
}
