package com.bpm.minotaur.playtest.scenarios;

import com.badlogic.gdx.Input;
import com.bpm.minotaur.gamedata.history.Figure;
import com.bpm.minotaur.gamedata.history.House;
import com.bpm.minotaur.managers.HistoryManager;
import com.bpm.minotaur.playtest.PlaytestContext;
import com.bpm.minotaur.playtest.PlaytestScenario;
import com.bpm.minotaur.playtest.PlaytestScript;
import com.bpm.minotaur.rendering.KnellOverlay;

/**
 * Tarmin's Knell (plan K5): a great event of the history tolls the gong and puts Tarmin's words in
 * red over the view, wherever the seeker stands -- here on the surface, and two strata down.
 */
public final class TarminsKnellScenario implements PlaytestScenario {

    @Override
    public String name() {
        return "tarmins-knell";
    }

    @Override
    public String description() {
        return "Force great events of the history on the surface and in the strata; hear the gong and read Tarmin's words.";
    }

    @Override
    public void buildScript(PlaytestScript script, PlaytestContext ctx) {
        script.wait("settle", 20);
        script.once("settle traits", ctx::settleTraits);
        script.wait("traits settle", 15);
        script.once("outfit hero", () -> ctx.outfitHero(8));
        toll(script, ctx, "surface", 0);
        script.key("descend", Input.Keys.NUMPAD_7, ctx);
        script.wait("arrive", 30);
        script.key("descend again", Input.Keys.NUMPAD_7, ctx);
        script.wait("arrive deeper", 30);
        toll(script, ctx, "strata", 1);
    }

    /** Kills the lord of a house in its court, takes a turn, and checks that the knell rang and spoke. */
    private static void toll(PlaytestScript script, PlaytestContext ctx, String where, int nth) {
        int[] rungBefore = {0};
        script.once("a lord dies (" + where + ")", () -> {
            HistoryManager h = ctx.getWorldManager().getHistory();
            rungBefore[0] = ctx.getGameScreen().getSoundManager().knellsRung();
            int seen = 0;
            for (House house : h.world().livingHouses()) {
                Figure lord = h.world().lordOf(house);
                if (lord == null || !lord.isAlive() || lord.ageless) continue;
                if (seen++ < nth) continue;
                h.recordLordSlainInCourt(lord.id, -1);
                ctx.log("Forced: " + lord.name + " of " + house.name + " slain in court, at level "
                        + ctx.getWorldManager().getCurrentLevel());
                return;
            }
        });
        script.key("a turn passes (" + where + ")", Input.Keys.PERIOD, ctx);
        script.wait("the words come (" + where + ")", 50);
        script.once("the knell rang and Tarmin spoke (" + where + ")", () -> {
            KnellOverlay knell = ctx.getGameScreen().getHud().getKnellOverlay();
            ctx.assertTrue(ctx.getGameScreen().getSoundManager().knellsRung() > rungBefore[0], "the gong rang (" + where + ")");
            ctx.assertTrue(knell.isSpeaking(), "Tarmin is speaking (" + where + ")");
            ctx.log("Knell (" + where + "): " + knell.currentLine());
            ctx.assertTrue(!knell.currentLine().isEmpty(), "his words are on screen");
        });
        script.shot("knell_" + where, ctx);
        script.wait("he falls quiet (" + where + ")", 420);
    }
}
