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
    public String visualArchetype = "FORCE_MISSILE";
    public String secondaryArchetype = "PROJECTILE";
    public String runeSchool = "EVOCATION";
    public String bespokeEffect;
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
        return visualArchetype != null ? visualArchetype : "FORCE_MISSILE";
    }

    public VisualArchetype getVisualArchetypeEnum() {
        return VisualArchetype.fromString(visualArchetype);
    }

    public String getSecondaryArchetype() {
        return secondaryArchetype != null ? secondaryArchetype : targetType;
    }

    public String getRuneSchool() {
        return runeSchool != null ? runeSchool : (school != null ? school.toUpperCase() : "EVOCATION");
    }

    public String getBespokeEffect() {
        return bespokeEffect;
    }

    public boolean hasBespokeEffect() {
        return bespokeEffect != null && !bespokeEffect.isEmpty();
    }

    public String getDescription() {
        return description;
    }
}
