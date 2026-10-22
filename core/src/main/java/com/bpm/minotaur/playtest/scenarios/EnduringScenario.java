package com.bpm.minotaur.playtest.scenarios;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.math.GridPoint2;
import com.bpm.minotaur.gamedata.history.HistoryWorld;
import com.bpm.minotaur.gamedata.history.House;
import com.bpm.minotaur.gamedata.history.Megabeast;
import com.bpm.minotaur.gamedata.history.Nemesis;
import com.bpm.minotaur.gamedata.history.OldGod;
import com.bpm.minotaur.gamedata.history.SeekerDeath;
import com.bpm.minotaur.managers.BonesManager;
import com.bpm.minotaur.managers.HistoryManager;
import com.bpm.minotaur.playtest.PlaytestContext;
import com.bpm.minotaur.playtest.PlaytestScenario;
import com.bpm.minotaur.playtest.PlaytestScript;
import com.bpm.minotaur.screens.GameScreen;
import com.bpm.minotaur.screens.PlayerDeathScreen;

import java.util.ArrayList;
import java.util.List;

/**
 * Dies for real and checks that the Enduring outlive the Shifting (#289): the Houses and their
 * lords, the Gashes and their holders, the living Megabeasts, a Nemesis, a spent god's dark
 * shrine and the bones of the dead are all still in the world when the maze has been remade.
 * It checks what the world holds, not where the new maze has put it.
 *
 * <p>The death is real and the slot keeps it (a spent god, a Nemesis, a grave): snapshot
 * {@code saves/} in the same command that runs it, as AGENTS.md requires, and restore from that
 * copy.
 */
public final class EnduringScenario implements PlaytestScenario {

    private final List<String> before = new ArrayList<>();
    private int bonesBefore;
    private int nemesisId = -1;

    @Override
    public String name() {
        return "enduring";
    }

    @Override
    public String description() {
        return "Die and shift the maze; the Houses, Gashes, Megabeasts, a Nemesis, a dark shrine and the bones endure.";
    }

    private static List<String> census(HistoryWorld world) {
        List<String> seen = new ArrayList<>();
        // Houses, not lords: a lord may die of age in any season, and that is the history, not the Shifting.
        for (House h : world.livingHouses()) seen.add("house " + h.name);
        for (int g = 0; g < HistoryWorld.GASH_COUNT; g++) {
            House holder = world.gashHolder(g);
            seen.add("gash " + world.gashName(g) + " held by " + (holder != null ? holder.name : "-"));
        }
        for (Megabeast b : world.megabeasts()) {
            if (b.isAlive()) seen.add("beast " + b.name);
        }
        return seen;
    }

    @Override
    public void buildScript(PlaytestScript script, PlaytestContext ctx) {
        script.wait("settle", 20);
        script.once("settle traits", ctx::settleTraits);
        script.wait("traits settle", 15);
        script.once("before", () -> {
            HistoryManager history = ctx.getWorldManager().getHistory();
            before.addAll(census(history.world()));
            history.recordGodSpent(OldGod.IXXATH);
            GridPoint2 chunk = ctx.getWorldManager().getCurrentPlayerChunkId();
            Nemesis n = history.recordSeekerFell(SeekerDeath.by("a Kobold", "KOBOLD", -1).of("A Former Life")
                    .at(ctx.getWorldManager().getCurrentLevel(), chunk.x, chunk.y));
            nemesisId = n != null ? n.id : -1;
            bonesBefore = BonesManager.getInstance().getEligibleBonesForStrata(1).size();
            ctx.log("Before the death: " + before.size() + " enduring things, Nemesis " + (n != null ? n.title() : "none"));
        });
        script.once("die", () -> ctx.getPlayer().getStats().setCurrentHP(0));
        script.until("the death screen", 1200, () -> {
            if (ctx.getGame().getScreen() instanceof PlayerDeathScreen) return true;
            if (ctx.getFrame() % 20 == 0) ctx.press(Input.Keys.PERIOD);
            return false;
        });
        script.wait("mourn", 30);
        script.until("awaken", 600, () -> {
            if (ctx.getGame().getScreen() instanceof GameScreen) return true;
            if (ctx.getFrame() % 20 == 0) ctx.press(Input.Keys.ENTER);
            return false;
        });
        script.wait("wake", 40);
        script.once("settle traits after", ctx::settleTraits);
        script.wait("traits settle after", 15);
        script.once("after", () -> {
            HistoryWorld world = ctx.getWorldManager().getHistory().world();
            List<String> after = census(world);
            for (String thing : before) {
                ctx.assertTrue(after.contains(thing), "endures the Shifting: " + thing);
            }
            Nemesis n = world.nemesis(nemesisId);
            ctx.assertTrue(n != null && n.isAlive(), "the Nemesis endures, alive, in its region");
            ctx.assertTrue(world.isSpent(OldGod.IXXATH), "the spent god's shrine stays dark");
            ctx.assertTrue(BonesManager.getInstance().getEligibleBonesForStrata(1).size() >= bonesBefore,
                    "the bones of the dead are not lost to the Shifting");
            ctx.log("After the death: " + after.size() + " enduring things checked");
        });
        script.shot("enduring_after", ctx);
    }
}
