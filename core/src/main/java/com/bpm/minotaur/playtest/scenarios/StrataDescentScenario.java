package com.bpm.minotaur.playtest.scenarios;

import com.badlogic.gdx.Input;
import com.bpm.minotaur.generation.Stratum;
import com.bpm.minotaur.playtest.PlaytestContext;
import com.bpm.minotaur.playtest.PlaytestScenario;
import com.bpm.minotaur.playtest.PlaytestScript;

public final class StrataDescentScenario implements PlaytestScenario {

    @Override
    public String name() {
        return "strata-descent";
    }

    @Override
    public String description() {
        return "Verifies descending through subterranean strata: Fungal Forest, Flooded Halls, Ossuary, and Magma Deeps.";
    }

    @Override
    public void buildScript(PlaytestScript script, PlaytestContext ctx) {
        script.wait("settle", 20);

        script.once("settle traits", ctx::settleTraits);
        script.wait("traits settle", 15);
        script.once("outfit hero", () -> ctx.outfitHero(5));

        // 1. Verify Surface
        script.once("verify surface start", () -> {
            Stratum s = ctx.getMaze().getStratum();
            ctx.log("Starting stratum: " + s.displayName + " (level " + ctx.getWorldManager().getCurrentLevel() + ")");
            ctx.assertTrue(s == Stratum.MAZE, "Starting stratum must be MAZE");
        });

        // 2. Descend to Stratum 2
        script.key("descend to stratum 2", Input.Keys.NUMPAD_7, ctx);
        script.wait("arrive stratum 2", 30);
        script.once("verify stratum 2", () -> {
            int level = ctx.getWorldManager().getCurrentLevel();
            Stratum s = ctx.getMaze().getStratum();
            ctx.log("Stratum after 1st descent: " + s.displayName + " (level " + level + ")");
            ctx.assertEquals(2, level, "Must be at level 2");
            Stratum expected = com.bpm.minotaur.generation.StratumMap.of(ctx.getWorldManager().getWorldSeed(),
                    ctx.getWorldManager().getCurrentPlayerChunkId(), level);
            ctx.assertEquals(expected, s, "Stratum at level 2 must match StratumMap");
        });
        script.shot("pt02_strata_level2", ctx);

        // 3. Descend to Stratum 3
        script.key("descend to stratum 3", Input.Keys.NUMPAD_7, ctx);
        script.wait("arrive stratum 3", 30);
        script.once("verify stratum 3", () -> {
            int level = ctx.getWorldManager().getCurrentLevel();
            Stratum s = ctx.getMaze().getStratum();
            ctx.log("Stratum after 2nd descent: " + s.displayName + " (level " + level + ")");
            ctx.assertEquals(3, level, "Must be at level 3");
            Stratum expected = com.bpm.minotaur.generation.StratumMap.of(ctx.getWorldManager().getWorldSeed(),
                    ctx.getWorldManager().getCurrentPlayerChunkId(), level);
            ctx.assertEquals(expected, s, "Stratum at level 3 must match StratumMap");
        });
        script.shot("pt02_strata_level3", ctx);

        // 4. Descend to Stratum 4
        script.key("descend to stratum 4", Input.Keys.NUMPAD_7, ctx);
        script.wait("arrive stratum 4", 30);
        script.once("verify stratum 4", () -> {
            int level = ctx.getWorldManager().getCurrentLevel();
            Stratum s = ctx.getMaze().getStratum();
            ctx.log("Stratum after 3rd descent: " + s.displayName + " (level " + level + ")");
            ctx.assertEquals(4, level, "Must be at level 4");
            Stratum expected = com.bpm.minotaur.generation.StratumMap.of(ctx.getWorldManager().getWorldSeed(),
                    ctx.getWorldManager().getCurrentPlayerChunkId(), level);
            ctx.assertEquals(expected, s, "Stratum at level 4 must match StratumMap");
        });
        script.shot("pt02_strata_level4", ctx);

        // 5. Descend to Stratum 5
        script.key("descend to stratum 5", Input.Keys.NUMPAD_7, ctx);
        script.wait("arrive stratum 5", 30);
        script.once("verify stratum 5", () -> {
            int level = ctx.getWorldManager().getCurrentLevel();
            Stratum s = ctx.getMaze().getStratum();
            ctx.log("Stratum after 4th descent: " + s.displayName + " (level " + level + ")");
            ctx.assertEquals(5, level, "Must be at level 5");
            Stratum expected = com.bpm.minotaur.generation.StratumMap.of(ctx.getWorldManager().getWorldSeed(),
                    ctx.getWorldManager().getCurrentPlayerChunkId(), level);
            ctx.assertEquals(expected, s, "Stratum at level 5 must match StratumMap");
        });
        script.shot("pt02_strata_level5", ctx);
    }
}
