package com.bpm.minotaur.playtest.scenarios;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.math.GridPoint2;
import com.bpm.minotaur.gamedata.Maze;
import com.bpm.minotaur.gamedata.history.war.Encounter;
import com.bpm.minotaur.gamedata.history.war.EncounterLedger;
import com.bpm.minotaur.gamedata.history.war.WarSoundscape;
import com.bpm.minotaur.gamedata.monster.Monster;
import com.bpm.minotaur.playtest.PlaytestContext;
import com.bpm.minotaur.playtest.PlaytestScenario;
import com.bpm.minotaur.playtest.PlaytestScript;

import java.util.HashSet;
import java.util.Set;

/**
 * The Living War as a simulation (A1, A2): nothing is placed for the player, so the scenario finds
 * an army on the roster, walks to where it stands, and meets it on the ground.
 */
public final class LivingWarScenario implements PlaytestScenario {

    @Override
    public String name() {
        return "living-war";
    }

    @Override
    public String description() {
        return "Reads the War Table, finds real armies on the roster, walks into them, and chooses a side in a battle under way.";
    }

    private static boolean shown(PlaytestContext ctx) {
        return ctx.getMaze() != null && ctx.getMaze() == ctx.getWorldManager().getCurrentMaze();
    }

    @Override
    public void buildScript(PlaytestScript script, PlaytestContext ctx) {
        script.wait("settle", 20);
        script.once("settle traits", ctx::settleTraits);
        script.wait("traits settle", 15);
        script.once("outfit hero", () -> {
            ctx.outfitHero(10);
            com.bpm.minotaur.gamedata.progression.ShelterAltar.getInstance().setStationUnlocked(
                    com.bpm.minotaur.gamedata.progression.ShelterAltar.Station.ARCHIVE_LECTERN, true);
            com.bpm.minotaur.gamedata.progression.ShelterAltar.getInstance().furnish(
                    ctx.getMaze(), ctx.getGame().getItemDataManager(), ctx.getGame().getAssetManager());
        });

        script.once("a war is under way", () -> {
            ctx.assertTrue(!ctx.getWorldManager().getHistory().world().activeWars().isEmpty(), "a new game opens on a war (W6)");
            ctx.assertEquals(1, ctx.getWorldManager().getCurrentLevel(), "on the surface");
        });

        script.once("the War Table stands beside the Lectern", () -> {
            int tables = 0;
            for (com.bpm.minotaur.gamedata.item.Item item : ctx.getMaze().getItems().values()) {
                if (item.getType() == com.bpm.minotaur.gamedata.item.Item.ItemType.HOME_WAR_TABLE) tables++;
            }
            ctx.log("War Tables in the home shelter: " + tables);
            ctx.assertTrue(tables == 1, "one War Table beside the Lectern (W27)");
            ctx.getGame().setScreen(new com.bpm.minotaur.screens.WarTableScreen(ctx.getGame(), ctx.getGameScreen(),
                    ctx.getWorldManager()));
        });
        script.wait("the table is read", 20);
        script.shot("lw00_war_table", ctx);
        script.once("back to the shelter", () -> ctx.getGame().setScreen(ctx.getGameScreen()));
        script.wait("back", 10);

        // A column on the march: find one, and stand in its way.
        Encounter[] column = {null};
        script.until("wait for a column", 6000, () -> {
            if (ctx.getFrame() % 2 != 0) return false;
            for (Encounter e : ctx.getWorldManager().currentEncounters()) {
                if (e.kind == Encounter.Kind.COLUMN) column[0] = e;
            }
            if (column[0] != null) return true;
            ctx.press(Input.Keys.PERIOD);
            return false;
        });
        script.once("stand in its way", () -> {
            long clock = ctx.getWorldManager().getHistory().warClock();
            ctx.log("Column: " + column[0]);
            ctx.getGameScreen().travelToChunk(column[0].chunkAt(clock), new GridPoint2(16, 16));
        });
        script.until("arrive at the column", 300, () -> shown(ctx));
        script.key("a turn passes", Input.Keys.PERIOD, ctx);
        script.wait("they come on", 10);
        script.once("a column marches through", () -> {
            int marching = 0;
            for (Monster m : ctx.getMaze().getMonsters().values()) if (m.isWarBand() && m.getMarchTarget() != null) marching++;
            ctx.log("Soldiers on the march: " + marching);
            ctx.assertTrue(marching >= 1, "the soldiers the roster says are marching");
        });
        script.shot("lw05_column", ctx);

        // A war camp: wherever a host has halted, which it does when it has nowhere better to be.
        script.until("wait for a host to halt", 6000, () -> {
            if (ctx.getFrame() % 2 != 0) return false;
            for (Encounter c : ctx.getWorldManager().currentCamps()) if (c.slot > 0) return true;
            ctx.press(Input.Keys.PERIOD);
            return false;
        });
        script.once("go to a camp", () -> {
            java.util.List<Encounter> camps = ctx.getWorldManager().currentCamps();
            ctx.log("Camp: " + camps.get(0));
            ctx.getGameScreen().travelToChunk(camps.get(0).from, new GridPoint2(16, 16));
        });
        script.until("arrive at the camp", 300, () -> shown(ctx));
        script.key("a turn passes", Input.Keys.PERIOD, ctx);
        script.wait("the camp settles", 10);
        script.once("tents, fires and sentries", () -> {
            int tents = 0;
            for (com.bpm.minotaur.gamedata.Scenery sc : ctx.getMaze().getScenery().values()) {
                if ("camp_tent".equals(sc.getPropId())) tents++;
            }
            ctx.log("Camp tents: " + tents + ", sentries: " + ctx.countWarBand());
            ctx.assertTrue(tents >= 1, "a camp's tents");
            ctx.assertTrue(ctx.countWarBand() >= 3, "its sentries");
        });
        script.shot("lw06_camp", ctx);

        // The oath (W20): a friend of the camp's house stands at its banner and swears.
        int[] campHouse = {-1};
        script.once("befriend the camp's house", () -> {
            campHouse[0] = ctx.getWorldManager().currentCamps().get(0).houseA;
            com.bpm.minotaur.managers.HistoryManager h = ctx.getWorldManager().getHistory();
            h.favour().set(campHouse[0], com.bpm.minotaur.gamedata.history.favour.Favour.FRIEND_AT);
        });
        script.key("a turn passes", Input.Keys.PERIOD, ctx);
        script.wait("greeted", 10);
        script.once("stand at the banner", () -> {
            GridPoint2 chunk = ctx.getWorldManager().getCurrentPlayerChunkId();
            GridPoint2 banner = null;
            for (com.bpm.minotaur.gamedata.history.war.EncounterLedger.Dressing d
                    : ctx.getWorldManager().getHistory().encounterLedger().dressings) {
                if (!"CAMP".equals(d.kind) || d.chunkX != chunk.x || d.chunkY != chunk.y) continue;
                for (int i = 0; i < d.props.size(); i++) {
                    if ("war_banner".equals(d.props.get(i))) banner = new GridPoint2(d.tiles.get(2 * i), d.tiles.get(2 * i + 1));
                }
            }
            ctx.assertTrue(banner != null, "the camp flies a banner");
            com.bpm.minotaur.gamedata.Direction[] dirs = com.bpm.minotaur.gamedata.Direction.values();
            for (com.bpm.minotaur.gamedata.Direction d : dirs) {
                int x = banner.x - (int) d.getVector().x, y = banner.y - (int) d.getVector().y;
                if (!ctx.getMaze().isPassable(x, y) || ctx.getMaze().getMonsters().containsKey(new GridPoint2(x, y))) continue;
                if (ctx.getMaze().isWallBlocking(x, y, d)) continue;
                ctx.getPlayer().setPosition(x + 0.5f, y + 0.5f);
                ctx.getPlayer().setFacing(d);
                return;
            }
        });
        script.wait("in place", 5);
        script.key("swear", Input.Keys.O, ctx);
        script.wait("sworn", 15);
        script.once("sworn, in its colours, with a task", () -> {
            com.bpm.minotaur.managers.HistoryManager h = ctx.getWorldManager().getHistory();
            ctx.log("Oath: house " + h.oath().houseId + "; contract "
                    + (h.oath().contract == null ? "none" : h.oath().contract.describe(h.world()))
                    + "; quartermaster " + (ctx.getMaze().getShopkeeper() != null));
            ctx.assertEquals(campHouse[0], h.oath().houseId, "sworn to the camp's house");
            boolean tabard = false;
            for (com.bpm.minotaur.gamedata.item.Item i : ctx.getPlayer().getInventory().getAllItems()) {
                if (i != null && i.getType() == com.bpm.minotaur.gamedata.item.Item.ItemType.TABARD && i.getHeraldry() != null) tabard = true;
            }
            ctx.assertTrue(tabard, "given the house's tabard");
            ctx.assertTrue(ctx.getMaze().getShopkeeper() != null, "its quartermaster trades with a friend");
        });
        script.shot("lw07_sworn", ctx);

        // A battle under way (A21, A26): find one on the roster, walk in, choose a side and fight.
        com.bpm.minotaur.gamedata.history.war.army.ArmyBattle[] battle = {null};
        script.until("wait for a battle", 8000, () -> {
            if (ctx.getFrame() % 2 != 0) return false;
            for (com.bpm.minotaur.gamedata.history.war.army.ArmyBattle b : ctx.getWorldManager().getHistory().roster().battles) {
                if (b.turn > 1 && b.turn < 60 && b.startA + b.startB >= 20) battle[0] = b;
            }
            if (battle[0] != null) return true;
            ctx.press(Input.Keys.PERIOD);
            return false;
        });
        script.once("walk into it", () -> {
            ctx.log("Battle at " + battle[0].x + "," + battle[0].y + ": " + battle[0].startA + " against " + battle[0].startB
                    + ", turn " + battle[0].turn);
            ctx.getGameScreen().travelToChunk(new GridPoint2(battle[0].x, battle[0].y), new GridPoint2(16, 16));
        });
        script.until("arrive at the battle", 300, () -> shown(ctx));
        script.key("a turn passes", Input.Keys.PERIOD, ctx);
        script.until("the side prompt comes up", 120, () -> ctx.getGame().getScreen() instanceof com.bpm.minotaur.screens.BattleSideScreen);
        script.shot("lw08_choose_a_side", ctx);
        String[] chosen = {null};
        script.once("choose a side", () -> {
            java.util.Set<String> labels = ((com.bpm.minotaur.screens.BattleSideScreen) ctx.getGame().getScreen()).labels();
            ctx.log("On offer: " + labels);
            ctx.assertTrue(labels.contains("Keep away"), "keeping away is always on offer");
            chosen[0] = labels.iterator().next();
            ctx.assertTrue(ctx.chooseSide(chosen[0]), "the side is taken");
        });
        script.wait("back on the field", 10);
        script.once("the lines stand from the numbers, and the side stands by you", () -> {
            int field = ctx.countWarBand();
            ctx.log("Soldiers on the field: " + field + ", chose " + chosen[0] + ", side " + battle[0].playerSide);
            ctx.assertTrue(field >= 2, "the lines are on the field");
            ctx.assertTrue(battle[0].playerSide != 0, "the player stands with a side");
            ctx.assertTrue(ctx.getWorldManager().armyLayer().watchedBattle() == battle[0], "the map is the truth while watched");
        });
        script.shot("lw09_in_the_battle", ctx);
        int[] alive = {0};
        script.once("note the numbers", () -> alive[0] = ctx.getWorldManager().armyLayer().aliveOn(battle[0], battle[0].playerSide != 1));
        script.until("cut down an enemy soldier", 1500, () -> {
            if (ctx.getFrame() % 2 != 0) return false;
            if (ctx.getWarManager().active() == null || !ctx.getWarManager().active().battle.equals(battle[0])) return true;
            if (battle[0].playerStruck != 0 && ctx.getWorldManager().armyLayer().aliveOn(battle[0], battle[0].playerSide != 1) < alive[0]) return true;
            Monster foe = null;
            float best = Float.MAX_VALUE;
            for (Monster m : ctx.getMaze().getMonsters().values()) {
                if (m == null || !m.isAlive() || !m.isWarBand() || m.isGateGuard() || m.getBattleSide() == battle[0].playerSide) continue;
                float d = m.getPosition().dst(ctx.getPlayer().getPosition());
                if (d < best) {
                    best = d;
                    foe = m;
                }
            }
            ctx.press(Input.Keys.END);
            if (foe != null) ctx.steerToward(new GridPoint2((int) foe.getPosition().x, (int) foe.getPosition().y));
            return false;
        });
        script.once("the blow counted", () -> {
            ctx.log("Struck " + battle[0].playerStruck + ", fought for " + battle[0].foughtFor() + ", battle "
                    + ctx.battlePhase() + ", enemy numbers " + alive[0] + " to "
                    + ctx.getWorldManager().armyLayer().aliveOn(battle[0], battle[0].playerSide != 1));
            ctx.assertTrue(battle[0].playerStruck != 0 || ctx.getWarManager().active() == null, "the player drew blood, or the battle broke first");
        });
        script.shot("lw10_fighting", ctx);
    }
}
