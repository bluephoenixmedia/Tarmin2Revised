package com.bpm.minotaur.gamedata.spells.effects;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.GridPoint2;
import com.badlogic.gdx.math.Vector2;
import com.bpm.minotaur.Tarmin2;
import com.bpm.minotaur.gamedata.GameEvent;
import com.bpm.minotaur.gamedata.Maze;
import com.bpm.minotaur.gamedata.monster.Monster;
import com.bpm.minotaur.gamedata.player.Player;
import com.bpm.minotaur.managers.CombatManager;
import com.bpm.minotaur.managers.GameEventManager;
import com.bpm.minotaur.rendering.Animation;
import com.bpm.minotaur.rendering.AnimationManager;
import com.bpm.minotaur.screens.GameScreen;

/**
 * Drain: pulls blood from a distant monster, damaging it and healing the player
 * for the same amount.
 *
 * Damage = 3 + (player level * 2).
 * Visual: a stream of blood-red particles travels from the monster to the player,
 * accompanied by a hand-raise overlay.
 */
public class DrainEffect implements SpellEffect {

    private static final Color BLOOD_RED = new Color(0.80f, 0.02f, 0.02f, 1f);
    private static final float DRAIN_DURATION = 0.85f;

    @Override
    public void execute(Player player, CombatManager combatManager, GameEventManager eventManager,
                        AnimationManager animationManager, Maze maze, Tarmin2 game) {

        // --- Acquire target ---
        Monster targetMonster = combatManager.getMonster();
        Vector2 targetPos;

        if (targetMonster == null) {
            CombatManager.HitResult hit = combatManager.raycastProjectile(
                    player.getPosition(), player.getFacing(), 8, true);
            if (hit.type == CombatManager.HitResult.HitType.MONSTER && hit.hitMonster != null) {
                targetMonster = hit.hitMonster;
            } else {
                eventManager.addEvent(new GameEvent("No target in range.", 1.5f));
                return;
            }
        }
        targetPos = targetMonster.getPosition();

        // --- Damage and heal ---
        int drainDamage = 3 + (player.getLevel() * 2);
        int actualDamage = targetMonster.takeDamage(drainDamage);
        player.getStats().heal(actualDamage);

        // --- Spawn blood-stream animation (monster -> player) ---
        if (animationManager != null) {
            // End position slightly in front of the player so it converges into the camera
            Vector2 playerPos = player.getPosition().cpy()
                    .add(player.getDirectionVector().cpy().scl(0.4f));
            animationManager.addAnimation(new Animation(
                    Animation.AnimationType.DRAIN_SPELL,
                    targetPos.cpy(),   // start = monster world position
                    playerPos,         // end   = just in front of player
                    BLOOD_RED,
                    DRAIN_DURATION,
                    new String[]{"*"}));
        }

        // --- Damage text on monster ---
        if (combatManager != null) {
            combatManager.showDamageText(actualDamage,
                    new GridPoint2((int) targetPos.x, (int) targetPos.y));
        }

        // --- Hand-raise overlay ---
        if (game != null && game.getScreen() instanceof GameScreen) {
            // ((GameScreen) game.getScreen()).triggerSpellCast(DRAIN_DURATION);
        }

        // --- Event log ---
        eventManager.addEvent(new GameEvent(
                "Drained " + actualDamage + " HP from " + targetMonster.getMonsterType() + "!", 2.5f));

        // --- Handle death or combat entry ---
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

    private void handleRemoteKill(Monster m, Maze maze, Player player, GameEventManager eventManager) {
        if (maze != null && m != null) {
            maze.getMonsters().remove(
                    new GridPoint2((int) m.getPosition().x, (int) m.getPosition().y));
        }
        if (player != null && m != null) {
            player.getStats().addExperience(m.getBaseExperience());
        }
        if (eventManager != null && m != null) {
            eventManager.addEvent(new GameEvent("Killed " + m.getMonsterType() + "!", 2f));
        }
    }
}
