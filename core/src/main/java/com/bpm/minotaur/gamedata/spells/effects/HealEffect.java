package com.bpm.minotaur.gamedata.spells.effects;

import com.bpm.minotaur.Tarmin2;
import com.bpm.minotaur.gamedata.GameEvent;
import com.bpm.minotaur.managers.GameEventManager;
import com.bpm.minotaur.gamedata.Maze;
import com.bpm.minotaur.gamedata.player.Player;
import com.bpm.minotaur.managers.CombatManager;
import com.bpm.minotaur.rendering.AnimationManager;

public class HealEffect implements SpellEffect {

    @Override
    public void execute(Player player, CombatManager combatManager, GameEventManager eventManager, AnimationManager animationManager, Maze maze, Tarmin2 game) {
        if (player != null) {
            int healAmount = 10 + (player.getLevel() * 2);
            int currentHp = player.getCurrentHP();
            int maxHp = player.getMaxHP();
            int actualHeal = Math.min(healAmount, maxHp - currentHp);

            player.heal(actualHeal);

            if (eventManager != null) {
                eventManager.addEvent(new GameEvent("You heal for " + actualHeal + " HP!", 2f));
            }
        }
    }
}
