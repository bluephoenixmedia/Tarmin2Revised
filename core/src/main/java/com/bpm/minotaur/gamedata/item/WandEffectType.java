package com.bpm.minotaur.gamedata.item;

/**
 * Defines the true effects of wands.
 */
public enum WandEffectType {

    DIGGING("Digging", "The beam digs a hole!"),
    MAGIC_MISSILE("Magic Missile", "Multiple missiles shoot out!"),
    FIRE("Fire", "A blast of fire erupts!"),
    COLD("Cold", "A freezing blast erupts!"),
    LIGHT("Light", "The room lights up!"),
    TELEPORTATION("Teleportation", "The target vanishes!");

    private final String baseName;
    private final String zapMessage;

    WandEffectType(String baseName, String zapMessage) {
        this.baseName = baseName;
        this.zapMessage = zapMessage;
    }

    public String getBaseName() {
        return baseName;
    }

    public String getZapMessage() {
        return zapMessage;
    }

    // Wands identify when the effect is seen
}
