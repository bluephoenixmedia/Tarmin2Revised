package com.bpm.minotaur.gamedata.spells.effects;

import com.bpm.minotaur.Tarmin2;
import com.bpm.minotaur.managers.GameEventManager;
import com.bpm.minotaur.gamedata.Maze;
import com.bpm.minotaur.gamedata.player.Player;
import com.bpm.minotaur.managers.CombatManager;
import com.bpm.minotaur.rendering.AnimationManager;

public interface SpellEffect {
    void execute(Player player, CombatManager combatManager, GameEventManager eventManager, AnimationManager animationManager, Maze maze, Tarmin2 game);
}
