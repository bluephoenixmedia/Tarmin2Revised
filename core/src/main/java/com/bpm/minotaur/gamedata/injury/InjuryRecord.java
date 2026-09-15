package com.bpm.minotaur.gamedata.injury;

/**
 * Encapsulates an active wound on a specific body part.
 */
public class InjuryRecord {
    private final BodyPart bodyPart;
    private final InjuryType injuryType;
    private int severity; // 1 = Minor, 2 = Moderate, 3 = Critical
    private boolean treated; // Has been bandaged, splinted, or salved
    private boolean infected;
    private int turnsUntreated;

    public InjuryRecord(BodyPart bodyPart, InjuryType injuryType, int severity) {
        this.bodyPart = bodyPart;
        this.injuryType = injuryType;
        this.severity = Math.max(1, Math.min(3, severity));
        this.treated = false;
        this.infected = false;
        this.turnsUntreated = 0;
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
        } else {
            s += " [UNTREATED]";
        }
        if (infected) {
            s += " (Infected!)";
        }
        return s;
    }
}
