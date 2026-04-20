package com.bpm.minotaur.utils;

import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility for parsing and rolling standard RPG dice notation (e.g., "1d6",
 * "2d8+5").
 */
public class DiceRoller {

    private static final Random rng = new Random();
    // Pattern matches: XdY or XdY+Z or XdY-Z
    // Group 1: Num Dice (Optional, default 1)
    // Group 2: Num Sides
    // Group 3: Modifier (Optional, includes sign)
    private static final Pattern DICE_PATTERN = Pattern.compile("(\\d*)d(\\d+)([+-]\\d+)?");

    /**
     * Rolls dice based on the given notation string.
     * Examples: "1d6", "2d8+3", "d20", "3d4-1"
     * 
     * @param notation The dice string.
     * @return The result of the roll. Returns 0 if parsing fails.
     */
    public static int roll(String notation) {
        if (notation == null || notation.isEmpty()) {
            return 0;
        }

        // Handle static numbers (e.g. "5")
        try {
            return Integer.parseInt(notation);
        } catch (NumberFormatException e) {
            // Not a static number, proceed to parse dice
        }

        Matcher matcher = DICE_PATTERN.matcher(notation.toLowerCase());
        if (matcher.matches()) {
            String numDiceStr = matcher.group(1);
            String numSidesStr = matcher.group(2);
            String modifierStr = matcher.group(3);

            int numDice = (numDiceStr == null || numDiceStr.isEmpty()) ? 1 : Integer.parseInt(numDiceStr);
            int numSides = Integer.parseInt(numSidesStr);
            int modifier = (modifierStr == null || modifierStr.isEmpty()) ? 0 : Integer.parseInt(modifierStr);

            int total = 0;
            for (int i = 0; i < numDice; i++) {
                total += rng.nextInt(numSides) + 1;
            }
            return Math.max(0, total + modifier);
        }

        return 0;
    }

    /**
     * Rolls a d20.
     */
    public static int d20() {
        return rng.nextInt(20) + 1;
    }

    /**
     * Rolls a d100.
     */
    public static int d100() {
        return rng.nextInt(100) + 1;
    }
}
