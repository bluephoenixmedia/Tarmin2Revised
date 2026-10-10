package com.bpm.minotaur.gamedata.boss;

import com.bpm.minotaur.gamedata.history.Doctrine;
import com.bpm.minotaur.gamedata.history.DoctrineCatalog;
import com.bpm.minotaur.gamedata.history.Figure;
import com.bpm.minotaur.gamedata.history.HistoryWorld;
import com.bpm.minotaur.gamedata.history.House;
import com.bpm.minotaur.gamedata.history.Trait;
import com.bpm.minotaur.gamedata.history.text.Epithets;
import com.bpm.minotaur.generation.ShelterRoads;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * The deep boss at the end of a seal road: whichever lord holds that road's gash right now
 * (plan D7, D30, T1.12). The fight is composed: the house's doctrine picks the body, the lord's
 * traits change how it fights, and the house's named sworn swords stand with it.
 *
 * <p>Pure: everything comes from the history, so the same world always fields the same lord.
 */
public final class SealLord {

    /** How the lord fights beyond its numbers. */
    public enum Behaviour {
        /** Wrathful: below half health it hits much harder. */
        BERSERK_AT_HALF,
        /** Craven: below a third of its health it shrinks behind its armour and calls its retinue. */
        CALLS_RETINUE,
        /** Zealous: it closes its own wounds, a little every turn. */
        REGENERATES,
        /** Honourable: its retinue holds back until it is wounded. */
        DUELIST
    }

    /** A named sworn sword who fights beside the lord. */
    public static final class Retainer {
        public final int figureId;
        public final String name;
        public final String monsterType;

        public Retainer(int figureId, String name, String monsterType) {
            this.figureId = figureId;
            this.name = name;
            this.monsterType = monsterType;
        }
    }

    /** A lord, composed for the fight. Multipliers and bonuses apply to the base type's stats. */
    public static final class Spec {
        public int figureId;
        public int houseId;
        public int gashIndex;
        /** "Vora the Restorer of House Gullet". */
        public String name;
        /** A monsters.json type: the doctrine's boss base. */
        public String monsterType;
        /** Multiplies {@link #BASE_HP}: traits, age and the house's renown. */
        public float hpMult = 1f;
        /** Added to the base type's damage dice modifier. */
        public int damageBonus;
        public int armorBonus;
        public int moveSpeedDelta;
        public final Set<Behaviour> behaviours = EnumSet.noneOf(Behaviour.class);
        public final List<Retainer> retinue = new ArrayList<>();

        public int maxHp() {
            return maxHp(1);
        }

        public String damageDice() {
            return damageDice(1);
        }

        public int armor() {
            return armor(1);
        }

        /**
         * At {@code level}, the court's effective difficulty: the lord grows as the strata around
         * it do, at half the rate of the wildlife there ({@link SealLord#levelMult}), so it stays a hard
         * single fight rather than a wall.
         */
        public int maxHp(int level) {
            return Math.round(BASE_HP * hpMult * levelMult(level));
        }

        public String damageDice(int level) {
            return withDamageBonus(BASE_DICE, damageBonus + Math.max(0, level - 1) / 5);
        }

        public int armor(int level) {
            return BASE_ARMOR + armorBonus + Math.max(0, level - 1) / 5;
        }
    }

    public static final int MAX_RETINUE = 3;
    /*
     * Every lord fights in one frame, whatever body its doctrine gives it: the body is the look.
     * The play-test found that tripling the body's own numbers made a Purple Worm lord a 700 HP,
     * 5d10+12 wall two strata down -- a level-nine seeker needed two thousand turns, and died to
     * one bite. A seal is meant to be hard won by a seeker of the middle levels, not unwinnable.
     */
    /** A lord's hit points before its traits. */
    public static final int BASE_HP = 180;
    /**
     * A lord's bite before its traits. At 2d10+4, eight clean duels of the play-test (no strays in
     * the court) against a seeker in middling kit were won six times at a median of 7 draughts, and
     * lost twice: seekers of 47 and 70 hit points against bites of 2d10+8 and 2d10+10, killed from
     * the third of health they drink at. The lord's burst, not its stamina, decided those fights.
     * At 2d8+3, six more: four lords fell (5 and 6 draughts where the seeker struck last), two
     * seekers died to a lord and its sworn sword striking in one turn -- the region's own beasts hit
     * as hard, so what is left is the seeker's health against the strata, not the lord.
     */
    public static final String BASE_DICE = "2d8+3";
    /** A lord's armour before its traits. */
    public static final int BASE_ARMOR = 15;

    private SealLord() {
    }

    /** The gash a seal road leads to; -1 for the castle road. */
    public static int gashIndexForRoad(int road) {
        if (road == ShelterRoads.CASTLE_ROAD || road < 0 || road >= ShelterRoads.ROAD_COUNT) return -1;
        return road < ShelterRoads.CASTLE_ROAD ? road : road - 1;
    }

    /**
     * What the map says of a gash's seal site (plan T2.8): who holds it, and, if it changed hands
     * this run, from whom it was taken and when. "held by House Pyreholt, taken from House Glassjaw
     * in the year 304".
     */
    public static String holderLine(HistoryWorld world, int gashIndex) {
        House holder = world.gashHolder(gashIndex);
        if (holder == null) return "";
        StringBuilder sb = new StringBuilder("held by ").append(holder.name);
        com.bpm.minotaur.gamedata.history.HistoryEvent taken = world.seizedThisRun(gashIndex);
        House from = taken != null ? world.house(taken.houseB) : null;
        if (from != null) {
            sb.append(", taken from ").append(from.name).append(" in the year ")
                    .append(taken.season / HistoryWorld.SEASONS_PER_YEAR + 1);
        }
        return sb.toString();
    }

    /** The news of a seized gash, as the player hears it on waking; null for any other event. */
    public static String seizureNotice(HistoryWorld world, com.bpm.minotaur.gamedata.history.HistoryEvent e) {
        if (e.type != com.bpm.minotaur.gamedata.history.EventType.SEAT_SEIZED || e.gashIndex < 0) return null;
        House winner = world.house(e.houseA);
        House loser = world.house(e.houseB);
        if (winner == null) return null;
        String gash = world.gashName(e.gashIndex);
        gash = gash.isEmpty() ? gash : Character.toUpperCase(gash.charAt(0)) + gash.substring(1);
        return gash + " has passed to " + winner.name
                + (loser != null ? ", taken from " + loser.name : "") + ". Its seal lord is " + winner.name + "'s now; the map is redrawn.";
    }

    /** The lord holding gash {@code gashIndex}, as a boss. */
    public static Spec compose(HistoryWorld world, int gashIndex, DoctrineCatalog catalog) {
        House house = world.gashHolder(gashIndex);
        Figure lord = world.lordOf(house);
        Doctrine doctrine = catalog.get(house.doctrineId);

        Spec spec = new Spec();
        spec.figureId = lord.id;
        spec.houseId = house.id;
        spec.gashIndex = gashIndex;
        spec.monsterType = doctrine.bossBase;
        String epithet = Epithets.of(world, lord, house.id);
        spec.name = lord.name + (epithet != null ? " " + epithet : "") + " of " + house.name;

        for (Trait t : lord.traits) {
            switch (t) {
                case AMBITIOUS:
                    spec.hpMult *= 1.15f;
                    spec.damageBonus += 2;
                    break;
                case WRATHFUL:
                    spec.behaviours.add(Behaviour.BERSERK_AT_HALF);
                    spec.damageBonus += 3;
                    break;
                case CRAVEN:
                    spec.behaviours.add(Behaviour.CALLS_RETINUE);
                    spec.hpMult *= 0.9f;
                    break;
                case ZEALOT:
                    spec.behaviours.add(Behaviour.REGENERATES);
                    break;
                case CUNNING:
                    spec.armorBonus += 2;
                    break;
                case HONOURABLE:
                    spec.behaviours.add(Behaviour.DUELIST);
                    spec.armorBonus += 1;
                    break;
                case CRUEL:
                    spec.damageBonus += 2;
                    break;
                case PATIENT:
                    spec.hpMult *= 1.25f;
                    spec.moveSpeedDelta -= 2;
                    break;
                default:
                    break;
            }
        }
        int age = lord.ageAt(world.season());
        if (age > 60) spec.hpMult *= 0.85f;
        else if (age < 20) spec.hpMult *= 0.9f;
        // A storied house's lord carries its renown into the fight.
        spec.hpMult *= 1f + Math.min(house.prestige, 100) / 400f;

        Random rng = new Random(world.seed ^ (lord.id * 0x9E3779B97F4A7C15L));
        for (Figure sword : world.swornSwords(house)) {
            if (spec.retinue.size() >= MAX_RETINUE) break;
            String type = doctrine.roster.get(rng.nextInt(doctrine.roster.size()));
            spec.retinue.add(new Retainer(sword.id, sword.name + ", sworn to " + house.name, type));
        }
        return spec;
    }

    /**
     * Hit-point growth with the court's level: half of the 15% a level that {@code Monster.scaleStats}
     * gives the wildlife. The duel play-test fought a lord on the full curve -- 550 HP at level 27,
     * five times its frame -- and a seeker's blows, which grow with gear and not with level, needed
     * eighteen draughts to get through it. On half the curve a lord stands at about twice the
     * strongest common beast of its region: one hard fight, where the wildlife come in packs.
     */
    static final float LEVEL_GROWTH = 0.075f;

    public static float levelMult(int level) {
        return level <= 1 ? 1f : 1f + (level - 1) * LEVEL_GROWTH;
    }

    /** {@code dice} with its flat modifier raised by {@code bonus}: "2d10+6" and 3 make "2d10+9". */
    public static String withDamageBonus(String dice, int bonus) {
        if (dice == null || dice.isEmpty()) dice = "1d4";
        if (bonus == 0) return dice;
        int plus = dice.indexOf('+');
        if (plus < 0) return dice + "+" + bonus;
        int base = Integer.parseInt(dice.substring(plus + 1).trim());
        return dice.substring(0, plus) + "+" + (base + bonus);
    }
}
