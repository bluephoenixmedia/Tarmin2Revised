package com.bpm.minotaur.playtest.scenarios;

import com.badlogic.gdx.math.GridPoint2;
import com.bpm.minotaur.gamedata.history.House;
import com.bpm.minotaur.gamedata.history.war.army.Army;
import com.bpm.minotaur.gamedata.history.war.army.ArmyBattle;
import com.bpm.minotaur.gamedata.history.war.army.WarRoster;
import com.bpm.minotaur.gamedata.map.ArmyKnowledge;
import com.bpm.minotaur.gamedata.map.MapKnowledge;
import com.bpm.minotaur.managers.HistoryManager;
import com.bpm.minotaur.playtest.PlaytestContext;
import com.bpm.minotaur.playtest.PlaytestScenario;
import com.bpm.minotaur.playtest.PlaytestScript;
import com.bpm.minotaur.screens.map.ExpeditionMapScreen;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * What the player knows, on the Expedition Map (Living War A46-A49): one of each mark around the
 * player, told through the same model the game uses, with the host in the player's chunk selected.
 */
public final class ArmyMarksScenario implements PlaytestScenario {

    @Override
    public String name() {
        return "army-marks";
    }

    @Override
    public String description() {
        return "Lays a sighting, an aged sighting, each rumour, tracks, a heard battle, a sworn host and a siege on the map.";
    }

    private static Army army(WarRoster roster, int id, int house, int x, int y, int tx, int ty, int soldiers) {
        Army a = new Army();
        a.id = id;
        a.houseId = house;
        a.x = x;
        a.y = y;
        a.targetX = tx;
        a.targetY = ty;
        a.soldiers = soldiers;
        a.captainId = -1;
        roster.armies.add(a);
        return a;
    }

    @Override
    public void buildScript(PlaytestScript script, PlaytestContext ctx) {
        script.wait("settle", 20);
        script.once("settle traits", ctx::settleTraits);
        script.wait("traits settle", 15);
        script.once("tell it everything", () -> {
            HistoryManager history = ctx.getWorldManager().getHistory();
            List<House> houses = history.world().livingHouses();
            ctx.assertTrue(houses.size() >= 4, "houses to mark");
            GridPoint2 here = ctx.getWorldManager().getCurrentPlayerChunkId();
            long clock = history.warClock();
            ArmyKnowledge known = MapKnowledge.getInstance().getArmies();

            // Seen a little while ago, then seen in the player's own chunk.
            WarRoster aged = new WarRoster();
            army(aged, 9001, houses.get(1).id, here.x - 3, here.y + 3, here.x - 8, here.y + 3, 8);
            known.onTurn(aged, here.x - 3, here.y + 3, clock - 300);
            WarRoster seen = new WarRoster();
            Army host = army(seen, 9002, houses.get(0).id, here.x, here.y, here.x + 5, here.y + 5, 30);
            host.captainId = houses.get(0).lordId;
            ArmyBattle far = new ArmyBattle();
            far.id = 9100;
            far.x = here.x - 2;
            far.y = here.y - 2;
            seen.battles.add(far);
            known.onTurn(seen, here.x, here.y, clock);

            WarRoster table = new WarRoster();
            army(table, 9003, houses.get(2).id, here.x + 6, here.y + 1, here.x + 6, here.y + 9, 25);
            known.hearWarTable(table, clock - 50);

            WarRoster town = new WarRoster();
            Army told = army(town, 9004, houses.get(3).id, here.x + 3, here.y - 3, here.x + 9, here.y - 3, 12);
            told.trail = new int[]{WarRoster.packedChunk(here.x + 4, here.y - 1), WarRoster.packedChunk(here.x + 5, here.y - 1)};
            ctx.assertTrue(known.hearInTown(town, clock, 0) != null, "the town has word");

            WarRoster trail = new WarRoster();
            Army left = army(trail, 9005, houses.get(1).id, here.x - 2, here.y - 2, here.x - 2, here.y - 2, 15);
            left.trail = new int[]{WarRoster.packedChunk(here.x + 2, here.y + 2), WarRoster.packedChunk(here.x + 1, here.y + 3)};
            ArmyKnowledge.Tracks tracks = known.readTracks(trail, here.x + 2, here.y + 2, clock);
            ctx.assertTrue(tracks != null, "the trail speaks");
            ctx.log(ArmyKnowledge.tracksLine(tracks, houses.get(1).name));

            // Live, so shown as it stands: the sworn player's own host, and a siege.
            WarRoster roster = history.roster();
            Army own = army(roster, 9006, houses.get(2).id, here.x - 1, here.y - 5, here.x + 3, here.y - 5, 27);
            own.objective = Army.Objective.SEAT;
            own.trail = new int[]{WarRoster.packedChunk(here.x - 4, here.y - 5), WarRoster.packedChunk(here.x - 2, here.y - 5)};
            history.oath().houseId = houses.get(2).id;
            ArmyBattle siege = new ArmyBattle();
            siege.id = 9101;
            siege.x = here.x + 3;
            siege.y = here.y + 6;
            siege.houseA = houses.get(3).id;
            siege.houseB = houses.get(0).id;
            siege.garrisonOf = houses.get(0).id;
            roster.battles.add(siege);

            Set<ArmyKnowledge.Source> sources = EnumSet.noneOf(ArmyKnowledge.Source.class);
            for (ArmyKnowledge.Marker m : known.markers(roster, history.oath().houseId, List.of(ArmyKnowledge.siege(siege.houseA, siege.x, siege.y, clock)), clock)) {
                sources.add(m.source);
            }
            ctx.log("Sources: " + sources + ", sounds " + known.sounds(clock).size());
            ctx.assertEquals(EnumSet.allOf(ArmyKnowledge.Source.class), sources, "one of each");
            ctx.assertEquals(1, known.sounds(clock).size(), "a battle heard");
            ctx.getGame().setScreen(new ExpeditionMapScreen(ctx.getGame(), ctx.getPlayer(), ctx.getMaze(), ctx.getGameScreen()));
        });
        script.wait("the map opens", 40);
        script.shot("marks01_the_card", ctx);
        script.once("back out to the world", () -> {
            ctx.press(com.badlogic.gdx.Input.Keys.MINUS);
            ctx.press(com.badlogic.gdx.Input.Keys.MINUS);
        });
        script.wait("the map eases out", 150);
        script.shot("marks02_each_kind", ctx);
        script.once("put it right", () -> {
            HistoryManager history = ctx.getWorldManager().getHistory();
            history.oath().houseId = -1;
            history.roster().armies.removeIf(a -> a.id >= 9000);
            history.roster().battles.removeIf(b -> b.id >= 9000);
            ctx.getGame().setScreen(ctx.getGameScreen());
        });
        script.wait("back", 10);
    }
}
