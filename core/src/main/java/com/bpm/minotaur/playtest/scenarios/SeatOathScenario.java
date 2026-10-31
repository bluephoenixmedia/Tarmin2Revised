package com.bpm.minotaur.playtest.scenarios;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.math.GridPoint2;
import com.bpm.minotaur.gamedata.Direction;
import com.bpm.minotaur.gamedata.history.House;
import com.bpm.minotaur.gamedata.history.favour.Contract;
import com.bpm.minotaur.gamedata.history.favour.Favour;
import com.bpm.minotaur.gamedata.history.war.EncounterLedger;
import com.bpm.minotaur.gamedata.history.war.SeatMap;
import com.bpm.minotaur.gamedata.history.war.army.Army;
import com.bpm.minotaur.managers.HistoryManager;
import com.bpm.minotaur.playtest.PlaytestContext;
import com.bpm.minotaur.playtest.PlaytestScenario;
import com.bpm.minotaur.playtest.PlaytestScript;

/**
 * The oath at a house's seat (Living War A39), a contract on a real thing in the roster (A37), its
 * target gone and the contract void (A38), and the oath broken with hunters sent at once (A41).
 */
public final class SeatOathScenario implements PlaytestScenario {

    @Override
    public String name() {
        return "seat-oath";
    }

    @Override
    public String description() {
        return "Swears at a seat's banner, takes a contract on a named host, sees it voided, and breaks the oath.";
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

        House[] house = {null};
        script.once("to a seat", () -> {
            ctx.outfitHero(10);
            SeatMap seats = ctx.getWorldManager().houseSeats();
            for (House h : history(ctx).world().livingHouses()) {
                if (h.holdsCastle || seats.seat(h.id) == null || history(ctx).world().activeWarCount(h.id) == 0) continue;
                house[0] = h;
                break;
            }
            ctx.assertTrue(house[0] != null, "a house at war, with a seat");
            history(ctx).favour().set(house[0].id, Favour.FRIEND_AT);
            ctx.getGameScreen().travelToChunk(seats.seat(house[0].id), new GridPoint2(16, 16));
        });
        script.until("arrive", 300, () -> shown(ctx));
        script.key("a turn passes", Input.Keys.PERIOD, ctx);
        script.wait("the banner goes up", 10);
        script.once("stand at the banner", () -> {
            GridPoint2 chunk = ctx.getWorldManager().getCurrentPlayerChunkId();
            GridPoint2 banner = null;
            for (EncounterLedger.Dressing d : history(ctx).encounterLedger().dressings) {
                if (!"CAMP".equals(d.kind) || d.chunkX != chunk.x || d.chunkY != chunk.y) continue;
                for (int i = 0; i < d.props.size(); i++) {
                    if ("war_banner".equals(d.props.get(i))) banner = new GridPoint2(d.tiles.get(2 * i), d.tiles.get(2 * i + 1));
                }
            }
            ctx.assertTrue(banner != null, "the seat flies a banner");
            for (Direction d : Direction.values()) {
                int x = banner.x - (int) d.getVector().x, y = banner.y - (int) d.getVector().y;
                if (!ctx.getMaze().isPassable(x, y) || ctx.getMaze().getMonsters().containsKey(new GridPoint2(x, y))) continue;
                if (ctx.getMaze().isWallBlocking(x, y, d)) continue;
                ctx.getPlayer().setPosition(x + 0.5f, y + 0.5f);
                ctx.getPlayer().setFacing(d);
                return;
            }
        });
        script.wait("in place", 5);
        script.shot("so01_the_banner_at_the_seat", ctx);
        script.once("an enemy host to name", () -> {
            HistoryManager h = history(ctx);
            int enemy = -1;
            for (House o : h.world().livingHouses()) {
                if (h.world().activeWarBetween(o.id, house[0].id) != null) enemy = o.id;
            }
            ctx.assertTrue(enemy >= 0, "an enemy");
            Army a = new Army();
            a.id = 9001;
            a.houseId = enemy;
            a.soldiers = 15;
            a.captainId = -1;
            a.x = 2;
            a.y = 2;
            h.roster().armies.add(a);
        });
        script.key("swear", Input.Keys.O, ctx);
        script.wait("sworn", 15);
        script.once("sworn at the seat, with a task on a real thing", () -> {
            HistoryManager h = history(ctx);
            ctx.assertTrue(h.oath().houseId == house[0].id, "sworn to the seat's house");
            Contract c = h.oath().contract;
            ctx.assertTrue(c != null, "a contract");
            ctx.log("Task: " + c.describe(h.world()));
            ctx.assertTrue(c.targetArmy >= 0 || c.targetSiege >= 0 || c.kind == Contract.Kind.SIEGE_ASSAULT, "it names a real thing");
        });
        script.shot("so02_sworn", ctx);
        script.once("the target goes", () -> {
            HistoryManager h = history(ctx);
            Contract c = h.oath().contract;
            c.kind = Contract.Kind.BREAK_HOST;
            c.targetArmy = 9001;
            c.targetFigure = -1;
            c.targetSiege = -1;
            h.roster().armies.remove(h.roster().army(9001));
        });
        script.key("a turn passes", Input.Keys.PERIOD, ctx);
        script.wait("the news", 10);
        script.once("void, no penalty", () -> {
            ctx.assertTrue(history(ctx).oath().contract == null, "the contract is void");
            ctx.assertTrue(history(ctx).oath().sworn(), "the oath stands");
            ctx.assertTrue(history(ctx).oath().done == 0, "and nothing paid");
        });
        script.shot("so03_void", ctx);
        int[] before = {0};
        script.once("break the oath", () -> {
            HistoryManager h = history(ctx);
            for (Army a : h.roster().armies) if (a.kind == Army.Kind.HUNTING_PARTY && a.houseId == house[0].id) before[0]++;
            h.breakOath();
            int after = 0;
            for (Army a : h.roster().armies) if (a.kind == Army.Kind.HUNTING_PARTY && a.houseId == house[0].id) after++;
            ctx.log("Hunting parties of the betrayed: " + before[0] + " to " + after);
            ctx.assertTrue(after >= 1, "the betrayed house hunts at once");
            ctx.assertTrue(!h.oath().sworn(), "the oath is gone");
        });
        script.wait("after", 6);
    }
}
