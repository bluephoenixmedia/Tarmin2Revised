package com.bpm.minotaur.gamedata.history;

import com.bpm.minotaur.gamedata.history.town.Allegiance;
import com.bpm.minotaur.gamedata.history.town.Settlement;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Forges the prisoner each Incarnation wakes in (#279), the Caves of Qud way: the event first
 * (a raid, a tithe), then its causes, drawn from what the history already holds -- a real
 * settlement, the Antlered Host's own living raiders, the power that keeps the town.
 * ASCII only (UiGlyphs).
 */
public final class LifeForge {

    private static final String[] CRIMES = {
            "horse theft", "debt", "heresy", "desertion", "poaching", "arson", "murder", "smuggling",
            "a broken oath", "grave robbing", "sedition", "striking a magistrate", "false weights",
            "witchcraft", "a stolen bell", "letting the wrong door stand open"
    };

    private static final String[] SOUL_FIRST = {
            "Ael", "Bran", "Cor", "Dra", "Eld", "Fen", "Gal", "Hal", "Ith", "Kael", "Mor", "Ny", "Os",
            "Rhe", "Syl", "Tor", "Ul", "Val", "Wy", "Yr"
    };
    private static final String[] SOUL_LAST = {
            "ric", "wen", "aster", "dane", "mir", "oth", "vane", "ion", "ris", "and", "eth", "orn",
            "abe", "ys", "ulf", "ane"
    };

    private LifeForge() {
    }

    /** The prisoner for this seed. The same world and seed always forge the same life. */
    public static Life forge(HistoryWorld world, long seed) {
        Random rng = new Random(seed);
        List<Settlement> towns = world.settlements();
        Settlement home = towns.get(rng.nextInt(towns.size()));
        Allegiance power = home.allegiance;
        String name = power.givenName(rng) + " " + power.familyName(rng);
        if (rng.nextBoolean()) {
            return new Life(name, home.id, home.name, power, Life.Origin.RAIDED, raider(world, home, rng), null, null);
        }
        String crime = CRIMES[rng.nextInt(CRIMES.length)];
        return new Life(name, home.id, home.name, power, Life.Origin.TITHED, null, crime, power.displayName);
    }

    /** A name for the soul itself, offered at a new game in place of a typed one. */
    public static String soulName(Random rng) {
        return SOUL_FIRST[rng.nextInt(SOUL_FIRST.length)] + SOUL_LAST[rng.nextInt(SOUL_LAST.length)];
    }

    /**
     * "the Antlered Host under Vorath", naming one of its living members when it has any, and
     * the house that bought the settlement when one has: a bought gate is how the Host got in.
     */
    private static String raider(HistoryWorld world, Settlement home, Random rng) {
        String host = "the Antlered Host";
        for (House h : world.livingHouses()) {
            if (!h.holdsCastle) continue;
            List<Figure> members = new ArrayList<>(world.livingMembers(h));
            host = members.isEmpty() ? h.name : h.name + " under " + members.get(rng.nextInt(members.size())).name;
            break;
        }
        House buyer = world.house(home.subornedBy);
        if (buyer != null && !buyer.isExtinct()) {
            return host + ", the gate opened by " + buyer.name;
        }
        return host;
    }
}
