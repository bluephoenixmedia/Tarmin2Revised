package com.bpm.minotaur.gamedata.gore;

/**
 * The player's gore setting: one multiplier every blood and gib spawn goes through.
 *
 * <p>Held as a process-wide current value rather than threaded through callers,
 * because gore is spawned from a dozen places (combat, spells, injuries) that
 * share no common context, and {@link com.bpm.minotaur.managers.SettingsManager}
 * is the one owner that writes it.
 */
public enum GoreLevel {
    /** No blood, decals, gibs or wound marks; kills play their death animation only. */
    OFF(0f, 1f, false, false, 1f),
    /** Half the blood, and it fades the way it used to. No drips. */
    LOW(0.5f, 1f, false, false, 1f),
    NORMAL(1f, 1f, true, true, 1f),
    /** Twice the blood, half again the budgets, and full gib deaths come twice as easily. */
    BRUTAL(2f, 1.5f, true, true, 0.5f);

    private final float countScale;
    private final float budgetScale;
    private final boolean persistent;
    private final boolean drips;
    private final float tier2ThresholdScale;

    private static volatile GoreLevel current = NORMAL;

    GoreLevel(float countScale, float budgetScale, boolean persistent, boolean drips, float tier2ThresholdScale) {
        this.countScale = countScale;
        this.budgetScale = budgetScale;
        this.persistent = persistent;
        this.drips = drips;
        this.tier2ThresholdScale = tier2ThresholdScale;
    }

    public static GoreLevel current() {
        return current;
    }

    public static void setCurrent(GoreLevel level) {
        current = (level != null) ? level : NORMAL;
    }

    public static GoreLevel parse(String name) {
        if (name == null) return NORMAL;
        try {
            return valueOf(name);
        } catch (IllegalArgumentException e) {
            return NORMAL;
        }
    }

    public boolean enabled() {
        return this != OFF;
    }

    /** A spawn count at this level. Any blood at all stays at least one drop. */
    public int count(int base) {
        if (base <= 0 || !enabled()) return 0;
        return Math.max(1, Math.round(base * countScale));
    }

    /** A pool budget at this level. */
    public int budget(int base) {
        return Math.round(base * budgetScale);
    }

    /** True when blood and gibs stay until recycled rather than fading on a timer. */
    public boolean persistent() {
        return persistent;
    }

    public boolean drips() {
        return drips;
    }

    public float tier2ThresholdScale() {
        return tier2ThresholdScale;
    }

    public GoreLevel next() {
        GoreLevel[] all = values();
        return all[(ordinal() + 1) % all.length];
    }
}
