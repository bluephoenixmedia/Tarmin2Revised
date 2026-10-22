package com.bpm.minotaur.gamedata.history.text;

import com.bpm.minotaur.gamedata.history.Nemesis;

import java.util.List;
import java.util.Random;

/**
 * What a Nemesis says when it sees a later life of the soul it has killed before (#269, #284).
 * Every line names a life it took, or how many. No life's sex is ever set, so no line guesses
 * at one. ASCII only (UiGlyphs).
 */
public final class NemesisSpeech {

    /** {v} is a life it took. */
    private static final String[] ONE = {
            "I remember {v}. You have the same look about you.",
            "{v} begged at the end. Will you?",
            "{v} came this way too. {v} did not leave.",
            "Ah. {v} again, in new skin.",
            "{v} fought harder than you will.",
            "I kept what {v} carried. Come and take it back.",
            "The Bridge has a stone for {v}. It has room for you."
    };

    /** {n} is how many lives it took. */
    private static final String[] MANY = {
            "I have taken {n} of you. What is one more?",
            "{n} of you already. You never learn.",
            "{n} of you have died here. Make it {m}."
    };

    private static final String[] COUNTS = {"none", "one", "two", "three", "four", "five", "six", "seven", "eight",
            "nine", "ten", "eleven", "twelve"};

    private NemesisSpeech() {
    }

    public static String line(Nemesis nemesis, Random rng) {
        List<String> victims = nemesis.victims;
        int taken = nemesis.tally();
        if (taken > 1 && rng.nextInt(3) == 0) {
            String line = MANY[rng.nextInt(MANY.length)].replace("{n}", count(taken)).replace("{m}", count(taken + 1));
            return Character.toUpperCase(line.charAt(0)) + line.substring(1);
        }
        if (taken == 0) throw new IllegalArgumentException(nemesis.name + " has taken no life to remember");
        String victim = victims.get(rng.nextInt(taken));
        String line = ONE[rng.nextInt(ONE.length)].replace("{v}", victim);
        return Character.toUpperCase(line.charAt(0)) + line.substring(1);
    }

    private static String count(int n) {
        return n < COUNTS.length ? COUNTS[n] : String.valueOf(n);
    }
}
