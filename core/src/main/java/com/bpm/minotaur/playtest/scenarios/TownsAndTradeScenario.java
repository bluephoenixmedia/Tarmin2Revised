package com.bpm.minotaur.playtest.scenarios;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.math.GridPoint2;
import com.bpm.minotaur.gamedata.Scenery;
import com.bpm.minotaur.gamedata.history.town.Town;
import com.bpm.minotaur.playtest.PlaytestContext;
import com.bpm.minotaur.playtest.PlaytestScenario;
import com.bpm.minotaur.playtest.PlaytestScript;
import com.bpm.minotaur.screens.TalkScreen;

public final class TownsAndTradeScenario implements PlaytestScenario {

    @Override
    public String name() {
        return "towns-and-trade";
    }

    @Override
    public String description() {
        return "Warp to an underground town, locate Reeve, initiate dialogue, inquire about quests and rumors, and depart.";
    }

    @Override
    public void buildScript(PlaytestScript script, PlaytestContext ctx) {
        script.wait("settle", 20);
        script.once("settle traits", ctx::settleTraits);
        script.wait("traits settle", 15);
        script.once("outfit hero", () -> ctx.outfitHero(3));

        // 1. Warp to underground town
        script.key("warp to underground town", Input.Keys.NUMPAD_0, ctx);
        script.wait("town arrive", 25);

        script.once("verify town existence and population", () -> {
            Town t = ctx.getWorldManager().townHere();
            ctx.assertTrue(t != null, "Must warp into a valid town");
            ctx.log("Town: " + t.name + " (" + t.allegiance.displayName + ")");
            ctx.assertTrue(t.allegiance != null, "Town must have a faction allegiance");
            ctx.assertTrue(!ctx.folkHere().isEmpty(), "Town must contain townsfolk scenery");
            ctx.assertTrue(ctx.guards() > 0, "Town must contain guards");
            ctx.log("Town folk standing: " + ctx.folkHere().size() + ", guards: " + ctx.guards());
        });

        script.shot("pt04_town_plaza", ctx);

        // 2. Steer toward Reeve until TalkScreen opens
        int[] walkTicks = {0};
        script.until("walk to Reeve and initiate talk", 600, () -> {
            if (ctx.getGame().getScreen() instanceof TalkScreen) return true;
            walkTicks[0]++;
            if (ctx.getFrame() % 4 != 0) return false;

            Scenery reeve = ctx.reeve();
            if (reeve == null) {
                // If Reeve not found by role, pick first townsfolk
                if (!ctx.folkHere().isEmpty()) reeve = ctx.folkHere().get(0);
                else return true;
            }
            ctx.steerToward(new GridPoint2((int) reeve.getPosition().x, (int) reeve.getPosition().y));
            if (walkTicks[0] > 40 && !(ctx.getGame().getScreen() instanceof TalkScreen)) {
                ctx.talkTo(reeve);
            }
            return ctx.getGame().getScreen() instanceof TalkScreen;
        });

        script.once("assert conversation open", () -> {
            boolean inTalk = ctx.getGame().getScreen() instanceof TalkScreen;
            ctx.assertTrue(inTalk, "TalkScreen must be active after walking to Reeve/folk");
        });

        script.shot("pt04_town_talk", ctx);

        // 3. Dialogue interactions
        script.once("ask for quest", () -> {
            boolean ok = ctx.talk("Quest");
            ctx.assertTrue(ok, "Reeve must offer Quest option");
        });
        script.wait("read quest", 10);
        script.shot("pt04_town_quest", ctx);

        script.once("ask for rumours", () -> {
            boolean ok = ctx.talk("Rumours");
            ctx.assertTrue(ok, "Town conversation must offer Rumours option");
        });
        script.wait("read rumours", 10);
        script.shot("pt04_town_rumours", ctx);

        script.once("leave conversation", () -> {
            ctx.talk("Leave");
        });
        script.wait("conversation close settle", 15);

        script.once("assert conversation closed cleanly", () -> {
            boolean inTalk = ctx.getGame().getScreen() instanceof TalkScreen;
            ctx.assertTrue(!inTalk, "TalkScreen must close cleanly after Leave action");
        });
    }
}
