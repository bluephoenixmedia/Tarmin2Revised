package com.bpm.minotaur.playtest.scenarios;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.math.GridPoint2;
import com.bpm.minotaur.gamedata.Maze;
import com.bpm.minotaur.gamedata.history.war.Encounter;
import com.bpm.minotaur.gamedata.history.war.EncounterLedger;
import com.bpm.minotaur.gamedata.history.war.WarSoundscape;
import com.bpm.minotaur.gamedata.monster.Monster;
import com.bpm.minotaur.playtest.PlaytestContext;
import com.bpm.minotaur.playtest.PlaytestScenario;
import com.bpm.minotaur.playtest.PlaytestScript;

import java.util.HashSet;
import java.util.Set;

/**
 * The Living War, phase 1 (L1.8): a new game hears the war at once, meets its first skirmish next
 * door within 150 turns, walks into it, sees it break and leave a battlefield, and keeps meeting
 * the war after.
 */
public final class LivingWarScenario implements PlaytestScenario {

    @Override
    public String name() {
        return "living-war";
    }

    @Override
    public String description() {
        return "Hears the war from the shelter, meets the first skirmish, watches it break, and counts the encounters after.";
    }

    private static Encounter first(PlaytestContext ctx) {
        for (Encounter e : ctx.getWorldManager().currentEncounters()) if (e.slot == -1) return e;
        return null;
    }

    private static boolean shown(PlaytestContext ctx) {
        return ctx.getMaze() != null && ctx.getMaze() == ctx.getWorldManager().getCurrentMaze();
    }

    @Override
    public void buildScript(PlaytestScript script, PlaytestContext ctx) {
        script.wait("settle", 20);
        script.once("settle traits", ctx::settleTraits);
        script.wait("traits settle", 15);
        script.once("outfit hero", () -> ctx.outfitHero(10));

        script.once("a war is under way", () -> {
            ctx.assertTrue(!ctx.getWorldManager().getHistory().world().activeWars().isEmpty(), "a new game opens on a war (W6)");
            ctx.assertEquals(1, ctx.getWorldManager().getCurrentLevel(), "on the surface");
        });

        long[] waited = {0};
        script.until("wait for the first skirmish", 600, () -> {
            if (ctx.getFrame() % 2 != 0) return false;
            ctx.press(Input.Keys.PERIOD);
            waited[0]++;
            return first(ctx) != null;
        });

        GridPoint2[] where = {null};
        script.once("it is next door, and heard", () -> {
            Encounter e = first(ctx);
            ctx.assertTrue(e != null, "the first skirmish has come");
            EncounterLedger ledger = ctx.getWorldManager().getHistory().encounterLedger();
            ctx.log("First skirmish at war-clock " + ledger.firstAt + ", after about " + waited[0] + " waits: " + e);
            GridPoint2 me = ctx.getWorldManager().getCurrentPlayerChunkId();
            long clock = ctx.getWorldManager().getHistory().warClock();
            where[0] = e.chunkAt(clock);
            ctx.assertEquals(1, com.bpm.minotaur.gamedata.history.war.EncounterScheduler.distance(me, where[0]), "next to the player");
            WarSoundscape.Mix mix = WarSoundscape.mix(ctx.getWorldManager().currentEncounters(),
                    ctx.getWorldManager().currentFronts(), clock, me, 1, ctx.getPlayer().getFacing(), false);
            ctx.log("Heard: near " + mix.volume(WarSoundscape.Bed.NEAR) + ", far " + mix.volume(WarSoundscape.Bed.FAR)
                    + ", drums " + mix.volume(WarSoundscape.Bed.DRUMS) + ", one-shots " + mix.shots.size());
            ctx.assertTrue(mix.volume(WarSoundscape.Bed.NEAR) > 0f, "steel and screams from next door (W9)");
        });

        for (int i = 0; i < 4; i++) {
            script.shot("lw01_horizon_" + i, ctx);
            script.key("turn", Input.Keys.RIGHT, ctx);
            script.wait("turned", 8);
        }

        script.once("walk into it", () -> {
            ctx.getGameScreen().travelToChunk(where[0], new GridPoint2(16, 16));
        });
        script.until("arrive", 300, () -> shown(ctx));
        script.key("a turn passes", Input.Keys.PERIOD, ctx);
        script.wait("the bands come on", 10);

        int[] houses = {0};
        script.once("two war-bands on the ground", () -> {
            Set<Integer> seen = new HashSet<>();
            for (Monster m : ctx.getMaze().getMonsters().values()) if (m.isWarBand()) seen.add(m.getHouseId());
            houses[0] = seen.size();
            com.bpm.minotaur.managers.HistoryManager h = ctx.getWorldManager().getHistory();
            ctx.log("At " + ctx.getWorldManager().getCurrentPlayerChunkId() + " clock " + h.warClock()
                    + "; encounters " + ctx.getWorldManager().currentEncounters() + "; spent " + h.encounterLedger().spent
                    + "; monsters " + ctx.getMaze().getMonsters().size() + "; shelter " + !ctx.getMaze().getSanctuaryTiles().isEmpty());
            ctx.log("War-band soldiers: " + ctx.countWarBand() + " of " + seen.size() + " houses");
            ctx.assertTrue(ctx.countWarBand() >= 12, "two war-bands of 6-10");
            ctx.assertTrue(seen.size() == 2, "of two houses");
        });
        script.shot("lw02_skirmish", ctx);

        script.until("watch it break", 3000, () -> {
            if (ctx.getFrame() % 2 != 0) return false;
            ctx.press(Input.Keys.PERIOD);
            return ctx.countWarBand() == 0;
        });

        script.once("a battlefield is left", () -> {
            EncounterLedger ledger = ctx.getWorldManager().getHistory().encounterLedger();
            boolean field = false;
            for (EncounterLedger.Dressing d : ledger.dressings) {
                if ("AFTERMATH".equals(d.kind) && d.chunkX == where[0].x && d.chunkY == where[0].y) field = true;
            }
            ctx.assertTrue(field, "the dead and the broken lie where they fought");
            ctx.assertTrue(ledger.firstDone || ledger.spent.contains(first(ctx) == null ? "" : first(ctx).key())
                    || first(ctx) == null, "the first skirmish is done with");
        });
        script.shot("lw03_aftermath", ctx);

        Set<String> kinds = new HashSet<>();
        script.until("keep walking the war", 4000, () -> {
            if (ctx.getFrame() % 2 != 0) return false;
            ctx.press(Input.Keys.PERIOD);
            for (Encounter e : ctx.getWorldManager().currentEncounters()) if (e.visible()) kinds.add(e.kind.name());
            return ctx.getWorldManager().getHistory().encounterLedger().anchors.size() >= 6;
        });
        script.once("the war keeps coming", () -> {
            EncounterLedger ledger = ctx.getWorldManager().getHistory().encounterLedger();
            int visible = 0;
            for (EncounterLedger.Anchor a : ledger.anchors) if (a.kind != null) visible++;
            ctx.log("Encounters met: " + kinds + "; slots anchored " + ledger.anchors.size() + ", visible " + visible);
            ctx.assertTrue(visible >= 3, "an encounter every 80-120 turns (W4)");
            Maze here = ctx.getMaze();
            ctx.log("Scenery in the chunk now: " + (here == null ? 0 : here.getScenery().size()));
        });
        script.shot("lw04_later", ctx);

        // A column on the march: find one, and stand in its way.
        Encounter[] column = {null};
        script.until("wait for a column", 6000, () -> {
            if (ctx.getFrame() % 2 != 0) return false;
            for (Encounter e : ctx.getWorldManager().currentEncounters()) {
                if (e.kind == Encounter.Kind.COLUMN) column[0] = e;
            }
            if (column[0] != null) return true;
            ctx.press(Input.Keys.PERIOD);
            return false;
        });
        script.once("stand in its way", () -> {
            long clock = ctx.getWorldManager().getHistory().warClock();
            ctx.log("Column: " + column[0]);
            ctx.getGameScreen().travelToChunk(column[0].chunkAt(clock), new GridPoint2(16, 16));
        });
        script.until("arrive at the column", 300, () -> shown(ctx));
        script.key("a turn passes", Input.Keys.PERIOD, ctx);
        script.wait("they come on", 10);
        script.once("a column marches through", () -> {
            int marching = 0;
            for (Monster m : ctx.getMaze().getMonsters().values()) if (m.isWarBand() && m.getMarchTarget() != null) marching++;
            ctx.log("Soldiers on the march: " + marching);
            ctx.assertTrue(marching >= 8, "a column of 8-15");
        });
        script.shot("lw05_column", ctx);

        // A war camp beside a front.
        script.once("go to a camp", () -> {
            java.util.List<Encounter> camps = ctx.getWorldManager().currentCamps();
            ctx.assertTrue(!camps.isEmpty(), "every war has its camps");
            ctx.log("Camp: " + camps.get(0));
            ctx.getGameScreen().travelToChunk(camps.get(0).from, new GridPoint2(16, 16));
        });
        script.until("arrive at the camp", 300, () -> shown(ctx));
        script.key("a turn passes", Input.Keys.PERIOD, ctx);
        script.wait("the camp settles", 10);
        script.once("tents, fires and sentries", () -> {
            int tents = 0;
            for (com.bpm.minotaur.gamedata.Scenery sc : ctx.getMaze().getScenery().values()) {
                if ("camp_tent".equals(sc.getPropId())) tents++;
            }
            ctx.log("Camp tents: " + tents + ", sentries: " + ctx.countWarBand());
            ctx.assertTrue(tents >= 1, "a camp's tents");
            ctx.assertTrue(ctx.countWarBand() >= 3, "its sentries");
        });
        script.shot("lw06_camp", ctx);
    }
}
