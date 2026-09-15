package com.bpm.minotaur.gamedata.injury;

/**
 * Encapsulates an active wound on a specific body part.
 */
public class InjuryRecord {

    /** Bleed ticks a fresh wound is worth, per severity rank: 6 / 8 / 10. */
    private static final int BLEED_TICKS_BASE = 4;
    private static final int BLEED_TICKS_PER_SEVERITY = 2;

    private final BodyPart bodyPart;
    private final InjuryType injuryType;
    private int severity; // 1 = Minor, 2 = Moderate, 3 = Critical
    private boolean treated; // Has been bandaged, splinted, or salved
    private boolean infected;
    private int turnsUntreated;
    /**
     * Remaining bleed ticks before the wound clots on its own. An untreated
     * bleed is a survivable clock, not an unbounded drain -- it costs a fixed
     * pool of HP and then stops, leaving the wound open to infection instead.
     */
    private int bleedTicksRemaining;

    public InjuryRecord(BodyPart bodyPart, InjuryType injuryType, int severity) {
        this.bodyPart = bodyPart;
        this.injuryType = injuryType;
        this.severity = Math.max(1, Math.min(3, severity));
        this.treated = false;
        this.infected = false;
        this.turnsUntreated = 0;
        this.bleedTicksRemaining = bleedTicksFor(this.severity);
    }

    private static int bleedTicksFor(int severity) {
        return BLEED_TICKS_BASE + BLEED_TICKS_PER_SEVERITY * Math.max(1, Math.min(3, severity));
    }

    public BodyPart getBodyPart() {
        return bodyPart;
    }

    public InjuryType getInjuryType() {
        return injuryType;
    }

    public int getSeverity() {
        return severity;
    }

    public void setSeverity(int severity) {
        this.severity = Math.max(1, Math.min(3, severity));
    }

    /**
     * Worsens the wound (a fresh strike landing on an already-injured limb),
     * topping the bleed pool back up to the new severity's allowance.
     */
    public void aggravate(int additionalSeverity) {
        int newSeverity = Math.max(1, Math.min(3, this.severity + Math.max(1, additionalSeverity)));
        if (newSeverity != this.severity) {
            this.severity = newSeverity;
        }
        this.bleedTicksRemaining = Math.max(this.bleedTicksRemaining, bleedTicksFor(this.severity));
    }

    /**
     * Partial recovery from a night's rest: steps the wound down one rank and
     * reports whether it has fully closed.
     */
    public boolean mend() {
        stopBleeding();
        this.turnsUntreated = 0;
        this.severity--;
        return this.severity <= 0;
    }

    /** True while the wound is an open, untreated bleeder with pool left. */
    public boolean isBleeding() {
        if (treated || bleedTicksRemaining <= 0) {
            return false;
        }
        return injuryType == InjuryType.LACERATION_BLEEDING || injuryType == InjuryType.PUNCTURE_WOUND;
    }

    /** Consumes one bleed tick. */
    public void consumeBleedTick() {
        if (bleedTicksRemaining > 0) {
            bleedTicksRemaining--;
        }
    }

    public int getBleedTicksRemaining() {
        return bleedTicksRemaining;
    }

    public void stopBleeding() {
        this.bleedTicksRemaining = 0;
    }

    public boolean isTreated() {
        return treated;
    }

    public void setTreated(boolean treated) {
        this.treated = treated;
    }

    public boolean isInfected() {
        return infected;
    }

    public void setInfected(boolean infected) {
        this.infected = infected;
    }

    public int getTurnsUntreated() {
        return turnsUntreated;
    }

    public void incrementTurnsUntreated() {
        this.turnsUntreated++;
    }

    public void resetTurnsUntreated() {
        this.turnsUntreated = 0;
    }

    public String getStatusLabel() {
        String s = (severity == 3 ? "Severe " : severity == 2 ? "Moderate " : "Minor ") + injuryType.getDisplayName();
        if (treated) {
            s += " [Dressed]";
        } else if (bleedTicksRemaining <= 0
                && (injuryType == InjuryType.LACERATION_BLEEDING || injuryType == InjuryType.PUNCTURE_WOUND)) {
            s += " [Clotted, Untreated]";
        } else {
            s += " [UNTREATED]";
        }
        if (infected) {
            s += " (Infected!)";
        }
        return s;
    }
}
