package com.bpm.minotaur.gamedata.spells.effects;

import com.badlogic.gdx.math.GridPoint2;
import com.badlogic.gdx.math.Vector2;
import com.bpm.minotaur.Tarmin2;
import com.bpm.minotaur.gamedata.GameEvent;
import com.bpm.minotaur.managers.GameEventManager;
import com.bpm.minotaur.gamedata.Maze;
import com.bpm.minotaur.gamedata.monster.Monster;
import com.bpm.minotaur.gamedata.player.Player;
import com.bpm.minotaur.managers.CombatManager;
import com.bpm.minotaur.rendering.Animation;
import com.bpm.minotaur.rendering.AnimationManager;

public class MagicArrowEffect implements SpellEffect {

    @Override
    public void execute(Player player, CombatManager combatManager, GameEventManager eventManager, AnimationManager animationManager, Maze maze, Tarmin2 game) {
        
        Vector2 startPos = player.getPosition().cpy().add(player.getDirectionVector().cpy().scl(0.6f));
        Vector2 targetPos = null;
        Monster targetMonster = combatManager.getMonster();

        if (targetMonster == null) {
            CombatManager.HitResult hit = combatManager.raycastProjectile(player.getPosition(), player.getFacing(), 8, true);
            if (hit.type == CombatManager.HitResult.HitType.MONSTER && hit.hitMonster != null) {
                targetMonster = hit.hitMonster;
                targetPos = targetMonster.getPosition();
            } else {
                targetPos = new Vector2(hit.collisionPoint.x + 0.5f, hit.collisionPoint.y + 0.5f);
            }
        } else {
            targetPos = targetMonster.getPosition();
        }

        if (animationManager != null) {
            animationManager.addAnimation(new Animation(
                    Animation.AnimationType.PROJECTILE_SPELL,
                    startPos, targetPos,
                    com.badlogic.gdx.graphics.Color.CYAN, 0.6f,
                    new String[]{"*"}));
        }

        if (targetMonster != null) {
            int magicDamage = 5 + (player.getLevel());
            int actualDamage = targetMonster.takeDamage(magicDamage);

            if (combatManager != null) {
                combatManager.showDamageText(actualDamage, new GridPoint2((int) targetMonster.getPosition().x, (int) targetMonster.getPosition().y));
            }

            if (targetMonster.getCurrentHP() <= 0) {
                if (combatManager != null && targetMonster == combatManager.getMonster()) {
                    combatManager.handleMonsterDeath();
                    combatManager.setCurrentState(CombatManager.CombatState.VICTORY);
                } else {
                    handleRemoteKill(targetMonster, maze, player, eventManager);
                }
            } else if (combatManager != null && combatManager.getMonster() == null) {
                combatManager.startCombat(targetMonster);
            }
        }
    }
    
    private void handleRemoteKill(Monster m, Maze maze, Player player, GameEventManager eventManager) {
        if (maze != null && m != null) {
            maze.getMonsters().remove(new GridPoint2((int) m.getPosition().x, (int) m.getPosition().y));
        }
        if (player != null && m != null) {
            player.getStats().addExperience(m.getBaseExperience());
        }
        if (eventManager != null && m != null) {
            eventManager.addEvent(new GameEvent("Killed " + m.getMonsterType() + "!", 2f));
        }
    }
}
