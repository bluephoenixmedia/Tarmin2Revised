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
        public float hpMult = BOSS_HP_MULT;
        /** Added to the base type's damage dice modifier. */
        public int damageBonus;
        public int armorBonus;
        public int moveSpeedDelta;
        public final Set<Behaviour> behaviours = EnumSet.noneOf(Behaviour.class);
        public final List<Retainer> retinue = new ArrayList<>();
    }

    public static final int MAX_RETINUE = 3;
    /** A seal lord is a deep boss: several times the body its type gives an ordinary monster. */
    static final float BOSS_HP_MULT = 3f;

    private SealLord() {
    }

    /** The gash a seal road leads to; -1 for the castle road. */
    public static int gashIndexForRoad(int road) {
        if (road == ShelterRoads.CASTLE_ROAD || road < 0 || road >= ShelterRoads.ROAD_COUNT) return -1;
        return road < ShelterRoads.CASTLE_ROAD ? road : road - 1;
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
