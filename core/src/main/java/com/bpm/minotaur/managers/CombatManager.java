package com.bpm.minotaur.managers;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.GridPoint2;
import com.bpm.minotaur.Tarmin2;
import com.bpm.minotaur.gamedata.*;
import com.bpm.minotaur.rendering.Animation;
import com.bpm.minotaur.rendering.AnimationManager;
import com.bpm.minotaur.screens.GameOverScreen;

import java.util.Random;

public class CombatManager {

    public enum CombatState {
        INACTIVE,
        PLAYER_TURN,
        MONSTER_TURN,
        VICTORY,
        DEFEAT
    }

    private CombatState currentState = CombatState.INACTIVE;
    private final Player player;
    private Monster monster;
    private final Maze maze;
    private final Random random = new Random();
    private final Tarmin2 game;
    private final AnimationManager animationManager;

    public CombatManager(Player player, Maze maze, Tarmin2 game, AnimationManager animationManager) {
        this.player = player;
        this.maze = maze;
        this.game = game;
        this.animationManager = animationManager;
    }

    public void startCombat(Monster monster) {
        if (currentState == CombatState.INACTIVE) {
            this.monster = monster;

            // --- AUTO-TURN JUMP SCARE LOGIC ---
            int playerX = (int) player.getPosition().x;
            int playerY = (int) player.getPosition().y;
            int monsterX = (int) monster.getPosition().x;
            int monsterY = (int) monster.getPosition().y;

            Direction directionToMonster = null;
            if (monsterX > playerX) {
                directionToMonster = Direction.EAST;
            } else if (monsterX < playerX) {
                directionToMonster = Direction.WEST;
            } else if (monsterY > playerY) {
                directionToMonster = Direction.NORTH;
            } else if (monsterY < playerY) {
                directionToMonster = Direction.SOUTH;
            }

            if (directionToMonster != null && player.getFacing() != directionToMonster) {
                player.setFacing(directionToMonster);
                Gdx.app.log("CombatManager", "Player auto-turned to face " + directionToMonster);
            }
            // --- END OF AUTO-TURN LOGIC ---

            currentState = CombatState.MONSTER_TURN;
            Gdx.app.log("CombatManager", "Combat started with " + monster.getType());
        }
    }

    public void endCombat() {
        currentState = CombatState.INACTIVE;
        monster = null;
        Gdx.app.log("CombatManager", "Combat ended.");
    }

    public void playerAttack() {
        if (currentState == CombatState.PLAYER_TURN) {
            Gdx.app.log("CombatManager", "Player attacks " + monster.getType());
            animationManager.addAnimation(new Animation(Animation.AnimationType.PROJECTILE, player.getPosition(), monster.getPosition(), Color.WHITE, 0.5f));
            Item weapon = player.getInventory().getRightHand();
            int damage;
            if (weapon != null && weapon.getWeaponStats() != null) {
                damage = weapon.getWeaponStats().damage + player.getWarStrength() / 10 + random.nextInt(5);
            } else {
                damage = player.getWarStrength() / 10 + random.nextInt(2);
                Gdx.app.log("CombatManager", "Player is unarmed!");
            }

            Gdx.app.log("CombatManager", "Player deals " + damage + " damage.");
            monster.takeDamage(damage);

            if (monster.getWarStrength() <= 0) {
                currentState = CombatState.VICTORY;
            } else {
                currentState = CombatState.MONSTER_TURN;
            }
        }
    }

    public void monsterAttack() {
        if (currentState == CombatState.MONSTER_TURN) {
            Gdx.app.log("CombatManager", "Monster attacks player");
            animationManager.addAnimation(new Animation(Animation.AnimationType.PROJECTILE, monster.getPosition(), player.getPosition(), monster.getColor(), 0.5f));
            int damage = monster.getWarStrength() / 4 + random.nextInt(3);
            Gdx.app.log("CombatManager", "Monster deals " + damage + " damage.");
            player.takeDamage(damage);

            if (player.getWarStrength() <= 0) {
                currentState = CombatState.DEFEAT;
            } else {
                currentState = CombatState.PLAYER_TURN;
            }
        }
    }

    public void checkForAdjacentMonsters() {
        if (currentState == CombatState.INACTIVE) {
            int playerX = (int) player.getPosition().x;
            int playerY = (int) player.getPosition().y;

            for (Monster m : maze.getMonsters().values()) {
                int monsterX = (int) m.getPosition().x;
                int monsterY = (int) m.getPosition().y;

                // Check if monster is on a cardinal tile (not diagonal)
                boolean isCardinal = (Math.abs(playerX - monsterX) == 1 && playerY == monsterY) ||
                    (Math.abs(playerY - monsterY) == 1 && playerX == monsterX);

                if (isCardinal) {
                    startCombat(m);
                    return;
                }
            }
        }
    }

    public void update(float delta) {
        if (currentState == CombatState.MONSTER_TURN) {
            monsterAttack();
        }

        if (currentState == CombatState.VICTORY) {
            Gdx.app.log("CombatManager", "Player is victorious!");
            GridPoint2 monsterPos = new GridPoint2((int)monster.getPosition().x, (int)monster.getPosition().y);
            maze.getMonsters().remove(monsterPos);
            endCombat();
        } else if (currentState == CombatState.DEFEAT) {
            Gdx.app.log("CombatManager", "Player has been defeated!");
            game.setScreen(new GameOverScreen(game));
        }
    }

    public CombatState getCurrentState() { return currentState; }
    public Monster getMonster() { return monster; }
}
