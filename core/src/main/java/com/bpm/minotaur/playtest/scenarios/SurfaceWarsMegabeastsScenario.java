package com.bpm.minotaur.playtest.scenarios;

import com.badlogic.gdx.Input;
import com.bpm.minotaur.gamedata.monster.Monster;
import com.bpm.minotaur.managers.BattleDirector;
import com.bpm.minotaur.playtest.PlaytestContext;
import com.bpm.minotaur.playtest.PlaytestScenario;
import com.bpm.minotaur.playtest.PlaytestScript;

public final class SurfaceWarsMegabeastsScenario implements PlaytestScenario {

    @Override
    public String name() {
        return "surface-wars-megabeasts";
    }

    @Override
    public String description() {
        return "Exercises megabeast summoning and weakness configuration, then sounds surface war horns and monitors battle lines.";
    }

    @Override
    public void buildScript(PlaytestScript script, PlaytestContext ctx) {
        script.wait("settle", 20);
        script.once("settle traits", ctx::settleTraits);
        script.wait("traits settle", 15);
        script.once("outfit hero", () -> ctx.outfitHero(8));

        // Part 1: Megabeast summoning
        script.key("call a megabeast", Input.Keys.NUMPAD_DIVIDE, ctx);
        script.repeat("wait for megabeast", 3, Input.Keys.PERIOD, ctx);

        script.once("verify megabeast spawned", () -> {
            Monster beast = null;
            for (Monster m : ctx.getMaze().getMonsters().values()) {
                if (m != null && m.getMegabeastId() >= 0) {
                    beast = m;
                    break;
                }
            }
            ctx.assertTrue(beast != null, "Megabeast must be spawned in chunk");
            ctx.assertTrue(beast.isAlive(), "Megabeast must be alive");
            ctx.log("Megabeast: " + beast.getDisplayName() + " (HP " + beast.getCurrentHP() + ", weak to " + beast.getExtraWeakness() + ")");
        });

        script.shot("pt05_megabeast", ctx);

        script.once("dismiss megabeast", () -> {
            for (Monster m : new java.util.ArrayList<>(ctx.getMaze().getMonsters().values())) {
                if (m != null && m.getMegabeastId() >= 0) {
                    ctx.getMaze().removeMonster(m);
                }
            }
        });

        // Part 2: Surface War
        script.key("warp to surface road shelter", Input.Keys.SEMICOLON, ctx);
        script.wait("arrive surface", 30);

        script.key("sound the horns", Input.Keys.NUMPAD_MULTIPLY, ctx);
        script.shot("pt05_horns", ctx);

        script.repeat("wait warning turns", BattleDirector.WARNING_TURNS + 1, Input.Keys.PERIOD, ctx);

        script.once("verify battle lines formed", () -> {
            int warBands = ctx.countWarBand();
            ctx.log("Battle phase: " + ctx.battlePhase() + ", soldiers on field: " + warBands);
            ctx.assertTrue(warBands > 0, "War-band soldiers must be spawned on the battlefield");
        });

        script.shot("pt05_lines_closed", ctx);

        script.until("wait out battle clash", 3000, () -> {
            if (ctx.getFrame() % 2 != 0) return false;
            ctx.press(Input.Keys.END);
            ctx.press(Input.Keys.PERIOD);
            return ctx.getWarManager().active() == null;
        });

        script.once("verify battle aftermath", () -> {
            ctx.log("Battle resolved. Items on field: " + ctx.getMaze().getItems().size());
            ctx.assertTrue(ctx.getWarManager().active() == null, "Battle should complete cleanly");
        });

        script.shot("pt05_after_battle", ctx);
    }
}
