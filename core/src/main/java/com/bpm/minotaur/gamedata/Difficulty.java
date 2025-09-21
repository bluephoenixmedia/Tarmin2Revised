package com.bpm.minotaur.gamedata;

public enum Difficulty {
    EASIEST(18, 9, 9, 9, 0.25f), // 1/4 Vulnerability
    EASY(16, 8, 8, 8, 0.50f),    // 1/2 Vulnerability
    MEDIUM(14, 7, 7, 7, 0.75f),   // 3/4 Vulnerability
    HARD(12, 6, 6, 6, 1.0f);     // Full Vulnerability

    public final int startWarStrength;
    public final int startSpiritualStrength;
    public final int startFood;
    public final int startArrows;
    public final float vulnerabilityMultiplier;

    Difficulty(int startWarStrength, int startSpiritualStrength, int startFood, int startArrows, float vulnerabilityMultiplier) {
        this.startWarStrength = startWarStrength;
        this.startSpiritualStrength = startSpiritualStrength;
        this.startFood = startFood;
        this.startArrows = startArrows;
        this.vulnerabilityMultiplier = vulnerabilityMultiplier;
    }
}
