package com.bpm.minotaur.playtest.scenarios;

import com.badlogic.gdx.Input;
import com.bpm.minotaur.gamedata.blight.CastleGate;
import com.bpm.minotaur.gamedata.shelter.ShelterNetwork;
import com.bpm.minotaur.playtest.PlaytestContext;
import com.bpm.minotaur.playtest.PlaytestScenario;
import com.bpm.minotaur.playtest.PlaytestScript;

public final class CastleTarminScenario implements PlaytestScenario {

    @Override
    public String name() {
        return "castle-tarmin";
    }

    @Override
    public String description() {
        return "Verifies the three-seal barrier locking mechanism, unsealing the gates of Castle Tarmin, and castle gate approach.";
    }

    @Override
    public void buildScript(PlaytestScript script, PlaytestContext ctx) {
        script.wait("settle", 20);
        script.once("settle traits", ctx::settleTraits);
        script.wait("traits settle", 15);
        script.once("outfit hero", () -> ctx.outfitHero(12));

        // 1. Verify initially sealed gate state
        script.once("verify gate initially sealed", () -> {
            ctx.assertTrue(!CastleGate.isOpen(), "Castle Tarmin gate must initially be sealed (0/3 seals)");
            String msg = CastleGate.knockMessage(0);
            ctx.log("Gate message with 0 seals: " + msg);
            ctx.assertTrue(msg.contains("0/3 seals held"), "Must report 0/3 seals held");
        });

        // 2. Warp out along castle road towards the castle
        script.key("warp along castle road", Input.Keys.SEMICOLON, ctx);
        script.wait("road arrive", 25);
        script.shot("pt06_castle_road_approach", ctx);

        // 3. Grant all 3 ancient seals
        script.once("grant all three seals", () -> {
            for (int r = 0; r < 3; r++) {
                ShelterNetwork.getInstance().awardSeal(r);
            }
            int held = CastleGate.sealsHeld();
            ctx.log("Seals held after granting: " + held);
            ctx.assertTrue(held >= CastleGate.SEALS_REQUIRED, "All 3 seals must be held in ShelterNetwork");
            ctx.assertTrue(CastleGate.isOpen(), "Castle Tarmin gates must now be unsealed");
            String unsealedMsg = CastleGate.knockMessage(held);
            ctx.log("Gate message with 3 seals: " + unsealedMsg);
            ctx.assertTrue(unsealedMsg.contains("burn in the gate's locks"), "Gate message must indicate seals burning in locks");
        });

        script.shot("pt06_castle_tarmin_unsealed", ctx);
    }
}
