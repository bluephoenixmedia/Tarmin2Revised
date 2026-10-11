package com.bpm.minotaur.gamedata.item;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.bpm.minotaur.utils.DiceRoller;
import org.junit.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.*;

/**
 * Validates that weapon damage dice are balanced and no weapon encountered in the world
 * is inferior to the starting sword (RUSTY_SWORD: 1d3, avg 2.0, max 3).
 */
public class WeaponBalanceIntegrityTest {

    private static final Pattern DICE_PATTERN = Pattern.compile("(\\d*)d(\\d+)([+-]\\d+)?");

    private static File asset(String relativePath) {
        File f = new File("assets/" + relativePath);
        if (!f.exists()) {
            f = new File("../assets/" + relativePath);
        }
        return f;
    }

    private double calculateAverageDamage(String diceNotation) {
        if (diceNotation == null || diceNotation.trim().isEmpty()) {
            return 0.0;
        }
        String clean = diceNotation.replaceAll("\\s+", "");
        try {
            return Double.parseDouble(clean);
        } catch (NumberFormatException ignored) {
        }

        Matcher matcher = DICE_PATTERN.matcher(clean.toLowerCase());
        if (matcher.matches()) {
            String numDiceStr = matcher.group(1);
            String numSidesStr = matcher.group(2);
            String modifierStr = matcher.group(3);

            int numDice = (numDiceStr == null || numDiceStr.isEmpty()) ? 1 : Integer.parseInt(numDiceStr);
            int numSides = Integer.parseInt(numSidesStr);
            int modifier = (modifierStr == null || modifierStr.isEmpty()) ? 0 : Integer.parseInt(modifierStr);

            return (numDice * (numSides + 1) / 2.0) + modifier;
        }
        return -1.0;
    }

    private int calculateMaxDamage(String diceNotation) {
        if (diceNotation == null || diceNotation.trim().isEmpty()) {
            return 0;
        }
        String clean = diceNotation.replaceAll("\\s+", "");
        try {
            return Integer.parseInt(clean);
        } catch (NumberFormatException ignored) {
        }

        Matcher matcher = DICE_PATTERN.matcher(clean.toLowerCase());
        if (matcher.matches()) {
            String numDiceStr = matcher.group(1);
            String numSidesStr = matcher.group(2);
            String modifierStr = matcher.group(3);

            int numDice = (numDiceStr == null || numDiceStr.isEmpty()) ? 1 : Integer.parseInt(numDiceStr);
            int numSides = Integer.parseInt(numSidesStr);
            int modifier = (modifierStr == null || modifierStr.isEmpty()) ? 0 : Integer.parseInt(modifierStr);

            return (numDice * numSides) + modifier;
        }
        return -1;
    }

    @Test
    public void noWeaponIsInferiorToStartingSword() {
        File weaponsFile = asset("data/weapons.json");
        assertTrue("weapons.json must exist", weaponsFile.exists());

        JsonValue root = new JsonReader().parse(new com.badlogic.gdx.files.FileHandle(weaponsFile));
        List<String> underpoweredWeapons = new ArrayList<>();
        List<String> invalidDiceFormats = new ArrayList<>();

        double starterAvg = 2.0; // 1d3 avg
        int starterMax = 3;     // 1d3 max

        for (JsonValue entry = root.child; entry != null; entry = entry.next) {
            boolean isWeapon = entry.getBoolean("isWeapon", false);
            if (!isWeapon) continue;

            String typeName = entry.name;
            String damageDice = entry.getString("damageDice", null);

            double avg = calculateAverageDamage(damageDice);
            int max = calculateMaxDamage(damageDice);

            if (avg < 0 || max < 0) {
                invalidDiceFormats.add(typeName + " has invalid dice: " + damageDice);
                continue;
            }

            // Exclude RUSTY_SWORD itself from being strictly better than itself
            if ("RUSTY_SWORD".equals(typeName)) {
                assertEquals("1d3", damageDice);
                continue;
            }

            if (avg < starterAvg || max < starterMax) {
                underpoweredWeapons.add(typeName + " (" + damageDice + ", avg=" + avg + ", max=" + max + ")");
            }
        }

        assertTrue("Invalid dice notations found in weapons.json: " + invalidDiceFormats, invalidDiceFormats.isEmpty());
        assertTrue("Weapons weaker than starting sword found: " + underpoweredWeapons, underpoweredWeapons.isEmpty());
    }

    @Test
    public void allWeaponDamageDiceAreParseableByDiceRoller() {
        File weaponsFile = asset("data/weapons.json");
        JsonValue root = new JsonReader().parse(new com.badlogic.gdx.files.FileHandle(weaponsFile));

        for (JsonValue entry = root.child; entry != null; entry = entry.next) {
            if (!entry.getBoolean("isWeapon", false)) continue;
            String dice = entry.getString("damageDice", null);
            assertNotNull(entry.name + " must define damageDice", dice);
            int roll = DiceRoller.roll(dice);
            assertTrue(entry.name + " (" + dice + ") roll must be >= 0, was " + roll, roll >= 0);
        }
    }
}
