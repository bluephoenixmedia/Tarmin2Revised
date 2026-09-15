package com.bpm.minotaur.gamedata.injury;

/**
 * Categorization of physical trauma that can afflict body parts.
 */
public enum InjuryType {
    LACERATION_BLEEDING(
            "Laceration (Bleeding)",
            "Active hemorrhage causing health loss with every step taken.",
            "Bandage (Cloth, Silk, or Pressure)"
    ),
    BONE_FRACTURE(
            "Bone Fracture",
            "Broken or cracked bone causing severe physical impairment (speed or accuracy).",
            "Splint (Rigid Bone/Wood + Cordage)"
    ),
    PUNCTURE_WOUND(
            "Deep Puncture",
            "Deep piercing wound with high danger of venom or bacterial infection.",
            "Cauterization / Antiseptic + Bandage"
    ),
    SEVERE_BURN(
            "Severe Burn",
            "Blistered tissue that increases vulnerability to physical and elemental damage.",
            "Soothing Salve (Slime Residue or Clean Water)"
    );

    private final String displayName;
    private final String description;
    private final String treatmentRequirement;

    InjuryType(String displayName, String description, String treatmentRequirement) {
        this.displayName = displayName;
        this.description = description;
        this.treatmentRequirement = treatmentRequirement;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public String getTreatmentRequirement() {
        return treatmentRequirement;
    }
}
