package com.bpm.minotaur.gamedata.worldlog;

import com.bpm.minotaur.gamedata.history.Figure;
import com.bpm.minotaur.gamedata.history.HistoryWorld;
import com.bpm.minotaur.gamedata.history.House;
import com.bpm.minotaur.gamedata.history.PlayerDeed;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The Houses' accounts of one life (#269, #285). Only a House the life touched tells it, and
 * Tarmin's Knell tells the lives that brought the Hunger to a new stage. The fixed facts never
 * change -- the name, the home, which seeker it was, that it died -- while the killer, the motive
 * and the crime bend to the teller. ASCII only (UiGlyphs).
 */
public final class Tellings {

    /** Why a House tells the life, worst first: a House tells it once, in its worst voice. */
    public enum Role {
        KILLER(4,
                "{house} ended the {nth} seeker, {name} of {home}, as it ends all who come against it.",
                "{name} of {home}, the {nth} seeker, came against {house}, and {house} gave the Bridge another stone."),
        OATHBROKEN(3,
                "{name} of {home}, the {nth} seeker, broke faith with {house}, and died as oathbreakers die.",
                "{house} spits on the name of the {nth} seeker, {name} of {home}. The maze keeps no faith with the faithless."),
        KIN_SLAIN(2,
                "{name} of {home}, the {nth} seeker, murdered one of {house} for gold, and {fate}",
                "The {nth} seeker, {name} of {home}, came as a thief in the dark and took one of {house}. The blood was answered."),
        SWORN(1,
                "{name} of {home}, the {nth} seeker, swore to {house} and died a loyal sword, cut down by {killer}.",
                "{house} remembers the {nth} seeker, {name} of {home}: faithful to the last, and lost to the dark."),
        KNELL(0,
                "Another stone for the Bridge: {name} of {home}, the {nth} seeker, is dead. Tarmin hungers.",
                "The Knell tolls for the {nth} seeker, {name} of {home}, and the far shore draws nearer.");

        final int weight;
        final String plain;
        final String mythic;

        Role(int weight, String plain, String mythic) {
            this.weight = weight;
            this.plain = plain;
            this.mythic = mythic;
        }
    }

    /** A House that will tell the life, and why. */
    public static final class Teller {
        public final int houseId;
        public final Role role;

        public Teller(int houseId, Role role) {
            this.houseId = houseId;
            this.role = role;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Teller && ((Teller) o).houseId == houseId && ((Teller) o).role == role;
        }

        @Override
        public int hashCode() {
            return Objects.hash(houseId, role);
        }

        @Override
        public String toString() {
            return role + "#" + houseId;
        }
    }

    private Tellings() {
    }

    /**
     * Who tells the life whose deeds these are: the Houses it swore to, broke faith with, took a
     * Figure from, or died to; and the Knell, when its death brought the Hunger to a new stage.
     */
    public static List<Teller> involved(HistoryWorld world, List<PlayerDeed> deeds, boolean newStage) {
        Map<Integer, Role> roles = new LinkedHashMap<>();
        for (PlayerDeed d : deeds) {
            switch (d.kind) {
                case SWORE_OATH: involve(roles, d.target, Role.SWORN); break;
                case BROKE_OATH: involve(roles, d.target, Role.OATHBROKEN); break;
                case SLEW_FIGURE: {
                    Figure f = world.figure(d.target);
                    if (f != null) involve(roles, f.houseId, Role.KIN_SLAIN);
                    break;
                }
                case SEEKER_FELL: involve(roles, d.target, Role.KILLER); break;
                default: break;
            }
        }
        List<Teller> tellers = new ArrayList<>();
        for (Map.Entry<Integer, Role> e : roles.entrySet()) {
            if (world.house(e.getKey()) != null) tellers.add(new Teller(e.getKey(), e.getValue()));
        }
        // The Knell is Tarmin's own house's voice; his Legion carries its Tellings. A house tells a
        // life once, so if his house already tells it as its killer, the Knell keeps silent.
        if (newStage && world.tarminHouse() != null && !roles.containsKey(world.tarminHouse().id)) {
            tellers.add(new Teller(world.tarminHouse().id, Role.KNELL));
        }
        return tellers;
    }

    private static void involve(Map<Integer, Role> roles, int houseId, Role role) {
        if (houseId < 0) return;
        Role had = roles.get(houseId);
        if (had == null || role.weight > had.weight) roles.put(houseId, role);
    }

    /** The teller's account of the life; {@code killerHouse} is the House that truly killed it, or -1. */
    public static Telling tell(Teller teller, LifeRecord life, HistoryWorld world, int killerHouse) {
        House house = world.house(teller.houseId);
        String houseName = house != null ? house.name : "a house of the maze";
        Telling t = new Telling();
        t.tellerHouseId = teller.houseId;
        t.role = teller.role;
        t.teller = teller.role == Role.KNELL ? "Tarmin's Knell" : capital(houseName);
        // The kin-slain House ran the murderer down itself, unless another House plainly did it.
        String fate = killerHouse < 0 || killerHouse == teller.houseId
                ? houseName + " ran the murderer down."
                : "died owing the debt to " + (life.killer != null ? life.killer : "the maze") + ".";
        t.plain = fill(teller.role.plain, life, houseName, fate);
        t.mythic = fill(teller.role.mythic, life, houseName, fate);
        return t;
    }

    private static String fill(String template, LifeRecord life, String house, String fate) {
        String text = template.replace("{name}", life.name).replace("{home}", life.homeName())
                .replace("{nth}", ordinal(life.number)).replace("{house}", house).replace("{fate}", fate)
                .replace("{killer}", life.killer != null ? life.killer : "the maze");
        return capital(text);
    }

    private static final String[] ORDINALS = {"", "first", "second", "third", "fourth", "fifth", "sixth",
            "seventh", "eighth", "ninth", "tenth", "eleventh", "twelfth", "thirteenth", "fourteenth", "fifteenth",
            "sixteenth", "seventeenth", "eighteenth", "nineteenth", "twentieth"};

    /** "third", "twentieth", "51st" */
    static String ordinal(int n) {
        if (n > 0 && n < ORDINALS.length) return ORDINALS[n];
        int mod100 = n % 100;
        String suffix = mod100 >= 11 && mod100 <= 13 ? "th"
                : n % 10 == 1 ? "st" : n % 10 == 2 ? "nd" : n % 10 == 3 ? "rd" : "th";
        return n + suffix;
    }

    private static String capital(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
