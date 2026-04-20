package com.bpm.minotaur.gamedata.spells.effects;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.GridPoint2;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.bpm.minotaur.Tarmin2;
import com.bpm.minotaur.gamedata.Direction;
import com.bpm.minotaur.gamedata.GameEvent;
import com.bpm.minotaur.gamedata.Maze;
import com.bpm.minotaur.gamedata.monster.Monster;
import com.bpm.minotaur.gamedata.player.Player;
import com.bpm.minotaur.managers.CombatManager;
import com.bpm.minotaur.managers.GameEventManager;
import com.bpm.minotaur.rendering.Animation;
import com.bpm.minotaur.rendering.AnimationManager;
import com.bpm.minotaur.managers.DebugManager;
import com.bpm.minotaur.screens.GameScreen;

/**
 * Force Push: launches a concussive blast that knocks a monster backward tile by tile.
 *
 * Push distance = 1 + (player.level / 2):
 *   Level 1 → 1 tile
 *   Level 3 → 2 tiles
 *   Level 5 → 3 tiles   ← gore threshold
 *   Level 7 → 4 tiles
 *
 * If the monster hits a wall and push strength is >= GORE_WALL_THRESHOLD,
 * it is instantly crushed into a gib explosion. Below that threshold the
 * monster takes bonus impact damage instead.
 */
public class ForcePushEffect implements SpellEffect {

    private static final Color FORCE_COLOR     = new Color(0.55f, 0.85f, 1.0f, 1f); // ice-blue
    private static final float BLAST_DURATION  = 0.30f;
    private static final int   GORE_WALL_THRESHOLD = 3; // push strength needed to gib on wall impact

    @Override
    public void execute(Player player, CombatManager combatManager, GameEventManager eventManager,
                        AnimationManager animationManager, Maze maze, Tarmin2 game) {

        // --- Acquire target ---
        Monster target = combatManager.getMonster();
        if (target == null) {
            CombatManager.HitResult hit = combatManager.raycastProjectile(
                    player.getPosition(), player.getFacing(), 8, true);
            if (hit.type == CombatManager.HitResult.HitType.MONSTER && hit.hitMonster != null) {
                target = hit.hitMonster;
            } else {
                eventManager.addEvent(new GameEvent("Nothing to push.", 1.5f));
                return;
            }
        }

        // Capture push direction and strength before any state changes
        final Direction pushDir     = player.getFacing();
        final Vector2  pushVec      = pushDir.getVector();
        final int      pushStrength = 1 + (player.getLevel() / 2);

        // --- Blast-wave animation (player → monster) ---
        if (animationManager != null) {
            Vector2 startPos = player.getPosition().cpy()
                    .add(player.getDirectionVector().cpy().scl(0.6f));
            animationManager.addAnimation(new Animation(
                    Animation.AnimationType.PROJECTILE_SPELL,
                    startPos, target.getPosition().cpy(),
                    FORCE_COLOR, BLAST_DURATION,
                    new String[]{"*"}));
        }

        // --- Screen shake on cast (the push feels weighty) ---
        GameScreen gs = null;
        if (game != null && game.getScreen() instanceof GameScreen) {
            gs = (GameScreen) game.getScreen();
            gs.addTrauma(0.25f);
            // gs.triggerSpellCast(BLAST_DURATION, FORCE_COLOR);
        }

        // --- Push the monster along the player's facing direction ---
        int tileX = (int) target.getPosition().x;
        int tileY = (int) target.getPosition().y;

        // Detach from the map while we reposition
        maze.getMonsters().remove(new GridPoint2(tileX, tileY));

        boolean hitWall   = false;
        int     tilesPushed = 0;

        for (int i = 0; i < pushStrength; i++) {
            int nextX = tileX + (int) pushVec.x;
            int nextY = tileY + (int) pushVec.y;

            if (maze.isWallBlocking(tileX, tileY, pushDir) || !maze.isPassable(nextX, nextY)) {
                hitWall = true;
                break;
            }
            tileX = nextX;
            tileY = nextY;
            tilesPushed++;
        }

        // Reposition monster to centre of the tile it landed on
        target.getPosition().set(tileX + 0.5f, tileY + 0.5f);
        Vector3 impactOrigin = new Vector3(tileX + 0.5f, 0.5f, tileY + 0.5f);

        // --- Gore explosion: wall slam at high push strength ---
        if (hitWall && pushStrength >= GORE_WALL_THRESHOLD) {
            goreSlam(target, impactOrigin, pushDir, maze, player, combatManager, eventManager, gs);
            // Monster is destroyed; do NOT re-insert into the map.
            return;
        }

        // --- Monster survived the push; re-insert at new position ---
        maze.getMonsters().put(new GridPoint2(tileX, tileY), target);

        // Damage: base concussive + wall-impact bonus
        int damage       = 2 + player.getLevel() + (hitWall ? tilesPushed * 3 + 2 : 0);
        int actualDamage = target.takeDamage(damage);

        if (combatManager != null) {
            combatManager.showDamageText(actualDamage, new GridPoint2(tileX, tileY));
        }

        // Blood spray on non-fatal wall impact
        if (hitWall) {
            Vector3 wallDir3 = new Vector3(pushVec.x, 0, pushVec.y);
            maze.getGoreManager().spawnBloodSpray(impactOrigin, wallDir3, 1);
            maze.addBlood(tileX, tileY, 0.15f);
            if (gs != null) {
                gs.addTrauma(0.35f);
                gs.triggerHitPause(0.06f);
            }
        }

        // Event log
        if (hitWall) {
            eventManager.addEvent(new GameEvent(
                    target.getMonsterType() + " slammed into the wall! (-" + actualDamage + " HP)", 2.5f));
        } else if (tilesPushed > 0) {
            eventManager.addEvent(new GameEvent(
                    "Pushed " + target.getMonsterType() + " back "
                    + tilesPushed + (tilesPushed == 1 ? " tile!" : " tiles!"), 2f));
        } else {
            eventManager.addEvent(new GameEvent(
                    target.getMonsterType() + " resisted the push!", 1.5f));
        }

        // Handle death or new combat entry
        if (target.getCurrentHP() <= 0) {
            if (combatManager != null && target == combatManager.getMonster()) {
                combatManager.handleMonsterDeath();
                combatManager.setCurrentState(CombatManager.CombatState.VICTORY);
            } else {
                handleRemoteKill(target, maze, player, eventManager);
            }
        } else if (combatManager != null && combatManager.getMonster() == null) {
            combatManager.startCombat(target);
        }
    }

    /**
     * Called when a monster is slammed into a wall with enough force to be destroyed.
     * Triggers a full gib explosion and awards XP without re-inserting the monster.
     */
    private void goreSlam(Monster monster, Vector3 origin, Direction pushDir,
                          Maze maze, Player player, CombatManager combatManager,
                          GameEventManager eventManager, GameScreen gs) {

        // Heavy blood spray into the wall
        Vector3 wallDir = new Vector3(pushDir.getVector().x, 0f, pushDir.getVector().y);
        maze.getGoreManager().spawnBloodSpray(origin, wallDir, 5);
        maze.addBlood((int) monster.getPosition().x, (int) monster.getPosition().y, 0.6f);

        // Gibs (retro or modern)
        if (DebugManager.getInstance().getRenderMode() == DebugManager.RenderMode.RETRO) {
            String[] sprite = monster.getSpriteData();
            if (sprite != null) {
                maze.getGoreManager().spawnRetroGibs(origin, sprite, monster.getColor());
            } else {
                maze.getGoreManager().spawnGibExplosion(origin);
            }
        } else {
            if (monster.getTexture() != null) {
                maze.getGoreManager().spawnTextureGibs(origin, monster.getTexture());
            }
            maze.getGoreManager().spawnGibExplosion(origin);
        }

        // Heavy screen feedback
        if (gs != null) {
            gs.addTrauma(0.75f);
            gs.triggerHitPause(0.14f);
        }

        // XP and kill events (mirrors handleMonsterDeath flow for remote kills)
        player.addExperience(monster.getBaseExperience(), eventManager);
        eventManager.addEvent(new GameEvent(
                monster.getMonsterType() + " was CRUSHED against the wall!", 3f));

        // Mark combat as won if this was the active combat target
        if (combatManager != null && monster == combatManager.getMonster()) {
            combatManager.setCurrentState(CombatManager.CombatState.VICTORY);
        }
    }

    private void handleRemoteKill(Monster m, Maze maze, Player player, GameEventManager eventManager) {
        if (maze != null && m != null) {
            maze.getMonsters().remove(
                    new GridPoint2((int) m.getPosition().x, (int) m.getPosition().y));
        }
        if (player != null && m != null) {
            player.addExperience(m.getBaseExperience(), eventManager);
        }
        if (eventManager != null && m != null) {
            eventManager.addEvent(new GameEvent("Killed " + m.getMonsterType() + "!", 2f));
        }
    }
}
