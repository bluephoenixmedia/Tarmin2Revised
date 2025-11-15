package com.bpm.minotaur.managers;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.GridPoint2;
import com.bpm.minotaur.Tarmin2;
import com.bpm.minotaur.gamedata.*;
import com.bpm.minotaur.gamedata.effects.ActiveStatusEffect;
import com.bpm.minotaur.gamedata.effects.EffectApplicationData;
import com.bpm.minotaur.gamedata.effects.StatusEffectType;
import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.gamedata.item.ItemModifier;
import com.bpm.minotaur.gamedata.item.ItemSpriteData;
import com.bpm.minotaur.gamedata.monster.Monster;
import com.bpm.minotaur.gamedata.monster.MonsterFamily;
import com.bpm.minotaur.gamedata.monster.MonsterTemplate;
import com.bpm.minotaur.gamedata.player.Player;
import com.bpm.minotaur.rendering.Animation;
import com.bpm.minotaur.rendering.AnimationManager;
import com.bpm.minotaur.screens.GameOverScreen;
import com.bpm.minotaur.gamedata.item.ItemCategory;

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


    public CombatManager(Player player, Maze maze, Tarmin2 game, AnimationManager animationManager,
                         GameEventManager eventManager, SoundManager soundManager, WorldManager worldManager) {
        this.player = player;
        this.maze = maze;
        this.game = game;
        this.animationManager = animationManager;
        this.eventManager = eventManager;
        this.soundManager = soundManager;
        this.worldManager = worldManager;
    }

    public void startCombat(Monster monster) {
        if (currentState == CombatState.INACTIVE) {
            this.monster = monster;

            if (this.monster != null && this.monster.getStatusManager() != null && this.eventManager != null) {
                this.monster.getStatusManager().initialize(eventManager);
            }

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
            monsterAttackDelay = MONSTER_ATTACK_DELAY_TIME; // ADD THIS LINE - Set the delay

            Gdx.app.log("CombatManager", "Combat started with " + monster.getType());
        }
    }

    public void endCombat() {
        currentState = CombatState.INACTIVE;
        monster = null;
        monsterAttackDelay = 0f; // ADD THIS LINE - Reset delay when combat ends

        Gdx.app.log("CombatManager", "Combat ended.");
    }

    /**
     * Processes all active status effects on the player at the start of their turn.
     * This is where effects like POISON, REGENERATION, etc. will be handled.
     */
    private void processPlayerStatusEffects() {
        if (player == null) return; // Safety check

        // --- Process POISON ---
        // We check for the effect *before* calling updateTurn()
        if (player.getStatusManager().hasEffect(StatusEffectType.POISONED)) {
            ActiveStatusEffect poison = player.getStatusManager().getEffect(StatusEffectType.POISONED);
            int damage = poison.getPotency();

            // Use takeDamage with the POISON type, so resistances can apply
            //player.takeDamage(damage, DamageType.POISON);
            player.takeStatusEffectDamage(damage, DamageType.POISON);

            // Send an event to the HUD
            eventManager.addEvent(new GameEvent("You take " + damage + " poison damage!", 2f));
            Gdx.app.log("CombatManager", "Player took " + damage + " poison damage.");
        }

        // ... (Future effects go here) ...
    }

    public void playerAttack() {
        if (currentState != CombatState.PLAYER_TURN) return;

        Item weapon = player.getInventory().getRightHand();
        int attackModifier = player.getAttackModifier();

        if (weapon != null) {
            Gdx.app.log("CombatManager", "Weapon is not null");
            Gdx.app.log("CombatManager", "Weapon category = " + weapon.getCategory().toString());

            // --- NEW: Calculate all bonus damages from modifiers ---
            Map<DamageType, Integer> elementalDamages = new HashMap<>();
            int bonusDamage = 0;
            int baneDamage = 0;

            List<ItemModifier> mods = weapon.getModifiers();
            for (ItemModifier mod : mods) {
                switch (mod.type) {
                    case BONUS_DAMAGE:
                        bonusDamage += mod.value;
                        break;
                    case ADD_FIRE_DAMAGE:
                        elementalDamages.merge(DamageType.FIRE, mod.value, Integer::sum);
                        break;
                    case ADD_ICE_DAMAGE:
                        elementalDamages.merge(DamageType.ICE, mod.value, Integer::sum);
                        break;
                    case ADD_POISON_DAMAGE:
                        elementalDamages.merge(DamageType.POISON, mod.value, Integer::sum);
                        break;
                    case ADD_BLEED_DAMAGE:
                        elementalDamages.merge(DamageType.BLEED, mod.value, Integer::sum);
                        break;
                    case ADD_DARK_DAMAGE:
                        elementalDamages.merge(DamageType.DARK, mod.value, Integer::sum);
                        break;
                    case ADD_LIGHT_DAMAGE:
                        elementalDamages.merge(DamageType.LIGHT, mod.value, Integer::sum);
                        break;
                    case ADD_SORCERY_DAMAGE:
                        elementalDamages.merge(DamageType.SORCERY, mod.value, Integer::sum);
                        break;

                    // Bane Damage
                    case BANE_BEAST:
                        if (monster.getFamily() == MonsterFamily.BEAST) baneDamage += mod.value;
                        break;
                    case BANE_HUMANOID:
                        if (monster.getFamily() == MonsterFamily.HUMANOID) baneDamage += mod.value;
                        break;
                    case BANE_UNDEAD:
                        if (monster.getFamily() == MonsterFamily.UNDEAD) baneDamage += mod.value;
                        break;
                    case BANE_MYTHICAL:
                        if (monster.getFamily() == MonsterFamily.MYTHICAL) baneDamage += mod.value;
                        break;
                    // Add other BANE types here
                }
            }

            if (weapon.getCategory() == ItemCategory.WAR_WEAPON && weapon.isWeapon()) {
                Gdx.app.log("CombatManager", "Weapon is war weapon and stats are not null");

                // Ranged weapon check
                if (weapon.isRanged()) {
                    Gdx.app.log("CombatManager", "Weapon is ranged");

                    if (player.getArrows() > 0) {
                        Gdx.app.log("CombatManager", "Player has arrows");
                        player.decrementArrow();

                        // BOW/CROSSBOW always uses DART sprite with weapon's color
                        animationManager.addAnimation(new Animation(
                            Animation.AnimationType.PROJECTILE_PLAYER,
                            player.getPosition(),
                            monster.getPosition(),
                            weapon.getColor(),  // CHANGED: Use weapon's color instead of WHITE
                            0.5f,
                            ItemSpriteData.DART
                        ));

                        monster.takeDamage(weapon.getWarDamage() + attackModifier);
                        showDamageText(weapon.getWarDamage() + attackModifier,
                            new GridPoint2((int)monster.getPosition().x, (int)monster.getPosition().y));

                        Gdx.app.log("CombatManager", "monster takes " + weapon.getWarDamage() + " damage");
                        eventManager.addEvent(new GameEvent("You fire an arrow!", 2f));
                    } else {
                        eventManager.addEvent(new GameEvent("You have no arrows!", 2f));
                        passTurnToMonster(); // New line
                        return; // Don't switch turns if no arrows
                    }
                } else { // Melee weapon
                    Gdx.app.log("CombatManager", "Melee weapon attack");

                    // Melee weapons use their own sprite data
                    String[] meleeSprite = weapon.getSpriteData();
                    if (meleeSprite == null) {
                        meleeSprite = ItemSpriteData.DART; // Fallback
                    }

                    animationManager.addAnimation(new Animation(
                        Animation.AnimationType.PROJECTILE_PLAYER,
                        player.getPosition(),
                        monster.getPosition(),
                        weapon.getColor(),  // CHANGED: Use weapon's color instead of WHITE
                        0.5f,
                        meleeSprite
                    ));

                    monster.takeDamage(weapon.getWarDamage() + attackModifier);
                    showDamageText(weapon.getWarDamage() + attackModifier,
                        new GridPoint2((int)monster.getPosition().x, (int)monster.getPosition().y));


                    eventManager.addEvent(new GameEvent("You attack with your " + weapon.getType() + "!", 2f));
                }

                if (weapon.isUsable()) {
                    player.getInventory().setRightHand(null);
                }

            } else if (weapon.getCategory() == ItemCategory.SPIRITUAL_WEAPON && weapon.isWeapon()) {
                Gdx.app.log("CombatManager", "Weapon is spiritual weapon and stats are not null");

                // BOOK/SCROLL always uses LARGE_LIGHTNING sprite with weapon's color
                animationManager.addAnimation(new Animation(
                    Animation.AnimationType.PROJECTILE_PLAYER,
                    player.getPosition(),
                    monster.getPosition(),
                    weapon.getColor(),  // Already correct - uses weapon's color
                    0.5f,
                    ItemSpriteData.LARGE_LIGHTNING
                ));

                monster.takeSpiritualDamage(weapon.getSpiritDamage() + attackModifier);
                showDamageText(weapon.getSpiritDamage() + attackModifier,
                    new GridPoint2((int)monster.getPosition().x, (int)monster.getPosition().y));

                Gdx.app.log("CombatManager", "monster takes " + weapon.getSpiritDamage() + " damage");
                eventManager.addEvent(new GameEvent("You cast a spell!", 2f));

                if (weapon.isUsable()) {
                    player.getInventory().setRightHand(null);
                }
            } else {
                eventManager.addEvent(new GameEvent("You can't attack with this item.", 2f));
                return; // Don't switch turns if the attack was invalid
            }
        } else {
            eventManager.addEvent(new GameEvent("You have no weapon to attack with.", 2f));
            passTurnToMonster(); // New line
            return; // Don't switch turns if there's no weapon
        }

        processPlayerStatusEffects(); // Process effects *before* ticking
        player.getStatusManager().updateTurn();

        if (monster.getWarStrength() <= 0 || monster.getSpiritualStrength() <= 0) {
            currentState = CombatState.VICTORY;
            Gdx.app.log("CombatManager","You have defeated" + monster.getMonsterType());
            eventManager.addEvent((new GameEvent("You have defeated " + monster.getMonsterType(), 2f)));

            // --- Experience Calculation ---
            int baseExp = monster.getBaseExperience();
            Gdx.app.log("CombatManager","Monster baseExp = " + baseExp);

            float colorMultiplier = monster.getMonsterColor().getXpMultiplier();
            Gdx.app.log("CombatManager","Monster colorMultiplier = " + colorMultiplier);

            float levelMultiplier = 1.0f + (maze.getLevel() * 0.1f);
            Gdx.app.log("CombatManager","Monster levelMultiplier = " + levelMultiplier);

            int totalExp = (int) (baseExp * colorMultiplier * levelMultiplier);
            eventManager.addEvent((new GameEvent("You have gained " + totalExp + " experience", 2f)));

            Gdx.app.log("CombatManager","You have gained " + totalExp + " experience");

            player.addExperience(totalExp, eventManager);
            // --------------------------
        } else {
            currentState = CombatState.MONSTER_TURN;
        }
    }

    public void monsterAttack() {
        if (currentState == CombatManager.CombatState.MONSTER_TURN && monsterAttackDelay <= 0f) {
            Gdx.app.log("CombatManager", "Monster attacks player");
            // Monster always uses DART sprite
            animationManager.addAnimation(new Animation(
                Animation.AnimationType.PROJECTILE_MONSTER,
                monster.getPosition(),
                player.getPosition(),
                monster.getColor(),
                0.5f,
                ItemSpriteData.DART
            ));
            soundManager.playMonsterAttackSound(monster);
            int damage;
            boolean isSpiritual = false;
            DamageType damageType = DamageType.PHYSICAL; // Default damage type

            // Determine attack type based on monster category
            switch (monster.getType()) {
                // Bad Monsters use Spiritual attacks
                case GIANT_ANT:
                case GIANT_SCORPION:
                case GIANT_SNAKE:
                    isSpiritual = true;
                    damageType = DamageType.POISON;
                    break;
                case DWARF:
                    isSpiritual = true;
                    damageType = DamageType.SPIRITUAL; // Dwarf is a "Bad Monster"
                    break;

                // Nasty Monsters use War attacks
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

                // Horrible Monsters can use either War or Spiritual attacks
                case ALLIGATOR:
                    isSpiritual = random.nextBoolean();
                    damageType = DamageType.PHYSICAL; // Alligator is pure physical
                    break;
                case DRAGON:
                    isSpiritual = random.nextBoolean();
                    damageType = isSpiritual ? DamageType.FIRE : DamageType.PHYSICAL;
                    break;
                case WRAITH:
                    isSpiritual = true; // Wraiths are always spiritual
                    damageType = DamageType.DARK;
                    break;
                case GIANT:
                    isSpiritual = random.nextBoolean();
                    damageType = DamageType.PHYSICAL; // Giant is pure physical
                    break;
                case MINOTAUR:
                    isSpiritual = random.nextBoolean();
                    damageType = isSpiritual ? DamageType.SORCERY : DamageType.PHYSICAL;
                    break;

                // Default to war attacks for any other monster type
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

                if (template.onHitEffects != null) {
                    for (EffectApplicationData effectData : template.onHitEffects) {
                        // Roll for chance
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

                if (monster != null) { // Safety check
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

                if (template.onHitEffects != null) {
                    for (EffectApplicationData effectData : template.onHitEffects) {
                        // Roll for chance
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

                if (monster != null) { // Safety check
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


    private void showDamageText(int damage, GridPoint2 position) {
        animationManager.addAnimation(new Animation(
            Animation.AnimationType.DAMAGE_TEXT,
            position,
            String.valueOf(damage),
            1.0f  // 1 second duration
        ));
    }

    public void update(float delta) {
        if (currentState == CombatState.MONSTER_TURN) {
            if (monsterAttackDelay > 0f) {
                monsterAttackDelay -= delta;
            } else {
                monsterAttack();
            }
        }

        if (currentState == CombatState.VICTORY) {
            Gdx.app.log("CombatManager", "Player is victorious!");
            GridPoint2 monsterPos = new GridPoint2((int)monster.getPosition().x, (int)monster.getPosition().y);
            maze.getMonsters().remove(monsterPos);
            endCombat();
        } else if (currentState == CombatState.DEFEAT) {
            Gdx.app.log("CombatManager", "Player has been defeated!");

            if (worldManager != null) {
                worldManager.disableSaving();
            }

            game.setScreen(new GameOverScreen(game));
        }
    }

    /**
     * Called when the player performs a non-attack action during their turn
     * (like swapping inventory). This passes the turn to the monster.
     */
    public void passTurnToMonster() {
        if (currentState == CombatState.PLAYER_TURN) {

            processPlayerStatusEffects(); // Process effects *before* ticking

            player.getStatusManager().updateTurn();

            Gdx.app.log("CombatManager", "Player passed turn. Monster's turn.");
            currentState = CombatState.MONSTER_TURN;
            monsterAttackDelay = MONSTER_ATTACK_DELAY_TIME;
        }
    }

    public CombatState getCurrentState() { return currentState; }
    public Monster getMonster() { return monster; }
}
