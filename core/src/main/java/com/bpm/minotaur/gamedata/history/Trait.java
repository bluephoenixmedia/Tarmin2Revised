package com.bpm.minotaur.gamedata.history;

/** A disposition of a historical figure; it biases what that figure does when they rule. */
public enum Trait {
    AMBITIOUS("Ambitious"),
    WRATHFUL("Wrathful"),
    CRAVEN("Craven"),
    ZEALOT("Zealous"),
    CUNNING("Cunning"),
    HONOURABLE("Honourable"),
    CRUEL("Cruel"),
    PATIENT("Patient");

    public final String displayName;

    Trait(String displayName) {
        this.displayName = displayName;
    }
}
