package com.bpm.minotaur.gamedata;

/**
 * Difficulty (#201). MEDIUM is the reference and the only difficulty played
 * ({@code SaveSlotSelectScreen.PLAY_DIFFICULTY}); the others are two fixed multipliers on it:
 * <ul>
 *   <li>{@link #kitScale} on the start HP, start MP and starting kit (arrows), rounded;</li>
 *   <li>{@link #damageTakenScale} on the damage taken ({@link #vulnerabilityMultiplier}).</li>
 * </ul>
 * Retune MEDIUM's numbers and every other difficulty follows.
 */
public enum Difficulty {
    EASIEST(1.30f, 1f / 3f), // 27 HP, 1/4 vulnerability
    EASY(1.15f, 2f / 3f),    // 24 HP, 1/2 vulnerability
    MEDIUM(1.00f, 1f),       // 21 HP, 3/4 vulnerability
    HARD(0.85f, 4f / 3f);    // 18 HP, full vulnerability

    /** MEDIUM's start HP (#207: 21, or 24 with the Father's Sword). */
    public static final int MEDIUM_START_HP = 21;
    public static final int MEDIUM_START_MP = 11;
    public static final int MEDIUM_START_ARROWS = 7;
    public static final float MEDIUM_VULNERABILITY = 0.75f;

    /** Multiplier on MEDIUM's start HP, MP and starting kit. */
    public final float kitScale;
    /** Multiplier on MEDIUM's damage taken. */
    public final float damageTakenScale;

    public final int startWarStrength;
    public final int startSpiritualStrength;
    public final int startArrows;
    public final float vulnerabilityMultiplier;

    Difficulty(float kitScale, float damageTakenScale) {
        this.kitScale = kitScale;
        this.damageTakenScale = damageTakenScale;
        this.startWarStrength = Math.round(MEDIUM_START_HP * kitScale);
        this.startSpiritualStrength = Math.round(MEDIUM_START_MP * kitScale);
        this.startArrows = Math.round(MEDIUM_START_ARROWS * kitScale);
        this.vulnerabilityMultiplier = MEDIUM_VULNERABILITY * damageTakenScale;
    }
}
