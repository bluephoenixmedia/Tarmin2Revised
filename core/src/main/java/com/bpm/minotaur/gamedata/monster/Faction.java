package com.bpm.minotaur.gamedata.monster;

/**
 * Faction affiliation for monsters and NPCs.
 * Used by FactionMatrix to determine interpersonal hostility and dynamic infighting.
 */
public enum Faction {
    TARMIN_LEGION("Tarmin Legion", "Disciplined garrison soldiers and elite knights bound to Castle Tarmin."),
    GOBLIN_CLANS("Goblin Clans", "Scrappy marauders, ambushers, and cave-dwelling scavengers."),
    UNDEAD("Undead Horde", "Risen skeletons, ghouls, and crypt wraiths cursed to wander."),
    BEASTS_AND_VERMIN("Beasts & Vermin", "Wild predators, giant arachnids, serpents, and feral denizens."),
    OUTCASTS_AND_HERMITS("Outcasts & Hermits", "Rogue cultists, exiled sorcerers, and subterranean vagabonds."),
    CHAOS_BERSERK("Chaos Berserkers", "Blood-maddened combatants who attack any living or dead creature on sight."),
    NEUTRAL("Neutral", "Merchants, passive observers, and non-combatants.");

    private final String displayName;
    private final String description;

    Faction(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    /**
     * Determines the default faction for a monster based on its name and family.
     */
    public static Faction getDefaultFaction(String monsterName, MonsterFamily family) {
        if (monsterName == null) return BEASTS_AND_VERMIN;
        String lower = monsterName.toLowerCase();

        if (lower.contains("tarmin") || lower.contains("knight") || lower.contains("guard") || lower.contains("soldier") || lower.contains("commander")) {
            return TARMIN_LEGION;
        }
        if (lower.contains("goblin") || lower.contains("orc") || lower.contains("hobgoblin")) {
            return GOBLIN_CLANS;
        }
        if (family == MonsterFamily.UNDEAD || lower.contains("skeleton") || lower.contains("ghoul") || lower.contains("wraith") || lower.contains("zombie")) {
            return UNDEAD;
        }
        if (lower.contains("cultist") || lower.contains("sorcerer") || lower.contains("hermit") || lower.contains("outcast") || lower.contains("bandit")) {
            return OUTCASTS_AND_HERMITS;
        }
        if (family == MonsterFamily.BEAST || family == MonsterFamily.DEMON || family == MonsterFamily.MAGICAL || family == MonsterFamily.MYTHICAL) {
            return BEASTS_AND_VERMIN;
        }
        return BEASTS_AND_VERMIN;
    }
}
