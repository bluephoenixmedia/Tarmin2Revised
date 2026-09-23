package com.bpm.minotaur.generation.theme;

/**
 * The completion predicate a themed chunk uses for contract slot (e).
 *
 * <p>Each theme deliberately uses a different kind. Six kill-the-boss rooms
 * would be six identical rooms wearing different textures, which is the failure
 * mode themed chunks exist to avoid. See
 * {@code docs/DEsign/Themed Chunk Contract.md} section 4.
 */
public enum ThemeObjectiveKind {

    /** Blood Colosseum: every combatant in the arena is dead. */
    LAST_COMBATANT_STANDING("Slay every combatant in the arena"),

    /** Ruined Castle / Wandering Battalion: one designated champion must die. */
    SLAY_CHAMPION("Slay the champion"),

    /** Makeshift Graveyard: reconsecrate every desecrated grave. */
    RECONSECRATE_GRAVES("Reconsecrate the desecrated graves"),

    /** Flooded Caverns: reach and open the drowned cache. */
    OPEN_DROWNED_CACHE("Open the drowned cache"),

    /** Overgrown Thicket: destroy the heart-bloom before the brambles close in. */
    DESTROY_HEART_BLOOM("Burn out the heart-bloom");

    private final String description;

    ThemeObjectiveKind(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
