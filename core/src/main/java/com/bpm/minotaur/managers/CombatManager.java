package com.bpm.minotaur.managers;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.GridPoint2;
import com.bpm.minotaur.Tarmin2;
import com.bpm.minotaur.gamedata.Item;
import com.bpm.minotaur.gamedata.Maze;
import com.bpm.minotaur.gamedata.Monster;
import com.bpm.minotaur.gamedata.Player;
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

    public CombatManager(Player player, Maze maze, Tarmin2 game) {
        this.player = player;
        this.maze = maze;
        this.game = game;
    }

    public void startCombat(Monster monster) {
        if (currentState == CombatState.INACTIVE) {
            this.monster = monster;
            // According to the manual, monsters often attack first
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
            Item weapon = player.getInventory().getRightHand();
            int damage;
            // A simple damage calculation
            if (weapon != null && weapon.getType() != Item.ItemType.KEY) {
                // Damage based on War Strength plus a random element
                damage = player.getWarStrength() / 4 + random.nextInt(5);
            } else {
                // Unarmed attack
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

                if (Math.abs(playerX - monsterX) <= 1 && Math.abs(playerY - monsterY) <= 1) {
                    if (playerX != monsterX || playerY != monsterY) {
                        startCombat(m);
                        return;
                    }
                }
            }
        }
    }

    public void update(float delta) {
        // A simple delay could be added here for the monster's turn
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
