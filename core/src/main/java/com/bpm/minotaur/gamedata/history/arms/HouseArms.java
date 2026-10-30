package com.bpm.minotaur.gamedata.history.arms;

import com.bpm.minotaur.gamedata.history.HistoryWorld;
import com.bpm.minotaur.gamedata.history.House;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * A house's Arms (Living War A43): the charge and field of its sigil, plus a second field colour
 * and a division of its own, so enemies of one creed still differ at a glance. All of it follows
 * from the house and the history's seed. Nothing is drawn from the history's own generators, so
 * Arms never move a world's history.
 */
public final class HouseArms {

    public enum Division {
        PER_PALE("per pale"), PER_BEND("per bend"), PER_CHEVRON("per chevron"), QUARTERLY("quarterly"), PER_FESS("per fess");

        public final String words;

        Division(String words) {
            this.words = words;
        }
    }

    /** The seven field colours a sigil can name, as RGB. */
    private static final Map<String, Integer> FIELDS = new LinkedHashMap<>();
    static {
        FIELDS.put("black", 0x141414);
        FIELDS.put("red", 0x8B1E1E);
        FIELDS.put("bone white", 0xE6DCC3);
        FIELDS.put("tarnished gold", 0x9C7A2B);
        FIELDS.put("ash grey", 0x7A7A7A);
        FIELDS.put("bruise purple", 0x4B2A5A);
        FIELDS.put("bile green", 0x6B7A24);
    }
    /** How far apart in contrast a house's two field colours must stand. */
    private static final double FIELD_CONTRAST = 2.0;

    public final int houseId;
    public final String doctrineId;
    /** The sigil as the history wrote it: "a closed manacle on bile green". */
    public final String sigil;
    public final String charge;
    public final String field;
    public final String secondField;
    public final Division division;

    private HouseArms(House h, String charge, String field, String secondField, Division division) {
        this.houseId = h.id;
        this.doctrineId = h.doctrineId;
        this.sigil = h.sigil;
        this.charge = charge;
        this.field = field;
        this.secondField = secondField;
        this.division = division;
    }

    /**
     * The Arms of every house in the world, founded or fallen, by id. No two are alike: a house
     * whose draw repeats an earlier one's takes the next division, then the next colour.
     */
    public static List<HouseArms> all(HistoryWorld world) {
        List<HouseArms> out = new ArrayList<>();
        for (House h : world.houses()) out.add(draw(world.seed, h, out));
        return out;
    }

    /** The Arms of one house. */
    public static HouseArms of(HistoryWorld world, int houseId) {
        List<House> houses = world.houses();
        List<HouseArms> before = new ArrayList<>();
        for (House h : houses) {
            HouseArms arms = draw(world.seed, h, before);
            if (h.id == houseId) return arms;
            before.add(arms);
        }
        return null;
    }

    private static HouseArms draw(long seed, House h, List<HouseArms> taken) {
        int on = h.sigil.lastIndexOf(" on ");
        String charge = on < 0 ? h.sigil : h.sigil.substring(0, on);
        String field = on < 0 ? "black" : h.sigil.substring(on + 4);
        List<String> seconds = new ArrayList<>();
        for (String f : FIELDS.keySet()) {
            if (!f.equals(field) && contrast(rgb(f), rgb(field)) >= FIELD_CONTRAST) seconds.add(f);
        }
        if (seconds.isEmpty()) seconds.add(field);
        Random r = new Random(seed ^ (h.id * 0x632BE59BD9B4E019L) ^ h.name.hashCode() ^ 0xA2A5L);
        int second = r.nextInt(seconds.size());
        int division = r.nextInt(Division.values().length);
        for (int s = 0; s < seconds.size(); s++) {
            for (int d = 0; d < Division.values().length; d++) {
                HouseArms arms = new HouseArms(h, charge, field, seconds.get((second + s) % seconds.size()),
                        Division.values()[(division + d) % Division.values().length]);
                if (!alike(arms, taken)) return arms;
            }
        }
        return new HouseArms(h, charge, field, seconds.get(second), Division.values()[division]);
    }

    private static boolean alike(HouseArms arms, List<HouseArms> others) {
        for (HouseArms o : others) if (o.looksLike(arms)) return true;
        return false;
    }

    /** Whether a shield of the one could be taken for a shield of the other. */
    public boolean looksLike(HouseArms o) {
        return charge.equals(o.charge) && field.equals(o.field) && secondField.equals(o.secondField) && division == o.division;
    }

    /** The blazon in words; the sigil as the history wrote it stays its prefix. */
    public String written() {
        return sigil + " and " + secondField + ", " + division.words;
    }

    /** The colour named, as RGB; black if the name is unknown. */
    public static int rgb(String field) {
        Integer c = FIELDS.get(field);
        return c == null ? FIELDS.get("black") : c;
    }

    /** WCAG contrast between two RGB colours, from 1 to 21. */
    public static double contrast(int a, int b) {
        double x = luminance(a), y = luminance(b);
        return (Math.max(x, y) + 0.05) / (Math.min(x, y) + 0.05);
    }

    private static double luminance(int rgb) {
        return 0.2126 * channel(rgb >> 16 & 255) + 0.7152 * channel(rgb >> 8 & 255) + 0.0722 * channel(rgb & 255);
    }

    private static double channel(int v) {
        double c = v / 255.0;
        return c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }
}
