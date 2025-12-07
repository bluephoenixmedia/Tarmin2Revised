package com.bpm.minotaur.managers;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.GridPoint2;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.bpm.minotaur.Tarmin2;
import com.bpm.minotaur.gamedata.*;
import com.bpm.minotaur.gamedata.effects.ActiveStatusEffect;
import com.bpm.minotaur.gamedata.effects.StatusEffectType;
import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.gamedata.item.ItemDataManager;
import com.bpm.minotaur.gamedata.monster.Monster;
import com.bpm.minotaur.gamedata.player.Player;
import com.bpm.minotaur.rendering.Animation;
import com.bpm.minotaur.rendering.AnimationManager;
import com.bpm.minotaur.screens.GameOverScreen;
import com.bpm.minotaur.gamedata.item.ItemCategory;

import java.util.Random;

public class CombatManager {

    public enum CombatState {
        INACTIVE,
        PLAYER_TURN,
        PHYSICS_RESOLUTION, // Rolling
        PHYSICS_DELAY,      // Viewing Result
        MONSTER_TURN,
        VICTORY,
        DEFEAT
    }

    public static class HitResult {
        public enum HitType {
            NOTHING,        // Reached max range without hitting anything
            WALL,           // Hit a wall, closed door, closed gate, or SCENERY
            MONSTER,        // Hit a monster
            PLAYER,         // Hit the player
            OUT_OF_BOUNDS   // Went off the map
        }

        public final GridPoint2 collisionPoint;
        public final HitType type;
        public final Monster hitMonster; // Null if not a monster hit

        public HitResult(GridPoint2 collisionPoint, HitType type, Monster hitMonster) {
            this.collisionPoint = collisionPoint;
            this.type = type;
            this.hitMonster = hitMonster;
        }
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
    private final WorldManager worldManager;

    private final StochasticManager stochasticManager;

    private float monsterAttackDelay = 0f;
    private static final float MONSTER_ATTACK_DELAY_TIME = 0.3f;

    private static final float PROJECTILE_SPEED = 15.0f;
    private final ItemDataManager itemDataManager;

    private int lastDamageDealt = 0;

    private Item pendingWeapon;
    private boolean pendingIsRanged;

    // --- TIMING VARIABLES ---
    private float physicsTimer = 0f;
    private static final float MIN_ROLL_TIME = 0.1f;    // Almost instant allow-settle
    private static final float RESULT_VIEW_TIME = 0.3f; // Quick glance at result (600ms)

    public CombatManager(Player player, Maze maze, Tarmin2 game, AnimationManager animationManager,
                         GameEventManager eventManager, SoundManager soundManager, WorldManager worldManager,
                         ItemDataManager itemDataManager, StochasticManager stochasticManager) {
        this.player = player;
        this.maze = maze;
        this.game = game;
        this.animationManager = animationManager;
        this.eventManager = eventManager;
        this.soundManager = soundManager;
        this.worldManager = worldManager;
        this.itemDataManager = itemDataManager;
        this.stochasticManager = stochasticManager;
    }

    public HitResult raycastProjectile(Vector2 origin, Direction direction, int maxRange, boolean sourceIsPlayer) {
        int startX = (int) origin.x;
        int startY = (int) origin.y;

        int currentX = startX;
        int currentY = startY;

        int dx = (int) direction.getVector().x;
        int dy = (int) direction.getVector().y;

        for (int i = 0; i < maxRange; i++) {
            if (maze.isWallBlocking(currentX, currentY, direction)) {
                return new HitResult(new GridPoint2(currentX, currentY), HitResult.HitType.WALL, null);
            }
            currentX += dx;
            currentY += dy;
            GridPoint2 currentPos = new GridPoint2(currentX, currentY);
            if (currentX < 0 || currentX >= maze.getWidth() || currentY < 0 || currentY >= maze.getHeight()) {
                return new HitResult(new GridPoint2(currentX - dx, currentY - dy), HitResult.HitType.OUT_OF_BOUNDS, null);
            }
            Object obj = maze.getGameObjectAt(currentX, currentY);
            if (obj instanceof Door) {
                if (((Door) obj).getState() != Door.DoorState.OPEN) {
                    return new HitResult(currentPos, HitResult.HitType.WALL, null);
                }
            } else if (obj instanceof Gate) {
                if (((Gate) obj).getState() != Gate.GateState.OPEN) {
                    return new HitResult(currentPos, HitResult.HitType.WALL, null);
                }
            }
            if (maze.getScenery() != null && maze.getScenery().containsKey(currentPos)) {
                Scenery s = maze.getScenery().get(currentPos);
                if (s.isImpassable()) {
                    return new HitResult(currentPos, HitResult.HitType.WALL, null);
                }
            }
            if (!sourceIsPlayer) {
                int pX = (int) player.getPosition().x;
                int pY = (int) player.getPosition().y;
                if (currentX == pX && currentY == pY) {
                    return new HitResult(currentPos, HitResult.HitType.PLAYER, null);
                }
            }
            if (maze.getMonsters().containsKey(currentPos)) {
                Monster m = maze.getMonsters().get(currentPos);
                return new HitResult(currentPos, HitResult.HitType.MONSTER, m);
            }
        }
        return new HitResult(new GridPoint2(currentX, currentY), HitResult.HitType.NOTHING, null);
    }

    public void startCombat(Monster monster) {
        if (currentState == CombatState.INACTIVE) {
            this.monster = monster;

            if (this.monster != null && this.monster.getStatusManager() != null && this.eventManager != null) {
                this.monster.getStatusManager().initialize(eventManager);
            }

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
            soundManager.playCombatStartSound();
            currentState = CombatState.MONSTER_TURN;
            monsterAttackDelay = MONSTER_ATTACK_DELAY_TIME;

            Gdx.app.log("CombatManager", "Combat started with " + monster.getType());
        }
    }

    public void endCombat() {
        currentState = CombatState.INACTIVE;
        monster = null;
        monsterAttackDelay = 0f;
        Gdx.app.log("CombatManager", "Combat ended.");
    }

    private void processPlayerStatusEffects() {
        if (player == null) return;
        if (player.getStatusManager().hasEffect(StatusEffectType.POISONED)) {
            ActiveStatusEffect poison = player.getStatusManager().getEffect(StatusEffectType.POISONED);
            int damage = poison.getPotency();
            player.takeStatusEffectDamage(damage, DamageType.POISON);
            eventManager.addEvent(new GameEvent("You take " + damage + " poison damage!", 2f));
            Gdx.app.log("CombatManager", "Player took " + damage + " poison damage.");
        }
    }

    // --- NEW: Helper to setup weapon and checks ---
    private boolean prepareAttack() {
        Item weapon = player.getInventory().getRightHand();

        if (weapon != null) {
            this.pendingWeapon = weapon;
            this.pendingIsRanged = weapon.isRanged();

            if (pendingIsRanged && player.getArrows() <= 0) {
                eventManager.addEvent(new GameEvent("You have no arrows!", 2f));
                passTurnToMonster();
                return false;
            }
            return true;
        } else {
            eventManager.addEvent(new GameEvent("You have no weapon to attack with.", 2f));
            passTurnToMonster();
            return false;
        }
    }

    // --- NEW: Helper to calculate die size/max damage ---
    private int calculateDieSize() {
        int attackModifier = player.getAttackModifier();
        int potentialDamage;

        if (pendingWeapon.getCategory() == ItemCategory.SPIRITUAL_WEAPON) {
            potentialDamage = pendingWeapon.getSpiritDamage() + attackModifier;
        } else {
            potentialDamage = pendingWeapon.getWarDamage() + attackModifier;
        }

        if (potentialDamage < 4) potentialDamage = 4;
        return potentialDamage;
    }

    // --- NEW: Standard Attack (KEY A/SPACE) - No Animation ---
    public void playerAttackInstant() {
        if (currentState != CombatState.PLAYER_TURN) return;
        if (!prepareAttack()) return;

        // Simulate the roll immediately (1 to Max)
        int dieSize = calculateDieSize();
        int simulatedRoll = random.nextInt(dieSize) + 1;

        // Log it so we know it worked
        Gdx.app.log("CombatManager", "Instant Attack: Rolled " + simulatedRoll + " on D" + dieSize);

        resolveAttack(simulatedRoll);
    }

    // --- RENAMED: Physics Attack (KEY 7) - With Animation ---
    public void playerAttackWithDice() {
        if (currentState != CombatState.PLAYER_TURN) return;
        if (!prepareAttack()) return;

        // TRIGGER PHYSICS STATE
        currentState = CombatState.PHYSICS_RESOLUTION;
        physicsTimer = 0f;

        // Calculate die size
        int dieSize = calculateDieSize();
        float force = 10f + (player.getWarStrength() * 2f);

        // Spawn the correct die type
        stochasticManager.spawnDie(dieSize, force);

        eventManager.addEvent(new GameEvent("Rolling for fate...", 1f));
    }

    // Kept for backward compatibility if called elsewhere, maps to Instant
    public void playerAttack() {
        playerAttackInstant();
    }

    private void resolveAttack(int rollBonus) {
        if (pendingWeapon == null) return;

        int attackModifier = player.getAttackModifier() + rollBonus;
        boolean attackSuccessful = false;

        if (pendingWeapon.getCategory() == ItemCategory.WAR_WEAPON && pendingWeapon.isWeapon()) {
            if (pendingWeapon.isRanged()) {
                player.decrementArrow();
                String[] projectileSprite = itemDataManager.getTemplate(Item.ItemType.DART).spriteData;
                animationManager.addAnimation(new Animation(Animation.AnimationType.PROJECTILE_PLAYER, player.getPosition(), monster.getPosition(), pendingWeapon.getColor(), 0.5f, projectileSprite));

                int damage = pendingWeapon.getWarDamage() + attackModifier;
                monster.takeDamage(damage);
                lastDamageDealt = damage;
                attackSuccessful = true;

                showDamageText(damage, new GridPoint2((int)monster.getPosition().x, (int)monster.getPosition().y));
                maze.addBlood((int)monster.getPosition().x, (int)monster.getPosition().y, 0.03f);
                eventManager.addEvent(new GameEvent("Ranged Hit! " + damage + " dmg", 2f));

            } else {
                String[] meleeSprite = pendingWeapon.getSpriteData();
                if (meleeSprite == null) meleeSprite = itemDataManager.getTemplate(Item.ItemType.DART).spriteData;

                animationManager.addAnimation(new Animation(Animation.AnimationType.PROJECTILE_PLAYER, player.getPosition(), monster.getPosition(), pendingWeapon.getColor(), 0.5f, meleeSprite));

                int damage = pendingWeapon.getWarDamage() + attackModifier;
                monster.takeDamage(damage);
                lastDamageDealt = damage;
                attackSuccessful = true;

                Vector3 hitPos = new Vector3(monster.getPosition().x, 0.5f, monster.getPosition().y);
                Vector3 dir = new Vector3(player.getDirectionVector().x, 0.2f, player.getDirectionVector().y).nor();
                maze.getGoreManager().spawnBloodSpray(hitPos, dir, 4);

                showDamageText(damage, new GridPoint2((int)monster.getPosition().x, (int)monster.getPosition().y));
                eventManager.addEvent(new GameEvent("Direct Hit! " + damage + " dmg", 2f));
            }

            if (pendingWeapon.isUsable()) {
                player.getInventory().setRightHand(null);
            }

        } else if (pendingWeapon.getCategory() == ItemCategory.SPIRITUAL_WEAPON && pendingWeapon.isWeapon()) {
            animationManager.addAnimation(new Animation(Animation.AnimationType.PROJECTILE_PLAYER, player.getPosition(), monster.getPosition(), pendingWeapon.getColor(), 0.5f, itemDataManager.getTemplate(Item.ItemType.LARGE_LIGHTNING).spriteData));

            int damage = pendingWeapon.getSpiritDamage() + attackModifier;
            monster.takeSpiritualDamage(damage);
            attackSuccessful = true;

            showDamageText(damage, new GridPoint2((int)monster.getPosition().x, (int)monster.getPosition().y));
            maze.addBlood((int)monster.getPosition().x, (int)monster.getPosition().y, 0.03f);
            eventManager.addEvent(new GameEvent("Spell Cast! " + damage + " dmg", 2f));

            if (pendingWeapon.isUsable()) {
                player.getInventory().setRightHand(null);
            }
        } else {
            eventManager.addEvent(new GameEvent("You can't attack with this item.", 2f));
            return;
        }

        processPlayerStatusEffects();
        player.getStatusManager().updateTurn();
        stochasticManager.clearDice();

        if (monster.getWarStrength() <= 0 || monster.getSpiritualStrength() <= 0) {
            currentState = CombatState.VICTORY;
            Gdx.app.log("CombatManager","You have defeated" + monster.getMonsterType());
            eventManager.addEvent((new GameEvent("You have defeated " + monster.getMonsterType(), 2f)));
            UnlockManager.getInstance().recordKill(monster.getMonsterType());
            int baseExp = monster.getBaseExperience();
            float colorMultiplier = monster.getMonsterColor().getXpMultiplier();
            float levelMultiplier = 1.0f + (maze.getLevel() * 0.1f);
            int totalExp = (int) (baseExp * colorMultiplier * levelMultiplier);
            eventManager.addEvent((new GameEvent("You have gained " + totalExp + " experience", 2f)));
            player.addExperience(totalExp, eventManager);
            maze.addBlood((int)monster.getPosition().x, (int)monster.getPosition().y, 0.10f);
            spawnCorpseEffects(monster);

        } else {
            currentState = CombatState.MONSTER_TURN;
        }
    }

    public boolean performMonsterRangedAttack(Monster attacker) {
        int range = attacker.getAttackRange();
        if (range <= 0) range = 8;
        Vector2 diff = player.getPosition().cpy().sub(attacker.getPosition());
        Direction fireDir = null;
        if (Math.abs(diff.x) < 0.5f) fireDir = (diff.y > 0) ? Direction.NORTH : Direction.SOUTH;
        else if (Math.abs(diff.y) < 0.5f) fireDir = (diff.x > 0) ? Direction.EAST : Direction.WEST;
        if (fireDir == null) return false;
        HitResult finalResult = raycastProjectile(attacker.getPosition(), fireDir, range, false);
        if (finalResult.type != HitResult.HitType.PLAYER) return false;
        float dist = attacker.getPosition().dst(player.getPosition());
        float animDuration = dist / PROJECTILE_SPEED;
        animationManager.addAnimation(new Animation(Animation.AnimationType.PROJECTILE_MONSTER, attacker.getPosition(), player.getPosition(), attacker.getColor(), animDuration, itemDataManager.getTemplate(Item.ItemType.DART).spriteData));
        soundManager.playMonsterAttackSound(attacker);
        int baseChance = 60;
        int monsterDex = attacker.getDexterity();
        int playerDex = player.getDexterity();
        int hitChance = baseChance + (monsterDex * 2) - (int)(playerDex * 1.5);
        if (hitChance < 10) hitChance = 10;
        if (hitChance > 95) hitChance = 95;

        if (random.nextInt(100) < hitChance) {
            int damage = attacker.getWarStrength() / 3;
            if (damage < 1) damage = 1;
            int actualDamage = player.takeDamage(damage, DamageType.PHYSICAL);
            if (actualDamage > 0) maze.addBlood((int)player.getPosition().x, (int)player.getPosition().y, 0.03f);

            if (actualDamage > 0) eventManager.addEvent(new GameEvent(attacker.getMonsterType() + " shoots you for " + actualDamage + " damage!", 1.5f));
            else eventManager.addEvent(new GameEvent("Your armor deflects the " + attacker.getMonsterType() + "'s shot!", 1.5f));
        } else {
            eventManager.addEvent(new GameEvent(attacker.getMonsterType() + " fires and misses!", 1.0f));
        }
        return true;
    }

    public void checkForAdjacentMonsters() {
        if (currentState == CombatState.INACTIVE) {
            int playerX = (int) player.getPosition().x;
            int playerY = (int) player.getPosition().y;
            for (Monster m : maze.getMonsters().values()) {
                int monsterX = (int) m.getPosition().x;
                int monsterY = (int) m.getPosition().y;
                boolean isCardinal = (Math.abs(playerX - monsterX) == 1 && playerY == monsterY) || (Math.abs(playerY - monsterY) == 1 && playerX == monsterX);
                if (isCardinal) {
                    startCombat(m);
                    return;
                }
            }
        }
    }

    private void showDamageText(int damage, GridPoint2 position) {
        animationManager.addAnimation(new Animation(Animation.AnimationType.DAMAGE_TEXT, position, String.valueOf(damage), 1.0f));
    }

    public void update(float delta) {
        // 1. ROLLING STATE
        if (currentState == CombatState.PHYSICS_RESOLUTION) {
            physicsTimer += delta;
            if (physicsTimer > MIN_ROLL_TIME && stochasticManager.areDiceSettled()) {
                currentState = CombatState.PHYSICS_DELAY;
                physicsTimer = 0f;
            }
            return;
        }

        // 2. VIEWING RESULT STATE
        if (currentState == CombatState.PHYSICS_DELAY) {
            physicsTimer += delta;
            if (physicsTimer > RESULT_VIEW_TIME) {
                // --- GET REAL ROLL ---
                int realRoll = stochasticManager.getRolledResult();
                resolveAttack(realRoll);
            }
            return;
        }

        if (currentState == CombatState.MONSTER_TURN) {
            if (monsterAttackDelay > 0f) monsterAttackDelay -= delta;
            else monsterAttack();
        }

        if (currentState == CombatState.VICTORY) {
            maze.getMonsters().remove(new GridPoint2((int)monster.getPosition().x, (int)monster.getPosition().y));
            endCombat();
        } else if (currentState == CombatState.DEFEAT) {
            game.setScreen(new GameOverScreen(game));
        }
    }

    public void passTurnToMonster() {
        if (currentState == CombatState.PLAYER_TURN) {
            processPlayerStatusEffects();
            player.getStatusManager().updateTurn();
            Gdx.app.log("CombatManager", "Player passed turn. Monster's turn.");
            currentState = CombatState.MONSTER_TURN;
            monsterAttackDelay = MONSTER_ATTACK_DELAY_TIME;
        }
    }

    private void spawnCorpseEffects(Monster monster) {
        String[] originalSprite = monster.getSpriteData();
        if (originalSprite == null || originalSprite.length == 0) return;

        Vector2 mPos2 = monster.getPosition();
        Vector3 centerPos = new Vector3(mPos2.x, 0.5f, mPos2.y);

        for (int q = 0; q < 4; q++) {
            String[] quadData = new String[12];

            int startX = (q % 2 == 0) ? 0 : 12;
            int startY = (q < 2) ? 0 : 12;

            for (int r = 0; r < 12; r++) {
                int sourceRowIndex = startY + r;
                if (sourceRowIndex < originalSprite.length) {
                    String row = originalSprite[sourceRowIndex];
                    if (row.length() >= startX + 12) {
                        quadData[r] = row.substring(startX, startX + 12);
                    } else {
                        quadData[r] = "            ";
                    }
                } else {
                    quadData[r] = "            ";
                }
            }

            float velX = (q % 2 == 0 ? -1f : 1f) * (0.2f + (float)Math.random() * 0.3f);
            float velZ = (q < 2 ? 1f : -1f) * (0.2f + (float)Math.random() * 0.3f);
            float velY = 0.8f + (float)Math.random() * 0.5f;

            Vector3 velocity = new Vector3(velX, velY, velZ);
            Vector3 partPos = new Vector3(centerPos).add(velX * 0.1f, 0, velZ * 0.1f);

            maze.getCorpses().add(new CorpsePart(partPos, velocity, quadData, monster.getColor()));
        }
    }

    public boolean performRangedAttack() {
        Item weapon = player.getInventory().getRightHand();
        if (weapon == null || !weapon.isRanged()) return false;
        playerAttackInstant(); // Default to instant for standard inputs if used
        return true;
    }

    public CombatState getCurrentState() { return currentState; }
    public Monster getMonster() { return monster; }
    public void monsterAttack() {
        if (monster == null) return;

        float dist = monster.getPosition().dst(player.getPosition());
        float animDuration = dist / PROJECTILE_SPEED;

        animationManager.addAnimation(new Animation(
            Animation.AnimationType.PROJECTILE_MONSTER,
            monster.getPosition(),
            player.getPosition(),
            monster.getColor(),
            animDuration,
            itemDataManager.getTemplate(Item.ItemType.DART).spriteData
        ));

        soundManager.playMonsterAttackSound(monster);
        int damage = monster.getWarStrength() / 4 + random.nextInt(3);
        player.takeDamage(damage, DamageType.PHYSICAL);
        maze.addBlood((int)player.getPosition().x, (int)player.getPosition().y, 0.03f);

        eventManager.addEvent(new GameEvent(monster.getMonsterType() + " hits you for " + damage, 1f));

        if (player.getWarStrength() <= 0) {
            currentState = CombatState.DEFEAT;
        } else {
            currentState = CombatState.PLAYER_TURN;
        }
    }
}
