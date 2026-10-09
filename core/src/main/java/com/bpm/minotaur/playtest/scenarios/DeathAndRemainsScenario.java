package com.bpm.minotaur.playtest.scenarios;

import com.bpm.minotaur.gamedata.GameMode;
import com.bpm.minotaur.gamedata.Scenery;
import com.bpm.minotaur.gamedata.bones.BonesData;
import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.gamedata.player.Player;
import com.bpm.minotaur.managers.BonesManager;
import com.bpm.minotaur.playtest.PlaytestContext;
import com.bpm.minotaur.playtest.PlaytestScenario;
import com.bpm.minotaur.playtest.PlaytestScript;

import java.util.List;

public final class DeathAndRemainsScenario implements PlaytestScenario {

    @Override
    public String name() {
        return "death-and-remains";
    }

    @Override
    public String description() {
        return "Verifies player permadeath recording, BonesData persistence, fallen hero remains extraction, and corpse binding.";
    }

    @Override
    public void buildScript(PlaytestScript script, PlaytestContext ctx) {
        script.wait("settle", 20);
        script.once("settle traits", ctx::settleTraits);
        script.wait("traits settle", 15);
        script.once("outfit hero", () -> ctx.outfitHero(4));

        script.once("verify bones recording on death", () -> {
            Player p = ctx.getPlayer();
            ctx.assertTrue(p != null, "Player must exist");

            // Record death bones
            BonesData bones = BonesManager.getInstance().recordBonesOnDeath(
                    p, 3, "Fell bravely in the automated playtest", GameMode.ADVANCED, "the Fearless");

            ctx.assertTrue(bones != null, "BonesManager must generate a BonesData record on death");
            ctx.assertTrue(bones.id != null, "BonesData must possess a unique UUID");
            ctx.log("Recorded bones: ID " + bones.id + ", Name: " + bones.playerName + ", Strata: " + bones.strataDepth);

            boolean exists = BonesManager.getInstance().bonesFileExists(bones);
            ctx.assertTrue(exists, "Bones json file must exist on disk");

            // Extract loot
            List<Item> loot = bones.getOrExtractItems(ctx.getGame().getItemDataManager(), ctx.getGame().getAssetManager());
            ctx.log("Recoverable items from remains: " + loot.size());
            ctx.assertTrue(!loot.isEmpty(), "Fallen hero bones must retain inventory equipment");

            // Bind to scenery corpse
            Scenery corpse = new Scenery(Scenery.SceneryType.DECOMPOSING_CORPSE, (int) p.getPosition().x, (int) p.getPosition().y);
            corpse.setBonesData(bones);
            ctx.assertTrue(corpse.getBonesData() != null, "Corpse scenery must retain bound BonesData");
            ctx.assertEquals(bones.id, corpse.getBonesData().id, "Bound BonesData ID must match");
        });

        script.shot("pt07_death_and_remains", ctx);
    }
}
