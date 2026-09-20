package com.bpm.minotaur.gamedata.progression;

import java.util.Collections;
import java.util.Set;

/**
 * Metadata and rule constraints for a single perk in the player skill tree.
 */
public class SkillDefinition {

    private final SkillId id;
    private final String name;
    private final String open5eReference;
    private final String description;
    private final Set<SkillId> prerequisites;
    private final ShelterAltar.StatType requiredStat;
    private final int requiredStatValue;

    public SkillDefinition(SkillId id, String name, String open5eReference, String description,
                           Set<SkillId> prerequisites, ShelterAltar.StatType requiredStat, int requiredStatValue) {
        this.id = id;
        this.name = name;
        this.open5eReference = open5eReference;
        this.description = description;
        this.prerequisites = prerequisites != null ? prerequisites : Collections.emptySet();
        this.requiredStat = requiredStat;
        this.requiredStatValue = requiredStatValue;
    }

    public SkillId getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getOpen5eReference() {
        return open5eReference;
    }

    public String getDescription() {
        return description;
    }

    public SkillId.Discipline getDiscipline() {
        return id.getDiscipline();
    }

    public int getTier() {
        return id.getTier();
    }

    public Set<SkillId> getPrerequisites() {
        return prerequisites;
    }

    public ShelterAltar.StatType getRequiredStat() {
        return requiredStat;
    }

    public int getRequiredStatValue() {
        return requiredStatValue;
    }
}
