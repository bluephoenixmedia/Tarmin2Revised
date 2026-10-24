package com.bpm.minotaur.gamedata.history;

import java.util.Random;

/**
 * The ends of Ages (#291, #292): the Champion slaying Tarmin, and the rare off-screen slaying by
 * a monster, rolled once each time a death brings the Hunger to a new Era stage. Everything comes
 * from the deeds and the history's seed, so the replay ends the same Ages the same way.
 */
final class Ages {

    /** The chance, at each new Era, that something slays Tarmin off-screen. */
    static final float USURP_CHANCE = 0.05f;
    /** Of those, the share that are a House's siege, and a Nemesis's challenge; the rest a Megabeast. */
    static final float BY_SIEGE = 0.6f;
    static final float BY_NEMESIS = 0.25f;

    private static final int[] STAGE_STARTS = {
            com.bpm.minotaur.managers.DoomManager.DEATHS_FOR_STAGE_2,
            com.bpm.minotaur.managers.DoomManager.DEATHS_FOR_STAGE_3,
            com.bpm.minotaur.managers.DoomManager.DEATHS_FOR_STAGE_4
    };

    private Ages() {
    }

    /** The Champion slew Tarmin: returned the Treasure, or took the throne as {@code life}. */
    static void slainByChampion(HistoryWorld world, boolean throne, String life) {
        if (throne) {
            String name = life != null ? life : "a nameless prisoner";
            end(world, AgeEnding.Kind.THRONE, AgeEnding.titleFrom(name), name);
            return;
        }
        // The Rift is sealed, and the old gods spent holding Tarmin back wake again (#278).
        world.spentGods.clear();
        House winner = strongest(world);
        Figure lord = winner != null ? world.lordOf(winner) : null;
        String by = lord != null ? lord.name : winner != null ? winner.name : "a lord of the Maze";
        end(world, AgeEnding.Kind.RETURNED, AgeEnding.titleFrom(by), by);
    }

    /** A death at {@code hunger}: if it opened a new Era, roll whether Tarmin is slain off-screen. */
    static void maybeUsurped(HistoryWorld world, int hunger) {
        if (hunger < 1 || stage(hunger) <= stage(hunger - 1)) return;
        Random rng = new Random(world.seed * 7_919L + world.seekersFallen * 104_729L + world.age);
        if (rng.nextFloat() >= USURP_CHANCE) return;
        float kind = rng.nextFloat();
        if (kind < BY_SIEGE || !(kind < BY_SIEGE + BY_NEMESIS ? byNemesis(world) : byMegabeast(world))) {
            bySiege(world);
        }
    }

    private static boolean bySiege(HistoryWorld world) {
        House besieger = strongest(world);
        if (besieger == null) return false;
        Figure lord = world.lordOf(besieger);
        String by = lord != null ? lord.name : besieger.name;
        besieger.prestige += 10;
        end(world, AgeEnding.Kind.SIEGE, AgeEnding.titleFrom(by), by);
        return true;
    }

    private static boolean byNemesis(HistoryWorld world) {
        Nemesis top = null;
        for (Nemesis n : world.nemeses) {
            if (n.isAlive() && (top == null || n.tally() > top.tally())) top = n;
        }
        if (top == null) return false;
        top.deathSeason = world.season; // it leaves its region for the throne
        end(world, AgeEnding.Kind.NEMESIS, AgeEnding.titleFrom(top.name), top.name);
        return true;
    }

    private static boolean byMegabeast(HistoryWorld world) {
        for (Megabeast b : world.megabeasts) {
            if (!b.isAlive()) continue;
            b.deathSeason = world.season; // it leaves its lair for the throne
            end(world, AgeEnding.Kind.MEGABEAST, AgeEnding.titleFrom(b.name), b.name);
            return true;
        }
        return false;
    }

    private static void end(HistoryWorld world, AgeEnding.Kind kind, String newTitle, String by) {
        world.ageEndings.add(new AgeEnding(world.age, kind, world.tarminTitle, newTitle, by));
        world.tarminTitle = newTitle;
        world.age++;
    }

    /** The strongest House of the Maze but Tarmin's own. */
    private static House strongest(HistoryWorld world) {
        House best = null;
        for (House h : world.livingHouses()) {
            if (h.holdsCastle) continue;
            if (best == null || h.strength > best.strength) best = h;
        }
        return best;
    }

    private static int stage(int hunger) {
        int stage = 0;
        for (int start : STAGE_STARTS) if (hunger >= start) stage++;
        return stage;
    }
}
