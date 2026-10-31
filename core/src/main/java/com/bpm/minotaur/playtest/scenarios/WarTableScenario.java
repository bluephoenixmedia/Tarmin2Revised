package com.bpm.minotaur.playtest.scenarios;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.math.GridPoint2;
import com.bpm.minotaur.gamedata.history.CasusBelli;
import com.bpm.minotaur.gamedata.history.HistoryWorld;
import com.bpm.minotaur.gamedata.history.House;
import com.bpm.minotaur.gamedata.history.War;
import com.bpm.minotaur.gamedata.history.war.army.Army;
import com.bpm.minotaur.gamedata.history.war.army.ArmyBattle;
import com.bpm.minotaur.gamedata.history.war.army.WarRoster;
import com.bpm.minotaur.gamedata.map.MapKnowledge;
import com.bpm.minotaur.managers.HistoryManager;
import com.bpm.minotaur.playtest.PlaytestContext;
import com.bpm.minotaur.playtest.PlaytestScenario;
import com.bpm.minotaur.playtest.PlaytestScript;
import com.bpm.minotaur.screens.WarTableScreen;

import java.util.ArrayList;
import java.util.List;

/**
 * The War Table as Dispatches (Living War A50): five wars, unsworn and then sworn, with hunters out,
 * a siege, and hosts near and far; a war's page; and a waypoint set from a row.
 */
public final class WarTableScenario implements PlaytestScenario {

    @Override
    public String name() {
        return "war-table";
    }

    @Override
    public String description() {
        return "Reads the War Table with five wars, unsworn and sworn, opens a war's page, and sets a waypoint from a row.";
    }

    private static Army army(WarRoster roster, int id, int house, int x, int y, Army.Kind kind, int soldiers) {
        Army a = new Army();
        a.id = id;
        a.houseId = house;
        a.x = x;
        a.y = y;
        a.targetX = x + 4;
        a.targetY = y;
        a.kind = kind;
        a.soldiers = soldiers;
        a.captainId = -1;
        roster.armies.add(a);
        return a;
    }

    /** Wars are the history's to declare; only a staged reading reaches past it, to lay on enough of them. */
    @SuppressWarnings("unchecked")
    private static List<War> warsOf(HistoryWorld world) {
        try {
            java.lang.reflect.Field wars = HistoryWorld.class.getDeclaredField("wars");
            wars.setAccessible(true);
            return (List<War>) wars.get(world);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void open(PlaytestContext ctx) {
        ctx.getGame().setScreen(new WarTableScreen(ctx.getGame(), ctx.getGameScreen(), ctx.getWorldManager()));
    }

    @Override
    public void buildScript(PlaytestScript script, PlaytestContext ctx) {
        script.wait("settle", 20);
        script.once("settle traits", ctx::settleTraits);
        script.wait("traits settle", 15);
        List<House> houses = new ArrayList<>();
        script.once("five wars, and word of them", () -> {
            HistoryManager history = ctx.getWorldManager().getHistory();
            HistoryWorld world = history.world();
            houses.addAll(world.livingHouses());
            ctx.assertTrue(houses.size() >= 6, "houses enough");
            for (War w : world.activeWars()) ctx.log("Already at war: " + w.attackerId + " with " + w.defenderId);
            int need = 5 - world.activeWars().size();
            for (int i = 0; i < need; i++) {
                House a = houses.get(i), b = houses.get((i + 1) % houses.size());
                warsOf(world).add(new War(9000 + i, a.id, b.id, CasusBelli.values()[i % CasusBelli.values().length], -1, world.season() - 3));
            }
            ctx.assertTrue(world.activeWars().size() >= 5, "five wars");
            GridPoint2 here = ctx.getWorldManager().getCurrentPlayerChunkId();
            WarRoster roster = history.roster();
            long clock = history.warClock();
            army(roster, 9000, houses.get(0).id, here.x + 4, here.y, Army.Kind.HOST, 45);
            army(roster, 9001, houses.get(1).id, here.x, here.y + 9, Army.Kind.HOST, 30);
            army(roster, 9002, houses.get(2).id, here.x - 20, here.y, Army.Kind.HOST, 15);
            army(roster, 9003, houses.get(3).id, here.x + 50, here.y + 3, Army.Kind.LEVY, 15);
            army(roster, 9004, houses.get(4).id, here.x, here.y - 55, Army.Kind.HOST, 8);
            army(roster, 9005, houses.get(5).id, here.x + 2, here.y, Army.Kind.HUNTING_PARTY, 5);
            ArmyBattle siege = new ArmyBattle();
            siege.id = 9100;
            siege.x = here.x + 12;
            siege.y = here.y - 3;
            siege.houseA = houses.get(1).id;
            siege.houseB = houses.get(2).id;
            siege.garrisonOf = houses.get(2).id;
            roster.battles.add(siege);
            MapKnowledge.getInstance().getArmies().hearWarTable(roster, clock);
            open(ctx);
        });
        script.wait("the table is read", 20);
        script.shot("wt01_five_wars", ctx);

        script.once("a row is chosen, and a waypoint set", () -> {
            ctx.press(Input.Keys.DOWN);
            ctx.press(Input.Keys.W);
            ctx.assertTrue(MapKnowledge.getInstance().getWaypoint() != null, "W set the Expedition Map's waypoint");
            ctx.log("Waypoint: " + MapKnowledge.getInstance().getWaypoint().getChunk());
        });
        script.once("a war's page", () -> ctx.press(Input.Keys.NUM_1));
        script.wait("the page is laid out", 10);
        script.shot("wt02_a_wars_page", ctx);
        script.once("back to the news", () -> ctx.press(Input.Keys.ESCAPE));
        script.wait("the news", 10);
        script.once("sworn, and asked", () -> {
            ctx.assertTrue(ctx.getGame().getScreen() instanceof WarTableScreen, "ESC on a page returned to the news, not the Shelter");
            HistoryManager history = ctx.getWorldManager().getHistory();
            history.oath().houseId = houses.get(0).id;
            history.roster().armies.get(history.roster().armies.size() - 6).objective = Army.Objective.SEAT;
            ctx.getGame().setScreen(ctx.getGameScreen());
            open(ctx);
        });
        script.wait("the table is read again", 20);
        script.shot("wt03_sworn", ctx);
        script.once("put it right", () -> {
            HistoryManager history = ctx.getWorldManager().getHistory();
            history.oath().houseId = -1;
            history.roster().armies.removeIf(a -> a.id >= 9000);
            history.roster().battles.removeIf(b -> b.id >= 9000);
            warsOf(history.world()).removeIf(w -> w.id >= 9000);
            MapKnowledge.getInstance().clearWaypoint();
            ctx.getGame().setScreen(ctx.getGameScreen());
        });
        script.wait("back", 10);
    }
}
