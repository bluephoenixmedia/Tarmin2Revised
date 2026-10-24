package com.bpm.minotaur.playtest.scenarios;

import com.bpm.minotaur.managers.WorldLogManager;
import com.bpm.minotaur.playtest.PlaytestContext;
import com.bpm.minotaur.playtest.PlaytestScenario;
import com.bpm.minotaur.playtest.PlaytestScript;
import com.bpm.minotaur.screens.ThroneChoiceScreen;

/**
 * Slays Tarmin without the fight, which is not built yet (#291): the throne choice opens, the
 * Treasure is returned, and the Age ends -- the history and the World Log both move on to the
 * next, and the soul wakes again. Run it on a slot you can lose: snapshot {@code saves/} in the
 * same command, as AGENTS.md requires.
 */
public final class TarminSlainScenario implements PlaytestScenario {

    @Override
    public String name() {
        return "tarmin-slain";
    }

    @Override
    public String description() {
        return "Fire the Tarmin-slain hook, return the Treasure, and check that the Age ends in the history and the World Log.";
    }

    @Override
    public void buildScript(PlaytestScript script, PlaytestContext ctx) {
        script.wait("settle", 20);
        script.once("settle traits", ctx::settleTraits);
        script.wait("traits settle", 15);
        final int[] ageBefore = new int[1];
        script.once("slay Tarmin", () -> {
            ageBefore[0] = ctx.getWorldManager().getHistory().world().age();
            ctx.getScreen().onTarminSlain();
        });
        script.wait("the throne", 20);
        script.once("the choice opens", () ->
                ctx.assertTrue(ctx.getGame().getScreen() instanceof ThroneChoiceScreen, "the throne choice is offered"));
        script.shot("throne_choice", ctx);
        script.once("return the Treasure", () -> {
            ctx.getGame().setScreen(ctx.getScreen());
            ctx.getScreen().endAgeByChampion(false);
        });
        script.wait("the soul wakes", 40);
        script.once("settle the new life", ctx::settleTraits);
        script.once("the Age has ended", () -> {
            ctx.assertTrue(ctx.getWorldManager().getHistory().world().age() == ageBefore[0] + 1, "the history begins a new Age");
            ctx.assertTrue(WorldLogManager.getInstance().log().currentAge() == ageBefore[0] + 1, "the World Log begins a new Age");
            ctx.log("Now reigns " + ctx.getWorldManager().getHistory().world().tarminTitle());
        });
    }
}
