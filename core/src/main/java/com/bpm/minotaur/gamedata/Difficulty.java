package com.bpm.minotaur.gamedata;

public enum Difficulty {
    EASIEST(18, 9, 99, 0.25f), // 1/4 Vulnerability
    EASY(16, 8, 8, 0.50f),    // 1/2 Vulnerability
    MEDIUM(14, 7, 7, 0.75f),   // 3/4 Vulnerability
    HARD(12, 6, 6, 1.0f);     // Full Vulnerability

    public final int startWarStrength;
    public final int startSpiritualStrength;
    public final int startArrows;
    public final float vulnerabilityMultiplier;

    Difficulty(int startWarStrength, int startSpiritualStrength, int startArrows, float vulnerabilityMultiplier) {
        this.startWarStrength = startWarStrength;
        this.startSpiritualStrength = startSpiritualStrength;
        this.startArrows = startArrows;
        this.vulnerabilityMultiplier = vulnerabilityMultiplier;
    }
}
