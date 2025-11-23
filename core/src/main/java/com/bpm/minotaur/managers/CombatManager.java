package com.bpm.minotaur.managers;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.GridPoint2;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.bpm.minotaur.Tarmin2;
import com.bpm.minotaur.gamedata.*;
import com.bpm.minotaur.gamedata.effects.ActiveStatusEffect;
import com.bpm.minotaur.gamedata.effects.EffectApplicationData;
import com.bpm.minotaur.gamedata.effects.StatusEffectType;
import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.gamedata.item.ItemDataManager;
import com.bpm.minotaur.gamedata.item.ItemModifier;
import com.bpm.minotaur.gamedata.monster.Monster;
import com.bpm.minotaur.gamedata.monster.MonsterFamily;
import com.bpm.minotaur.gamedata.monster.MonsterTemplate;
import com.bpm.minotaur.gamedata.player.Player;
import com.bpm.minotaur.rendering.Animation;
import com.bpm.minotaur.rendering.AnimationManager;
import com.bpm.minotaur.screens.GameOverScreen;
import com.bpm.minotaur.gamedata.item.ItemCategory;
import com.bpm.minotaur.weather.WeatherManager;
import com.bpm.minotaur.weather.WeatherType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class CombatManager {

    public enum CombatState {
        INACTIVE,
        PLAYER_TURN,
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

    private float monsterAttackDelay = 0f;
    private static final float MONSTER_ATTACK_DELAY_TIME = 0.3f; // Delay in seconds

    private static final float PROJECTILE_SPEED = 15.0f;
    private final ItemDataManager itemDataManager;


    public CombatManager(Player player, Maze maze, Tarmin2 game, AnimationManager animationManager,
                         GameEventManager eventManager, SoundManager soundManager, WorldManager worldManager, ItemDataManager itemDataManager) {
        this.player = player;
        this.maze = maze;
        this.game = game;
        this.animationManager = animationManager;
        this.eventManager = eventManager;
        this.soundManager = soundManager;
        this.worldManager = worldManager;
        this.itemDataManager = itemDataManager;
    }

    // [UNCHANGED]
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

    public boolean performRangedAttack() {
        Item weapon = player.getInventory().getRightHand();

        if (weapon == null || !weapon.isRanged()) {
            eventManager.addEvent(new GameEvent("You need a ranged weapon equipped!", 1.0f));
            return false;
        }

        if (player.getArrows() <= 0) {
            eventManager.addEvent(new GameEvent("You have no arrows!", 1.0f));
            return false;
        }

        player.decrementArrow();

        int range = weapon.getRange();
        if (range <= 0) range = 8;

        HitResult result = raycastProjectile(player.getPosition(), player.getFacing(), range, true);

        Vector2 animTarget = new Vector2(result.collisionPoint.x + 0.5f, result.collisionPoint.y + 0.5f);
        float distance = player.getPosition().dst(animTarget);
        float animDuration = distance / PROJECTILE_SPEED;

        String[] projectileSprite = itemDataManager.getTemplate(Item.ItemType.DART).spriteData;
        if (weapon.getCategory() == ItemCategory.SPIRITUAL_WEAPON) {
            projectileSprite = itemDataManager.getTemplate(Item.ItemType.LARGE_LIGHTNING).spriteData;
        }

        animationManager.addAnimation(new Animation(
            Animation.AnimationType.PROJECTILE_PLAYER,
            player.getPosition(),
            animTarget,
            weapon.getColor(),
            animDuration,
            projectileSprite
        ));

        if (result.type == HitResult.HitType.MONSTER && result.hitMonster != null) {
            Monster target = result.hitMonster;

            int baseChance = 60;
            int playerDex = player.getDexterity();
            int weaponTier = weapon.getTemplate().accuracyModifier;
            int targetDex = target.getDexterity();

            float dist = Math.abs(player.getPosition().x - target.getPosition().x) + Math.abs(player.getPosition().y - target.getPosition().y);
            int hitChance = baseChance + (playerDex * 2) + (weaponTier * 5) - (int)(targetDex * 1.5) - (int)(dist * 5);

            if (worldManager != null && worldManager.getWeatherManager() != null) {
                WeatherManager wm = worldManager.getWeatherManager();
                WeatherType wType = wm.getCurrentWeather();
                if (wType == WeatherType.RAIN || wType == WeatherType.SNOW) hitChance -= 10;
                else if (wType == WeatherType.STORM || wType == WeatherType.BLIZZARD) hitChance -= 25;
                else if (wType == WeatherType.TORNADO) hitChance -= 50;
            }

            if (hitChance < 5) hitChance = 5;
            if (hitChance > 95) hitChance = 95;

            int roll = random.nextInt(100);

            if (roll < hitChance) {
                int damage = weapon.getWarDamage() + player.getAttackModifier();

                if (weapon.getCategory() == ItemCategory.SPIRITUAL_WEAPON) {
                    damage = weapon.getSpiritDamage() + player.getAttackModifier();
                    target.takeSpiritualDamage(damage);
                } else {
                    target.takeDamage(damage);
                }

                maze.addBlood((int)target.getPosition().x, (int)target.getPosition().y, 0.03f);

                showDamageText(damage, result.collisionPoint);
                eventManager.addEvent(new GameEvent("Ranged Hit! (" + damage + " dmg)", 1.0f));
                Gdx.app.log("Combat", "Ranged Hit on " + target.getMonsterType() + ". Roll: " + roll + " < Chance: " + hitChance);

                if (target.getWarStrength() <= 0 || target.getSpiritualStrength() <= 0) {
                    eventManager.addEvent(new GameEvent(target.getMonsterType() + " defeated!", 2.0f));
                    maze.getMonsters().remove(result.collisionPoint);
                    player.addExperience(target.getBaseExperience(), eventManager);

                    if (this.monster == target) {
                        endCombat();
                    }
                }

            } else {
                showDamageText(0, result.collisionPoint);
                eventManager.addEvent(new GameEvent("You missed!", 1.0f));
                Gdx.app.log("Combat", "Ranged Miss on " + target.getMonsterType() + ". Roll: " + roll + " >= Chance: " + hitChance);
            }
        } else {
            eventManager.addEvent(new GameEvent("The shot hit nothing.", 0.5f));
        }

        return true;
    }

    public void playerAttack() {
        if (currentState != CombatState.PLAYER_TURN) return;

        Item weapon = player.getInventory().getRightHand();
        int attackModifier = player.getAttackModifier();

        if (weapon != null) {
            Map<DamageType, Integer> elementalDamages = new HashMap<>();
            int bonusDamage = 0;
            int baneDamage = 0;
            List<ItemModifier> mods = weapon.getModifiers();

            if (weapon.getCategory() == ItemCategory.WAR_WEAPON && weapon.isWeapon()) {
                if (weapon.isRanged()) {
                    if (player.getArrows() > 0) {
                        player.decrementArrow();
                        animationManager.addAnimation(new Animation(
                            Animation.AnimationType.PROJECTILE_PLAYER,
                            player.getPosition(),
                            monster.getPosition(),
                            weapon.getColor(),
                            0.5f,
                            itemDataManager.getTemplate(Item.ItemType.DART).spriteData
                        ));

                        monster.takeDamage(weapon.getWarDamage() + attackModifier);

                        maze.addBlood((int)monster.getPosition().x, (int)monster.getPosition().y, 0.03f);

                        showDamageText(weapon.getWarDamage() + attackModifier,
                            new GridPoint2((int)monster.getPosition().x, (int)monster.getPosition().y));

                        eventManager.addEvent(new GameEvent("You fire an arrow!", 2f));
                    } else {
                        eventManager.addEvent(new GameEvent("You have no arrows!", 2f));
                        passTurnToMonster();
                        return;
                    }
                } else { // Melee weapon
                    String[] meleeSprite = weapon.getSpriteData();
                    if (meleeSprite == null) {
                        meleeSprite = itemDataManager.getTemplate(Item.ItemType.DART).spriteData;
                    }

                    animationManager.addAnimation(new Animation(
                        Animation.AnimationType.PROJECTILE_PLAYER,
                        player.getPosition(),
                        monster.getPosition(),
                        weapon.getColor(),
                        0.5f,
                        meleeSprite
                    ));

                    monster.takeDamage(weapon.getWarDamage() + attackModifier);
                    maze.addBlood((int)monster.getPosition().x, (int)monster.getPosition().y, 0.05f);

                    showDamageText(weapon.getWarDamage() + attackModifier,
                        new GridPoint2((int)monster.getPosition().x, (int)monster.getPosition().y));


                    eventManager.addEvent(new GameEvent("You attack with your " + weapon.getType() + "!", 2f));
                }

                if (weapon.isUsable()) {
                    player.getInventory().setRightHand(null);
                }

            } else if (weapon.getCategory() == ItemCategory.SPIRITUAL_WEAPON && weapon.isWeapon()) {
                animationManager.addAnimation(new Animation(
                    Animation.AnimationType.PROJECTILE_PLAYER,
                    player.getPosition(),
                    monster.getPosition(),
                    weapon.getColor(),
                    0.5f,
                    itemDataManager.getTemplate(Item.ItemType.LARGE_LIGHTNING).spriteData
                ));

                monster.takeSpiritualDamage(weapon.getSpiritDamage() + attackModifier);
                maze.addBlood((int)monster.getPosition().x, (int)monster.getPosition().y, 0.03f);

                showDamageText(weapon.getSpiritDamage() + attackModifier,
                    new GridPoint2((int)monster.getPosition().x, (int)monster.getPosition().y));

                eventManager.addEvent(new GameEvent("You cast a spell!", 2f));

                if (weapon.isUsable()) {
                    player.getInventory().setRightHand(null);
                }
            } else {
                eventManager.addEvent(new GameEvent("You can't attack with this item.", 2f));
                return;
            }
        } else {
            eventManager.addEvent(new GameEvent("You have no weapon to attack with.", 2f));
            passTurnToMonster();
            return;
        }

        processPlayerStatusEffects();
        player.getStatusManager().updateTurn();

        if (monster.getWarStrength() <= 0 || monster.getSpiritualStrength() <= 0) {
            currentState = CombatState.VICTORY;
            Gdx.app.log("CombatManager","You have defeated" + monster.getMonsterType());
            eventManager.addEvent((new GameEvent("You have defeated " + monster.getMonsterType(), 2f)));

            int baseExp = monster.getBaseExperience();
            float colorMultiplier = monster.getMonsterColor().getXpMultiplier();
            float levelMultiplier = 1.0f + (maze.getLevel() * 0.1f);
            int totalExp = (int) (baseExp * colorMultiplier * levelMultiplier);
            eventManager.addEvent((new GameEvent("You have gained " + totalExp + " experience", 2f)));
            player.addExperience(totalExp, eventManager);
            maze.addBlood((int)monster.getPosition().x, (int)monster.getPosition().y, 0.10f);

        } else {
            currentState = CombatState.MONSTER_TURN;
        }
    }

    public void monsterAttack() {
        if (currentState == CombatManager.CombatState.MONSTER_TURN && monsterAttackDelay <= 0f) {

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
            int damage;
            boolean isSpiritual = false;
            DamageType damageType = DamageType.PHYSICAL;

            switch (monster.getType()) {
                case GIANT_ANT:
                case GIANT_SCORPION:
                case GIANT_SNAKE:
                    isSpiritual = true;
                    damageType = DamageType.POISON;
                    break;
                case DWARF:
                    isSpiritual = true;
                    damageType = DamageType.SPIRITUAL;
                    break;
                case GHOUL:
                    isSpiritual = false;
                    damageType = DamageType.DISEASE;
                    break;
                case SKELETON:
                    isSpiritual = false;
                    damageType = DamageType.PHYSICAL;
                    break;
                case CLOAKED_SKELETON:
                    isSpiritual = false;
                    damageType = DamageType.DARK;
                    break;
                case ALLIGATOR:
                    isSpiritual = random.nextBoolean();
                    damageType = DamageType.PHYSICAL;
                    break;
                case DRAGON:
                    isSpiritual = random.nextBoolean();
                    damageType = isSpiritual ? DamageType.FIRE : DamageType.PHYSICAL;
                    break;
                case WRAITH:
                    isSpiritual = true;
                    damageType = DamageType.DARK;
                    break;
                case GIANT:
                    isSpiritual = random.nextBoolean();
                    damageType = DamageType.PHYSICAL;
                    break;
                case MINOTAUR:
                    isSpiritual = random.nextBoolean();
                    damageType = isSpiritual ? DamageType.SORCERY : DamageType.PHYSICAL;
                    break;
                default:
                    isSpiritual = false;
                    damageType = DamageType.PHYSICAL;
                    break;
            }

            MonsterTemplate template = game.getMonsterDataManager().getTemplate(monster.getType());

            if (isSpiritual) {
                damage = monster.getSpiritualStrength() / 4 + random.nextInt(3);
                Gdx.app.log("CombatManager", "Monster deals " + damage + " " + damageType.name() + " (Spiritual) damage.");
                player.takeSpiritualDamage(damage, damageType);
                if (damage > 0) maze.addBlood((int)player.getPosition().x, (int)player.getPosition().y, 0.03f);

                if (template.onHitEffects != null) {
                    for (EffectApplicationData effectData : template.onHitEffects) {
                        if (random.nextFloat() < effectData.chance) {
                            player.getStatusManager().addEffect(
                                effectData.type,
                                effectData.duration,
                                effectData.potency,
                                effectData.stackable
                            );
                            Gdx.app.log("CombatManager", "Applied effect " + effectData.type.name() + " to player.");
                        }
                    }
                }

                if (monster != null) {
                    monster.getStatusManager().updateTurn();
                }
                if (player.getSpiritualStrength() <= 0) {
                    currentState = CombatState.DEFEAT;
                } else {
                    currentState = CombatState.PLAYER_TURN;
                }
            } else {
                damage = monster.getWarStrength() / 4 + random.nextInt(3);
                Gdx.app.log("CombatManager", "Monster deals " + damage + " " + damageType.name() + " (War) damage.");
                player.takeDamage(damage, damageType);
                if (damage > 0) maze.addBlood((int)player.getPosition().x, (int)player.getPosition().y, 0.03f);

                if (template.onHitEffects != null) {
                    for (EffectApplicationData effectData : template.onHitEffects) {
                        if (random.nextFloat() < effectData.chance) {
                            player.getStatusManager().addEffect(
                                effectData.type,
                                effectData.duration,
                                effectData.potency,
                                effectData.stackable
                            );
                            Gdx.app.log("CombatManager", "Applied effect " + effectData.type.name() + " to player.");
                        }
                    }
                }

                if (monster != null) {
                    monster.getStatusManager().updateTurn();
                }
                if (player.getWarStrength() <= 0) {
                    currentState = CombatState.DEFEAT;
                } else {
                    currentState = CombatState.PLAYER_TURN;
                }
            }
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
        if (currentState == CombatState.MONSTER_TURN) {
            if (monsterAttackDelay > 0f) monsterAttackDelay -= delta;
            else monsterAttack();
        }
        if (currentState == CombatState.VICTORY) {
            Gdx.app.log("CombatManager", "Player is victorious!");
            GridPoint2 monsterPos = new GridPoint2((int)monster.getPosition().x, (int)monster.getPosition().y);

            // --- SPAWN CORPSE (Only Debris, No Blood Shader) ---
            spawnCorpseEffects(monster);
            // ---------------------------------------------------

            maze.getMonsters().remove(monsterPos);
            endCombat();
        } else if (currentState == CombatState.DEFEAT) {
            Gdx.app.log("CombatManager", "Player has been defeated!");
            if (worldManager != null) worldManager.disableSaving();
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

    // --- MODIFIED: Fixed velocity to be less violent, removed BloodSpray ---
    private void spawnCorpseEffects(Monster monster) {
        String[] originalSprite = monster.getSpriteData();
        if (originalSprite == null || originalSprite.length == 0) return;

        Vector2 mPos2 = monster.getPosition();
        Vector3 centerPos = new Vector3(mPos2.x, 0.5f, mPos2.y);

        // Removed: maze.getBloodSprays().add(...)

        // Split Sprite into 4 Quadrants
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

            // --- FIXED VELOCITY: Gentle "Crumble" ---
            // X/Z: Very small drift (0.2 - 0.5)
            float velX = (q % 2 == 0 ? -1f : 1f) * (0.2f + (float)Math.random() * 0.3f);
            float velZ = (q < 2 ? 1f : -1f) * (0.2f + (float)Math.random() * 0.3f);

            // Y: Tiny hop (0.8 - 1.3)
            float velY = 0.8f + (float)Math.random() * 0.5f;

            Vector3 velocity = new Vector3(velX, velY, velZ);
            Vector3 partPos = new Vector3(centerPos).add(velX * 0.1f, 0, velZ * 0.1f);

            maze.getCorpses().add(new CorpsePart(partPos, velocity, quadData, monster.getColor()));
        }
    }
    // ----------------------------------------------------------------------

    public CombatState getCurrentState() { return currentState; }
    public Monster getMonster() { return monster; }
}
