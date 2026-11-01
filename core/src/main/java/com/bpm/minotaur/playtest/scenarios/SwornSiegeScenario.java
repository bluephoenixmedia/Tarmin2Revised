package com.bpm.minotaur.playtest.scenarios;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.math.GridPoint2;
import com.bpm.minotaur.gamedata.history.House;
import com.bpm.minotaur.gamedata.history.favour.Contract;
import com.bpm.minotaur.gamedata.history.favour.Favour;
import com.bpm.minotaur.gamedata.history.war.SeatRing;
import com.bpm.minotaur.gamedata.history.war.army.Army;
import com.bpm.minotaur.gamedata.history.war.army.ArmyBattle;
import com.bpm.minotaur.gamedata.history.war.army.ArmyLayer;
import com.bpm.minotaur.gamedata.history.war.army.SeatSiege;
import com.bpm.minotaur.gamedata.monster.Monster;
import com.bpm.minotaur.managers.HistoryManager;
import com.bpm.minotaur.playtest.PlaytestContext;
import com.bpm.minotaur.playtest.PlaytestScenario;
import com.bpm.minotaur.playtest.PlaytestScript;
import com.bpm.minotaur.screens.ChoiceScreen;

import java.util.ArrayList;

/**
 * A sworn player in a siege (Living War A42): stands with the besiegers, waits, is asked for the first
 * ladder, is woken by the assault, comes over the wall and fights until the seat falls.
 */
public final class SwornSiegeScenario implements PlaytestScenario {

    @Override
    public String name() {
        return "sworn-siege";
    }

    @Override
    public String description() {
        return "Stands with a besieging host, waits in the siege, takes the first ladder and fights in the assault.";
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
        Army[] host = {null};
        House[] sides = new House[2];
        script.once("a host of the liege camps around an enemy seat", () -> {
            ctx.outfitHero(14);
            HistoryManager h = history(ctx);
            siege[0] = SeatAssaultScenario.layASiege(ctx, sides);
            // Today is about the assault: nothing else is fought here first.
            h.world().figure(sides[1].lordId).traits.clear();
            keepThePeace(ctx, sides[0].id);
            host[0] = h.roster().army(siege[0].armies[0]);
            host[0].soldiers = 60;
            // Short of the ladders, and too few to go in before they ask.
            siege[0].works = SeatSiege.LADDER_TURNS - 200;
            sides[1].strength = 100f;
            h.favour().set(sides[0].id, Favour.FRIEND_AT);
            h.swear(sides[0].id);
            ctx.getGameScreen().travelToChunk(new GridPoint2(siege[0].x, siege[0].y), new GridPoint2(3, 3));
        });
        script.until("arrive", 300, () -> {
            keepThePeace(ctx, sides[0].id);
            return shown(ctx);
        });
        script.once("the natives are out of the way", () -> {
            for (Monster m : new ArrayList<>(ctx.getMaze().getMonsters().values())) ctx.getMaze().removeMonster(m);
            ctx.dismissModals();
        });
        script.key("a turn passes", Input.Keys.PERIOD, ctx);
        script.wait("the camp goes up", 10);
        script.key("ask to stand with the host", Input.Keys.Y, ctx);
        script.wait("asked", 6);
        script.once("the captain agrees", () -> {
            ctx.assertTrue(history(ctx).oath().following == host[0].id, "following the besieger");
        });
        script.shot("ss01_in_the_siege", ctx);

        long[] began = {0};
        script.once("note the clock", () -> began[0] = history(ctx).warClock());
        script.key("wait in the siege", Input.Keys.Y, ctx);
        script.until("the captain asks for the first ladder", 200, () -> ctx.getGame().getScreen() instanceof ChoiceScreen);
        script.once("the wait passed time and the works came on", () -> {
            ctx.log("Waited " + (history(ctx).warClock() - began[0]) + " turns; works " + siege[0].works);
            ctx.assertTrue(history(ctx).warClock() - began[0] >= 190, "time was skipped");
            ctx.assertTrue(siege[0].laddersBuilt(), "the ladders are built");
            ctx.assertTrue(history(ctx).roster().battleOf(host[0].id) == null, "and nobody has gone in: the captain only asks");
        });
        script.shot("ss02_the_first_ladder", ctx);
        script.once("take it", () -> {
            ctx.assertTrue(ctx.choose("Take the first ladder"), "it is on offer");
        });
        script.wait("taken", 6);
        script.once("a hard contract in hand", () -> {
            Contract c = history(ctx).oath().contract;
            ctx.assertTrue(c != null && c.kind == Contract.Kind.FIRST_LADDER && c.hard, "the first ladder is the task");
            // Now the numbers say go: only they do, and the player's word never did.
            host[0].soldiers = 2000;
        });
        script.key("wait on", Input.Keys.Y, ctx);
        ArmyBattle[] battle = {null};
        script.until("the assault wakes them", 200, () -> {
            battle[0] = history(ctx).roster().battleOf(host[0].id);
            return battle[0] != null && battle[0].assault;
        });
        GridPoint2 standing = new GridPoint2();
        script.until("over the wall", 400, () -> {
            battle[0].moraleA = 100;
            if (ctx.getFrame() % 2 == 0) {
                ctx.press(Input.Keys.END);
                ctx.press(Input.Keys.PERIOD);
            }
            standing.set((int) ctx.getPlayer().getPosition().x, (int) ctx.getPlayer().getPosition().y);
            return "BATTLE".equals(ctx.battlePhase()) && SeatRing.inside(ctx.getMaze().getWidth(), ctx.getMaze().getHeight(), standing.x, standing.y);
        });
        script.once("in the fight, with no side to choose", () -> {
            ctx.assertTrue(battle[0].playerSide == 1, "with the besiegers");
            ctx.assertTrue(!(ctx.getGame().getScreen() instanceof ChoiceScreen), "and asked nothing");
            ctx.log("Over the wall at " + standing);
            ctx.getPlayer().getStats().setMaxHP(5000);
            ctx.getPlayer().getStats().setCurrentHP(5000);
        });
        script.wait("drawn", 4);
        script.shot("ss03_over_the_wall", ctx);
        // The first blow a real one: walked up to and struck.
        script.until("strike one of the garrison", 900, () -> {
            battle[0].moraleA = 100;
            ctx.getPlayer().getStats().setCurrentHP(5000);
            if (battle[0].playerKills > 0 || (battle[0].playerStruck & 2) != 0) return true;
            if (ctx.getCombatManager().getCurrentState() != com.bpm.minotaur.managers.CombatManager.CombatState.INACTIVE) {
                if (ctx.getFrame() % 2 == 0) ctx.press(Input.Keys.SPACE);
                return false;
            }
            Monster near = nearestDefender(ctx);
            if (near == null) return false;
            GridPoint2 at = new GridPoint2((int) near.getPosition().x, (int) near.getPosition().y);
            // The walk is not what is under test, and a wall may lie between: every so often they are set beside one.
            if (ctx.getFrame() % 120 == 0) {
                GridPoint2 beside = com.bpm.minotaur.managers.WorldManager.findSafeArrivalTile(ctx.getMaze(), at.x, at.y);
                if (beside != null) ctx.getPlayer().setPosition(beside.x + 0.5f, beside.y + 0.5f);
            }
            if (ctx.getFrame() % 3 == 0) ctx.steerToward(at);
            return false;
        });
        script.once("the blow is counted", () -> ctx.log("Kills " + battle[0].playerKills + ", struck " + battle[0].playerStruck));
        script.until("the seat falls", 1500, () -> {
            HistoryManager h = history(ctx);
            if (!h.roster().battles.contains(battle[0])) return true;
            ctx.getPlayer().getStats().setCurrentHP(5000);
            if (ctx.getFrame() % 2 != 0) return false;
            ArmyLayer layer = ctx.getWorldManager().armyLayer();
            for (Monster m : new ArrayList<>(ctx.getMaze().getMonsters().values())) {
                if (m != null && m.isWarBand() && m.getBattleSide() == 2) m.setCurrentHP(0);
            }
            for (int i = 0; i < 4 && layer.aliveOn(battle[0], false) > 16; i++) layer.soldierFell(battle[0], false, sides[1].id);
            battle[0].moraleA = 100;
            battle[0].moraleB = 0;
            battle[0].turn = 2;
            ctx.press(Input.Keys.END);
            ctx.press(Input.Keys.PERIOD);
            return false;
        });
        script.once("the first ladder is kept, at the hard rate", () -> {
            ctx.assertEquals(sides[0].id, battle[0].winner, "the besieger holds the field");
            Contract c = history(ctx).oath().contract;
            ctx.assertTrue(c != null && c.done, "the contract is done");
            ctx.assertEquals(Contract.HARD_FAVOUR, c.favour(), "at the hard rate");
        });
        script.key("a turn after", Input.Keys.PERIOD, ctx);
        script.wait("drawn", 6);
        script.shot("ss04_the_seat_fell", ctx);
    }

    /** Only the besiegers stand on this ground, and no battle is on it, whichever war this world rolled. */
    private static void keepThePeace(PlaytestContext ctx, int besieger) {
        HistoryManager h = history(ctx);
        h.roster().armies.removeIf(o -> o.side() != besieger);
        h.roster().battles.clear();
        for (Army a : h.roster().armies) a.battle = false;
    }

    private static Monster nearestDefender(PlaytestContext ctx) {
        Monster best = null;
        float bestDist = Float.MAX_VALUE;
        for (Monster m : ctx.getMaze().getMonsters().values()) {
            if (m == null || !m.isWarBand() || m.getBattleSide() != 2) continue;
            float d = m.getPosition().dst(ctx.getPlayer().getPosition());
            if (d < bestDist) {
                bestDist = d;
                best = m;
            }
        }
        return best;
    }
}
