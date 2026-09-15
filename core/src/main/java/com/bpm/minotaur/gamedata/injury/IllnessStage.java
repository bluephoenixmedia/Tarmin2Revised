package com.bpm.minotaur.gamedata.injury;

/**
 * Progression stages of wound infection and systemic disease.
 */
public enum IllnessStage {
    HEALTHY(
            "Healthy",
            "No active infections or fever.",
            0
    ),
    STAGE_1_INFECTED_WOUND(
            "Infected Wound",
            "Localized inflammation and throbbing pain. Natural recovery is halved.",
            1
    ),
    STAGE_2_ACUTE_FEVER(
            "Acute Fever",
            "High fever with shivering and cold sweats. Stamina recovery halted; rapid thirst.",
            2
    ),
    STAGE_3_SEPTIC_DELIRIUM(
            "Septic Delirium",
            "Blood toxicity causing periodic damage, disorientation, and delirium.",
            3
    );

    private final String displayName;
    private final String description;
    private final int severityLevel;

    IllnessStage(String displayName, String description, int severityLevel) {
        this.displayName = displayName;
        this.description = description;
        this.severityLevel = severityLevel;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public int getSeverityLevel() {
        return severityLevel;
    }

    public boolean isIll() {
        return this != HEALTHY;
    }
}
