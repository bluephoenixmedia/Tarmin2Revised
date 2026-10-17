package com.bpm.minotaur.gamedata.history.text;

import com.bpm.minotaur.gamedata.history.Doctrine;
import com.bpm.minotaur.gamedata.history.DoctrineCatalog;
import com.bpm.minotaur.gamedata.history.HistoryEvent;
import com.bpm.minotaur.gamedata.history.HistoryWorld;
import com.bpm.minotaur.gamedata.history.House;
import com.bpm.minotaur.gamedata.history.NameForge;

import java.util.List;
import java.util.Random;

/**
 * Who wrote an account of an event, and whose side they were on (plan D29). A {@code FOR}
 * chronicler serves the house that acted; an {@code AGAINST} chronicler serves the house acted
 * upon, or a rival; a {@code NEUTRAL} one is a rumour with no house at all.
 */
public final class Chronicler {

    private static final String[] RUMOURS = {
            "A refugee's account", "A tavern rumour", "Scrawled on a shelter wall", "A herald's cry",
            "An unsigned broadsheet"
    };

    public final ChronicleGrammar.Bias bias;
    /** The house the chronicler serves, or -1 for a rumour or a mortal. */
    public final int houseId;
    /** Doctrine voice key for symbol flavour, or null. */
    public final String voice;
    /** "Cantor Sibine of House Sallow", "A tavern rumour". */
    public final String byline;

    private Chronicler(ChronicleGrammar.Bias bias, int houseId, String voice, String byline) {
        this.bias = bias;
        this.houseId = houseId;
        this.voice = voice;
        this.byline = byline;
    }

    public static Chronicler of(HistoryWorld world, HistoryEvent e, ChronicleGrammar.Bias bias, DoctrineCatalog catalog) {
        Random rng = new Random(world.seed * 0x2545F4914F6CDD1DL + e.id * 31L + bias.ordinal());
        if (bias == ChronicleGrammar.Bias.NEUTRAL) {
            return new Chronicler(bias, -1, null, RUMOURS[rng.nextInt(RUMOURS.length)]);
        }
        int house;
        if (bias == ChronicleGrammar.Bias.FOR) {
            house = e.houseA >= 0 ? e.houseA : e.houseB >= 0 ? rival(world, e.houseB, e.id) : world.tarminHouse() != null ? world.tarminHouse().id : -1;
        } else {
            house = e.houseB >= 0 ? e.houseB : e.houseA >= 0 ? rival(world, e.houseA, e.id) : -1;
        }
        House h = world.house(house);
        if (h == null) {
            return new Chronicler(bias, -1, null, "A shelter-keeper's ledger");
        }
        Doctrine d = catalog.get(h.doctrineId);
        return new Chronicler(bias, h.id, d.voice, title(d.voice) + " " + NameForge.given(d, rng) + " of " + h.name);
    }

    /** Some other house to tell the story against {@code houseId}: its bitterest enemy, else any. */
    private static int rival(HistoryWorld world, int houseId, int salt) {
        List<House> houses = world.houses();
        int best = -1;
        float bestGrudge = 0f;
        for (House h : houses) {
            if (h.id == houseId) continue;
            float g = world.grudgeWeight(h.id, houseId);
            if (g > bestGrudge) {
                bestGrudge = g;
                best = h.id;
            }
        }
        if (best >= 0) return best;
        int n = houses.size();
        for (int i = 0; i < n; i++) {
            House h = houses.get(Math.floorMod(salt + i, n));
            if (h.id != houseId) return h.id;
        }
        return -1;
    }

    private static String title(String voice) {
        if (voice == null) return "Scribe";
        switch (voice) {
            case "liturgical": return "Cantor";
            case "martial": return "Herald";
            case "chronicler": return "Ossuarist";
            case "feral": return "Gnawer";
            case "courtly": return "Courtier";
            case "mercantile": return "Tallyman";
            default: return "Scribe";
        }
    }
}
