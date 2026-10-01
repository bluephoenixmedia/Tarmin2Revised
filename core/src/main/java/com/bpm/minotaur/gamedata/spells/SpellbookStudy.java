package com.bpm.minotaur.gamedata.spells;

import java.util.Random;

/**
 * The risk in studying a spellbook above your level, after NetHack.
 *
 * <p>A book at or under the reader's level is read safely every time. Above it, the reader may
 * try anyway: the chance of a mishap rises with the book's level and falls with Intelligence and
 * experience, and a mishap is graded by how powerful the book is. Whatever goes wrong also costs
 * turns, because a failed reading is a long one.
 *
 * <p>The formula is NetHack's: the reader's ability is Intelligence + 4 + level/2 - 2*bookLevel,
 * and the reading fails when a d20 rolls above it.
 */
public final class SpellbookStudy {

    /** Longest a mishap can keep the reader out of the world, in turns. */
    public static final int MAX_DELAY_TURNS = 6;

    public enum Mishap {
        /** A small sting from the book's own magic. */
        STING,
        CONFUSION,
        BLINDNESS,
        HALLUCINATION,
        /** A paper-cut that will not stop bleeding. */
        WOUND,
        POISON,
        /** The book goes up in the reader's hands. */
        EXPLOSION
    }

    /** What went wrong, how long it keeps the reader down, and whether the book survives. */
    public static final class Failure {
        public final Mishap mishap;
        public final int delayTurns;
        public final boolean destroysBook;

        Failure(Mishap mishap, int delayTurns, boolean destroysBook) {
            this.mishap = mishap;
            this.delayTurns = delayTurns;
            this.destroysBook = destroysBook;
        }
    }

    private SpellbookStudy() {
    }

    public static int ability(int intelligence, int playerLevel, int bookLevel) {
        return intelligence + 4 + playerLevel / 2 - 2 * bookLevel;
    }

    /** Chance in [0, 1] that reading an above-level book goes wrong. */
    public static float failureChance(int intelligence, int playerLevel, int bookLevel) {
        float chance = (20 - ability(intelligence, playerLevel, bookLevel)) / 20f;
        return Math.max(0f, Math.min(1f, chance));
    }

    /** Whether reading is risky at all: only a book above the reader's level is. */
    public static boolean isRisky(int requiredPlayerLevel, int playerLevel) {
        return playerLevel < requiredPlayerLevel;
    }

    /** Rolls the mishap for a failed reading of a book of this level. */
    public static Failure rollFailure(int bookLevel, Random rng) {
        Mishap mishap;
        if (bookLevel <= 2) {
            mishap = rng.nextBoolean() ? Mishap.STING : Mishap.CONFUSION;
        } else if (bookLevel <= 4) {
            mishap = rng.nextBoolean() ? Mishap.BLINDNESS : Mishap.HALLUCINATION;
        } else if (bookLevel <= 6) {
            mishap = rng.nextBoolean() ? Mishap.WOUND : Mishap.POISON;
        } else {
            mishap = Mishap.EXPLOSION;
        }
        int delay = Math.min(MAX_DELAY_TURNS, 1 + Math.max(0, bookLevel) / 2);
        // A book above the reader may survive being misread, but not a third of the time, and
        // never one that has just exploded.
        boolean destroyed = mishap == Mishap.EXPLOSION || rng.nextInt(3) == 0;
        return new Failure(mishap, delay, destroyed);
    }
}
