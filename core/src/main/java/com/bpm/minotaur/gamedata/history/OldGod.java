package com.bpm.minotaur.gamedata.history;

/**
 * The thirteen old gods of the maze (#272): the creatures of the original Treasure of Tarmin,
 * woken from the world's forgetting, each keeping a shrine (a Statue) and a Kindred. They keep the
 * Champion's soul. ASCII only (UiGlyphs).
 */
public enum OldGod {
    ASTERION("EVENT_TARMIN_STATUE", "Asterion, the Labyrinth King", "Asterion", "the maze, oaths and the warden's duty", Kindred.DREAD),
    IXXATH("EVENT_DRAGON_STATUE", "Ixxath the Ember-Wyrm", "Ixxath", "fire, vigour and hoarded things", Kindred.SPIRIT),
    OSSERY("EVENT_SKELETON_STATUE", "Ossery the Watchful", "Ossery", "the honoured dead, arms and the vigil", Kindred.WAR),
    HARROWGAST("EVENT_DWARF_STATUE", "Harrowgast the Anvil", "Harrowgast", "craft, stone and the endurance of work", Kindred.SPIRIT),
    GRANDFATHER_ASH("EVENT_OLD_DWARF_STATUE", "Grandfather Ash", "Grandfather Ash", "the first forge and the first gates", Kindred.SPIRIT),
    THE_UNREMEMBERED("EVENT_GHOST_STATUE", "the Unremembered", "the Unremembered", "memory and the forgotten", Kindred.DREAD),
    MAWGRIM("EVENT_GHOUL_STATUE", "Mawgrim the Carrion Saint", "Mawgrim", "hunger, decay and endings", Kindred.DREAD),
    SESSALINE("EVENT_SNAKE_STATUE", "Sessaline of the Twin Coils", "Sessaline", "healing and poison, and knowledge", Kindred.SPIRIT),
    BULWARK("EVENT_GIANT_STATUE", "Bulwark", "Bulwark", "endurance, the pillar that holds the roof", Kindred.WAR),
    THE_COLONY("EVENT_ANT_STATUE", "the Colony", "the Colony", "many as one, labour and stores", Kindred.SPIRIT),
    OLD_MUDDJAW("EVENT_CROC_STATUE", "Old Muddjaw", "Old Muddjaw", "patience, ambush and protection", Kindred.SPIRIT),
    VENNA("EVENT_SCORPION_STATUE", "Venna of the Ichor", "Venna", "venom and the poisoned edge", Kindred.SPIRIT),
    THE_HOLLOW_HOOD("EVENT_WRAITH_STATUE", "the Hollow Hood", "the Hollow Hood", "the void and unseen ways", Kindred.DREAD);

    /** The original game's monster classes, as the gods' kin (#272). */
    public enum Kindred {
        SPIRIT("Spirit-born", "its sleeping kin fear spiritual weapons"),
        WAR("War-born", "its sleeping kin fear weapons of war"),
        DREAD("Dread-born", "its sleeping kin fear spiritual weapons and weapons of war alike");

        public final String title;
        public final String fear;

        Kindred(String title, String fear) {
            this.title = title;
            this.fear = fear;
        }
    }

    /** The statue encounter that is this god's shrine. */
    public final String encounterId;
    /** "Ixxath the Ember-Wyrm" */
    public final String fullName;
    /** "Ixxath": the name its rites go by. */
    public final String shortName;
    public final String domain;
    public final Kindred kindred;

    OldGod(String encounterId, String fullName, String shortName, String domain, Kindred kindred) {
        this.encounterId = encounterId;
        this.fullName = fullName;
        this.shortName = shortName;
        this.domain = domain;
        this.kindred = kindred;
    }

    /** "Ixxath's rite", "The Colony's rite" */
    public String riteOwner() {
        return Character.toUpperCase(shortName.charAt(0)) + shortName.substring(1) + "'s rite";
    }

    /** The god whose shrine this encounter is, or null for any other encounter. */
    public static OldGod forEncounter(String encounterId) {
        for (OldGod god : values()) {
            if (god.encounterId.equals(encounterId)) return god;
        }
        return null;
    }
}
