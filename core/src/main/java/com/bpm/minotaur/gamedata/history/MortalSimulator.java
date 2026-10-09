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

    private static Mortal add(HistoryWorld world, Settlement s, int seat, String family, int born, int seated,
            int predecessor, Random rng) {
        Mortal m = new Mortal(world.mortals.size(), s.allegiance.givenName(rng) + " " + family, family, s.id, seat,
                born, seated, predecessor, s.allegiance.folkSprite(rng));
        world.mortals.add(m);
        return m;
    }
}
