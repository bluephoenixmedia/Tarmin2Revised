package com.bpm.minotaur.gamedata.history;

import com.bpm.minotaur.gamedata.history.town.Allegiance;
import com.bpm.minotaur.gamedata.history.town.Mortal;
import com.bpm.minotaur.gamedata.history.town.Settlement;
import com.bpm.minotaur.gamedata.history.town.Town;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

/**
 * The mortal settlements' seasons (ADR 0005): founding, ageing, death and succession. Every draw
 * comes from a generator keyed on the history seed and the season, never the house simulator's,
 * so the houses' history is the same with mortals or without.
 */
final class MortalSimulator {

    private static final long SALT = 0x5E771E5L;
    /** Settlements are founded within the first century of prehistory. */
    static final int FOUNDING_YEARS = 100;
    /** A keeper's yearly chance of death rises from this age. */
    static final int OLD_AGE = 45;
    static final float YOUNG_DEATH = 0.002f;
    static final float AGEING = 0.004f;

    private MortalSimulator() {
    }

    /** Founds the settlements and lives out prehistory's seasons, up to the world's present. */
    static void prehistory(HistoryWorld world) {
        Random rng = new Random(world.seed ^ SALT);
        Set<String> names = new HashSet<>();
        for (int id = 0; id < Settlement.COUNT; id++) {
            Allegiance allegiance = Allegiance.values()[rng.nextInt(Allegiance.values().length)];
            String name;
            do {
                name = Town.placeName(rng);
            } while (!names.add(name));
            int founded = rng.nextInt(FOUNDING_YEARS * HistoryWorld.SEASONS_PER_YEAR);
            Settlement s = new Settlement(id, name, allegiance, founded, Town.welcomeRoll(rng), Town.seatsFor(rng));
            world.settlements.add(s);
            for (int seat = 0; seat < s.seats.length; seat++) {
                int born = founded - (20 + rng.nextInt(30)) * HistoryWorld.SEASONS_PER_YEAR;
                s.holders[seat] = add(world, s, seat, allegiance.familyName(rng), born, founded, -1, rng).id;
            }
        }
        for (int season = 0; season < world.season; season++) step(world, season);
    }

    /** One season of mortal lives: the old and the unlucky die, and their heirs take their seats. */
    static void step(HistoryWorld world, int season) {
        Random rng = new Random(world.seed ^ SALT ^ (season * 0x9E3779B97F4A7C15L));
        for (Settlement s : world.settlements) {
            if (season < s.foundedSeason) continue;
            for (int seat = 0; seat < s.seats.length; seat++) {
                Mortal keeper = world.mortals.get(s.holders[seat]);
                float yearly = YOUNG_DEATH + Math.max(0, keeper.ageAt(season) - OLD_AGE) * AGEING;
                if (rng.nextFloat() >= yearly / HistoryWorld.SEASONS_PER_YEAR) continue;
                keeper.deathSeason = season;
                int born = season - (16 + rng.nextInt(20)) * HistoryWorld.SEASONS_PER_YEAR;
                s.holders[seat] = add(world, s, seat, keeper.family, born, season, keeper.id, rng).id;
            }
        }
    }

    /** A founded, unbought settlement's chance each live season of being bought by a house. */
    static final float SUBORN_CHANCE = 0.004f;
    static final int BETRAYAL_MIN_SEASONS = 6;
    static final int BETRAYAL_MAX_SEASONS = 15;
    private static final long PLOT_SALT = 0x9107L;

    /**
     * One live season of the houses' reach into the deep (plan T4.6): now and then a house buys a
     * settlement, in secret; some seasons later the bought settlement betrays. Live play only, so
     * prehistory and the golden files stay as they were (ADR 0005).
     */
    static void intrigue(HistoryWorld world, int season) {
        Random rng = new Random(world.seed ^ PLOT_SALT ^ (season * 0xC2B2AE3D27D4EB4FL));
        for (Settlement s : world.settlements) {
            if (s.betrayed || season < s.foundedSeason) continue;
            if (s.subornedBy >= 0) {
                House buyer = world.house(s.subornedBy);
                if (buyer == null || buyer.isExtinct()) {
                    // The house fell before its plot ripened; the bargain dies with it.
                    s.subornedBy = -1;
                    s.betrayalSeason = -1;
                } else if (season >= s.betrayalSeason) {
                    s.betrayed = true;
                    record(world, season, EventType.TOWN_BETRAYED, s);
                }
                continue;
            }
            if (rng.nextFloat() >= SUBORN_CHANCE) continue;
            java.util.List<House> houses = world.livingHouses();
            if (houses.isEmpty()) continue;
            suborn(world, s, houses.get(rng.nextInt(houses.size())), season,
                    BETRAYAL_MIN_SEASONS + rng.nextInt(BETRAYAL_MAX_SEASONS - BETRAYAL_MIN_SEASONS + 1));
        }
    }

    /** House {@code house} buys settlement {@code s} now; it betrays {@code delay} seasons on. */
    static void suborn(HistoryWorld world, Settlement s, House house, int season, int delay) {
        s.subornedBy = house.id;
        s.betrayalSeason = season + delay;
        record(world, season, EventType.TOWN_SUBORNED, s).houseA = house.id;
    }

    private static HistoryEvent record(HistoryWorld world, int season, EventType type, Settlement s) {
        HistoryEvent e = new HistoryEvent(world.events.size(), season, type);
        e.houseA = s.subornedBy;
        e.place = s.name;
        e.detail = s.id;
        world.events.add(e);
        return e;
    }

    private static Mortal add(HistoryWorld world, Settlement s, int seat, String family, int born, int seated,
            int predecessor, Random rng) {
        Mortal m = new Mortal(world.mortals.size(), s.allegiance.givenName(rng) + " " + family, family, s.id, seat,
                born, seated, predecessor, s.allegiance.folkSprite(rng));
        world.mortals.add(m);
        return m;
    }
}
