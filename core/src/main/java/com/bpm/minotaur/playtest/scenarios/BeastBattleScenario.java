package com.bpm.minotaur.playtest.scenarios;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.math.GridPoint2;
import com.bpm.minotaur.gamedata.history.EventType;
import com.bpm.minotaur.gamedata.history.HistoryEvent;
import com.bpm.minotaur.gamedata.history.House;
import com.bpm.minotaur.gamedata.history.Megabeast;
import com.bpm.minotaur.gamedata.history.war.army.ArmyBattle;
import com.bpm.minotaur.gamedata.history.war.SeatMap;
import com.bpm.minotaur.gamedata.monster.Monster;
import com.bpm.minotaur.managers.HistoryManager;
import com.bpm.minotaur.playtest.PlaytestContext;
import com.bpm.minotaur.playtest.PlaytestScenario;
import com.bpm.minotaur.playtest.PlaytestScript;

/**
 * A megabeast fights a host on the field (Living War A28): its wounds go into the numbers, and when
 * it falls the house has the credit and it stays dead.
 */
public final class BeastBattleScenario implements PlaytestScenario {

    @Override
    public String name() {
        return "beast-battle";
    }

    @Override
    public String description() {
        return "Watches a megabeast fall on a host, its wounds carried in the battle, until it dies to the house.";
    }

    private static boolean shown(PlaytestContext ctx) {
        return ctx.getMaze() != null && ctx.getMaze() == ctx.getWorldManager().getCurrentMaze();
    }

    private static Monster beastOnField(PlaytestContext ctx, int id) {
        for (Monster m : ctx.getMaze().getMonsters().values()) {
            if (m != null && m.getMegabeastId() == id) return m;
        }
        return null;
    }

    @Override
    public void buildScript(PlaytestScript script, PlaytestContext ctx) {
        script.wait("settle", 20);
        script.once("settle traits", ctx::settleTraits);
        script.wait("traits settle", 15);
        script.key("the armies muster", Input.Keys.PERIOD, ctx);
        script.wait("a moment", 10);

        Megabeast[] beast = {null};
        House[] house = {null};
        GridPoint2 field = new GridPoint2(14, 6);
        script.once("out to open ground", () -> {
            ctx.outfitHero(10);
            ctx.getGameScreen().travelToChunk(field, new GridPoint2(16, 16));
        });
        script.until("arrive", 300, () -> shown(ctx));
        ArmyBattle[] battle = {null};
        script.once("the beast falls on a host", () -> {
            for (Monster m : new java.util.ArrayList<>(ctx.getMaze().getMonsters().values())) ctx.getMaze().removeMonster(m);
            ctx.dismissModals();
            HistoryManager history = ctx.getWorldManager().getHistory();
            SeatMap seats = ctx.getWorldManager().houseSeats();
            for (Megabeast b : history.world().megabeasts()) if (b.isAlive()) beast[0] = b;
            for (House h : history.world().livingHouses()) if (!h.holdsCastle && seats.seat(h.id) != null) house[0] = h;
            ctx.assertTrue(beast[0] != null && house[0] != null, "a beast and a house");
            battle[0] = ctx.getWorldManager().armyLayer().beastFightsNow(beast[0].id, house[0], field.x, field.y);
            ctx.assertTrue(battle[0] != null, "the fight is on");
            ctx.log(beast[0].name + " falls on " + house[0].name + ", hit points " + battle[0].beastHp);
        });
        script.until("the lines close", 300, () -> {
            if (ctx.getFrame() % 2 == 0) {
                ctx.press(Input.Keys.END);
                ctx.press(Input.Keys.PERIOD);
            }
            return "BATTLE".equals(ctx.battlePhase()) && beastOnField(ctx, beast[0].id) != null;
        });
        script.wait("drawn", 6);
        script.once("the beast and the soldiers are both there", () -> {
            Monster m = beastOnField(ctx, beast[0].id);
            ctx.assertTrue(m.isWarBand() && m.getBattleSide() == 1, "the beast is a side of the battle");
            ctx.assertTrue(ctx.countWarBand() > 1, "against soldiers");
            ctx.log("On the field: " + ctx.countWarBand() + ", the beast at " + m.getCurrentHP() + "/" + m.getMaxHP());
            // Trees would hide what the shot is here to show.
            ctx.getMaze().getScenery().clear();
            ctx.getMaze().getBackdropScenery().clear();
            ctx.getPlayer().setPosition((int) m.getPosition().x + 0.5f, (int) m.getPosition().y - 2.5f);
            ctx.getPlayer().setFacing(com.bpm.minotaur.gamedata.Direction.NORTH);
        });
        script.wait("drawn", 4);
        script.shot("bb01_the_beast_on_the_field", ctx);
        int[] start = {0};
        script.once("note its wounds", () -> start[0] = battle[0].beastHp);
        script.until("soldiers cut it, the field's blows counted", 900, () -> {
            HistoryManager history = ctx.getWorldManager().getHistory();
            if (!history.roster().battles.contains(battle[0])) return true;
            Monster m = beastOnField(ctx, beast[0].id);
            if (m != null && ctx.getFrame() % 40 == 0) m.setCurrentHP(Math.max(1, m.getCurrentHP() - 20));
            if (ctx.getFrame() % 2 == 0) {
                battle[0].moraleB = 100;
                ctx.press(Input.Keys.END);
                ctx.press(Input.Keys.PERIOD);
            }
            return battle[0].beastHp < start[0];
        });
        script.once("its wounds are in the numbers", () -> {
            ctx.log("Hit points " + start[0] + " to " + battle[0].beastHp);
            ctx.assertTrue(battle[0].beastHp < start[0], "what it took on the field the battle knows");
        });
        script.shot("bb02_wounded", ctx);
        int[] eventsBefore = {0};
        script.once("it falls", () -> {
            eventsBefore[0] = ctx.getWorldManager().getHistory().world().events().size();
            Monster m = beastOnField(ctx, beast[0].id);
            ctx.assertTrue(m != null, "still on the field");
            m.setCurrentHP(0);
        });
        script.until("the fight ends", 200, () -> {
            if (ctx.getFrame() % 2 == 0) {
                ctx.press(Input.Keys.END);
                ctx.press(Input.Keys.PERIOD);
            }
            return !ctx.getWorldManager().getHistory().roster().battles.contains(battle[0]);
        });
        script.once("the house has the credit, and the beast stays dead", () -> {
            HistoryManager history = ctx.getWorldManager().getHistory();
            ctx.assertTrue(!beast[0].isAlive(), "the beast is dead");
            HistoryEvent felled = null;
            for (int i = eventsBefore[0]; i < history.world().events().size(); i++) {
                HistoryEvent e = history.world().events().get(i);
                if (e.type == EventType.MEGABEAST_FELLED) felled = e;
            }
            ctx.assertTrue(felled != null && felled.houseA == house[0].id, "felled by the house");
            HistoryManager loaded = HistoryManager.fromSave(0L, history.toSave(), com.bpm.minotaur.gamedata.history.DoctrineCatalog.getInstance());
            ctx.assertTrue(!loaded.world().megabeast(beast[0].id).isAlive(), "dead after a reload");
        });
        script.wait("after", 6);
        script.shot("bb03_it_fell", ctx);
    }
}
