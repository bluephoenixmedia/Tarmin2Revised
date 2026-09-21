package com.bpm.minotaur.gamedata.progression;

/**
 * Identifiers for player skills in the progression tree, grounded in Open5e/SRD feats and archetypes.
 */
public enum SkillId {
    // Warfare Discipline (Might, Melee, Two-Weapon Combat & Armor)
    DUAL_WIELDER(Discipline.WARFARE, 1),
    SHIELD_MASTER(Discipline.WARFARE, 1),
    BRUTAL_CLEAVE(Discipline.WARFARE, 2),
    HEAVY_ARMOR_MASTERY(Discipline.WARFARE, 2),
    WHIRLWIND_EXECUTIONER(Discipline.WARFARE, 3),

    // Finesse Discipline (Agility, Survival, Archery & Mobility)
    SKIRMISHER(Discipline.FINESSE, 1),
    RAPID_QUAFF(Discipline.FINESSE, 1),
    DEADEYE_SNIPER(Discipline.FINESSE, 2),
    DUNGEON_SCAVENGER(Discipline.FINESSE, 2),
    ELUSIVE_REFLEXES(Discipline.FINESSE, 3),

    // Arcana Discipline (Sorcery, Spell Potency & Rune Conservation)
    BATTLE_CASTER(Discipline.ARCANA, 1),
    SPELL_WEAVER(Discipline.ARCANA, 1),
    PRIMORDIAL_FOCUS(Discipline.ARCANA, 2),
    RUNIC_CONSERVATION(Discipline.ARCANA, 2),
    ARCHMAGES_SURGE(Discipline.ARCANA, 3);

    public enum Discipline {
        WARFARE("Warfare", "Mastery of physical weapons, dual wielding, shield defense, and brutal melee power."),
        FINESSE("Finesse", "Agility, evasion, ranged precision, rapid recovery, and dungeon survival."),
        ARCANA("Arcana", "Spellcraft, mana efficiency, elemental destruction, and runic mastery.");

        private final String displayName;
        private final String description;

        Discipline(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getDescription() {
            return description;
        }
    }

    private final Discipline discipline;
    private final int tier;

    SkillId(Discipline discipline, int tier) {
        this.discipline = discipline;
        this.tier = tier;
    }

    public Discipline getDiscipline() {
        return discipline;
    }

    public int getTier() {
        return tier;
    }
}
