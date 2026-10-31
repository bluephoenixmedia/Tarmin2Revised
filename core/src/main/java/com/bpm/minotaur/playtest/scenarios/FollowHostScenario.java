package com.bpm.minotaur.playtest.scenarios;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.math.GridPoint2;
import com.bpm.minotaur.gamedata.history.House;
import com.bpm.minotaur.gamedata.history.War;
import com.bpm.minotaur.gamedata.history.favour.Favour;
import com.bpm.minotaur.gamedata.history.war.SeatMap;
import com.bpm.minotaur.gamedata.history.war.army.Army;
import com.bpm.minotaur.gamedata.history.war.army.ArmyBattle;
import com.bpm.minotaur.gamedata.history.war.army.ArmyLayer;
import com.bpm.minotaur.managers.HistoryManager;
import com.bpm.minotaur.playtest.PlaytestContext;
import com.bpm.minotaur.playtest.PlaytestScenario;
import com.bpm.minotaur.playtest.PlaytestScript;

/**
 * Following a host of the liege into a battle (Living War A36): asked at its camp, marching on chunk
 * by chunk, and standing in its line when it meets the enemy.
 */
public final class FollowHostScenario implements PlaytestScenario {

    @Override
    public String name() {
        return "follow-host";
    }

    @Override
    public String description() {
        return "Asks a camped host to be followed, marches on with it, and stands in its line in a battle.";
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

        Army[] host = {null};
        Army[] enemy = {null};
        GridPoint2 camp = new GridPoint2(14, 6);
        GridPoint2 ahead = new GridPoint2(14, 10);
        script.once("a host of the liege camps here, an enemy on the road ahead", () -> {
            ctx.outfitHero(10);
            HistoryManager h = history(ctx);
            SeatMap seats = ctx.getWorldManager().houseSeats();
            // Whichever war this world rolled: the enemy needs no seat, only soldiers on the road.
            War war = null;
            int liege = -1;
            for (War w : h.world().activeWars()) {
                for (int side : new int[] {w.attackerId, w.defenderId}) {
                    House o = h.world().house(side);
                    if (o != null && !o.holdsCastle && seats.seat(side) != null) {
                        war = w;
                        liege = side;
                    }
                }
            }
            ctx.assertTrue(war != null, "a war with a seated house in it");
            h.roster().armies.removeIf(a -> a.isHost());
            host[0] = soldiers(9101, liege, war.id, camp);
            enemy[0] = soldiers(9102, war.enemyOf(liege), war.id, ahead);
            h.roster().armies.add(host[0]);
            h.roster().armies.add(enemy[0]);
            h.favour().set(liege, Favour.FRIEND_AT);
            h.swear(liege);
            ctx.getGameScreen().travelToChunk(camp, new GridPoint2(16, 16));
        });
        script.until("arrive", 300, () -> shown(ctx));
        script.key("a turn passes", Input.Keys.PERIOD, ctx);
        script.wait("the camp goes up", 10);
        script.key("ask to follow", Input.Keys.Y, ctx);
        script.wait("asked", 6);
        script.once("the captain agrees", () -> {
            ctx.assertTrue(history(ctx).oath().following == host[0].id, "following the host");
            ctx.log("Following host " + host[0].id + " of " + host[0].soldiers);
        });
        script.shot("fh01_in_its_camp", ctx);
        script.once("the host marches", () -> {
            ArmyLayer layer = ctx.getWorldManager().armyLayer();
            layer.marchNow(host[0], ahead.x, ahead.y);
            ctx.assertTrue(!host[0].halted(), "on the road");
        });
        script.key("march on", Input.Keys.Y, ctx);
        script.until("the host meets the enemy", 600, () -> {
            if (ctx.getFrame() % 4 == 0) {
                ctx.press(Input.Keys.END);
                if (!host[0].battle) ctx.press(Input.Keys.Y);
            }
            return host[0].battle;
        });
        script.once("the march kept to the host, and the routes are its own", () -> {
            GridPoint2 at = ctx.getWorldManager().getCurrentPlayerChunkId();
            ctx.log("Host at " + host[0].x + "," + host[0].y + "; the player at " + at);
            ctx.assertTrue(host[0].x == at.x && host[0].y == at.y, "the player is in its chunk");
            ctx.assertTrue(at.y != camp.y || at.x != camp.x, "and has come along the road");
        });
        ArmyBattle[] battle = {null};
        script.until("the lines close", 300, () -> {
            if (ctx.getFrame() % 2 == 0) {
                ctx.press(Input.Keys.END);
                ctx.press(Input.Keys.PERIOD);
            }
            GridPoint2 at = ctx.getWorldManager().getCurrentPlayerChunkId();
            battle[0] = ctx.getWorldManager().armyLayer().battleIn(at.x, at.y);
            return "BATTLE".equals(ctx.battlePhase()) && ctx.countWarBand() > 1;
        });
        script.wait("drawn", 4);
        script.once("in the line from the start, with no side to choose", () -> {
            ctx.assertTrue(battle[0] != null, "a battle here");
            int side = ArmyBattle.holds(battle[0].armiesA, host[0].id) ? 1 : 2;
            ctx.assertTrue(battle[0].playerSide == side, "standing with the host");
            ctx.log("Standing on side " + side + " at " + ctx.getPlayer().getPosition());
        });
        script.shot("fh02_in_the_line", ctx);
    }

    private static Army soldiers(int id, int house, int war, GridPoint2 at) {
        Army a = new Army();
        a.id = id;
        a.houseId = house;
        a.warId = war;
        a.soldiers = 30;
        a.captainId = -1;
        a.x = at.x;
        a.y = at.y;
        a.marchFromX = at.x;
        a.marchFromY = at.y;
        a.objective = Army.Objective.GUARD;
        return a;
    }
}
