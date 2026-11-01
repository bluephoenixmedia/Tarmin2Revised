package com.bpm.minotaur.playtest.scenarios;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.math.GridPoint2;
import com.bpm.minotaur.gamedata.GameEvent;
import com.bpm.minotaur.gamedata.history.favour.Favour;
import com.bpm.minotaur.gamedata.history.war.army.Army;
import com.bpm.minotaur.gamedata.history.war.army.ArmyBattle;
import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.managers.HintManager;
import com.bpm.minotaur.managers.HistoryManager;
import com.bpm.minotaur.managers.SettingsManager;
import com.bpm.minotaur.playtest.PlaytestContext;
import com.bpm.minotaur.playtest.PlaytestScenario;
import com.bpm.minotaur.playtest.PlaytestScript;
import com.bpm.minotaur.screens.CodexScreen;
import com.bpm.minotaur.screens.GameScreen;
import com.bpm.minotaur.screens.WarTableScreen;

/**
 * Teaching the war (Living War A51): a host in sight, horns in earshot and a house's toleration each
 * hint once; the return to the Shelter points out the Table; its first opening says what it is, and
 * the Codex page is there afterward.
 */
public final class WarTeachingScenario implements PlaytestScenario {

    @Override
    public String name() {
        return "war-teaching";
    }

    @Override
    public String description() {
        return "Fires the war's once-only hints in play, opens the War Table for the first time, and reads the Codex page.";
    }

    private static boolean shown(PlaytestContext ctx) {
        return ctx.getMaze() != null && ctx.getMaze() == ctx.getWorldManager().getCurrentMaze();
    }

    private static HistoryManager history(PlaytestContext ctx) {
        return ctx.getWorldManager().getHistory();
    }

    private static boolean told(PlaytestContext ctx, String words) {
        for (GameEvent e : ctx.getEventManager().getMessageEvents()) {
            if (e.message.contains(words)) return true;
        }
        return false;
    }

    private static Army soldiers(int id, int house, int x, int y) {
        Army a = new Army();
        a.id = id;
        a.houseId = house;
        a.soldiers = 30;
        a.x = x;
        a.y = y;
        a.marchFromX = x;
        a.marchFromY = y;
        return a;
    }

    @Override
    public void buildScript(PlaytestScript script, PlaytestContext ctx) {
        script.wait("settle", 20);
        script.once("settle traits", ctx::settleTraits);
        script.wait("traits settle", 15);
        script.once("a fresh slate of hints", () -> {
            SettingsManager.getInstance().setShowHints(true);
            HintManager.getInstance().resetForNewGame();
        });

        GridPoint2 away = new GridPoint2(14, 6);
        int[] house = {-1};
        script.once("leave the Shelter, a host in sight, a battle in earshot", () -> {
            HistoryManager h = history(ctx);
            house[0] = h.world().livingHouses().get(0).id;
            h.roster().armies.clear();
            h.roster().battles.clear();
            Army near = soldiers(9201, house[0], away.x + 1, away.y);
            h.roster().armies.add(near);
            ArmyBattle fight = new ArmyBattle();
            fight.id = 9202;
            fight.x = away.x + 3;
            fight.y = away.y;
            fight.houseA = house[0];
            fight.houseB = h.world().livingHouses().get(1).id;
            // Soldiers on both sides, or it is over before it is heard.
            Army one = soldiers(9203, fight.houseA, fight.x, fight.y), other = soldiers(9204, fight.houseB, fight.x, fight.y);
            one.battle = true;
            other.battle = true;
            h.roster().armies.add(one);
            h.roster().armies.add(other);
            fight.armiesA = new int[] {one.id};
            fight.armiesB = new int[] {other.id};
            fight.startA = one.soldiers;
            fight.startB = other.soldiers;
            h.roster().battles.add(fight);
            ctx.getGameScreen().travelToChunk(away, new GridPoint2(16, 16));
        });
        script.until("arrive", 300, () -> shown(ctx));
        script.key("a turn passes", Input.Keys.PERIOD, ctx);
        script.wait("told", 6);
        script.once("the sighting and the horns are told once", () -> {
            ctx.assertTrue(told(ctx, "the map remembers where you saw them"), "the sighting");
            ctx.assertTrue(told(ctx, "Follow the sound"), "the horns");
            ctx.assertTrue(told(ctx, "to the east"), "from the right side");
            history(ctx).favour().set(house[0], Favour.TOLERATED_AT);
        });
        script.shot("wt01_a_host_in_sight", ctx);
        script.key("another turn", Input.Keys.PERIOD, ctx);
        script.wait("told", 6);
        script.once("the house's toleration is told", () -> {
            ctx.assertTrue(told(ctx, "tolerates you"), "toleration");
            ctx.assertTrue(HintManager.getInstance().hasSeen(HintManager.HintId.WAR_TOLERATED), "and noted");
            history(ctx).roster().armies.clear();
            history(ctx).roster().battles.clear();
            ctx.getGameScreen().travelToChunk(new GridPoint2(0, 0), new GridPoint2(16, 16));
        });
        script.until("back at the Shelter", 300, () -> shown(ctx) && ctx.getWorldManager().getCurrentPlayerChunkId().equals(new GridPoint2(0, 0)));
        script.once("the War Table stands here", () -> {
            for (Item i : ctx.getMaze().getItems().values()) {
                if (i != null && i.getType() == Item.ItemType.HOME_WAR_TABLE) return;
            }
            // A young Shelter has yet to build the Lectern it stands beside: this one has.
            GridPoint2 p = new GridPoint2((int) ctx.getPlayer().getPosition().x + 1, (int) ctx.getPlayer().getPosition().y);
            Item table = ctx.getGame().getItemDataManager().createItem(Item.ItemType.HOME_WAR_TABLE, p.x, p.y,
                    com.bpm.minotaur.gamedata.item.ItemColor.TAN, ctx.getGame().getAssetManager());
            ctx.getMaze().addItem(table);
        });
        script.key("a turn at home", Input.Keys.PERIOD, ctx);
        script.wait("told", 6);
        script.once("the Table is pointed out", () -> ctx.assertTrue(told(ctx, "The War Table stands by the Lectern"), "the first return"));
        script.shot("wt02_the_table_pointed_out", ctx);

        GameScreen[] parent = {null};
        script.once("open the Table", () -> {
            ctx.assertTrue(!HintManager.getInstance().livingWarUnlocked(), "the Codex page is still shut");
            parent[0] = ctx.getGameScreen();
            ctx.getGame().setScreen(new WarTableScreen(ctx.getGame(), parent[0], ctx.getWorldManager()));
        });
        script.wait("drawn", 10);
        script.shot("wt03_the_first_opening", ctx);
        script.once("open the Codex on its new page", () -> {
            ctx.assertTrue(HintManager.getInstance().livingWarUnlocked(), "the Codex page has opened");
            ctx.getGame().setScreen(new CodexScreen(ctx.getGame(), parent[0], ctx.getPlayer()).openOn(CodexScreen.Tab.LIVING_WAR));
        });
        script.wait("drawn", 10);
        script.shot("wt04_the_codex_page", ctx);
        script.once("the second opening says nothing", () -> {
            ctx.assertTrue(!HintManager.getInstance().openWarTable(), "the three lines are shown once");
            ctx.getGame().setScreen(parent[0]);
        });
        script.wait("back", 6);
    }
}
