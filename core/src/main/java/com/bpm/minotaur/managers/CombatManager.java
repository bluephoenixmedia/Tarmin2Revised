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
    private final GameEventManager eventManager;
    private final SoundManager soundManager;

    public CombatManager(Player player, Maze maze, Tarmin2 game, AnimationManager animationManager,  GameEventManager eventManager, SoundManager soundManager) {
        this.player = player;
        this.maze = maze;
        this.game = game;
        this.animationManager = animationManager;
        this.eventManager = eventManager;
        this.soundManager = soundManager;
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
            soundManager.playCombatStartSound();
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
        if (currentState != CombatState.PLAYER_TURN) return;

        Item weapon = player.getInventory().getRightHand();
       // Gdx.app.log("CombatManager", "Player equipped weapon = " + weapon.getType().toString());
       // Gdx.app.log("CombatManager", "Player weapon category = " + weapon.getCategory().toString() + ", weapon stats = " + weapon.getWeaponStats().toString());

        if (weapon != null) {
            Gdx.app.log("CombatManager", "Weapon is not null");
            Gdx.app.log("CombatManager", "Weapon category = " + weapon.getCategory().toString());
            if (weapon.getWeaponStats() != null) {
                Gdx.app.log("CombatManager", "weapon stats =  " + weapon.getWeaponStats().damage + " damage");
            } else {
                Gdx.app.log("CombatManager", "weapon stats are null");

            }
            if (weapon.getSpiritualWeaponStats() != null) {
                Gdx.app.log("CombatManager", "spiritual weapon stats =  " + weapon.getSpiritualWeaponStats().damage + " damage");
            } else {
                Gdx.app.log("CombatManager", "spiritual weapon stats are null");

            }
            if (weapon.getCategory() == Item.ItemCategory.WAR_WEAPON && weapon.getWeaponStats() != null) {
                Gdx.app.log("CombatManager", "Weapon is war weapon and stats are not null");

                // Ranged weapon check
                if (weapon.getWeaponStats().isRanged) {
                    Gdx.app.log("CombatManager", "Weapon is ranged");

                    if (player.getArrows() > 0) {
                        Gdx.app.log("CombatManager", "Player has arrows");

                        player.decrementArrow();
                        // soundManager.playPlayerAttackSound();
                        animationManager.addAnimation(new Animation(Animation.AnimationType.PROJECTILE, player.getPosition(), monster.getPosition(), Color.WHITE, 0.5f));
                        monster.takeDamage(weapon.getWeaponStats().damage);
                        Gdx.app.log("CombatManager", "monster takes " + weapon.getWeaponStats().damage + " damage");

                        eventManager.addEvent(new GameEvent("You fire an arrow!", 2f));
                    } else {
                        eventManager.addEvent(new GameEvent("You have no arrows!", 2f));
                    }
                } else { // Melee weapon
                    //  soundManager.playPlayerAttackSound();
                    animationManager.addAnimation(new Animation(Animation.AnimationType.PROJECTILE, player.getPosition(), monster.getPosition(), Color.WHITE, 0.5f));
                    monster.takeDamage(weapon.getWeaponStats().damage);
                    eventManager.addEvent(new GameEvent("You attack with your " + weapon.getType() + "!", 2f));
                }

                if (weapon.vanishesOnUse()) {
                    player.getInventory().setRightHand(null);
                }

            } else if (weapon.getCategory() == Item.ItemCategory.SPIRITUAL_WEAPON && weapon.getSpiritualWeaponStats() != null) {
                Gdx.app.log("CombatManager", "Weapon is spiritual weapon and stats are not null");

                //soundManager.playPlayerSpiritualAttackSound();
                animationManager.addAnimation(new Animation(Animation.AnimationType.PROJECTILE, player.getPosition(), monster.getPosition(), weapon.getColor(), 0.5f));
                monster.takeSpiritualDamage(weapon.getSpiritualWeaponStats().damage);
                Gdx.app.log("CombatManager", "monster takes " + weapon.getSpiritualWeaponStats().damage + " damage");

                eventManager.addEvent(new GameEvent("You cast a spell!", 2f));

                if (weapon.vanishesOnUse()) {
                    player.getInventory().setRightHand(null);
                }
            } else {
                eventManager.addEvent(new GameEvent("You can't attack with this item.", 2f));
                return; // Don't switch turns if the attack was invalid
            }
        } else {
            eventManager.addEvent(new GameEvent("You have no weapon to attack with.", 2f));
            return; // Don't switch turns if there's no weapon
        }


        if (monster.getWarStrength() <= 0 || monster.getSpiritualStrength() <= 0) {
            currentState = CombatState.VICTORY;
        } else {
            currentState = CombatState.MONSTER_TURN;
        }
    }

    public void monsterAttack() {
        if (currentState == CombatState.MONSTER_TURN) {
            Gdx.app.log("CombatManager", "Monster attacks player");
            animationManager.addAnimation(new Animation(Animation.AnimationType.PROJECTILE, monster.getPosition(), player.getPosition(), monster.getColor(), 0.5f));
            soundManager.playMonsterAttackSound(monster);
            int damage;

            // Determine attack type based on monster category
            switch (monster.getType()) {
                // Bad Monsters use Spiritual attacks
                case GIANT_ANT:
                case DWARF:
                case GIANT_SCORPION:
                case GIANT_SNAKE:
                    damage = monster.getSpiritualStrength() / 4 + random.nextInt(3);
                    Gdx.app.log("CombatManager", "Monster deals " + damage + " spiritual damage.");
                    player.takeSpiritualDamage(damage);
                    if (player.getSpiritualStrength() <= 0) {
                        currentState = CombatState.DEFEAT;
                    } else {
                        currentState = CombatState.PLAYER_TURN;
                    }
                    break;

                // Nasty Monsters use War attacks
                case GHOUL:
                case SKELETON:
                case CLOAKED_SKELETON:
                    damage = monster.getWarStrength() / 4 + random.nextInt(3);
                    Gdx.app.log("CombatManager", "Monster deals " + damage + " war damage.");
                    player.takeDamage(damage);
                    if (player.getWarStrength() <= 0) {
                        currentState = CombatState.DEFEAT;
                    } else {
                        currentState = CombatState.PLAYER_TURN;
                    }
                    break;

                // Horrible Monsters can use either War or Spiritual attacks
                case ALLIGATOR:
                case DRAGON:
                case WRAITH:
                case GIANT:
                case MINOTAUR:
                    if (random.nextBoolean()) { // 50/50 chance for either attack
                        damage = monster.getWarStrength() / 4 + random.nextInt(3);
                        Gdx.app.log("CombatManager", "Monster deals " + damage + " war damage.");
                        player.takeDamage(damage);
                        if (player.getWarStrength() <= 0) {
                            currentState = CombatState.DEFEAT;
                        } else {
                            currentState = CombatState.PLAYER_TURN;
                        }
                    } else {
                        damage = monster.getSpiritualStrength() / 4 + random.nextInt(3);
                        Gdx.app.log("CombatManager", "Monster deals " + damage + " spiritual damage.");
                        player.takeSpiritualDamage(damage);
                        if (player.getSpiritualStrength() <= 0) {
                            currentState = CombatState.DEFEAT;
                        } else {
                            currentState = CombatState.PLAYER_TURN;
                        }
                    }
                    break;

                // Default to war attacks for any other monster type
                default:
                    damage = monster.getWarStrength() / 4 + random.nextInt(3);
                    Gdx.app.log("CombatManager", "Monster deals " + damage + " war damage.");
                    player.takeDamage(damage);
                    if (player.getWarStrength() <= 0) {
                        currentState = CombatState.DEFEAT;
                    } else {
                        currentState = CombatState.PLAYER_TURN;
                    }
                    break;
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
