package com.bpm.minotaur.playtest.scenarios;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.math.GridPoint2;
import com.badlogic.gdx.math.Vector2;
import com.bpm.minotaur.gamedata.Direction;
import com.bpm.minotaur.gamedata.Door;
import com.bpm.minotaur.gamedata.Maze;
import com.bpm.minotaur.gamedata.monster.Monster;
import com.bpm.minotaur.gamedata.player.Player;
import com.bpm.minotaur.playtest.PlaytestContext;
import com.bpm.minotaur.playtest.PlaytestScenario;
import com.bpm.minotaur.playtest.PlaytestScript;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

public final class StochasticExplorerScenario implements PlaytestScenario {

    @Override
    public String name() {
        return "explore";
    }

    @Override
    public String description() {
        return "Autonomous stochastic explorer bot that plays honestly without cheats for N turns, asserting world and player invariants.";
    }

    @Override
    public void buildScript(PlaytestScript script, PlaytestContext ctx) {
        script.wait("settle", 20);
        script.once("settle traits", ctx::settleTraits);
        script.wait("traits settle", 15);
        script.once("outfit starter", () -> ctx.outfitHero(1));

        int targetTurns = ctx.getConfig().getTurns();
        int[] turnsExecuted = {0};
        int[] doorsOpened = {0};
        int[] monstersFought = {0};
        Set<GridPoint2> visitedTiles = new HashSet<>();
        Random random = ctx.getConfig().getSeed() != null ? new Random(ctx.getConfig().getSeed()) : new Random();

        script.until("autonomous exploration loop", targetTurns * 12 + 100, () -> {
            if (ctx.getFrame() % 4 != 0) return false;

            Player p = ctx.getPlayer();
            Maze maze = ctx.getMaze();
            if (p == null || maze == null) return true;

            GridPoint2 here = new GridPoint2((int) p.getPosition().x, (int) p.getPosition().y);
            visitedTiles.add(here);

            // Invariant assertions
            ctx.assertTrue(p.getPosition().x >= 0 && p.getPosition().x < maze.getWidth(), "Player X within bounds");
            ctx.assertTrue(p.getPosition().y >= 0 && p.getPosition().y < maze.getHeight(), "Player Y within bounds");
            ctx.assertTrue(p.getStats().getCurrentHP() >= 0, "Player HP cannot be negative");
            if (p.getStats().getCurrentHP() <= 10) {
                ctx.press(Input.Keys.END);
            }

            // Check adjacent monsters to attack
            Monster targetMonster = null;
            Direction monsterDir = null;
            for (Direction dir : Direction.values()) {
                Vector2 dv = dir.getVector();
                int nx = here.x + (int) dv.x;
                int ny = here.y + (int) dv.y;
                Monster m = maze.getMonsters().get(new GridPoint2(nx, ny));
                if (m != null && m.isAlive()) {
                    targetMonster = m;
                    monsterDir = dir;
                    break;
                }
            }

            if (targetMonster != null) {
                // Engage in combat
                if (p.getFacing() == monsterDir) {
                    ctx.press(Input.Keys.UP); // Bump attack
                    monstersFought[0]++;
                } else if (p.getFacing().getRight() == monsterDir) {
                    ctx.press(Input.Keys.RIGHT);
                } else {
                    ctx.press(Input.Keys.LEFT);
                }
                turnsExecuted[0]++;
                return turnsExecuted[0] >= targetTurns;
            }

            // Check door ahead
            Vector2 forward = p.getFacing().getVector();
            Object ahead = maze.getGameObjectAt((int) (here.x + forward.x), (int) (here.y + forward.y));
            if (ahead instanceof Door && ((Door) ahead).getState() != Door.DoorState.OPEN) {
                ctx.press(Input.Keys.O); // Open door
                doorsOpened[0]++;
                turnsExecuted[0]++;
                return turnsExecuted[0] >= targetTurns;
            }

            // Explore navigation: pick passable neighbor
            List<Direction> passableDirs = new ArrayList<>();
            List<Direction> unvisitedDirs = new ArrayList<>();
            for (Direction dir : Direction.values()) {
                Vector2 dv = dir.getVector();
                int nx = here.x + (int) dv.x;
                int ny = here.y + (int) dv.y;
                if (maze.isPassable(nx, ny)) {
                    passableDirs.add(dir);
                    if (!visitedTiles.contains(new GridPoint2(nx, ny))) {
                        unvisitedDirs.add(dir);
                    }
                }
            }

            Direction chosenDir = null;
            if (!unvisitedDirs.isEmpty()) {
                chosenDir = unvisitedDirs.get(random.nextInt(unvisitedDirs.size()));
            } else if (!passableDirs.isEmpty()) {
                chosenDir = passableDirs.get(random.nextInt(passableDirs.size()));
            }

            if (chosenDir != null) {
                if (p.getFacing() == chosenDir) {
                    ctx.press(Input.Keys.UP);
                } else if (p.getFacing().getRight() == chosenDir) {
                    ctx.press(Input.Keys.RIGHT);
                } else {
                    ctx.press(Input.Keys.LEFT);
                }
            } else {
                ctx.press(Input.Keys.RIGHT); // Turn around if boxed in
            }

            turnsExecuted[0]++;
            if (turnsExecuted[0] % 100 == 0) {
                ctx.log(String.format("Explorer turn %d/%d: %d tiles explored, %d doors opened, %d fights",
                        turnsExecuted[0], targetTurns, visitedTiles.size(), doorsOpened[0], monstersFought[0]));
            }

            return turnsExecuted[0] >= targetTurns;
        });

        script.once("log explorer results", () -> {
            ctx.log(String.format("Explorer completed %d turns. Unique tiles visited: %d. Doors opened: %d. Monsters fought: %d.",
                    turnsExecuted[0], visitedTiles.size(), doorsOpened[0], monstersFought[0]));
            ctx.assertTrue(turnsExecuted[0] > 0, "Explorer bot must execute turns");
        });

        script.shot("pt08_explorer_bot", ctx);
    }
}
