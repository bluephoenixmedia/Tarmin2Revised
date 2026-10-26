package com.bpm.minotaur.playtest.scenarios;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.math.GridPoint2;
import com.badlogic.gdx.math.Vector2;
import com.bpm.minotaur.gamedata.Direction;
import com.bpm.minotaur.gamedata.Door;
import com.bpm.minotaur.gamedata.Maze;
import com.bpm.minotaur.gamedata.player.Player;
import com.bpm.minotaur.managers.SettingsManager;
import com.bpm.minotaur.playtest.PlaytestContext;
import com.bpm.minotaur.playtest.PlaytestScenario;
import com.bpm.minotaur.playtest.PlaytestScript;
import com.bpm.minotaur.screens.map.ExpeditionMapScreen;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Walks a few surface chunks and one stratum, then photographs the expedition map at every
 * zoom and floor, so the isometric diorama can be checked against the handoff screenshots.
 */
public final class ExpeditionMapScenario implements PlaytestScenario {

    @Override
    public String name() {
        return "map";
    }

    @Override
    public String description() {
        return "Explores several chunks and a stratum, then captures the expedition map at each zoom and floor.";
    }

    @Override
    public void buildScript(PlaytestScript script, PlaytestContext ctx) {
        script.wait("settle", 20);
        script.once("settle traits", ctx::settleTraits);
        script.wait("traits settle", 15);
        script.once("outfit hero", () -> ctx.outfitHero(8));

        Random random = new Random(7L);
        explore(script, ctx, "home", 160, random);
        visit(script, ctx, new GridPoint2(1, 0), random);
        visit(script, ctx, new GridPoint2(1, 1), random);
        visit(script, ctx, new GridPoint2(0, 1), random);
        visit(script, ctx, new GridPoint2(-1, 1), random);
        visit(script, ctx, new GridPoint2(1, 0), random);

        script.once("refill before descent", () -> ctx.press(Input.Keys.END));
        script.key("descend to stratum 2", Input.Keys.NUMPAD_7, ctx);
        script.wait("arrive stratum 2", 30);
        explore(script, ctx, "stratum", 120, random);

        script.once("report ground", () -> ctx.log("Level " + ctx.getWorldManager().getCurrentLevel()
                + " chunk " + ctx.getWorldManager().getCurrentPlayerChunkId()
                + ", surface chunks saved: " + ctx.getWorldManager().getVisitedChunkIds(1)));
        script.once("refill", () -> ctx.press(Input.Keys.END));
        script.once("open map", () -> ctx.getGame().setScreen(new ExpeditionMapScreen(ctx.getGame(),
                ctx.getPlayer(), ctx.getMaze(), ctx.getGameScreen())));
        script.until("map open", 120, () -> ctx.getGame().getScreen() instanceof ExpeditionMapScreen);
        script.wait("map settle", 90);
        script.shot("map_01_stratum_region", ctx);

        mapKey(script, ctx, "surface", Input.Keys.RIGHT_BRACKET);
        script.wait("floor ease", 90);
        script.shot("map_02_surface_region", ctx);

        mapKey(script, ctx, "zoom out", Input.Keys.ESCAPE);
        script.wait("zoom ease", 60);
        script.shot("map_03_surface_world", ctx);

        mapKey(script, ctx, "zoom in", Input.Keys.ENTER);
        mapKey(script, ctx, "zoom in again", Input.Keys.ENTER);
        script.wait("zoom ease", 60);
        script.shot("map_04_surface_chunk", ctx);

        mapKey(script, ctx, "rotate", Input.Keys.E);
        script.wait("rotate ease", 60);
        script.shot("map_05_surface_chunk_rotated", ctx);

        mapKey(script, ctx, "back to region", Input.Keys.ESCAPE);
        script.wait("zoom ease", 60);
        script.shot("map_06_surface_region_rotated", ctx);

        mapKey(script, ctx, "pan right", Input.Keys.RIGHT);
        mapKey(script, ctx, "pan right again", Input.Keys.RIGHT);
        mapKey(script, ctx, "pan up", Input.Keys.UP);
        script.wait("pan ease", 60);
        script.shot("map_07_region_keyboard_pan", ctx);

        mapKey(script, ctx, "down to the stratum", Input.Keys.LEFT_BRACKET);
        mapKey(script, ctx, "centre on the player", Input.Keys.C);
        mapKey(script, ctx, "into the chunk view", Input.Keys.ENTER);
        script.wait("floor and zoom ease", 120);
        script.shot("map_08_stratum_chunk", ctx);

        mapKey(script, ctx, "close", SettingsManager.getInstance().getKey("MAP"));
        script.wait("closed", 20);
    }

    private static void mapKey(PlaytestScript script, PlaytestContext ctx, String name, int keycode) {
        script.once(name, () -> {
            InputProcessor in = Gdx.input.getInputProcessor();
            if (in != null) {
                in.keyDown(keycode);
                in.keyUp(keycode);
            }
        });
        script.wait(name + " (settle)", 8);
    }

    private static void visit(PlaytestScript script, PlaytestContext ctx, GridPoint2 chunk, Random random) {
        script.once("travel to " + chunk, () -> {
            Maze m = ctx.getMaze();
            GridPoint2 tile = new GridPoint2(m.getWidth() / 2, m.getHeight() / 2);
            ctx.getGameScreen().travelToChunk(chunk, tile);
        });
        script.wait("arrive " + chunk, 20);
        explore(script, ctx, chunk.toString(), 90, random);
    }

    /** The explorer bot's walk, without its fights: prefer unwalked tiles, open doors ahead. */
    private static void explore(PlaytestScript script, PlaytestContext ctx, String name, int turns, Random random) {
        int[] done = {0};
        Set<GridPoint2> walked = new HashSet<>();
        script.until("explore " + name, turns * 6 + 60, () -> {
            if (ctx.getFrame() % 4 != 0) return false;
            Player p = ctx.getPlayer();
            Maze maze = ctx.getMaze();
            if (p == null || maze == null) return true;
            if (done[0] % 25 == 0) ctx.press(Input.Keys.END); // food, water and HP: fainting eats moves
            GridPoint2 here = new GridPoint2((int) p.getPosition().x, (int) p.getPosition().y);
            walked.add(here);

            Vector2 f = p.getFacing().getVector();
            Object ahead = maze.getGameObjectAt((int) (here.x + f.x), (int) (here.y + f.y));
            if (ahead instanceof Door && ((Door) ahead).getState() != Door.DoorState.OPEN) {
                ctx.press(Input.Keys.O);
                return ++done[0] >= turns;
            }

            List<Direction> open = new ArrayList<>();
            List<Direction> fresh = new ArrayList<>();
            for (Direction d : Direction.values()) {
                int nx = here.x + (int) d.getVector().x;
                int ny = here.y + (int) d.getVector().y;
                if (!maze.isPassable(nx, ny) || maze.isWallBlocking(here.x, here.y, d)) continue;
                open.add(d);
                if (!walked.contains(new GridPoint2(nx, ny))) fresh.add(d);
            }
            List<Direction> pick = fresh.isEmpty() ? open : fresh;
            Direction want = pick.isEmpty() ? null : pick.get(random.nextInt(pick.size()));
            if (want == null || p.getFacing().getRight() == want) ctx.press(Input.Keys.RIGHT);
            else if (p.getFacing() == want) ctx.press(Input.Keys.UP);
            else ctx.press(Input.Keys.LEFT);
            return ++done[0] >= turns;
        });
    }
}
