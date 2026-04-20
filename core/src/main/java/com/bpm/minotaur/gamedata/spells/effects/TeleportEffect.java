package com.bpm.minotaur.gamedata.spells.effects;

import com.badlogic.gdx.math.GridPoint2;
import com.bpm.minotaur.Tarmin2;
import com.bpm.minotaur.gamedata.GameEvent;
import com.bpm.minotaur.managers.GameEventManager;
import com.bpm.minotaur.gamedata.Maze;
import com.bpm.minotaur.gamedata.player.Player;
import com.bpm.minotaur.managers.CombatManager;
import com.bpm.minotaur.rendering.AnimationManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class TeleportEffect implements SpellEffect {

    @Override
    public void execute(Player player, CombatManager combatManager, GameEventManager eventManager, AnimationManager animationManager, Maze maze, Tarmin2 game) {
        if (maze != null && player != null) {
            List<GridPoint2> validTiles = new ArrayList<>();
            for (int x = 0; x < maze.getWidth(); x++) {
                for (int y = 0; y < maze.getHeight(); y++) {
                    if (maze.isPassable(x, y) && !maze.getMonsters().containsKey(new GridPoint2(x, y))) {
                        validTiles.add(new GridPoint2(x, y));
                    }
                }
            }

            if (!validTiles.isEmpty()) {
                Random random = new Random();
                GridPoint2 newTarget = validTiles.get(random.nextInt(validTiles.size()));
                player.setPosition(newTarget);

                if (eventManager != null) {
                    eventManager.addEvent(new GameEvent("You teleport to a new location!", 2f));
                }
            } else {
                if (eventManager != null) {
                    eventManager.addEvent(new GameEvent("Teleport failed!", 2f));
                }
            }
        }
    }
}
