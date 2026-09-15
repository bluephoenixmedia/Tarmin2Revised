package com.bpm.minotaur.gamedata.injury;

/**
 * Anatomical zones for player injuries in Tarmin 2.
 */
public enum BodyPart {
    HEAD("Head", 15, "Concussions, blurred vision, disoriented aim, mana penalty."),
    TORSO("Torso", 40, "Deep lacerations, active bleeding per step, stamina drain."),
    ARMS("Arms", 25, "Bone fractures, accuracy loss, blocks two-handed weapons."),
    LEGS("Legs", 20, "Fractures and sprains, reduces movement speed by 50%.");

    private final String displayName;
    private final int hitWeight;
    private final String description;

    BodyPart(String displayName, int hitWeight, String description) {
        this.displayName = displayName;
        this.hitWeight = hitWeight;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getHitWeight() {
        return hitWeight;
    }

    public String getDescription() {
        return description;
    }
}
