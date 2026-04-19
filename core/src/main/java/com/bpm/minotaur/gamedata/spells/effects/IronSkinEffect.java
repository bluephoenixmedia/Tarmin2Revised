package com.bpm.minotaur.gamedata.spells.effects;

import com.bpm.minotaur.Tarmin2;
import com.bpm.minotaur.gamedata.GameEvent;
import com.bpm.minotaur.managers.GameEventManager;
import com.bpm.minotaur.gamedata.Maze;
import com.bpm.minotaur.gamedata.effects.StatusEffectType;
import com.bpm.minotaur.gamedata.player.Player;
import com.bpm.minotaur.managers.CombatManager;
import com.bpm.minotaur.rendering.AnimationManager;

public class IronSkinEffect implements SpellEffect {

    @Override
    public void execute(Player player, CombatManager combatManager, GameEventManager eventManager, AnimationManager animationManager, Maze maze, Tarmin2 game) {
        if (player != null && player.getStatusManager() != null) {
            player.getStatusManager().addEffect(StatusEffectType.HARDENED, 10, 1, false);
            if (eventManager != null) {
                eventManager.addEvent(new GameEvent("Your skin turns to iron!", 2f));
            }
        }
    }
}
