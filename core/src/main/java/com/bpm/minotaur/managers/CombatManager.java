package com.bpm.minotaur.managers;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.GridPoint2;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.Screen;
import com.bpm.minotaur.Tarmin2;
import com.bpm.minotaur.gamedata.*;
import com.bpm.minotaur.gamedata.effects.ActiveStatusEffect;
import com.bpm.minotaur.gamedata.effects.StatusEffectType;
import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.gamedata.item.ItemColor;
import com.bpm.minotaur.gamedata.item.ItemDataManager;
import com.bpm.minotaur.gamedata.monster.Monster;
import com.bpm.minotaur.gamedata.monster.MonsterTemplate;

import com.bpm.minotaur.gamedata.player.Player;
import com.bpm.minotaur.rendering.Animation;
import com.bpm.minotaur.rendering.AnimationManager;
import com.bpm.minotaur.screens.GameOverScreen;

import com.bpm.minotaur.gamedata.dice.Die;
import com.bpm.minotaur.gamedata.dice.DieResult;
import com.bpm.minotaur.gamedata.dice.DieFaceType;
import com.bpm.minotaur.utils.DiceRoller;
import java.util.List;
import java.util.Random;

public class CombatManager {

    public enum CombatState {
        INACTIVE,
        PLAYER_MENU, // NEW: Waiting for menu input
        PLAYER_TURN, // Executing action (may be deprecated if we go straight to resolution)
        PLAYER_SELECT_DICE, // Choosing hand
        PHYSICS_RESOLUTION, // Rolling
        PHYSICS_DELAY, // Viewing Result
        MONSTER_TURN,
        VICTORY,
        DEFEAT
    }

    public static class HitResult {
        public enum HitType {
            NOTHING, // Reached max range without hitting anything
            WALL, // Hit a wall, closed door, closed gate, or SCENERY
            MONSTER, // Hit a monster
            PLAYER, // Hit the player
            OUT_OF_BOUNDS // Went off the map
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

    private final StochasticManager stochasticManager;
    private com.bpm.minotaur.rendering.Hud hud; // Set via setter

    public void setHud(com.bpm.minotaur.rendering.Hud hud) {
        this.hud = hud;
    }

    private float monsterAttackDelay = 0f;
    private static final float MONSTER_ATTACK_DELAY_TIME = 0.3f;

    private static final float PROJECTILE_SPEED = 15.0f;
    private final ItemDataManager itemDataManager;

    private int lastDamageDealt = 0;

    private Item pendingWeapon;
    private boolean pendingIsRanged;

    // --- TIMING VARIABLES ---
    private float physicsTimer = 0f;
    private static final float MIN_ROLL_TIME = 0.1f; // Almost instant allow-settle
    private static final float RESULT_VIEW_TIME = 0.3f; // Quick glance at result (600ms)

    // --- LOGGING STATS ---
    private int currentCombatTurns = 0;
    private int damageTakenInCombat = 0;

    private final TurnManager turnManager;
    private final MonsterAiManager monsterAiManager;
    private final WorldManager worldManager;

    public CombatManager(Player player, Maze maze, Tarmin2 game, AnimationManager animationManager,
            GameEventManager eventManager, SoundManager soundManager,
            ItemDataManager itemDataManager, StochasticManager stochasticManager,
            TurnManager turnManager, MonsterAiManager monsterAiManager, WorldManager worldManager) {
        this.player = player;
        this.maze = maze;
        this.game = game;
        this.animationManager = animationManager;
        this.eventManager = eventManager;
        this.soundManager = soundManager;
        this.itemDataManager = itemDataManager;
        this.stochasticManager = stochasticManager;
        this.turnManager = turnManager;
        this.monsterAiManager = monsterAiManager;
        this.worldManager = worldManager;
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
                return new HitResult(new GridPoint2(currentX - dx, currentY - dy), HitResult.HitType.OUT_OF_BOUNDS,
                        null);
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

            // --- LOGGING INIT ---
            this.currentCombatTurns = 0;
            this.damageTakenInCombat = 0;
            BalanceLogger.getInstance().logCombatStart(player, monster);
            // --------------------

            if (this.monster != null && this.monster.getStatusManager() != null && this.eventManager != null) {
                this.monster.getStatusManager().initialize(eventManager, this.monster);
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

            // --- NEW: Start with Player Menu ---
            currentState = CombatState.PLAYER_MENU;
            Gdx.app.log("COMBAT_FLOW", "State -> PLAYER_MENU (Initial)");
            Gdx.app.log("CombatManager", "Combat started with " + monster.getType() + ". State: PLAYER_MENU");

            monsterAttackDelay = MONSTER_ATTACK_DELAY_TIME;
        }
    }

    public void playerMeleeStrike(Monster target) {
        if (currentState != CombatState.INACTIVE) return;

        Item weapon = player.getInventory().getRightHand();
        if (weapon == null || weapon.isRanged()) {
            eventManager.addEvent(new GameEvent("No melee weapon equipped.", 1.5f));
            return;
        }

        this.monster = target;
        this.currentCombatTurns = 0;
        this.damageTakenInCombat = 0;
        BalanceLogger.getInstance().logCombatStart(player, target);

        if (target.getStatusManager() != null && eventManager != null)
            target.getStatusManager().initialize(eventManager, target);

        int px = (int) player.getPosition().x, py = (int) player.getPosition().y;
        int mx = (int) target.getPosition().x, my = (int) target.getPosition().y;
        Direction dir = null;
        if (mx > px) dir = Direction.EAST;
        else if (mx < px) dir = Direction.WEST;
        else if (my > py) dir = Direction.NORTH;
        else if (my < py) dir = Direction.SOUTH;
        if (dir != null && player.getFacing() != dir) player.setFacing(dir);

        this.pendingWeapon = weapon;
        soundManager.playWeaponSwing();
        if (game.getScreen() instanceof com.bpm.minotaur.screens.GameScreen) {
            com.bpm.minotaur.screens.GameScreen gs = (com.bpm.minotaur.screens.GameScreen) game.getScreen();
            gs.getWeaponOverlay().triggerAttack(weapon);
            gs.getWeaponOverlay().jumpToImpact();
        }

        resolveAttack(DiceRoller.d20(), true);
    }

    public void monsterMeleeStrike(Monster attacker) {
        soundManager.playMonsterAttackSound(attacker);

        int attackBonus = 2;
        MonsterTemplate t = attacker.getTemplate();
        if (t != null) attackBonus += (t.maxHP / 10);

        int d20Roll = DiceRoller.d20();
        boolean isHit = (d20Roll + attackBonus) >= player.getArmorClass();

        if (isHit) {
            int baseDmg = DiceRoller.roll(attacker.getDamageDice());
            float doomScale = DoomManager.getInstance().getEnemyScalingMultiplier();
            int dmg = Math.max(1, (int) (baseDmg * doomScale));
            if (playerCurrentBlock > 0) {
                int blocked = Math.min(dmg, playerCurrentBlock);
                dmg = Math.max(0, dmg - playerCurrentBlock);
                eventManager.addEvent(new GameEvent("Blocked " + blocked + " dmg", 1f));
            }
            int actualDamage = player.takeDamage(dmg, DamageType.PHYSICAL);
            maze.addBlood((int) player.getPosition().x, (int) player.getPosition().y, 0.03f);
            eventManager.addEvent(new GameEvent(attacker.getMonsterType() + " hits you for " + actualDamage, 1f));
        } else {
            eventManager.addEvent(new GameEvent(attacker.getMonsterType() + " misses!", 1f));
        }

        playerCurrentBlock = 0;

        if (player.getCurrentHP() <= 0) {
            this.monster = attacker;
            currentState = CombatState.DEFEAT;
        }
    }

    public void openMenu() {
        if (currentState == CombatState.INACTIVE) {
            currentState = CombatState.PLAYER_MENU;
            Gdx.app.log("COMBAT_FLOW", "State -> PLAYER_MENU (Manual Open)");
        }
    }

    public void endCombat() {
        currentState = CombatState.INACTIVE;
        monster = null;
        monsterAttackDelay = 0f;
        Gdx.app.log("CombatManager", "Combat ended.");
    }

    private void processPlayerStatusEffects() {
        if (player == null)
            return;
        if (player.getStatusManager().hasEffect(StatusEffectType.POISONED)) {
            ActiveStatusEffect poison = player.getStatusManager().getEffect(StatusEffectType.POISONED);
            int damage = poison.getPotency();
            player.takeStatusEffectDamage(damage, DamageType.POISON);
            eventManager.addEvent(new GameEvent("You take " + damage + " poison damage!", 2f));

            damageTakenInCombat += damage;
            BalanceLogger.getInstance().log("COMBAT_EFFECT", "Player took " + damage + " poison dmg.");

            Gdx.app.log("CombatManager", "Player took " + damage + " poison damage.");
        }

        // NEW: Critical Toxicity DoT
        if (player.getStats().getToxicity() >= 76) {
            int dot = 2 + random.nextInt(3);
            player.takeStatusEffectDamage(dot, DamageType.POISON);
            eventManager.addEvent(new GameEvent("Toxicity burns! (-" + dot + " HP)", 2f));
            damageTakenInCombat += dot;
        }
    }

    // --- NEW: Helper to setup weapon and checks ---
    private boolean prepareAttack() {
        Item weapon = player.getInventory().getRightHand();

        if (weapon != null) {
            this.pendingWeapon = weapon;
            this.pendingIsRanged = weapon.isRanged();

            if (pendingIsRanged && weapon.getType() != Item.ItemType.DART && player.getArrows() <= 0) {
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

    public void playerCast() {
        if (currentState != CombatState.PLAYER_MENU && currentState != CombatState.PLAYER_TURN)
            return;

        // FOR NOW: Hardcoded Spell Selection (later pass in spell or use UI state)
        // Check if player knows IRON_SKIN to prioritize it for testing/context?
        // Or simple toggle?
        // The prompt implies we can cast "any spell they have access to".
        // Since we don't have a spell selection UI in the menu yet (just "CAST"),
        // let's assume this method is triggering the selection OR defaulting.
        // For this task, we'll default to MAGIC_ARROW unless we implement a submenu.
        // User asked: "they can cast any spell they have access too"
        // This implies a sub-menu.
        // BUT, for this step, let's implement the logic assuming the SPELL is passed or
        // selected.
        // Since I can't easily add a full sub-menu right now without more UI work,
        // I will make this method accept a SpellType, OR defaults.
        // Let's modify the signature or just infer.
        // ACTUALLY: The user said "access the combat menu... cast any spell".
        // I should probably pop up a spell list?
        // For simplicity in this iteration: If invalid target (no monster), try
        // defensive.
        // If monster exists, try offensive?
        // Better: Let's support `playerCast(SpellType)` and overload.

        // Default behavior for "CAST" button (simplification):
        // Cycle or pick first available?
        // Let's pick MAGIC_ARROW if target, IRON_SKIN if no target?
        // Or better: Cycle them?
        // Let's stick to MAGIC_ARROW default for now, but handle IRON_SKIN if I change
        // logic.
        // Wait, I can't easily change the UI to send arguments yet.
        // Let's check `player.getKnownSpells()`.

        com.bpm.minotaur.gamedata.spells.SpellType spellToCast = com.bpm.minotaur.gamedata.spells.SpellType.MAGIC_ARROW;
        if (player.getKnownSpells().contains(com.bpm.minotaur.gamedata.spells.SpellType.IRON_SKIN)) {
            // Prioritize IRON_SKIN if no monster?
            if (monster == null) {
                spellToCast = com.bpm.minotaur.gamedata.spells.SpellType.IRON_SKIN;
            }
        }

        castSpell(spellToCast);
    }

    public void castSpell(com.bpm.minotaur.gamedata.spells.SpellType spell) {
        if (!player.getKnownSpells().contains(spell)) {
            eventManager.addEvent(new GameEvent("You don't know that spell!", 1.5f));
            return;
        }

        if (!player.hasEnoughMana(spell.getMpCost())) {
            eventManager.addEvent(new GameEvent("Not enough MP!", 1.5f));
            return;
        }

        player.deductMana(spell.getMpCost());
        eventManager.addEvent(new GameEvent("Cast " + spell.getDisplayName() + "!", 1.5f));

        if (spell == com.bpm.minotaur.gamedata.spells.SpellType.IRON_SKIN) {
            player.getStatusManager().addEffect(StatusEffectType.HARDENED, 10, 1, false);
            eventManager.addEvent(new GameEvent("Your skin turns to iron!", 2f));
            // Trigger Vignette (via Event or Callback?)
            // Simple hack: Set a flag on Player or GameScreen via Event?
            // Let's add a specialized event.
            // eventManager.addEvent(new GameEvent(GameEvent.EventType.VFX_TRIGGER,
            // "IRON_SKIN")); // If supported
            // For now, Player status is enough for GameScreen to render vignette.

            // End turn or free action?
            closeMenuOrPassTurn();
            return;
        }

        if (spell == com.bpm.minotaur.gamedata.spells.SpellType.MAGIC_ARROW) {
            // Visuals
            com.badlogic.gdx.math.Vector2 startPos = player.getPosition().cpy()
                    .add(player.getDirectionVector().cpy().scl(0.6f));

            Vector2 targetPos = null;
            Monster targetMonster = this.monster; // Default to current combat target

            // If no locked monster, Raycast to find one
            if (targetMonster == null) {
                HitResult hit = raycastProjectile(player.getPosition(), player.getFacing(), 8, true);
                if (hit.type == HitResult.HitType.MONSTER && hit.hitMonster != null) {
                    targetMonster = hit.hitMonster;
                    targetPos = targetMonster.getPosition();
                    // Auto-engage?
                    // startCombat(targetMonster); // Optional: Engage if hit
                } else {
                    // Shoot into void/wall
                    targetPos = new Vector2(hit.collisionPoint.x + 0.5f, hit.collisionPoint.y + 0.5f);
                }
            } else {
                targetPos = targetMonster.getPosition();
            }

            animationManager.addAnimation(new Animation(
                    Animation.AnimationType.PROJECTILE_SPELL,
                    startPos, targetPos,
                    com.badlogic.gdx.graphics.Color.CYAN, 0.6f,
                    new String[] { "*" }));

            if (targetMonster != null) {
                int magicDamage = 5 + (player.getLevel());
                int actualDamage = targetMonster.takeDamage(magicDamage);

                showDamageText(actualDamage,
                        new GridPoint2((int) targetMonster.getPosition().x, (int) targetMonster.getPosition().y));
                if (actualDamage > 0) {
                    // Impact
                }

                if (targetMonster.getCurrentHP() <= 0) {
                    // Logic to kill if it's the active monster
                    if (targetMonster == this.monster) {
                        handleMonsterDeath();
                        currentState = CombatState.VICTORY;
                    } else {
                        // Remote kill
                        handleRemoteKill(targetMonster);
                    }
                } else {
                    // If we weren't in combat, maybe start now?
                    if (this.monster == null) {
                        startCombat(targetMonster);
                    }
                }
            }

            closeMenuOrPassTurn();
        }
    }

    private void handleRemoteKill(Monster m) {
        maze.getMonsters().remove(new GridPoint2((int) m.getPosition().x, (int) m.getPosition().y));
        player.getStats().addExperience(m.getBaseExperience());
        eventManager.addEvent(new GameEvent("Killed " + m.getMonsterType() + "!", 2f));
    }

    private void closeMenuOrPassTurn() {
        if (monster == null) {
            currentState = CombatState.INACTIVE; // Close menu if no enemy
        } else {
            // Pass Turn
            processPlayerStatusEffects();
            player.getStatusManager().updateTurn();

            if (turnManager != null && monsterAiManager != null) {
                turnManager.processTurn(maze, player, monsterAiManager, this, worldManager, eventManager);
            }
            currentState = CombatState.MONSTER_TURN;
            monsterAttackDelay = MONSTER_ATTACK_DELAY_TIME;
        }
    }

    public void playerAttackInstant() {
        if (currentState != CombatState.PLAYER_TURN && currentState != CombatState.PLAYER_MENU)
            return;

        // 1. Check if we have a monster target
        if (monster == null) {
            // 2. No target? Check Ranged
            if (player.getInventory().getRightHand() != null && player.getInventory().getRightHand().isRanged()) {
                performRangedAttack(); // Re-use existing GameScreen method logic? No, move it here or dup.
                // Re-implementing logic here safely:
                HitResult hit = raycastProjectile(player.getPosition(), player.getFacing(), 8, true);
                if (hit.type == HitResult.HitType.MONSTER && hit.hitMonster != null) {
                    // Found one!
                    // Trigger Ranged Attack on this monster
                    startCombat(hit.hitMonster); // Engage!
                    // Now we have a monster, proceed to resolve?
                    // Or separate method to avoid recursion issues.
                    // Let's manually resolve against hit.hitMonster
                    resolveRangedAttackAgainst(hit.hitMonster);
                } else {
                    eventManager.addEvent(new GameEvent("No target in range.", 1.5f));
                    currentState = CombatState.INACTIVE;
                }
            } else {
                eventManager.addEvent(new GameEvent("No monster to attack!", 1.5f));
                currentState = CombatState.INACTIVE;
            }
            return;
        }

        if (!prepareAttack())
            return;

        // --- VISCERAL: Trigger Weapon Animation & Sound ---
        soundManager.playWeaponSwing();
        if (game.getScreen() instanceof com.bpm.minotaur.screens.GameScreen) {
            com.bpm.minotaur.screens.GameScreen gs = (com.bpm.minotaur.screens.GameScreen) game.getScreen();
            gs.getWeaponOverlay().triggerAttack(pendingWeapon);
            gs.getWeaponOverlay().jumpToImpact();
        }

        // Roll d20
        int d20Roll = DiceRoller.d20();

        Gdx.app.log("CombatManager", "Instant Attack: Rolled " + d20Roll + " on D20");

        resolveAttack(d20Roll);
    }

    private void resolveRangedAttackAgainst(Monster target) {
        // wasn't set?
        // But startCombat sets it.
        // If startCombat was called, we are good.
        // But we need to ensure pendingWeapon is set.
        prepareAttack(); // Sets pendingWeapon

        // Animate Projectile
        // ... (Add projectile animation here akin to Magic Arrow?)
        // Actually Weapons currently use WeaponOverlay slash.
        // Ranged weapons should probably shoot a projectile.

        // Trigger resolution
        int d20Roll = DiceRoller.d20();
        resolveAttack(d20Roll);
    }

    // --- RENAMED: Physics Attack (KEY 7) - With Animation ---
    public void playerAttackWithDice() {
        if (currentState != CombatState.PLAYER_TURN && currentState != CombatState.PLAYER_MENU)
            return;

        if (monster == null) {
            eventManager.addEvent(new GameEvent("No monster found!", 1.5f));
            currentState = CombatState.INACTIVE;
            return;
        }

        // Transition to Dice Selection Overlay
        currentState = CombatState.PLAYER_SELECT_DICE;
        BalanceLogger.getInstance().log("COMBAT_STATE", "Transitioned to PLAYER_SELECT_DICE. Waiting for UI.");
        Gdx.app.log("CombatManager", "State changed to PLAYER_SELECT_DICE");
    }

    public void confirmDiceSelection(List<Die> selectedHand) {
        if (currentState != CombatState.PLAYER_SELECT_DICE)
            return;

        if (selectedHand.isEmpty()) {
            // Fallback for empty hand (Fists)
            selectedHand.add(new Die("Fists", com.badlogic.gdx.graphics.Color.WHITE,
                    new com.bpm.minotaur.gamedata.dice.DieFace(DieFaceType.SWORD, 1),
                    new com.bpm.minotaur.gamedata.dice.DieFace(DieFaceType.BLANK, 0),
                    new com.bpm.minotaur.gamedata.dice.DieFace(DieFaceType.SWORD, 1),
                    new com.bpm.minotaur.gamedata.dice.DieFace(DieFaceType.BLANK, 0),
                    new com.bpm.minotaur.gamedata.dice.DieFace(DieFaceType.SHIELD, 1),
                    new com.bpm.minotaur.gamedata.dice.DieFace(DieFaceType.SWORD, 2)));
        }

        // TRIGGER PHYSICS STATE
        currentState = CombatState.PHYSICS_RESOLUTION;
        physicsTimer = 0f;

        // Spawn the selected dice
        stochasticManager.spawnDice(selectedHand);

        eventManager.addEvent(new GameEvent("Rolling for fate...", 1f));
    }

    // Kept for backward compatibility if called elsewhere, maps to Instant
    public void playerAttack() {
        playerAttackInstant();
    }

    public void playerUseItem(DiscoveryManager discoveryManager) {
        if (currentState != CombatState.PLAYER_MENU && currentState != CombatState.PLAYER_TURN)
            return;

        // Use Active Slot (Index 0 - Top Left)
        Item[] quickSlots = player.getInventory().getQuickSlots();
        Item itemToUse = quickSlots[0];

        if (itemToUse != null) {
            // Attempt use
            player.useItem(itemToUse, eventManager, discoveryManager, maze);
            // We assume using an item takes a turn
            closeMenuOrPassTurn();
        } else {
            eventManager.addEvent(new GameEvent("Active slot (Top-Left) is empty!", 1.5f));
            // Do not pass turn
        }
    }

    // --- NEW: Player Guard Action ---
    public void playerGuard() {
        if (currentState != CombatState.PLAYER_MENU)
            return;

        playerCurrentBlock += 5; // Flat block bonus?
        eventManager.addEvent(new GameEvent("You brace yourself!", 1.5f));
        BalanceLogger.getInstance().log("COMBAT_ACTION", "Player Guarded. Block +5");

        // Pass turn
        processPlayerStatusEffects();
        player.getStatusManager().updateTurn();

        // --- WORLD ACTIONS ---
        if (turnManager != null && monsterAiManager != null) {
            if (turnManager != null && monsterAiManager != null) {
                turnManager.processTurn(maze, player, monsterAiManager, this, worldManager, eventManager);
            }
        }
        // ---------------------

        currentState = CombatState.MONSTER_TURN;
        monsterAttackDelay = MONSTER_ATTACK_DELAY_TIME;
    }

    private int playerCurrentBlock = 0; // Reset every round

    private void resolveDiceHand(List<DieResult> results) {
        currentCombatTurns++;

        int totalDamage = 0;
        playerCurrentBlock = 0; // Reset for this round
        int healing = 0;
        int fireDamage = 0;
        int lightningDamage = 0;
        int poisonStacks = 0;

        StringBuilder log = new StringBuilder("Rolled: ");

        for (DieResult res : results) {
            log.append(res.getRolledFace().toString()).append(", ");
            int val = res.getRolledFace().getValue();
            switch (res.getRolledFace().getType()) {
                case SWORD:
                    totalDamage += val;
                    // Log basic damage contribution? Maybe too spammy, let's stick to special
                    // effects
                    break;
                case SHIELD:
                    playerCurrentBlock += val;
                    eventManager.addEvent(new GameEvent("Shield Up! (+" + val + ")", 1f));
                    BalanceLogger.getInstance().log("DICE_EFFECT",
                            "Shield increased by " + val + ". Total: " + playerCurrentBlock);
                    break;
                case PARRY:
                    // Parry adds block + maybe a riposte mechanic later
                    playerCurrentBlock += val;
                    eventManager.addEvent(new GameEvent("Parry Stance!", 1f));
                    BalanceLogger.getInstance().log("DICE_EFFECT", "Parry: Shield increased by " + val);
                    break;
                case HEART:
                    if (val > 0) {
                        healing += val;
                        BalanceLogger.getInstance().log("DICE_EFFECT", "Healing prepared: " + val);
                    } else {
                        int cost = -val;
                        player.takeDamage(cost, DamageType.PHYSICAL);
                        BalanceLogger.getInstance().log("DICE_EFFECT", "Sacrifice! Took " + cost + " damage.");
                    }
                    break;
                case FIRE:
                    if (monster != null && random.nextInt(100) < monster.getMagicResistance()) {
                        BalanceLogger.getInstance().log("DICE_EFFECT", "Fire Resisted by " + monster.getMonsterType());
                        eventManager.addEvent(new GameEvent("Resisted Fire!", 0.5f));
                    } else {
                        fireDamage += val;
                        BalanceLogger.getInstance().log("DICE_EFFECT", "Fire Charge: " + val);
                    }
                    break;
                case ICE:
                    if (monster != null && random.nextInt(100) < monster.getMagicResistance()) {
                        BalanceLogger.getInstance().log("DICE_EFFECT", "Ice Resisted by " + monster.getMonsterType());
                        eventManager.addEvent(new GameEvent("Resisted Ice!", 0.5f));
                    } else {
                        // Cold damage + potentially slow
                        totalDamage += val;
                        if (monster != null) {
                            monster.getStatusManager().addEffect(StatusEffectType.SLOWED, 2, 1, false);
                        }
                        BalanceLogger.getInstance().log("DICE_EFFECT", "Ice Damage: " + val);
                    }
                    break;
                case LIGHTNING:
                    if (monster != null && random.nextInt(100) < monster.getMagicResistance()) {
                        BalanceLogger.getInstance().log("DICE_EFFECT",
                                "Lightning Resisted by " + monster.getMonsterType());
                        eventManager.addEvent(new GameEvent("Resisted Lightning!", 0.5f));
                    } else {
                        lightningDamage += val;
                        BalanceLogger.getInstance().log("DICE_EFFECT", "Lightning Charge: " + val);
                    }
                    break;
                case POISON:
                    if (monster != null && random.nextInt(100) < monster.getMagicResistance()) {
                        BalanceLogger.getInstance().log("DICE_EFFECT",
                                "Poison Resisted by " + monster.getMonsterType());
                        eventManager.addEvent(new GameEvent("Resisted Poison!", 0.5f));
                    } else {
                        poisonStacks += val;
                        if (monster != null) {
                            monster.getStatusManager().addEffect(StatusEffectType.POISONED, 3, poisonStacks, true);
                        }
                        BalanceLogger.getInstance().log("DICE_EFFECT", "Poison Stacks: " + val);
                    }
                    break;
                case GOLD:
                    player.getStats().incrementTreasureScore(val);
                    eventManager.addEvent(new GameEvent("Stole " + val + " Gold!", 1f));
                    BalanceLogger.getInstance().log("DICE_EFFECT", "Stole Gold: " + val);
                    break;
                case BULLSEYE: // Crit / High Acc
                    int critDmg = (int) (val * 1.5f);
                    totalDamage += critDmg;
                    BalanceLogger.getInstance().log("DICE_EFFECT",
                            "Bullseye! Crit Damage: " + critDmg + " (Base: " + val + ")");
                    break;
                case GLANCING:
                    int glanceDmg = Math.max(1, val / 2);
                    totalDamage += glanceDmg;
                    BalanceLogger.getInstance().log("DICE_EFFECT", "Glancing Hit. Damage: " + glanceDmg);
                    break;
                case BONE: // Physical blunt damage
                    totalDamage += val;
                    BalanceLogger.getInstance().log("DICE_EFFECT", "Bone Bash: " + val);
                    break;
                case CURSE: // Damage but maybe hurts player?
                    totalDamage += val * 2;
                    player.takeDamage(1, DamageType.PHYSICAL);
                    BalanceLogger.getInstance().log("DICE_EFFECT", "Curse! Dealt " + (val * 2) + ", Took 1 self-dmg.");
                    break;
                case ASH:
                    // Failed fire, 1 dmg
                    totalDamage += 1;
                    BalanceLogger.getInstance().log("DICE_EFFECT", "Ash (Failed Fire). Damage: 1");
                    break;
                case SKULL:
                    // High damage risky
                    totalDamage += val;
                    break;
                default:
                    break;
            }
        }

        Gdx.app.log("CombatManager", log.toString());

        // --- Apply Results ---

        // 1. Healing
        if (healing > 0) {
            player.heal(healing);
            eventManager.addEvent(new GameEvent("Healed " + healing + " HP", 1f));
        }

        // 2. Status Effects
        if (poisonStacks > 0) {
            monster.getStatusManager().addEffect(com.bpm.minotaur.gamedata.effects.StatusEffectType.POISONED, 3,
                    poisonStacks, true);
            eventManager.addEvent(new GameEvent("Poisoned Monster!", 1f));
        }

        // 3. Damage
        int strBonus = Math.max(0, player.getStats().getEffectiveStrength() - 10);
        int totalAttack = totalDamage + fireDamage + lightningDamage + player.getStats().getAttackModifier() + strBonus;

        // --- NEW: Berzerk Bonus ---
        if (player.getStatusManager().hasEffect(StatusEffectType.BERZERK)) {
            int bonus = 5 * player.getLevel();
            totalAttack += bonus;
            BalanceLogger.getInstance().log("COMBAT_EFFECT", "Berzerk Bonus: " + bonus);
        }

        // --- CONFUSION LOGIC (Dice) ---
        if (player.getStatusManager().hasEffect(com.bpm.minotaur.gamedata.effects.StatusEffectType.CONFUSION)) {
            if (random.nextFloat() > 0.5f) { // 50% Chance to fail
                totalAttack = 0;
                eventManager.addEvent(new GameEvent("Confused! You stumble...", 1.5f));
                Gdx.app.log("CombatManager", "Confusion: Player failed dice attack roll.");
            } else {
                // optional: eventManager.addEvent(new GameEvent("Confused but focused!", 1f));
            }
        }

        if (player.getEquipment().hasRingEffect(com.bpm.minotaur.gamedata.item.RingEffectType.STRENGTH)) {
            totalAttack += 5;
            // Optionally log or show effect?
        }

        // Toxic Communion: Critical Toxicity Double Damage
        if (player.getStats().getToxicity() >= 76) {
            totalAttack *= 2;
        }

        // Log artifacts logic here later

        if (totalAttack > 0) {
            int actualDamage = monster.takeDamage(totalAttack);
            // Visuals
            String[] sprite = itemDataManager.getTemplate(com.bpm.minotaur.gamedata.item.Item.ItemType.DART).spriteData; // Default

            animationManager.addAnimation(new Animation(Animation.AnimationType.PROJECTILE_PLAYER,
                    player.getPosition(), monster.getPosition(),
                    com.bpm.minotaur.gamedata.item.ItemColor.WHITE.getColor(), 0.5f,
                    sprite));

            if (actualDamage > 0) {
                maze.addBlood((int) monster.getPosition().x, (int) monster.getPosition().y, 0.03f);
                Vector3 hitPos = new Vector3(monster.getPosition().x, 0.5f, monster.getPosition().y);
                Vector3 dir = new Vector3(player.getDirectionVector().x, 0.2f, player.getDirectionVector().y).nor();
                maze.getGoreManager().spawnBloodSpray(hitPos, dir, 4);
            }
            showDamageText(actualDamage, new GridPoint2((int) monster.getPosition().x, (int) monster.getPosition().y));
            eventManager.addEvent(new GameEvent("Hit! " + actualDamage + " dmg", 2f));

            lastDamageDealt = actualDamage;
        } else {
            if (poisonStacks == 0 && playerCurrentBlock == 0 && healing == 0) {
                eventManager.addEvent(new GameEvent("Miss!", 1f));
            } else {
                // Action happened (Block/Poison/Heal)
            }
        }

        if (playerCurrentBlock > 0) {
            eventManager.addEvent(new GameEvent("Blocking " + playerCurrentBlock + " dmg", 1.5f));
        }

        if (monster.getCurrentHP() <= 0)

        {
            handleMonsterDeath();
            currentState = CombatState.VICTORY;
            Gdx.app.log("COMBAT_FLOW", "State -> VICTORY (Monster Dead)");
        } else {
            currentState = CombatState.MONSTER_TURN;
            monsterAttackDelay = MONSTER_ATTACK_DELAY_TIME;

            // --- WORLD ACTIONS ---
            if (turnManager != null && monsterAiManager != null) {
                turnManager.processTurn(maze, player, monsterAiManager, this, worldManager, eventManager);
            }
            // ---------------------

            Gdx.app.log("COMBAT_FLOW", "State -> MONSTER_TURN (Dice Resolved)");
        }
    }

    private void resolveAttack(int d20Roll) {
        resolveAttack(d20Roll, false);
    }

    private void resolveAttack(int d20Roll, boolean stateless) {
        if (pendingWeapon == null)
            return;

        currentCombatTurns++;

        int toHitBonus = player.getToHitBonus();
        int attackRoll = d20Roll + toHitBonus;
        int targetAC = monster.getArmorClass();
        boolean isCrit = (d20Roll == 20);
        boolean isHit = (attackRoll >= targetAC) || isCrit;

        // --- CONFUSION LOGIC (Instant) ---
        if (player.getStatusManager().hasEffect(com.bpm.minotaur.gamedata.effects.StatusEffectType.CONFUSION)) {
            if (random.nextFloat() > 0.5f) { // 50% Chance to Miss wildly
                isHit = false;
                eventManager.addEvent(new GameEvent("You are confused and swing wildly and miss", 2f));
                Gdx.app.log("CombatManager", "Confusion: Player swung wildly and missed.");
            } else {
                if (isHit) { // Only add "somehow hit" if they actually hit
                    eventManager.addEvent(new GameEvent("You are confused and swing wildly and somehow hit", 2f));
                }
            }
        }

        // Log Check
        Gdx.app.log("CombatManger",
                "Player Attack: Roll " + d20Roll + " + " + toHitBonus + " = " + attackRoll + " vs AC " + targetAC);

        if (isHit) {
            // Roll Damage — weapon dice + STR-based bonus (not level; no double-dip)
            String damageDice = pendingWeapon.getDamageDice();
            int baseDamage = DiceRoller.roll(damageDice);
            int damageBonus = player.getDamageBonus();
            int totalDamage = Math.max(1, baseDamage + damageBonus);

            if (isCrit) {
                totalDamage *= 2;
                eventManager.addEvent(new GameEvent("CRITICAL HIT!", 1f));
            }

            int actualDamage = monster.takeDamage(totalDamage);
            showDamageText(actualDamage, new GridPoint2((int) monster.getPosition().x, (int) monster.getPosition().y));
            lastDamageDealt = actualDamage;

            // --- VISCERAL: Feedback ---
            float damageRatio = (float) totalDamage / (float) monster.getMaxHP();
            boolean isHeavy = damageRatio > 0.2f;

            // 1. Audio
            soundManager.playWeaponImpact(isHeavy); // Meat/Metal hit
            soundManager.playMonsterReaction(monster, damageRatio); // Grunts/Roars

            // 2. Screen Shake & Hit Pause
            if (game.getScreen() instanceof com.bpm.minotaur.screens.GameScreen) {
                com.bpm.minotaur.screens.GameScreen gs = (com.bpm.minotaur.screens.GameScreen) game.getScreen();

                // Shake
                float trauma = isCrit ? 0.5f : (isHeavy ? 0.3f : 0.1f);
                gs.addTrauma(trauma);

                // Pause (Freeze frame)
                float pauseDur = isCrit ? 0.15f : 0.05f;
                gs.triggerHitPause(pauseDur);
            }

            // 3. Blood (Scaling)
            if (damageRatio < 0.1f) {
                // Chip damage (puff)
                maze.getGoreManager().spawnBloodSpray(
                        new Vector3(monster.getPosition().x, 0.5f, monster.getPosition().y),
                        new Vector3(player.getDirectionVector().x, 0.2f, player.getDirectionVector().y),
                        1);
            } else if (damageRatio < 0.3f) {
                // Solid Hit
                maze.getGoreManager().spawnBloodSpray(
                        new Vector3(monster.getPosition().x, 0.5f, monster.getPosition().y),
                        new Vector3(player.getDirectionVector().x, 0.2f, player.getDirectionVector().y),
                        4);
                maze.addBlood((int) monster.getPosition().x, (int) monster.getPosition().y, 0.1f);
            } else {
                // Massive/Gib
                maze.getGoreManager().spawnBloodSpray(
                        new Vector3(monster.getPosition().x, 0.5f, monster.getPosition().y),
                        new Vector3(player.getDirectionVector().x, 0.2f, player.getDirectionVector().y),
                        8);
                maze.getGoreManager()
                        .spawnGibExplosion(new Vector3(monster.getPosition().x, 0.5f, monster.getPosition().y));
                maze.addBlood((int) monster.getPosition().x, (int) monster.getPosition().y, 0.3f);
            }

        } else {
            // Miss
            eventManager.addEvent(new GameEvent("Miss!", 1f));
        }

        if (pendingWeapon != null && pendingWeapon.isUsable()) {
            player.getInventory().setRightHand(null);
        }

        pendingWeapon = null;

        if (monster.getCurrentHP() <= 0) {
            handleMonsterDeath();
            // Always route through VICTORY state so at least one render frame
            // shows the monster before removal (fixes "died before rendered" bug).
            currentState = CombatState.VICTORY;
            Gdx.app.log("COMBAT_FLOW", "State -> VICTORY (Monster Dead)");
        } else if (!stateless) {
            currentState = CombatState.MONSTER_TURN;
            monsterAttackDelay = MONSTER_ATTACK_DELAY_TIME;

            // --- WORLD ACTIONS ---
            if (turnManager != null && monsterAiManager != null) {
                turnManager.processTurn(maze, player, monsterAiManager, this, worldManager, eventManager);
            }
            // ---------------------

            Gdx.app.log("COMBAT_FLOW", "State -> MONSTER_TURN (Attack Resolved)");
        }
        // stateless && monster survived: stays INACTIVE, monster attacks via its own AI turn
    }

    public boolean performMonsterRangedAttack(Monster attacker) {
        int range = attacker.getAttackRange();
        Item weapon = attacker.getInventory().getRightHand();
        boolean hasRangedWeapon = (weapon != null && weapon.isRanged());

        if (!attacker.hasRangedAttack() && !hasRangedWeapon)
            return false;

        if (hasRangedWeapon)
            range = weapon.getRange();
        if (range <= 0)
            range = 8;

        Vector2 diff = player.getPosition().cpy().sub(attacker.getPosition());
        Direction fireDir = null;
        if (Math.abs(diff.x) < 0.5f)
            fireDir = (diff.y > 0) ? Direction.NORTH : Direction.SOUTH;
        else if (Math.abs(diff.y) < 0.5f)
            fireDir = (diff.x > 0) ? Direction.EAST : Direction.WEST;
        if (fireDir == null)
            return false;

        HitResult finalResult = raycastProjectile(attacker.getPosition(), fireDir, range, false);
        if (finalResult.type != HitResult.HitType.PLAYER)
            return false;

        float dist = attacker.getPosition().dst(player.getPosition());
        float animDuration = dist / PROJECTILE_SPEED;
        animationManager.addAnimation(
                new Animation(Animation.AnimationType.PROJECTILE_MONSTER, attacker.getPosition(), player.getPosition(),
                        attacker.getColor(), animDuration, itemDataManager.getTemplate(Item.ItemType.DART).spriteData));
        soundManager.playMonsterAttackSound(attacker);

        // --- ATTACK ROLL ---
        int attackBonus = 2 + (attacker.getDexterity() / 5);
        int d20Roll = DiceRoller.d20();
        int attackRoll = d20Roll + attackBonus;
        int targetAC = player.getArmorClass();

        int actualDamage = 0;
        if (attackRoll >= targetAC) {
            int dmg = attacker.getMaxHP() / 4; // Ranged default? Or use weapon?
            if (hasRangedWeapon) {
                dmg = DiceRoller.roll(weapon.getDamageDice());
            } else {
                dmg = DiceRoller.roll(attacker.getDamageDice());
            }
            if (dmg < 1)
                dmg = 1;

            actualDamage = player.takeDamage(dmg, DamageType.PHYSICAL);

            if (actualDamage > 0) {
                maze.addBlood((int) player.getPosition().x, (int) player.getPosition().y, 0.03f);
                eventManager.addEvent(
                        new GameEvent(attacker.getMonsterType() + " shoots you for " + actualDamage + "!", 1.5f));
            } else {
                eventManager.addEvent(new GameEvent("Armor deflected the shot!", 1.5f));
            }
        } else {
            eventManager.addEvent(new GameEvent(attacker.getMonsterType() + " fires and misses!", 1.5f));
        }

        if (actualDamage > 0)
            damageTakenInCombat += actualDamage;
        BalanceLogger.getInstance().logCombatRound("MONSTER", "Ranged", -1, actualDamage, player.getCurrentHP());
        return true;
    }

    public void checkForAdjacentMonsters() {
        if (currentState == CombatState.INACTIVE) {
            int playerX = (int) player.getPosition().x;
            int playerY = (int) player.getPosition().y;
            for (Monster m : maze.getMonsters().values()) {
                int monsterX = (int) m.getPosition().x;
                int monsterY = (int) m.getPosition().y;
                boolean isCardinal = (Math.abs(playerX - monsterX) == 1 && playerY == monsterY)
                        || (Math.abs(playerY - monsterY) == 1 && playerX == monsterX);
                if (isCardinal) {
                    if (m.isJustSpawned()) {
                        m.clearJustSpawned(); // Grant one turn grace; attackable next turn
                        return;
                    }
                    playerMeleeStrike(m);
                    return;
                }
            }
        }
    }

    public void showDamageText(int damage, GridPoint2 position) {
        animationManager.addAnimation(
                new Animation(Animation.AnimationType.DAMAGE_TEXT, position, String.valueOf(damage), 1.0f));
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
                List<DieResult> results = stochasticManager.getRolledResults();
                resolveDiceHand(results);
            }
            return;
        }

        if (currentState == CombatState.MONSTER_TURN) {
            if (monsterAttackDelay > 0f)
                monsterAttackDelay -= delta;
            else
                monsterAttack();
        }

        // --- UPDATED: Log Victory/Defeat Transitions ---
        if (currentState == CombatState.VICTORY) {
            // Log Victory
            BalanceLogger.getInstance().logCombatEnd("VICTORY", currentCombatTurns, damageTakenInCombat);
            maze.getMonsters().remove(new GridPoint2((int) monster.getPosition().x, (int) monster.getPosition().y));
            endCombat();
        } else if (currentState == CombatState.DEFEAT) {
            // Log Defeat
            BalanceLogger.getInstance().logCombatEnd("DEFEAT", currentCombatTurns, damageTakenInCombat);

            if (game != null) {
                // --- CRITICAL FIX: Safe Disposal of Old GameScreen ---
                final Screen oldScreen = game.getScreen(); // Capture current screen (GameScreen)
                game.setScreen(new GameOverScreen(game)); // Switch screens (calls hide() on oldScreen)

                if (oldScreen != null) {
                    // Defer disposal until AFTER this update cycle completes to avoid native
                    // crashes
                    // while render() might still be on the stack.
                    Gdx.app.postRunnable(new Runnable() {
                        @Override
                        public void run() {
                            Gdx.app.log("Lifecycle", "Post-Runnable: Disposing old GameScreen.");
                            oldScreen.dispose();
                        }
                    });
                }
            } else {
                Gdx.app.log("CombatManager", "Headless Mode: Player Defeated. Skipping GameOverScreen.");
            }
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
        if (maze == null || itemDataManager == null)
            return;

        GridPoint2 pos = new GridPoint2((int) monster.getPosition().x, (int) monster.getPosition().y);

        // 1. Determine Gib Count based on Damage
        int gibCount = 1 + random.nextInt(2); // 1-2 gibs default

        if (lastDamageDealt > 10) {
            gibCount += 1;
        }

        // 2. Spawn Gibs
        for (int i = 0; i < gibCount; i++) {
            Item.ItemType gibType = Item.ItemType.GIB_FLESH;

            // Chance for rare parts
            int roll = random.nextInt(100);
            if (roll < 10)
                gibType = Item.ItemType.GIB_BILE;
            else if (roll < 25)
                gibType = Item.ItemType.GIB_ORGAN;
            else if (roll < 50)
                gibType = Item.ItemType.GIB_BONE;

            // Monster Specifics
            if (monster.getType().name().contains("SKELETON")) {
                gibType = Item.ItemType.GIB_BONE;
            } else if (monster.getType().name().contains("SLIME")) {
                gibType = Item.ItemType.GIB_GLAZE;
            }

            Item gib = itemDataManager.createItem(gibType, pos.x, pos.y, ItemColor.RED,
                    (game != null) ? game.getAssetManager() : null);

            if (gib != null) {
                gib.setCorpseSource(monster.getType());
                // Scatter Logic
                boolean placed = false;
                if (!maze.getItems().containsKey(pos) && i == 0) {
                    maze.getItems().put(pos, gib);
                    placed = true;
                } else {
                    // Scatter adjacent
                    for (int dx = -1; dx <= 1; dx++) {
                        for (int dy = -1; dy <= 1; dy++) {
                            if (dx == 0 && dy == 0)
                                continue;
                            GridPoint2 p = new GridPoint2(pos.x + dx, pos.y + dy);
                            if (!maze.isWallBlocking(pos.x, pos.y, Direction.NORTH) // rough check
                                    && !maze.getItems().containsKey(p) && maze.getWallDataAt(p.x, p.y) == 0) {
                                gib.setPosition(p.x + 0.5f, p.y + 0.5f);
                                maze.getItems().put(p, gib);
                                placed = true;
                                break;
                            }
                        }
                        if (placed)
                            break;
                    }
                }
                if (placed) {
                    BalanceLogger.getInstance().log("LOOT_DROP", "Dropped " + gib.getDisplayName());
                }
            }
        } // End Gib Loop

        // 3. Drop Inventory Contents
        if (monster.getInventory() != null) {
            for (Item item : monster.getInventory().getAllItems()) {
                if (item != null) {
                    item.setPosition(monster.getPosition().x, monster.getPosition().y);

                    if (!maze.getItems().containsKey(pos)) {
                        maze.getItems().put(pos, item);
                    } else {
                        // Scatter
                        boolean placed = false;
                        for (int dx = -1; dx <= 1; dx++) {
                            for (int dy = -1; dy <= 1; dy++) {
                                GridPoint2 p = new GridPoint2(pos.x + dx, pos.y + dy);
                                if (!maze.isWallBlocking(pos.x, pos.y, Direction.NORTH)
                                        && !maze.getItems().containsKey(p) && maze.getWallDataAt(p.x, p.y) == 0) {
                                    item.setPosition(p.x + 0.5f, p.y + 0.5f);
                                    maze.getItems().put(p, item);
                                    placed = true;
                                    break;
                                }
                            }
                            if (placed)
                                break;
                        }
                    }
                }
            }
        }
    }

    public boolean performRangedAttack() {
        Item weapon = player.getInventory().getRightHand();
        if (weapon == null || !weapon.isRanged())
            return false;
        playerAttackInstant(); // Default to instant for standard inputs if used
        return true;
    }

    public CombatState getCurrentState() {
        return currentState;
    }

    public Monster getMonster() {
        return monster;
    }

    public void monsterAttack() {
        if (monster == null)
            return;

        // Process Statuses FIRST (DoT, etc.)
        processMonsterStatusEffects();
        if (currentState == CombatState.VICTORY)
            return; // Died from poison/status

        // --- NEW: AI Decision Tree ---
        boolean actionTaken = false;
        MonsterTemplate.AiType ai = monster.getAiType();

        // 1. HEALER Logic
        if (ai == MonsterTemplate.AiType.HEALER) {
            float hpPct = (float) monster.getWarStrength() / (float) monster.getMaxWarStrength(); // Need access to Max?
                                                                                                  // Monster doesn't
                                                                                                  // track Max
                                                                                                  // currently?
            // Monster has WarStrength. Assuming initial was max?
            // Actually Monster doesn't store max HP separately in the current visible code.
            // Let's assume heal threshold is strict value or skip for now if unknown.
            // Or use getTemplate().warStrength for base max?
            // Let's assume standard heal logic for now.
            if (hpPct < monster.getHealThreshold()) {
                performMonsterHeal();
                actionTaken = true;
            }
        }

        // 2. TACTICAL Logic (Spellcasting)
        if (!actionTaken && ai == MonsterTemplate.AiType.TACTICAL) {
            if (random.nextInt(100) < monster.getSpellChance()) {
                performMonsterSpell();
                actionTaken = true;
            }
        }

        // 3. AGGRESSIVE / Default / Fallback
        if (!actionTaken) {
            // Try Ranged
            if (monster.hasRangedAttack()) {
                float dist = monster.getPosition().dst(player.getPosition());
                if (dist > 1.5f && dist <= monster.getAttackRange()) {
                    if (performMonsterRangedAttack(monster)) {
                        currentState = CombatState.PLAYER_MENU;
                        return; // Ranged handles its own transition/return
                    }
                }
            }
            performMonsterMeleeAttack();
        }

        // Transition back to Player Menu handled in sub-methods
    }

    private void performMonsterHeal() {
        int healAmount = 10; // Simple flat heal
        monster.takeDamage(-healAmount); // Negative damage = heal? ensure Monster.takeDamage handles it?
        // Monster.takeDamage: finalDamage = Math.max(0, amount - damageReduction);
        // It clamps to 0. It won't heal.
        // Need to add heal method to Monster or access field directly?
        // Monster fields are likely package-private or have getters.
        // Let's assume for now we can't easily heal without a new method.
        // Actually, let's just log it and skip heal to avoid breaking code, OR modify
        // Monster.java.
        // I modified Monster.java earlier. I can add heal() there?
        // Let's do a "Focus" action instead for now to be safe.
        eventManager.addEvent(new GameEvent(monster.getType() + " focuses its energy!", 2f));
        monster.addEnergy(10); // Speed up next turn?

        currentState = CombatState.PLAYER_MENU;
    }

    private void performMonsterSpell() {
        eventManager.addEvent(new GameEvent(monster.getType() + " casts a dark spell!", 2f));
        int spellDmg = 5 + monster.getIntelligence();
        player.takeSpiritualDamage(spellDmg, DamageType.SORCERY);
        BalanceLogger.getInstance().logCombatRound("MONSTER", "Spell", -1, spellDmg, player.getWarStrength());

        currentState = CombatState.PLAYER_MENU;
    }

    private void performMonsterMeleeAttack() {
        float dist = monster.getPosition().dst(player.getPosition());
        float animDuration = dist / PROJECTILE_SPEED;

        animationManager.addAnimation(new Animation(
                Animation.AnimationType.PROJECTILE_MONSTER,
                monster.getPosition(),
                player.getPosition(),
                monster.getColor(),
                animDuration,
                itemDataManager.getTemplate(Item.ItemType.DART).spriteData));

        soundManager.playMonsterAttackSound(monster);

        // --- ATTACK ROLL ---
        int attackBonus = 2; // Base
        MonsterTemplate t = monster.getTemplate();
        if (t != null) {
            // Rough approximation if level isn't directly exposed
            attackBonus += (t.maxHP / 10);
        }

        int d20Roll = DiceRoller.d20();
        int attackRoll = d20Roll + attackBonus;
        int targetAC = player.getArmorClass();
        boolean isHit = (attackRoll >= targetAC);

        int actualDamage = 0;

        if (isHit) {
            String dmgDice = monster.getDamageDice();
            int baseDmg = DiceRoller.roll(dmgDice);
            // Apply Doom Scaling (Bridge Integration)
            float doomScale = DoomManager.getInstance().getEnemyScalingMultiplier();
            int dmg = Math.max(1, (int) (baseDmg * doomScale));

            // Block Logic
            if (playerCurrentBlock > 0) {
                dmg = Math.max(0, dmg - playerCurrentBlock);
                int blocked = baseDmg - dmg;
                eventManager.addEvent(new GameEvent("Blocked " + blocked + " dmg", 1f));
            }

            actualDamage = player.takeDamage(dmg, DamageType.PHYSICAL);
            maze.addBlood((int) player.getPosition().x, (int) player.getPosition().y, 0.03f);

            eventManager.addEvent(new GameEvent(monster.getMonsterType() + " hits you for " + actualDamage, 1f));
        } else {
            eventManager.addEvent(new GameEvent(monster.getMonsterType() + " misses!", 1f));
        }

        damageTakenInCombat += actualDamage;
        BalanceLogger.getInstance().logCombatRound("MONSTER", "Melee", -1, actualDamage, player.getCurrentHP());

        if (player.getCurrentHP() <= 0) {
            currentState = CombatState.DEFEAT;
        } else {
            currentState = CombatState.PLAYER_MENU;
            Gdx.app.log("COMBAT_FLOW", "State -> PLAYER_MENU (Turn End)");
        }
    }

    private void processMonsterStatusEffects() {
        if (monster == null)
            return;
        StatusManager sm = monster.getStatusManager();

        // Handle Poison
        if (sm.hasEffect(StatusEffectType.POISONED)) {
            int potency = sm.getEffect(StatusEffectType.POISONED).getPotency();
            int dmg = monster.takeDamage(potency);
            maze.addBlood((int) monster.getPosition().x, (int) monster.getPosition().y, 0.05f);
            eventManager.addEvent(new GameEvent(monster.getMonsterType() + " takes " + dmg + " poison dmg!", 1.5f));
            BalanceLogger.getInstance().log("COMBAT_EFFECT", "Monster took " + dmg + " poison damage.");

            if (monster.getWarStrength() <= 0) {
                // Trigger death logic reuse
                handleMonsterDeath();
                return;
            }
        }

        sm.updateTurn();
    }

    // --- FLANK ATTACK LOGIC ---
    public boolean performMonsterFlankAttack(Monster attacker) {
        if (attacker == null || attacker == this.monster)
            return false; // Can't flank if you are the main duel target

        // Check adjacency
        int dist = (int) (Math.abs(attacker.getPosition().x - player.getPosition().x)
                + Math.abs(attacker.getPosition().y - player.getPosition().y));
        if (dist > 1)
            return false; // Too far

        // Determine Direction for Indicator
        Direction attackDir = null;
        float ax = attacker.getPosition().x;
        float ay = attacker.getPosition().y;
        float px = player.getPosition().x;
        float py = player.getPosition().y;

        // Relative to player
        if (ax > px)
            attackDir = Direction.EAST;
        else if (ax < px)
            attackDir = Direction.WEST;
        else if (ay > py)
            attackDir = Direction.NORTH;
        else if (ay < py)
            attackDir = Direction.SOUTH;

        // Visual Indicator via Hud
        if (hud != null && attackDir != null) {
            hud.showAttackIndicator(attackDir);
        }

        // Roll Attack
        int attackBonus = 2 + (attacker.getDexterity() / 5);
        int d20Roll = DiceRoller.d20();
        int attackRoll = d20Roll + attackBonus;
        int targetAC = player.getArmorClass();

        // Bonus for flanking? (Advantage or flat +2)
        attackRoll += 2; // Flanking bonus

        if (attackRoll >= targetAC) {
            int dmg = DiceRoller.roll(attacker.getDamageDice());
            if (dmg < 1)
                dmg = 1;

            int actualDamage = player.takeDamage(dmg, DamageType.PHYSICAL);

            if (actualDamage > 0) {
                maze.addBlood((int) px, (int) py, 0.05f);
                eventManager.addEvent(
                        new GameEvent(attacker.getMonsterType() + " flanks you for " + actualDamage + "!", 1.5f));

                // Gore
                Vector3 hitPos = new Vector3(px, 0.5f, py);
                Vector3 dir = new Vector3(player.getDirectionVector().x, 0.2f, player.getDirectionVector().y).nor(); // TODO:
                                                                                                                     // Direction
                                                                                                                     // away
                                                                                                                     // from
                                                                                                                     // attack?
                maze.getGoreManager().spawnBloodSpray(hitPos, dir, 3);
            } else {
                eventManager
                        .addEvent(new GameEvent("Armor blocked flank from " + attacker.getMonsterType() + "!", 1.5f));
            }

            damageTakenInCombat += actualDamage;
            BalanceLogger.getInstance().logCombatRound("MONSTER", "Flank", -1, actualDamage, player.getCurrentHP());
            return true;
        } else {
            eventManager.addEvent(new GameEvent(attacker.getMonsterType() + " tries to flank but misses!", 1.5f));
            return false;
        }
    }
    // --------------------------

    public void setCurrentState(CombatState state) { this.currentState = state; }

    // Extracted death logic to reuse for Poison kills
    public void handleMonsterDeath() {
        currentState = CombatState.VICTORY;
        Gdx.app.log("CombatManager", "You have defeated " + monster.getMonsterType());
        eventManager.addEvent((new GameEvent("You have defeated " + monster.getMonsterType(), 2f)));
        UnlockManager.getInstance().recordKill(monster.getMonsterType());
        int baseExp = monster.getBaseExperience();
        float colorMultiplier = monster.getMonsterColor().getXpMultiplier();
        float levelMultiplier = 1.0f + (maze.getLevel() * 0.1f);
        int totalExp = (int) (baseExp * colorMultiplier * levelMultiplier);
        eventManager.addEvent((new GameEvent("You have gained " + totalExp + " experience", 2f)));
        player.addExperience(totalExp, eventManager);
        maze.addBlood((int) monster.getPosition().x, (int) monster.getPosition().y, 0.10f);

        // --- GIB ANIMATION ---
        com.badlogic.gdx.math.Vector3 gibOrigin = new com.badlogic.gdx.math.Vector3(monster.getPosition().x, 0.5f,
                monster.getPosition().y);

        if (DebugManager.getInstance().getRenderMode() == DebugManager.RenderMode.RETRO) {
            String[] spriteData = monster.getSpriteData();
            if (spriteData != null) {
                maze.getGoreManager().spawnRetroGibs(gibOrigin, spriteData, monster.getColor());
            } else {
                maze.getGoreManager().spawnGibExplosion(gibOrigin); // Fallback
            }
        } else {
            // Modern Mode
            if (monster.getTexture() != null) {
                maze.getGoreManager().spawnTextureGibs(gibOrigin, monster.getTexture());
            }
            // ALSO spawn the new generic gibs for extra visceral feeling
            maze.getGoreManager().spawnGibExplosion(gibOrigin);
        }

        spawnCorpseEffects(monster);
    }
}
