package com.bpm.minotaur.playtest.scenarios;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.math.GridPoint2;
import com.bpm.minotaur.gamedata.history.EventType;
import com.bpm.minotaur.gamedata.history.HistoryEvent;
import com.bpm.minotaur.gamedata.history.HistoryWorld;
import com.bpm.minotaur.gamedata.history.House;
import com.bpm.minotaur.gamedata.history.War;
import com.bpm.minotaur.gamedata.history.war.SeatMap;
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
 * A seat stormed from ladders to yard (Living War A30-A33): the ring and the camp, the assault's lines
 * on the walls, the garrison falling back, and the seat falling only when the yard is cleared.
 */
public final class SeatAssaultScenario implements PlaytestScenario {

    @Override
    public String name() {
        return "seat-assault";
    }

    @Override
    public String description() {
        return "Lays a siege at a seat, walks to its ring, and fights the assault from the ladders to the yard.";
    }

    /** Wars are the history's to declare; only a staged fight reaches past it. */
    @SuppressWarnings("unchecked")
    private static java.util.List<War> warsOf(HistoryWorld world) {
        try {
            java.lang.reflect.Field wars = HistoryWorld.class.getDeclaredField("wars");
            wars.setAccessible(true);
            return (java.util.List<War>) wars.get(world);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Which side to take is asked on a screen of its own, which stops the turns until it is answered. */
    private static boolean answerAnyPrompt(PlaytestContext ctx) {
        if (!(ctx.getGame().getScreen() instanceof ChoiceScreen)) return false;
        java.util.Set<String> labels = ((ChoiceScreen) ctx.getGame().getScreen()).labels();
        ctx.log("On offer: " + labels);
        ctx.choose(labels.iterator().next());
        return true;
    }

    private static boolean shown(PlaytestContext ctx) {
        return ctx.getMaze() != null && ctx.getMaze() == ctx.getWorldManager().getCurrentMaze();
    }

    private static boolean seatTaken(HistoryManager history, House holder, int since) {
        for (int i = since; i < history.world().events().size(); i++) {
            HistoryEvent e = history.world().events().get(i);
            if (e.type == EventType.SEAT_SEIZED || (e.type == EventType.BATTLE && e.houseB == holder.id)) return true;
        }
        return false;
    }

    @Override
    public void buildScript(PlaytestScript script, PlaytestContext ctx) {
        script.wait("settle", 20);
        script.once("settle traits", ctx::settleTraits);
        script.wait("traits settle", 15);
        script.key("the armies muster", Input.Keys.PERIOD, ctx);
        script.wait("a moment", 10);

        House[] sides = new House[2];
        SeatSiege[] siege = {null};
        script.once("a host camps around a seat", () -> {
            ctx.outfitHero(10);
            HistoryManager history = ctx.getWorldManager().getHistory();
            HistoryWorld world = history.world();
            ArmyLayer layer = ctx.getWorldManager().armyLayer();
            SeatMap seats = ctx.getWorldManager().houseSeats();
            layer.tick(history.warClock());
            // A world at peace, or at war only with the castle, is given a war to stage.
            House first = null, second = null;
            for (House h : world.livingHouses()) {
                if (h.holdsCastle || seats.seat(h.id) == null) continue;
                if (first == null) first = h;
                else if (second == null) second = h;
            }
            ctx.assertTrue(second != null, "two houses with seats");
            if (world.activeWarBetween(first.id, second.id) == null) warsOf(world).add(new War(9000, first.id, second.id, com.bpm.minotaur.gamedata.history.CasusBelli.values()[0], -1, world.season()));
            ctx.log("Wars: " + world.activeWars().size() + ", " + first.name + " strength " + first.strength + ", " + second.name + " strength " + second.strength);
            for (War w : world.activeWars()) {
                House a = world.house(w.attackerId), b = world.house(w.defenderId);
                if (a == null || b == null || a.holdsCastle || b.holdsCastle || seats.seat(b.id) == null || seats.seat(a.id) == null) continue;
                siege[0] = layer.investNow(a, b, history.warClock());
                if (siege[0] != null) {
                    sides[0] = a;
                    sides[1] = b;
                    break;
                }
            }
            ctx.assertTrue(siege[0] != null, "a seat under siege");
            ctx.log(sides[0].name + " besieges " + sides[1].name + " at " + siege[0].x + "," + siege[0].y + ", stone " + siege[0].stone);
            ctx.getGameScreen().travelToChunk(new GridPoint2(siege[0].x, siege[0].y), new GridPoint2(3, 18));
        });
        script.until("arrive at the seat", 300, () -> shown(ctx));
        script.once("the natives are out of the way", () -> {
            for (Monster m : new ArrayList<>(ctx.getMaze().getMonsters().values())) ctx.getMaze().removeMonster(m);
            ctx.dismissModals();
        });
        script.key("look about", Input.Keys.PERIOD, ctx);
        script.wait("the ring is up", 10);
        script.once("the ring stands", () -> {
            long walls = ctx.getMaze().getScenery().values().stream()
                    .filter(s -> "siege_palisade".equals(s.getPropId()) || "siege_wall".equals(s.getPropId())).count();
            int shut = 0, all = 0;
            for (GridPoint2 t : com.bpm.minotaur.gamedata.history.war.SeatRing.wall(ctx.getMaze().getWidth(), ctx.getMaze().getHeight())) {
                all++;
                if (!ctx.getMaze().isPassable(t.x, t.y)) shut++;
            }
            ctx.log("Maze " + ctx.getMaze().getWidth() + "x" + ctx.getMaze().getHeight() + ", biome " + ctx.getMaze().getBiome() + "; ring pieces " + walls + ", ring tiles shut " + shut + " of " + all);
            ctx.assertTrue(walls > 0 && shut * 10 >= all * 9, "a walled ring around the chunk's centre");
            ctx.getPlayer().setPosition(ctx.getMaze().getWidth() / 2f + 4.5f, ctx.getMaze().getHeight() / 2f - 12f + 0.5f);
            // Trees would hide what the shots are here to show: only the siege stands for the camera.
            ctx.getMaze().getScenery().values().removeIf(sc -> sc.getPropId() == null || !(sc.getPropId().startsWith("siege_") || sc.getPropId().equals("camp_tent")));
            ctx.getMaze().getBackdropScenery().clear();
            ctx.getPlayer().setFacing(com.bpm.minotaur.gamedata.Direction.NORTH);
        });
        script.wait("drawn", 6);
        script.shot("sa01_the_ring_and_a_gate", ctx);

        ArmyBattle[] battle = {null};
        int[] before = {0};
        script.once("the captain goes in", () -> {
            HistoryManager history = ctx.getWorldManager().getHistory();
            before[0] = history.world().events().size();
            // Nothing else is fought here today, and no relief is on its way.
            history.roster().battles.removeIf(o -> o.x == siege[0].x && o.y == siege[0].y);
            history.roster().armies.removeIf(o -> o.houseId == sides[1].id && o.isHost());
            for (int id : siege[0].armies) history.roster().army(id).battle = false;
            // Enough of them to see it through to the yard.
            for (int id : siege[0].armies) history.roster().army(id).soldiers = 120;
            battle[0] = ctx.getWorldManager().armyLayer().assaultNow(siege[0]);
            ctx.assertTrue(battle[0] != null && battle[0].assault, "an assault under way");
            ctx.log("Assault: " + battle[0].startA + " against " + battle[0].startB + ", edge " + battle[0].wallEdge);
        });
        script.until("the lines close and the side is taken", 900, () -> {
            if (answerAnyPrompt(ctx)) return false;
            if (ctx.getFrame() % 2 == 0) {
                ctx.press(Input.Keys.END);
                ctx.press(Input.Keys.PERIOD);
            }
            return ctx.countWarBand() >= 8 && ctx.battlePhase().equals("BATTLE");
        });
        script.until("the side is taken", 120, () -> {
            answerAnyPrompt(ctx);
            return !(ctx.getGame().getScreen() instanceof ChoiceScreen) && ctx.getWorldManager().getHistory().roster().battles.contains(battle[0]) && battle[0].playerSide != 0;
        });
        script.wait("drawn", 6);
        script.once("the garrison holds the ring", () -> {
            int inside = 0, defenders = 0;
            int w = ctx.getMaze().getWidth(), h = ctx.getMaze().getHeight();
            for (Monster m : ctx.getMaze().getMonsters().values()) {
                if (m == null || !m.isWarBand() || m.getBattleSide() != 2 || m.isGateGuard()) continue;
                defenders++;
                if (com.bpm.minotaur.gamedata.history.war.SeatRing.inside(w, h, (int) m.getPosition().x, (int) m.getPosition().y)) inside++;
            }
            ctx.log("Defenders " + defenders + ", within the ring " + inside);
            ctx.assertTrue(defenders > 0 && inside * 2 >= defenders, "the garrison stands within the ring");
        });
        script.shot("sa02_the_walls", ctx);

        script.once("the garrison breaks on the walls", () -> {
            ctx.assertTrue(!battle[0].yard, "still on the walls");
        });
        script.until("it falls back to the yard", 300, () -> {
            if (answerAnyPrompt(ctx)) return false;
            battle[0].moraleA = 100;
            if (!battle[0].yard) battle[0].moraleB = 0;
            if (ctx.getFrame() % 2 == 0) {
                ctx.press(Input.Keys.END);
                ctx.press(Input.Keys.PERIOD);
            }
            return battle[0].yard;
        });
        script.once("the seat has not fallen", () -> {
            HistoryManager history = ctx.getWorldManager().getHistory();
            ctx.assertTrue(history.roster().battles.contains(battle[0]), "the fight goes on in the yard");
            ctx.assertTrue(!seatTaken(history, sides[1], before[0]), "the seat stands until the yard is cleared");
            ctx.assertTrue(ctx.getWorldManager().armyLayer().aliveOn(battle[0], false) > 0, "some are left to clear");
        });
        script.wait("drawn", 4);
        script.shot("sa03_the_yard", ctx);

        script.until("clear the yard", 1500, () -> {
            HistoryManager history = ctx.getWorldManager().getHistory();
            if (!history.roster().battles.contains(battle[0])) return true;
            if (answerAnyPrompt(ctx)) return false;
            if (ctx.getFrame() % 2 != 0) return false;
            ArmyLayer layer = ctx.getWorldManager().armyLayer();
            for (Monster m : new ArrayList<>(ctx.getMaze().getMonsters().values())) {
                if (m != null && m.isWarBand() && m.getBattleSide() == 2) m.setCurrentHP(0);
            }
            for (int i = 0; i < 4 && layer.aliveOn(battle[0], false) > 16; i++) layer.soldierFell(battle[0], false, sides[1].id);
            battle[0].moraleA = 100;
            battle[0].turn = 2;
            ctx.press(Input.Keys.END);
            ctx.press(Input.Keys.PERIOD);
            return false;
        });
        script.once("the seat fell", () -> {
            HistoryManager history = ctx.getWorldManager().getHistory();
            ctx.log("Winner " + battle[0].winner + ", besieger " + sides[0].id);
            ctx.assertEquals(sides[0].id, battle[0].winner, "the besieger holds the field");
            ctx.assertTrue(history.roster().siegeAt(siege[0].x, siege[0].y) == null, "the siege is over");
        });
        script.key("a turn after", Input.Keys.PERIOD, ctx);
        script.wait("drawn", 6);
        script.shot("sa04_after_the_fall", ctx);
    }
}
