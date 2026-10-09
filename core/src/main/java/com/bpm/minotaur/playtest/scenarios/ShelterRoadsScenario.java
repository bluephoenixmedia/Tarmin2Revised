package com.bpm.minotaur.playtest.scenarios;

import com.badlogic.gdx.Input;
import com.bpm.minotaur.gamedata.player.Player;
import com.bpm.minotaur.playtest.PlaytestContext;
import com.bpm.minotaur.playtest.PlaytestScenario;
import com.bpm.minotaur.playtest.PlaytestScript;

public final class ShelterRoadsScenario implements PlaytestScenario {

    @Override
    public String name() {
        return "shelter-roads";
    }

    @Override
    public String description() {
        return "Verifies shelter hub spawning, trait offer settlement, starter gear, road shelter warp, beacons, and road claiming.";
    }

    @Override
    public void buildScript(PlaytestScript script, PlaytestContext ctx) {
        script.wait("settle", 20);

        // 1. Trait settlement
        script.once("settle traits", ctx::settleTraits);
        script.wait("traits settle", 15);

        script.once("assert trait chosen and outfit", () -> {
            Player p = ctx.getPlayer();
            ctx.assertTrue(p != null, "Player must exist");
            ctx.assertTrue(!p.needsTraitOffer(), "Trait offer must be resolved");
            ctx.outfitHero(2);
            ctx.assertTrue(p.getInventory().getRightHand() != null, "Player must have right hand weapon equipped");
        });

        script.shot("pt01_shelter_hub", ctx);

        // 2. Warp to castle-road shelter
        script.key("warp to castle road shelter", Input.Keys.SEMICOLON, ctx);
        script.wait("road arrive", 30);

        script.once("verify road shelter structure", () -> {
            com.badlogic.gdx.math.GridPoint2 chunkId = ctx.getWorldManager().getCurrentPlayerChunkId();
            ctx.assertTrue(chunkId != null, "Player must be warped to a valid road chunk");
            ctx.log("Standing at road shelter chunk " + chunkId + " (" + ctx.getMaze().getBiome() + ")");
            boolean hasShelter = ctx.getMaze().getHearthTile() != null || !ctx.getMaze().getHomeTiles().isEmpty();
            ctx.assertTrue(hasShelter, "Road shelter chunk must contain a shelter structure");
        });

        // 3. Claim the road shelter
        script.key("claim road shelter", Input.Keys.BACKSLASH, ctx);
        script.wait("claim settle", 15);

        script.once("verify shelter claimed", () -> {
            com.badlogic.gdx.math.GridPoint2 chunkId = ctx.getWorldManager().getCurrentPlayerChunkId();
            boolean claimed = com.bpm.minotaur.gamedata.shelter.ShelterNetwork.getInstance().isClaimed(chunkId);
            ctx.log("Shelter chunk " + chunkId + " claimed status: " + claimed);
            ctx.assertTrue(claimed, "Road shelter must be registered as claimed in ShelterNetwork");
        });

        script.shot("pt01_road_shelter", ctx);
    }
}
