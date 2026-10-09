package com.bpm.minotaur.playtest.scenarios;

import com.badlogic.gdx.Input;
import com.bpm.minotaur.gamedata.monster.Monster;
import com.bpm.minotaur.managers.BattleDirector;
import com.bpm.minotaur.playtest.PlaytestContext;
import com.bpm.minotaur.playtest.PlaytestScenario;
import com.bpm.minotaur.playtest.PlaytestScript;

public final class SurfaceWarsMegabeastsScenario implements PlaytestScenario {

    /** Plan T2.4: the frame-time record is taken with this many fighting. */
    static final int FULL_FIELD = 40;
    /** Frames timed at a full field. */
    static final int SAMPLE_FRAMES = 300;
    static final int FULL_FIELD_FRAMES = 3000;
    /** 30 frames a second, on average, at worst. */
    static final float FRAME_BUDGET_MS = 1000f / 30f;

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

        // Plan T2.4: frame time must hold with forty fighting. A battle's lines hold sixteen a side,
        // so the field is topped up to forty with more of the same war-bands, held there while the
        // battle runs, and every frame of that stretch is timed.
        float[] frames = {0f, 0f, 0f}; // count, total seconds, worst seconds
        int[] added = {0};
        script.until("hold forty on the field", FULL_FIELD_FRAMES, () -> {
            java.util.List<Monster> bands = new java.util.ArrayList<>();
            for (Monster m : ctx.getMaze().getMonsters().values()) if (m != null && m.isWarBand() && m.isAlive()) bands.add(m);
            for (int i = 0; bands.size() + i < FULL_FIELD && !bands.isEmpty(); i++) {
                Monster like = bands.get(i % bands.size());
                com.badlogic.gdx.math.GridPoint2 at = com.bpm.minotaur.managers.WorldManager.findSafeArrivalTile(ctx.getMaze(),
                        (int) like.getPosition().x + 1, (int) like.getPosition().y);
                Monster more = at == null ? null : ctx.getWorldManager().recruit(like.getType().name(), at.x, at.y);
                if (more == null) break;
                more.setFaction(like.getFaction());
                more.setHouseId(like.getHouseId());
                more.setWarBand(true);
                ctx.getMaze().addMonster(more);
                added[0]++;
            }
            if (ctx.countWarBand() >= FULL_FIELD) {
                float dt = com.badlogic.gdx.Gdx.graphics.getDeltaTime();
                frames[0]++;
                frames[1] += dt;
                frames[2] = Math.max(frames[2], dt);
            }
            if (ctx.getFrame() % 2 == 0) {
                ctx.press(Input.Keys.END);
                ctx.press(Input.Keys.PERIOD);
            }
            return frames[0] >= SAMPLE_FRAMES || ctx.getWarManager().active() == null;
        });

        script.once("record frame time", () -> {
            ctx.assertTrue(frames[0] > 0, "the field held " + FULL_FIELD + " combatants");
            float avgMs = frames[1] / frames[0] * 1000f;
            ctx.log(String.format(java.util.Locale.ROOT, "Frame time at %d combatants (%d topped up): avg %.1f ms, worst %.1f ms over %d frames",
                    FULL_FIELD, added[0], avgMs, frames[2] * 1000f, (int) frames[0]));
            ctx.assertTrue(avgMs <= FRAME_BUDGET_MS, "frame time holds with forty fighting (avg " + avgMs + " ms)");
        });

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
