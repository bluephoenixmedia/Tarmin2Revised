package com.bpm.minotaur.gamedata.worldlog;

/**
 * One House's account of one life (#285): written when the life ends, found later in the maze
 * on one of that House's soldiers, then read in the World Log under "As the chronicles tell it".
 */
public final class Telling {

    /** The House that tells it; for the Knell, Tarmin's own house. */
    public int tellerHouseId = -1;
    public Tellings.Role role;
    /** "House Gall", or "Tarmin's Knell". */
    public String teller;
    public String plain;
    public String mythic;
    public boolean found;

    /** For the save reader. */
    public Telling() {
    }
}
