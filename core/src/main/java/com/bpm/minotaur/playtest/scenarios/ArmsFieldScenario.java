package com.bpm.minotaur.playtest.scenarios;

import com.badlogic.gdx.math.GridPoint2;
import com.badlogic.gdx.math.Vector2;
import com.bpm.minotaur.gamedata.history.Doctrine;
import com.bpm.minotaur.gamedata.history.DoctrineCatalog;
import com.bpm.minotaur.gamedata.history.House;
import com.bpm.minotaur.gamedata.history.war.WarTally;
import com.bpm.minotaur.gamedata.monster.Faction;
import com.bpm.minotaur.gamedata.monster.Monster;
import com.bpm.minotaur.playtest.PlaytestContext;
import com.bpm.minotaur.playtest.PlaytestScenario;
import com.bpm.minotaur.playtest.PlaytestScript;
import com.bpm.minotaur.ui.ArmsInField;

import java.util.ArrayList;
import java.util.List;

/**
 * Arms in the field (Living War A45-A46): soldiers of four houses stood at one, two and three tiles,
 * a captain far off, and natives who should show nothing, on the battle bar and the minimap.
 */
public final class ArmsFieldScenario implements PlaytestScenario {

    /** The maze's walls would hide what this is here to show: out in the desert, where the ground is open. */
    private static GridPoint2 openChunk(PlaytestContext ctx) {
        com.bpm.minotaur.managers.BiomeManager biomes = ctx.getWorldManager().getBiomeManager();
        for (int reach = 11; reach < 60; reach++) {
            for (int[] step : new int[][]{{reach, 0}, {-reach, 0}, {0, reach}, {0, -reach}}) {
                GridPoint2 at = new GridPoint2(step[0], step[1]);
                if (biomes.getBiome(at) == com.bpm.minotaur.generation.Biome.DESERT) return at;
            }
        }
        return new GridPoint2(11, 0);
    }

    /** A tile with the rows ahead of it clear, for the player to stand on looking up them. */
    private static boolean clearAhead(com.bpm.minotaur.gamedata.Maze maze, int x, int y, com.bpm.minotaur.gamedata.Direction facing) {
        Vector2 forward = facing.getVector();
        Vector2 right = facing.getRight().getVector();
        for (int ahead = 0; ahead <= 8; ahead++) {
            for (int aside = -2; aside <= 2; aside++) {
                int tx = x + Math.round(forward.x * ahead + right.x * aside);
                int ty = y + Math.round(forward.y * ahead + right.y * aside);
                if (!maze.isPassable(tx, ty) || maze.isWallBlocking(tx, ty, facing) || maze.isWallBlocking(tx, ty, facing.getRight())) return false;
            }
        }
        return true;
    }

    private static boolean standInTheOpen(PlaytestContext ctx) {
        com.bpm.minotaur.gamedata.Maze maze = ctx.getMaze();
        for (int y = 2; y < maze.getHeight() - 2; y++) {
            for (int x = 2; x < maze.getWidth() - 2; x++) {
                for (com.bpm.minotaur.gamedata.Direction facing : com.bpm.minotaur.gamedata.Direction.values()) {
                    if (!clearAhead(maze, x, y, facing)) continue;
                    ctx.getPlayer().setPosition(x + 0.5f, y + 0.5f);
                    ctx.getPlayer().setFacing(facing);
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public String name() {
        return "arms-field";
    }

    @Override
    public String description() {
        return "Stands four houses' soldiers at one, two and three tiles, with a far captain and natives, and shoots each distance.";
    }

    @Override
    public void buildScript(PlaytestScript script, PlaytestContext ctx) {
        script.wait("settle", 20);
        script.once("settle traits", ctx::settleTraits);
        script.wait("traits settle", 15);
        script.once("out into the field", () -> ctx.getGameScreen().travelToChunk(openChunk(ctx), new GridPoint2(16, 16)));
        script.until("arrive", 300, () -> ctx.getMaze() != null && ctx.getMaze() == ctx.getWorldManager().getCurrentMaze());
        script.wait("look about", 10);

        List<Monster> nearest = new ArrayList<>();
        script.once("stand them up", () -> {
            for (Monster m : new ArrayList<>(ctx.getMaze().getMonsters().values())) ctx.getMaze().removeMonster(m);
            ctx.getMaze().getScenery().clear();
            ctx.dismissModals();
            ctx.assertTrue(standInTheOpen(ctx), "open ground to stand them on");
            List<House> houses = ctx.getWorldManager().getHistory().world().livingHouses();
            ctx.assertTrue(houses.size() >= 4, "four houses to set against each other");
            Monster one = soldier(ctx, houses.get(0), 1, 0);
            nearest.add(one);
            Monster two = soldier(ctx, houses.get(1), 2, 1);
            nearest.add(two);
            soldier(ctx, houses.get(2), 3, -1);
            soldier(ctx, houses.get(3), 3, 1);
            Monster captain = soldier(ctx, houses.get(3), 7, -1);
            captain.setStandardBearer(true);
            soldier(ctx, houses.get(2), 7, 1);
            Monster native_ = stand(ctx, "GIANT_ANT", 2, -1);
            native_.setHouseId(-1);
            Monster guard = stand(ctx, "GIANT_ANT", 3, 2);
            guard.setHouseId(-1);
            guard.setFaction(Faction.TARMIN_LEGION);

            ctx.assertTrue(ArmsInField.bearsArms(one), "a house's soldier bears Arms");
            ctx.assertTrue(!ArmsInField.bearsArms(native_), "a native does not");
            ctx.assertTrue(!ArmsInField.bearsArms(guard), "nor a regular Legion spawn");
            ctx.assertTrue(ArmsInField.bearsBanner(captain), "the captain carries the banner");
            ctx.getGameScreen().getHud().setWarTally(new WarTally(houses.get(0).id, houses.get(3).id, 14, 9));
            ctx.log("Houses: " + houses.get(0).name + ", " + houses.get(1).name + ", " + houses.get(2).name + ", " + houses.get(3).name);
        });
        script.wait("drawn", 6);
        script.shot("arms01_one_two_three_tiles", ctx);
        com.bpm.minotaur.managers.DebugManager debug = com.bpm.minotaur.managers.DebugManager.getInstance();
        // The other engine draws them its own way, and must agree, with one in its face too.
        script.once("to the raycaster", () -> debug.setRenderEngine(com.bpm.minotaur.managers.DebugManager.RenderEngine.RAYCASTER));
        script.wait("drawn", 4);
        script.shot("arms04_raycaster", ctx);
        script.once("back to the planes", () -> debug.setRenderEngine(com.bpm.minotaur.managers.DebugManager.RenderEngine.PLANAR_3D));
        script.wait("drawn", 4);
        script.once("the nearest stands aside", () -> ctx.getMaze().removeMonster(nearest.get(0)));
        script.wait("drawn", 4);
        script.shot("arms02_two_and_three_tiles", ctx);
        script.once("and the next", () -> ctx.getMaze().removeMonster(nearest.get(1)));
        script.wait("drawn", 4);
        script.shot("arms03_three_tiles_and_the_captain", ctx);

        // Retro mode flattens the world to one colour a surface; the Arms must keep theirs.
        script.once("to Retro", () -> debug.setRenderModeDirect(com.bpm.minotaur.managers.DebugManager.RenderMode.RETRO));
        script.wait("drawn", 4);
        script.shot("arms05_retro", ctx);
        script.once("back to Modern", () -> debug.setRenderModeDirect(com.bpm.minotaur.managers.DebugManager.RenderMode.MODERN));
    }

    private static Monster soldier(PlaytestContext ctx, House house, int ahead, int aside) {
        Doctrine doctrine = DoctrineCatalog.getInstance().get(house.doctrineId);
        Monster m = stand(ctx, doctrine.roster.get(0), ahead, aside);
        m.setFaction(Faction.MAZE_HOUSE);
        m.setHouseId(house.id);
        m.setWarBand(true);
        return m;
    }

    private static Monster stand(PlaytestContext ctx, String type, int ahead, int aside) {
        Vector2 forward = ctx.getPlayer().getFacing().getVector();
        Vector2 right = ctx.getPlayer().getFacing().getRight().getVector();
        int x = (int) ctx.getPlayer().getPosition().x + Math.round(forward.x * ahead + right.x * aside);
        int y = (int) ctx.getPlayer().getPosition().y + Math.round(forward.y * ahead + right.y * aside);
        Monster m = ctx.getWorldManager().recruit(type, x, y);
        ctx.assertTrue(m != null, "recruited " + type);
        ctx.getMaze().addMonster(m);
        return m;
    }
}
