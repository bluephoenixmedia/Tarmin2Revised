package com.bpm.minotaur.playtest.scenarios;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.math.GridPoint2;
import com.bpm.minotaur.gamedata.Direction;
import com.bpm.minotaur.gamedata.Maze;
import com.bpm.minotaur.gamedata.liquid.LiquidType;
import com.bpm.minotaur.gamedata.monster.Monster;
import com.bpm.minotaur.generation.theme.ChunkTheme;
import com.bpm.minotaur.playtest.PlaytestContext;
import com.bpm.minotaur.playtest.PlaytestScenario;
import com.bpm.minotaur.playtest.PlaytestScript;

/**
 * The Bridge of Souls, rebuilt 2026-10-10: warps into one, stands at a gate looking down the bridge
 * toward the island, and photographs it in each render mode -- the molten chasm must read as a chasm
 * in all of them, not as an invisible wall.
 */
public final class BridgeOfSoulsScenario implements PlaytestScenario {

    @Override
    public String name() {
        return "bridge-of-souls";
    }

    @Override
    public String description() {
        return "Warp into a Bridge of Souls, stand at a gate and look down the bridge, in every render mode.";
    }

    @Override
    public void buildScript(PlaytestScript script, PlaytestContext ctx) {
        script.wait("settle", 20);
        script.once("settle traits", ctx::settleTraits);
        script.wait("traits settle", 15);
        script.once("outfit hero", () -> ctx.outfitHero(10));
        script.until("warp until the Bridge of Souls", 2000, () -> {
            // The world's current maze, not only the screen's copy, which can lag a warp.
            Maze here = ctx.getWorldManager().getCurrentMaze();
            Maze shown = ctx.getMaze();
            if ((here != null && here.getChunkTheme() == ChunkTheme.BRIDGE_OF_SOULS)
                    && shown == here) return true;
            if (here != null && here.getChunkTheme() == ChunkTheme.BRIDGE_OF_SOULS) return false;
            if (ctx.getFrame() % 30 == 0) ctx.press(Input.Keys.F4);
            return false;
        });
        script.wait("arrive", 30);
        script.once("stand at a gate, facing the island", () -> {
            Maze maze = ctx.getMaze();
            int fire = 0;
            for (int y = 0; y < maze.getHeight(); y++) {
                for (int x = 0; x < maze.getWidth(); x++) {
                    if (maze.getLiquidManager().getLiquidAt(x, y) == LiquidType.MOLTEN_FIRE) fire++;
                }
            }
            Monster keeper = null;
            for (Monster m : maze.getMonsters().values()) if (m.isThemeChampion()) keeper = m;
            ctx.assertTrue(fire > 100, "a chasm of fire (" + fire + " tiles)");
            ctx.assertTrue(keeper != null && keeper.getType() == Monster.MonsterType.BRINGER_OF_DEATH, "the Bringer waits");
            ctx.assertTrue(keeper.getTether() != null, "tethered to his island");
            GridPoint2 gate = maze.getGates().keySet().iterator().next();
            int dx = gate.x == 0 ? 1 : gate.x == maze.getWidth() - 1 ? -1 : 0;
            int dy = gate.y == 0 ? 1 : gate.y == maze.getHeight() - 1 ? -1 : 0;
            ctx.getPlayer().setPosition(gate.x + dx * 2 + 0.5f, gate.y + dy * 2 + 0.5f);
            ctx.getPlayer().setFacing(dx > 0 ? Direction.EAST : dx < 0 ? Direction.WEST : dy > 0 ? Direction.NORTH : Direction.SOUTH);
            ctx.log("Bridge of Souls: " + fire + " tiles of fire; the Bringer (HP " + keeper.getMaxHP()
                    + ") at " + keeper.getPosition() + "; standing by the gate at " + gate);
        });
        script.wait("look", 20);
        script.shot("bridge_of_souls_1", ctx);
        script.key("other render engine", Input.Keys.F7, ctx);
        script.wait("redraw", 30);
        script.shot("bridge_of_souls_2", ctx);
        script.key("retro", Input.Keys.F2, ctx);
        script.wait("shift", 90);
        script.shot("bridge_of_souls_3", ctx);
    }
}
