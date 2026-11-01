package com.bpm.minotaur.playtest.scenarios;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.math.GridPoint2;
import com.bpm.minotaur.gamedata.Direction;
import com.bpm.minotaur.gamedata.history.House;
import com.bpm.minotaur.gamedata.history.favour.Contract;
import com.bpm.minotaur.gamedata.history.favour.Favour;
import com.bpm.minotaur.gamedata.history.war.SeatRing;
import com.bpm.minotaur.gamedata.history.war.army.Army;
import com.bpm.minotaur.gamedata.history.war.army.SeatSiege;
import com.bpm.minotaur.gamedata.monster.Monster;
import com.bpm.minotaur.managers.HistoryManager;
import com.bpm.minotaur.playtest.PlaytestContext;
import com.bpm.minotaur.playtest.PlaytestScenario;
import com.bpm.minotaur.playtest.PlaytestScript;

import java.util.ArrayList;

/**
 * A sworn player relieving their liege's seat (Living War A42): through the ring's south gate on foot,
 * inside when the besiegers give it up, and the contract kept for being there.
 */
public final class SeatReliefScenario implements PlaytestScenario {

    @Override
    public String name() {
        return "seat-relief";
    }

    @Override
    public String description() {
        return "Walks through the ring of a besieged liege's seat, and keeps the relief contract when the siege is raised.";
    }

    private static boolean shown(PlaytestContext ctx) {
        return ctx.getMaze() != null && ctx.getMaze() == ctx.getWorldManager().getCurrentMaze();
    }

    private static HistoryManager history(PlaytestContext ctx) {
        return ctx.getWorldManager().getHistory();
    }

    @Override
    public void buildScript(PlaytestScript script, PlaytestContext ctx) {
        script.wait("settle", 20);
        script.once("settle traits", ctx::settleTraits);
        script.wait("traits settle", 15);
        script.key("the armies muster", Input.Keys.PERIOD, ctx);
        script.wait("a moment", 10);

        SeatSiege[] siege = {null};
        script.once("the liege's seat is under siege, and the task is to relieve it", () -> {
            ctx.outfitHero(10);
            HistoryManager h = history(ctx);
            siege[0] = SeatAssaultScenario.layASiege(ctx, new House[2]);
            SeatSiege s = siege[0];
            s.works = 0;
            h.roster().armies.removeIf(o -> o.houseId == s.holder && o.isHost());
            h.favour().set(s.holder, Favour.FRIEND_AT);
            h.swear(s.holder);
            Contract relief = new Contract();
            relief.kind = Contract.Kind.RELIEVE_SEAT;
            relief.giverId = s.holder;
            relief.targetId = s.besieger;
            relief.targetSiege = s.id;
            relief.chunkX = s.x;
            relief.chunkY = s.y;
            h.oath().contract = relief;
            ctx.getGameScreen().travelToChunk(new GridPoint2(s.x, s.y), new GridPoint2(3, 3));
        });
        script.until("arrive", 300, () -> shown(ctx));
        script.once("the natives and the sentries are out of the way", () -> {
            for (Monster m : new ArrayList<>(ctx.getMaze().getMonsters().values())) ctx.getMaze().removeMonster(m);
            ctx.dismissModals();
        });
        script.key("a turn passes", Input.Keys.PERIOD, ctx);
        script.wait("the ring goes up", 10);
        script.once("stand before the south gate", () -> {
            GridPoint2 gate = SeatRing.gates(ctx.getMaze().getWidth(), ctx.getMaze().getHeight()).get(1);
            ctx.assertTrue(ctx.getMaze().isPassable(gate.x, gate.y), "the gate stands open");
            ctx.getPlayer().setPosition(gate.x + 0.5f, gate.y - 1 + 0.5f);
            ctx.getPlayer().setFacing(Direction.NORTH);
            for (Monster m : new ArrayList<>(ctx.getMaze().getMonsters().values())) ctx.getMaze().removeMonster(m);
        });
        script.wait("drawn", 6);
        script.shot("sr01_the_gate", ctx);
        script.until("walk through it", 120, () -> {
            for (Monster m : new ArrayList<>(ctx.getMaze().getMonsters().values())) ctx.getMaze().removeMonster(m);
            if (ctx.getFrame() % 8 == 0) ctx.press(Input.Keys.UP);
            return SeatRing.inside(ctx.getMaze().getWidth(), ctx.getMaze().getHeight(),
                    (int) ctx.getPlayer().getPosition().x, (int) ctx.getPlayer().getPosition().y);
        });
        script.wait("a turn within", 8);
        script.shot("sr02_within_the_walls", ctx);
        script.once("the besiegers give it up", () -> {
            HistoryManager h = history(ctx);
            ctx.assertTrue(!h.oath().contract.done, "nothing is done yet");
            for (int id : siege[0].armies) {
                Army a = h.roster().army(id);
                if (a != null) a.objective = Army.Objective.RECOVER;
            }
        });
        script.until("the siege is raised", 120, () -> {
            if (ctx.getFrame() % 2 == 0) ctx.press(Input.Keys.PERIOD);
            return history(ctx).roster().sieges.isEmpty() || history(ctx).roster().siegeAt(siege[0].x, siege[0].y) == null;
        });
        script.wait("told", 6);
        script.once("the contract is kept", () -> {
            Contract c = history(ctx).oath().contract;
            ctx.assertTrue(c != null && c.done, "relief done, for being inside");
        });
        script.shot("sr03_relieved", ctx);
    }
}
