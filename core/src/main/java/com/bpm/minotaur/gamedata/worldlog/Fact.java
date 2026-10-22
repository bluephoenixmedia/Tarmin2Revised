package com.bpm.minotaur.gamedata.worldlog;

/**
 * One notable line of a life's record (#280), kept as a fact -- what happened and to whom -- and
 * told two ways: plainly, as the World Log reads it, and mythically, as a telling would. Names are
 * resolved when the fact is noted, so the line reads the same however the history moves on.
 * ASCII only (UiGlyphs).
 */
public final class Fact {

    /** What kind of line, how notable it is, and its two texts ({s} subject, {o} object). */
    public enum Kind {
        WARDEN_DESTROYED(100, "Destroyed the Warden of the Bridge.",
                "The Warden fell, and the Bridge of Souls crumbled back to nothing."),
        BOSS_SLAIN(60, "Slew {s}.", "{s} fell before them, and the maze remembers it."),
        SLEW_MEGABEAST(55, "Slew {s}.", "{s} was ended, and the deep went quiet."),
        SEAL_WON(50, "Won the seal of {s}.", "The seal of {s} was torn from its keeper."),
        SEAL_GRANTED(50, "Was granted the seal of {o}.", "{o} put its seal in their hand."),
        SLEW_FIGURE(40, "Slew {s} of {o}.", "{s} of {o} fell, and did not rise."),
        DEEPEST_STRATUM(30, "Went deeper than any life before, to {s}.",
                "No life of the soul had gone so deep as {s}."),
        ART_LEARNED(25, "Learned {s}.", "The soul first remembered {s} in this life."),
        MASTERY(20, "Reached {o} with {s} weapons.", "Their hands learned the way of {s}, to the rank of {o}."),
        FIRST_HEAVY(15, "Felled their first Heavy: {s}.", "{s} was the first great foe to fall before them."),
        BATTLE_WITNESSED(12, "Stood in the battle where {s} held the field.",
                "They saw {s} hold the field, and lived to tell it."),
        SKIRMISH(12, "Fought in a skirmish {s} won.", "Blood was spilled for {s}, some of it theirs."),
        SWORE_OATH(10, "Swore to {s}.", "They gave their word to {s}."),
        BROKE_OATH(10, "Broke their oath to {s}.", "{s} names them faithless."),
        QUEST_DONE(8, "Did the work of {s}.", "{s} owes them a debt."),
        PACIFIED_MEGABEAST(8, "Bought the peace of {s}.", "{s} was given its due, and let them pass."),
        BROKE_BEAST_PEACE(6, "Broke the peace bought from {s}.", "{s} was struck in its peace, and remembers it.");

        public final int weight;
        private final String plain;
        private final String mythic;

        Kind(int weight, String plain, String mythic) {
            this.weight = weight;
            this.plain = plain;
            this.mythic = mythic;
        }
    }

    public Kind kind;
    /** Who or what the line is about: a figure, a beast, a house, a stratum. */
    public String subject;
    /** A second name where the line needs one: a figure's house, a Mastery rank. */
    public String object;

    /** For the save reader. */
    public Fact() {
    }

    public static Fact of(Kind kind, String... names) {
        Fact f = new Fact();
        f.kind = kind;
        f.subject = names.length > 0 ? names[0] : null;
        f.object = names.length > 1 ? names[1] : null;
        return f;
    }

    public String plain() {
        return fill(kind.plain);
    }

    public String mythic() {
        return fill(kind.mythic);
    }

    private String fill(String template) {
        String text = template.replace("{s}", subject != null ? subject : "something");
        if (object == null) {
            // "Slew Vorath of {o}." reads as "Slew Vorath." when the figure had no house.
            text = text.replace(" of {o}", "").replace("{o}", "a power of the maze");
        } else {
            text = text.replace("{o}", object);
        }
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }
}
