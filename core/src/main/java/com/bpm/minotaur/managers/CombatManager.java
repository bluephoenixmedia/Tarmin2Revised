package com.bpm.minotaur.managers;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.GridPoint2;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.Screen;
import com.bpm.minotaur.Tarmin2;
import com.bpm.minotaur.gamedata.*;
import com.bpm.minotaur.gamedata.gore.GoreProfile;
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
import com.bpm.minotaur.screens.GameScreen;
import com.bpm.minotaur.gamedata.injury.InjuryRecord;
import com.bpm.minotaur.gamedata.monster.GhostPlayerMonster;
import com.bpm.minotaur.gamedata.bones.BonesData;

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

    public AnimationManager getAnimationManager() {
        return animationManager;
    }

    public SoundManager getSoundManager() {
        return soundManager;
    }

    public GameScreen getGameScreen() {
        if (game != null && game.getScreen() instanceof GameScreen) {
            return (GameScreen) game.getScreen();
        }
        return null;
    }

    private float monsterAttackDelay = 0f;
    private static final float MONSTER_ATTACK_DELAY_TIME = 0.3f;

    private static final float PROJECTILE_SPEED = 15.0f;
    private final ItemDataManager itemDataManager;

    private int lastDamageDealt = 0;

    private Item pendingWeapon;
    private boolean pendingIsRanged;
    private com.bpm.minotaur.rendering.animation.CombatMotionProfile currentMotionProfile;

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

    // --- Active Parrying / Guard Stance (right-click hold) ---
    private boolean playerGuarding = false;
    private long guardStartTimeMillis = 0L;
    private static final long PERFECT_PARRY_WINDOW_MS = 200L;

    public void setPlayerGuardStance(boolean guarding) {
        if (guarding && !this.playerGuarding) {
            this.guardStartTimeMillis = System.currentTimeMillis();
        }
        this.playerGuarding = guarding;
    }

    public boolean isPlayerGuarding() {
        return playerGuarding;
    }

    /** Applies active-parry mitigation: full negation on perfect timing, else flat DR. */
    private int applyGuardMitigation(int dmg) {
        if (!playerGuarding || dmg <= 0) return dmg;

        long elapsed = System.currentTimeMillis() - guardStartTimeMillis;
        if (elapsed <= PERFECT_PARRY_WINDOW_MS) {
            eventManager.addEvent(new GameEvent("PERFECT PARRY! You negate the attack entirely!", 1.5f));
            return 0;
        }

        Item offHand = player.getInventory().getLeftHand();
        boolean shieldRaised = offHand != null && offHand.isShield();
        int dr = shieldRaised ? 4 : 2;
        int mitigated = Math.max(0, dmg - dr);
        if (mitigated < dmg) {
            eventManager.addEvent(new GameEvent("Guard stance absorbs " + (dmg - mitigated) + " dmg!", 1.2f));
        }
        return mitigated;
    }

    // --- Twitchy Monster Attack Indicator (Component 5) ---
    public enum AttackIndicatorVariant { EYE_FLARE_LUNGE, RETRO_AURA, SCREEN_SLASH }

    private Monster attackIndicatorMonster;
    private AttackIndicatorVariant attackIndicatorVariant;
    private float attackIndicatorElapsed = 0f;
    private float attackIndicatorDuration = 0.1f;

    /** Fires an ultra-fast (0.08-0.12s) randomized visual telegraph for a monster's melee attack. */
    private void triggerAttackIndicator(Monster attacker) {
        if (attacker == null) return;
        AttackIndicatorVariant[] variants = AttackIndicatorVariant.values();
        attackIndicatorMonster = attacker;
        attackIndicatorVariant = variants[random.nextInt(variants.length)];
        attackIndicatorElapsed = 0f;
        attackIndicatorDuration = 0.08f + random.nextFloat() * 0.04f;
    }

    public Monster getAttackIndicatorMonster() {
        return (attackIndicatorMonster != null && attackIndicatorElapsed < attackIndicatorDuration) ? attackIndicatorMonster : null;
    }

    public AttackIndicatorVariant getAttackIndicatorVariant() {
        return attackIndicatorVariant;
    }

    /** 0 = just triggered, 1 = fully elapsed. */
    public float getAttackIndicatorProgress() {
        if (attackIndicatorDuration <= 0f) return 1f;
        return Math.min(1f, attackIndicatorElapsed / attackIndicatorDuration);
    }

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
        if (currentState != CombatState.INACTIVE || target == null) return;

        int px = (int) player.getPosition().x, py = (int) player.getPosition().y;
        int mx = (int) target.getPosition().x, my = (int) target.getPosition().y;
        if (Math.abs(px - mx) + Math.abs(py - my) > 1) {
            // Melee capped to 1 tile
            return;
        }

        Item weapon = player.getInventory().getRightHand();
        if (weapon != null && weapon.isTwoHanded() && !player.canWieldTwoHanded()) {
            eventManager.addEvent(new GameEvent("Your fractured arm cannot support a two-handed weapon!", 2.0f));
            return;
        }
        if (weapon != null && weapon.isRanged()) {
            eventManager.addEvent(new GameEvent("Cannot melee with a ranged weapon.", 1.5f));
            return;
        }

        // Only reset counters and log a fresh COMBAT_START when this is actually a
        // new encounter. The stateless instant-attack flow never leaves INACTIVE
        // while the monster survives (see the "stays INACTIVE" comment in
        // resolveAttack()), so without this check every subsequent swing against
        // the SAME monster would re-enter this INACTIVE guard and wipe the turn
        // and damage counters mid-fight, making COMBAT_END always report 0/0.
        boolean isNewEncounter = (this.monster != target);
        this.monster = target;
        if (isNewEncounter) {
            this.currentCombatTurns = 0;
            this.damageTakenInCombat = 0;
            BalanceLogger.getInstance().logCombatStart(player, target);
        }
        this.currentCombatTurns++;

        if (target.getStatusManager() != null && eventManager != null)
            target.getStatusManager().initialize(eventManager, target);
        Direction dir = null;
        if (mx > px) dir = Direction.EAST;
        else if (mx < px) dir = Direction.WEST;
        else if (my > py) dir = Direction.NORTH;
        else if (my < py) dir = Direction.SOUTH;
        if (dir != null && player.getFacing() != dir) player.setFacing(dir);

        this.pendingWeapon = weapon;
        soundManager.playWeaponSwing();
        if (weapon != null && game != null && game.getScreen() instanceof com.bpm.minotaur.screens.GameScreen) {
            com.bpm.minotaur.screens.GameScreen gs = (com.bpm.minotaur.screens.GameScreen) game.getScreen();
            gs.getWeaponOverlay().triggerAttack(weapon);
            gs.getWeaponOverlay().setHitFrameCallback(profile -> {
                this.currentMotionProfile = profile;
                resolveAttack(DiceRoller.d20(), true);
                this.currentMotionProfile = null;
            });
        } else {
            resolveAttack(DiceRoller.d20(), true);
        }
    }

    public void monsterMeleeStrike(Monster attacker) {
        soundManager.playMonsterAttackSound(attacker);
        triggerAttackIndicator(attacker);

        int attackBonus = calculateMonsterAttackBonus(attacker);

        int d20Roll = DiceRoller.d20();
        boolean isHit = (d20Roll + attackBonus) >= player.getArmorClass();

        int actualDamage = 0;
        if (isHit) {
            int baseDmg = DiceRoller.roll(attacker.getDamageDice());
            float doomScale = DoomManager.getInstance().getEnemyScalingMultiplier();
            int dmg = Math.max(1, (int) (baseDmg * doomScale));
            if (playerCurrentBlock > 0) {
                int blocked = Math.min(dmg, playerCurrentBlock);
                dmg = Math.max(0, dmg - playerCurrentBlock);
                eventManager.addEvent(new GameEvent("Blocked " + blocked + " dmg", 1f));
                com.bpm.minotaur.telemetry.TelemetryManager.getInstance().recordDamageMitigated(blocked);
            }
            dmg = applyGuardMitigation(dmg);
            actualDamage = player.takeDamage(dmg, DamageType.PHYSICAL);
            showPlayerDamageText(actualDamage);
            bleedPlayer(actualDamage);
            com.bpm.minotaur.telemetry.TelemetryManager.getInstance().recordDamageTaken(actualDamage);
            com.bpm.minotaur.telemetry.TelemetryManager.getInstance().setLastDamageSource(attacker.getMonsterType());
            maze.addBlood((int) player.getPosition().x, (int) player.getPosition().y, 0.03f);
            eventManager.addEvent(new GameEvent(attacker.getMonsterType() + " hits you for " + actualDamage, 1f));

            // --- Anatomical Trauma Infliction ---
            // Gated twice over: the blow must be genuinely traumatic (see
            // InjuryManager.isTraumaticHit -- both a large share of max HP and an
            // absolute damage floor) and then pass a chance roll. Crits get a
            // better roll. Without both gates a 14 HP starting character picks up
            // a permanent wound from nearly every goblin swing.
            if (actualDamage > 0 && player.getInjuryManager() != null) {
                int maxHp = player.getStats().getMaxHP();
                boolean isCrit = (d20Roll == 20);
                DamageType dmgType = DamageType.PHYSICAL;
                String mType = (attacker.getMonsterType() != null) ? attacker.getMonsterType().toUpperCase() : "";
                if (mType.contains("FIRE") || mType.contains("DRAGON") || mType.contains("DEMON")) {
                    dmgType = DamageType.FIRE;
                } else if (mType.contains("SNAKE") || mType.contains("SPIDER") || mType.contains("SCORPION")) {
                    dmgType = DamageType.POISON;
                } else if (mType.contains("WRAITH") || mType.contains("LICH") || mType.contains("GHOST")) {
                    dmgType = DamageType.MAGICAL;
                }

                InjuryRecord inj = player.getInjuryManager().rollForInjury(dmgType, actualDamage, maxHp, isCrit);
                if (inj != null) {
                    com.bpm.minotaur.telemetry.TelemetryManager.getInstance().recordInjurySustained();
                    eventManager.addEvent(new GameEvent("CRITICAL TRAUMA! Your " + inj.getBodyPart().getDisplayName() + " suffered a " + inj.getInjuryType().getDisplayName() + "!", 3.0f));
                }
            }

            // --- Caves of Qud Metabolic Triggers on Hit ---
            if (player.getStatusManager() != null && actualDamage > 0) {
                // 1. Carapace Hardening
                if (player.getStatusManager().hasEffect(StatusEffectType.CARAPACE_HARDENING) && actualDamage >= 5) {
                    if (!player.getStatusManager().hasEffect(StatusEffectType.HARDENED)) {
                        player.getStatusManager().addEffect(StatusEffectType.HARDENED, 12, 1, false);
                        eventManager.addEvent(new GameEvent("METABOLIC TRIGGER: Carapace hardened from the blow!", 2.0f));
                    }
                }
                // 2. Blood Surge (low HP)
                if (player.getStatusManager().hasEffect(StatusEffectType.BLOOD_SURGE)) {
                    float hpPct = (float) player.getCurrentHP() / (float) player.getStats().getMaxHP();
                    if (hpPct <= 0.35f && !player.getStatusManager().hasEffect(StatusEffectType.ADRENALINE_BOOST)) {
                        player.getStatusManager().addEffect(StatusEffectType.ADRENALINE_BOOST, 15, 1, false);
                        eventManager.addEvent(new GameEvent("METABOLIC TRIGGER: Blood Surge! Adrenaline courses through you!", 2.5f));
                    }
                }
                // 3. Spiritual Ward
                if (player.getStatusManager().hasEffect(StatusEffectType.SPIRITUAL_WARD)) {
                    int retaliateDmg = Math.max(3, actualDamage / 2);
                    attacker.takeDamage(retaliateDmg, DamageType.SPIRITUAL);
                    eventManager.addEvent(new GameEvent("METABOLIC TRIGGER: Spiritual Ward retributively shocks " + attacker.getMonsterType() + " for " + retaliateDmg + "!", 2.0f));
                }

                // Tactical Venom DoT: Snakes & Spiders
                String mType = (attacker.getMonsterType() != null) ? attacker.getMonsterType().toUpperCase() : "";
                if (mType.contains("SNAKE") || mType.contains("SPIDER")) {
                    if (Math.random() < 0.30f && !player.getStatusManager().hasEffect(StatusEffectType.POISONED)) {
                        player.getStatusManager().addEffect(StatusEffectType.POISONED, 10, 1, false);
                        eventManager.addEvent(new GameEvent("VENOMOUS BITE! " + attacker.getMonsterType() + " injects deadly venom!", 2.0f));
                    }
                }
            }
        } else {
            eventManager.addEvent(new GameEvent(attacker.getMonsterType() + " misses!", 1f));
        }

        damageTakenInCombat += actualDamage;
        BalanceLogger.getInstance().logCombatRound("MONSTER", "Melee", -1, actualDamage, player.getCurrentHP());

        playerCurrentBlock = 0;

        if (player.getCurrentHP() <= 0) {
            this.monster = attacker;
            currentState = CombatState.DEFEAT;
        }
    }

    /**
     * Resolves an attack between two infighting monsters.
     */
    public void monsterVsMonsterStrike(Monster attacker, Monster defender, Maze targetMaze) {
        if (attacker == null || defender == null || targetMaze == null) return;
        if (!attacker.isAlive() || !defender.isAlive()) return;

        int attackBonus = calculateMonsterAttackBonus(attacker);
        int d20Roll = DiceRoller.d20();
        boolean isHit = (d20Roll + attackBonus) >= defender.getArmorClass();

        if (isHit) {
            int baseDmg = DiceRoller.roll(attacker.getDamageDice());
            float doomScale = DoomManager.getInstance().getEnemyScalingMultiplier();
            int dmg = Math.max(1, (int) (baseDmg * doomScale));
            int taken = defender.takeDamage(dmg, DamageType.PHYSICAL);

            targetMaze.addBlood((int) defender.getPosition().x, (int) defender.getPosition().y, 0.04f);
            if (eventManager != null) {
                eventManager.addEvent(new GameEvent(attacker.getMonsterType() + " strikes " + defender.getMonsterType() + " for " + taken + " dmg!", 1.5f));
            }
            defender.onAttackedBy(attacker);

            if (defender.getCurrentHP() <= 0) {
                GridPoint2 defPos = new GridPoint2((int) defender.getPosition().x, (int) defender.getPosition().y);
                targetMaze.getMonsters().remove(defPos);
                if (eventManager != null) {
                    eventManager.addEvent(new GameEvent(attacker.getMonsterType() + " slayed " + defender.getMonsterType() + "!", 2f));
                }
                spawnCorpseEffects(defender, Math.max(0, taken - defender.getMaxHP()));
            }
        } else {
            if (eventManager != null) {
                eventManager.addEvent(new GameEvent(attacker.getMonsterType() + " misses " + defender.getMonsterType() + "!", 1f));
            }
            defender.onAttackedBy(attacker);
        }
    }

    /**
     * 5e Standard Monster To-Hit accuracy formula: Proficiency (2 + level/3) + Stat Modifier.
     * Nimble beasts/skirmishers scale from DEX; brute humanoids/undead scale from STR/HP.
     */
    /**
     * Minimum stat modifier contribution to a monster's attack bonus, regardless
     * of how weak its actual Dex/HP would otherwise put it. Lowered from 2 to 1
     * as part of the "way off again" balance fix: rest-to-heal (the R key) was
     * removed from the game (see the tend-wounds/first-aid work) without
     * retuning monster accuracy to compensate, so a fresh player facing several
     * low-tier fights back-to-back with no recovery window had no realistic
     * out even against trash monsters. This floor is what made every low-HP,
     * low-Dex monster (Kobold, Giant Ant, ...) hit almost as often as one with
     * real stats behind it; softening it here (not their damage dice, not the
     * Doom escalation curve) is the smallest lever that fixes the specific
     * compounding effect without touching anything else.
     */
    private static final int MONSTER_STAT_MOD_FLOOR = 1;

    public static int calculateMonsterAttackBonus(Monster attacker) {
        if (attacker == null) return 2;
        int monsterLevel = Math.max(1, attacker.getLevel());
        int profBonus = 2 + (monsterLevel / 3);
        int statMod = 1;
        MonsterTemplate t = attacker.getTemplate();
        if (t != null) {
            if (t.dexterity >= 12 && t.family == com.bpm.minotaur.gamedata.monster.MonsterFamily.BEAST) {
                statMod = Math.max(MONSTER_STAT_MOD_FLOOR, (t.dexterity - 10) / 2);
            } else {
                statMod = Math.max(MONSTER_STAT_MOD_FLOOR, t.maxHP / 14);
            }
        }
        return profBonus + statMod;
    }

    public boolean shouldTriggerDeathInversion(Monster attacker) {
        if (attacker == null) return false;
        if (com.bpm.minotaur.managers.DimensionalManager.getInstance().isInVoid()) return false;
        Monster.MonsterType type = attacker.getType();
        return type == Monster.MonsterType.WRAITH ||
               type == Monster.MonsterType.GHAST ||
               type == Monster.MonsterType.LICH ||
               type == Monster.MonsterType.BRINGER_OF_DEATH ||
               type == Monster.MonsterType.FALL_ANGEL ||
               type == Monster.MonsterType.VAMPIRE ||
               type == Monster.MonsterType.ZOMBIE ||
               type == Monster.MonsterType.GHOUL ||
               type == Monster.MonsterType.MUMMY;
    }

    public void triggerDeathInversion(Monster attacker) {
        Gdx.app.log("CombatManager", "Death Inversion triggered by " + attacker.getMonsterType());
        int currentLvl = (worldManager != null) ? worldManager.getCurrentLevel() : 1;
        com.badlogic.gdx.math.GridPoint2 currentChunk = (worldManager != null)
                ? worldManager.getCurrentPlayerChunkId()
                : new com.badlogic.gdx.math.GridPoint2(0, 0);

        com.bpm.minotaur.managers.DimensionalManager.getInstance().enterVoid(true, player.getPosition(), currentLvl, currentChunk);

        int revivedHp = Math.max(1, player.getStats().getMaxHP() / 2);
        player.getStats().setCurrentHP(revivedHp);
        player.getStatusManager().clearEffects();

        eventManager.addEvent(new GameEvent("DEATH INVERSION! " + attacker.getMonsterType() + " severed your mortal soul!", 3.0f));
        eventManager.addEvent(new GameEvent("You awaken in the Ancient Void as a Hollow Shade!", 3.5f));
        eventManager.addEvent(new GameEvent("Find a Resonating Rift Anchor to reclaim your mortal form!", 4.0f));

        com.bpm.minotaur.managers.DebugManager.getInstance().triggerDimensionalWarp(true);
        soundManager.playDimensionalWarpSound();
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

        // NEW: Critical Toxicity DoT (threshold shifted by Fortitude)
        if (player.getStats().getToxicity() >= 76 + player.getToxicityThresholdShift()) {
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
                String ammoName = (weapon.getType() == Item.ItemType.CROSSBOW || (weapon.getFriendlyName() != null && weapon.getFriendlyName().toLowerCase().contains("crossbow"))) ? "bolts" : "arrows";
                eventManager.addEvent(new GameEvent("You have no " + ammoName + "!", 2f));
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

    /**
     * Combat menu CAST: casts the first prepared spell. Picking a specific spell is
     * done with the quick-cast keys (Z,X,V,B,N) or the Spellbook.
     */
    public void playerCast() {
        if (currentState != CombatState.PLAYER_MENU && currentState != CombatState.PLAYER_TURN)
            return;

        String[] prepared = player.getPreparedSpells();
        for (int slot = 0; slot < player.getUnlockedSpellSlots() && slot < prepared.length; slot++) {
            if (prepared[slot] != null) {
                if (player.castPreparedSpell(slot, maze, eventManager, this)) {
                    closeMenuOrPassTurn();
                }
                return;
            }
        }
        eventManager.addEvent(new GameEvent("No spell prepared! Assign one in the Spellbook.", 1.5f));
    }

    public void handleRemoteKill(Monster m) {
        maze.getMonsters().remove(new GridPoint2((int) m.getPosition().x, (int) m.getPosition().y));
        player.getStats().addExperience(m.getBaseExperience());
        eventManager.addEvent(new GameEvent("Killed " + m.getMonsterType() + "!", 2f));
        com.bpm.minotaur.gamedata.monster.MonsterTemplate remoteTemplate = m.getTemplate();
        if (remoteTemplate != null) {
            DivinityManager.getInstance().awardKillDivinities(remoteTemplate.baseLevel, maze.getLevel());
        }
        DivinityOrbManager.getInstance().spawnOrb();
        spawnCorpseEffects(m, 0);

        // Caves of Qud Night Hunter trigger
        if (player != null && player.getStatusManager() != null && player.getStatusManager().hasEffect(StatusEffectType.NIGHT_HUNTER)) {
            player.getStatusManager().addEffect(StatusEffectType.TELEPATHY, 15, 1, false);
            eventManager.addEvent(new GameEvent("METABOLIC TRIGGER: Night Hunter grants void ESP!", 2.0f));
        }
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
        if (game != null && game.getScreen() instanceof com.bpm.minotaur.screens.GameScreen) {
            com.bpm.minotaur.screens.GameScreen gs = (com.bpm.minotaur.screens.GameScreen) game.getScreen();
            gs.getWeaponOverlay().triggerAttack(pendingWeapon);
            gs.getWeaponOverlay().setHitFrameCallback(profile -> {
                this.currentMotionProfile = profile;
                int d20Roll = DiceRoller.roll("1d20");
                Gdx.app.log("CombatManager", "Instant Attack: Rolled " + d20Roll + " on D20");
                resolveAttack(d20Roll);
                this.currentMotionProfile = null;
            });
        } else {
            int d20Roll = DiceRoller.roll("1d20");
            Gdx.app.log("CombatManager", "Instant Attack: Rolled " + d20Roll + " on D20");
            resolveAttack(d20Roll);
        }

        // AGI Flurry: at AGI 14+ the player has a chance at a bonus instant attack this turn.
        // Stateless so it doesn't re-trigger MONSTER_TURN or turn processing.
        if (monster != null && monster.getCurrentHP() > 0 && currentState != CombatState.VICTORY) {
            int agi = player.getEffectiveAgility();
            float flurryChance = agi >= 18 ? 0.35f : agi >= 14 ? 0.20f : 0f;
            if (flurryChance > 0f && random.nextFloat() < flurryChance) {
                Item flurryWeapon = player.getInventory().getRightHand();
                if (flurryWeapon != null) {
                    pendingWeapon = flurryWeapon;
                    eventManager.addEvent(new GameEvent("Flurry!", 0.8f));
                    resolveAttack(DiceRoller.roll("1d20"), true);
                }
            }
        }
    }

    public boolean throwWeapon(Item weapon) {
        if (weapon == null) return false;
        int maxRange = Math.max(3, weapon.getRange());
        HitResult hit = raycastProjectile(player.getPosition(), player.getFacing(), maxRange, true);

        Vector2 startPos = player.getPosition().cpy().add(player.getDirectionVector().cpy().scl(0.6f));
        Vector2 targetPos = hit.collisionPoint != null ?
                new Vector2(hit.collisionPoint.x + 0.5f, hit.collisionPoint.y + 0.5f) :
                startPos.cpy().add(player.getDirectionVector().cpy().scl(maxRange));

        soundManager.playWeaponSwing();
        if (animationManager != null) {
            animationManager.addAnimation(new Animation(
                    Animation.AnimationType.PROJECTILE_PLAYER,
                    startPos, targetPos,
                    com.badlogic.gdx.graphics.Color.LIGHT_GRAY, 0.4f,
                    new String[] { "/" }));
        }

        int statBonus = Math.max(player.getEffectiveStrengthModifier(), player.getEffectiveDexterityModifier());
        int attackRoll = DiceRoller.roll("1d20") + statBonus;

        if (hit.type == HitResult.HitType.MONSTER && hit.hitMonster != null) {
            Monster target = hit.hitMonster;
            if (Monster.isImmuneToType(target.getType(), DamageType.PHYSICAL)) {
                eventManager.addEvent(new GameEvent(target.getType() + " is immune to thrown weapons!", 1.5f));
                showDamageText(0, hit.collisionPoint);
            } else if (attackRoll >= target.getArmorClass()) {
                int dmg = DiceRoller.roll(weapon.getDamageDice()) + statBonus;
                dmg = Math.max(1, dmg);
                int actual = target.takeDamage(dmg, DamageType.PHYSICAL, false);
                showDamageText(actual, hit.collisionPoint);
                eventManager.addEvent(new GameEvent("Threw " + weapon.getFriendlyName() + " into " + target.getType() + " for " + actual + " dmg!", 1.5f));

                if (target.getCurrentHP() <= 0) {
                    if (target == this.monster) {
                        handleMonsterDeath();
                        currentState = CombatState.VICTORY;
                    } else {
                        handleRemoteKill(target);
                    }
                }
            } else {
                eventManager.addEvent(new GameEvent("Thrown " + weapon.getFriendlyName() + " glanced off " + target.getType() + "!", 1.0f));
            }
            weapon.setPosition(hit.collisionPoint.x + 0.5f, hit.collisionPoint.y + 0.5f);
            maze.addItem(weapon);
        } else {
            eventManager.addEvent(new GameEvent("Thrown " + weapon.getFriendlyName() + " clatters to the stone.", 1.0f));
            if (hit.collisionPoint != null) {
                weapon.setPosition(hit.collisionPoint.x + 0.5f, hit.collisionPoint.y + 0.5f);
                maze.addItem(weapon);
            }
        }

        player.getInventory().removeItem(weapon);
        return true;
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
        playerUseItem(0, discoveryManager);
    }

    public void playerUseItem(int slotIndex, DiscoveryManager discoveryManager) {
        if (currentState != CombatState.PLAYER_MENU && currentState != CombatState.PLAYER_TURN)
            return;

        Item[] quickSlots = player.getInventory().getQuickSlots();
        if (slotIndex < 0 || slotIndex >= quickSlots.length) return;
        Item itemToUse = quickSlots[slotIndex];

        if (itemToUse != null) {
            if (itemToUse.isWeapon() || itemToUse.isShield()) {
                player.useQuickSlot(slotIndex, eventManager, discoveryManager, maze, this);
                closeMenuOrPassTurn();
            } else {
                player.useItem(itemToUse, eventManager, discoveryManager, maze, this);
                closeMenuOrPassTurn();
            }
        } else {
            eventManager.addEvent(new GameEvent("Quick slot " + (slotIndex + 1) + " is empty!", 1.5f));
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

    public void playerShieldBash(Monster target) {
        if (target == null) return;
        this.monster = target;
        Item shield = player.getInventory().getLeftHand();
        if (shield == null || !shield.isShield()) return;

        soundManager.playWeaponSwing();
        if (game != null && game.getScreen() instanceof com.bpm.minotaur.screens.GameScreen) {
            com.bpm.minotaur.screens.GameScreen gs = (com.bpm.minotaur.screens.GameScreen) game.getScreen();
            gs.getWeaponOverlay().triggerShieldBash(shield);
            gs.getWeaponOverlay().setHitFrameCallback(profile -> {
                int bashDmg = Math.max(1, DiceRoller.roll("1d4") + shield.getArmorClassBonus());
                int actual = target.takeDamage(bashDmg, DamageType.PHYSICAL);
                soundManager.playWeaponImpact(true);
                gs.addTrauma(0.28f);
                eventManager.addEvent(new GameEvent("SHIELD BASH! Staggered " + target.getMonsterType() + " for " + actual, 1.2f));
                showDamageText(actual, new GridPoint2((int) target.getPosition().x, (int) target.getPosition().y), "BASH! ", com.badlogic.gdx.graphics.Color.ORANGE);
            });
        }
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

        // 3. Damage — unified formula: same STR+equipment bonus as instant combat
        int totalAttack;
        if (com.bpm.minotaur.managers.DimensionalManager.getInstance().isInVoid()) {
            float physMult = com.bpm.minotaur.managers.DimensionalManager.getInstance().getPhysicalDamageMultiplier();
            float spiritMult = com.bpm.minotaur.managers.DimensionalManager.getInstance().getSpiritualDamageMultiplier();
            totalAttack = Math.max(1, (int) (totalDamage * physMult + (fireDamage + lightningDamage) * spiritMult + player.getDamageBonus()));
            eventManager.addEvent(new GameEvent("VOID INVERSION! Physical dampened, Elements amplified!", 1.2f));
        } else {
            totalAttack = totalDamage + fireDamage + lightningDamage + player.getDamageBonus();
        }

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

        // Toxic Communion: Critical Toxicity Double Damage (threshold shifted by Fortitude)
        if (player.getStats().getToxicity() >= 76 + player.getToxicityThresholdShift()) {
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
                GridPoint2 cid = (worldManager != null) ? worldManager.getCurrentPlayerChunkId() : new GridPoint2(0, 0);
                float wx = cid.x * 36.0f + monster.getPosition().x;
                float wz = cid.y * 36.0f + monster.getPosition().y;
                Vector3 hitPos = new Vector3(wx, 0.5f, wz);
                Vector3 dir = new Vector3(monster.getPosition().x - player.getPosition().x, 0.15f, monster.getPosition().y - player.getPosition().y).nor();
                GoreProfile profile = GoreProfile.fromMonster(monster);
                maze.getGoreManager().spawnBloodSpray(hitPos, dir, Math.max(2, actualDamage / 2), profile);
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

    /** Glancing Blow threshold: an otherwise-missed attack within this many points of AC still lands a partial hit. */
    public static final int GLANCING_BLOW_MAX_DELTA = 3;
    /** Glancing Blow damage fraction of the weapon's normal roll. */
    public static final float GLANCING_BLOW_DAMAGE_MULTIPLIER = 0.35f;

    public static boolean isHit(int attackRoll, int targetAC, boolean isCrit) {
        return (attackRoll - targetAC >= 0) || isCrit;
    }

    public static boolean isGlancingBlow(int attackRoll, int targetAC, boolean isCrit) {
        int delta = attackRoll - targetAC;
        boolean hit = (delta >= 0) || isCrit;
        return !hit && (delta >= -GLANCING_BLOW_MAX_DELTA);
    }

    public static int glancingBlowDamage(int fullDamage) {
        return Math.max(1, (int) (fullDamage * GLANCING_BLOW_DAMAGE_MULTIPLIER));
    }

    /** Arcane Spark damage dice: rolled instead of a book's own damage dice on every attack. */
    public static final String ARCANE_SPARK_DICE = "1d4";

    public static boolean isBookWeapon(Item weapon) {
        if (weapon == null || weapon.getType() == null) {
            return false;
        }
        Item.ItemType type = weapon.getType();
        return type == Item.ItemType.WAR_BOOK || type == Item.ItemType.SPIRITUAL_BOOK
                || type == Item.ItemType.SPECIAL_BOOK;
    }

    /** Arcane Spark: 1d4 + INT modifier Spiritual damage, minimum 1. */
    public static int arcaneSparkDamage(int intModifier, int d4Roll) {
        return Math.max(1, d4Roll + intModifier);
    }

    private void resolveAttack(int d20Roll, boolean stateless) {
        if (monster == null)
            return;

        // Consume ammunition for ranged weapons (bows, crossbows)
        if (pendingWeapon != null && pendingWeapon.isRanged() && pendingWeapon.getType() != Item.ItemType.DART) {
            player.decrementArrow();
        }

        int toHitBonus = (pendingWeapon != null && pendingWeapon.isFinesse()) ? player.getFinesseToHitBonus() : player.getToHitBonus();
        if (player.getInjuryManager() != null) {
            toHitBonus += player.getInjuryManager().getEffectiveAttackModifier();
        }
        int attackRoll = d20Roll + toHitBonus;
        int targetAC = monster.getArmorClass();
        boolean isCrit = (d20Roll == 20) || (random.nextFloat() < player.getCritChance());
        boolean isHit = isHit(attackRoll, targetAC, isCrit);
        boolean isGlancing = isGlancingBlow(attackRoll, targetAC, isCrit);

        // --- CONFUSION LOGIC (Instant) ---
        if (player.getStatusManager().hasEffect(com.bpm.minotaur.gamedata.effects.StatusEffectType.CONFUSION)) {
            if (random.nextFloat() > 0.5f) { // 50% Chance to Miss wildly
                isHit = false;
                isGlancing = false;
                eventManager.addEvent(new GameEvent("You are confused and swing wildly and miss", 2f));
                Gdx.app.log("CombatManager", "Confusion: Player swung wildly and missed.");
            } else {
                if (isHit || isGlancing) { // Only add "somehow hit" if they actually hit
                    eventManager.addEvent(new GameEvent("You are confused and swing wildly and somehow hit", 2f));
                }
            }
        }

        // Log Check
        Gdx.app.log("CombatManger",
                "Player Attack: Roll " + d20Roll + " + " + toHitBonus + " = " + attackRoll + " vs AC " + targetAC
                        + " (Hit=" + isHit + ", Glance=" + isGlancing + ")");

        if (isHit || isGlancing) {
            DamageType dmgType = DamageType.PHYSICAL;
            String damageDice = "1d2";
            boolean isArcaneSpark = isBookWeapon(pendingWeapon);
            if (isArcaneSpark) {
                // Tome Weapon Attack: a Spiritual Arcane Spark replaces the book's own
                // damage dice entirely -- see arcaneSparkDamage() for the 1d4+INT roll.
                dmgType = DamageType.SPIRITUAL;
                damageDice = ARCANE_SPARK_DICE;
            } else if (pendingWeapon != null) {
                damageDice = player.getInventory().getActiveDamageDice(pendingWeapon);
                if (damageDice == null || damageDice.isEmpty()) damageDice = "1d4";
                if ("SPIRITUAL".equalsIgnoreCase(pendingWeapon.getDamageType()) ||
                        pendingWeapon.getCategory() == com.bpm.minotaur.gamedata.item.ItemCategory.SPIRITUAL_WEAPON) {
                    dmgType = DamageType.SPIRITUAL;
                }
            }

            if (Monster.isImmuneToType(monster.getType(), dmgType)) {
                String attackCategory = (dmgType == DamageType.PHYSICAL) ? "War" : "Spiritual";
                eventManager.addEvent(new GameEvent(monster.getType() + " is immune to " + attackCategory + " attacks!", 1.5f));
                showDamageText(0, new GridPoint2((int) monster.getPosition().x, (int) monster.getPosition().y));
                lastDamageDealt = 0;
                soundManager.playWeaponImpact(false);
            } else {
                int totalDamage;
                if (isArcaneSpark) {
                    int intModifier = (player.getEffectiveIntelligence() - 10) / 2;
                    totalDamage = arcaneSparkDamage(intModifier, DiceRoller.roll(damageDice));
                } else {
                    int baseDamage = DiceRoller.roll(damageDice);
                    int damageBonus = (pendingWeapon != null && pendingWeapon.isFinesse()) ? player.getFinesseDamageBonus() : player.getDamageBonus();
                    totalDamage = Math.max(1, baseDamage + damageBonus);
                }

                // Combo Damage Multiplier
                if (currentMotionProfile != null && currentMotionProfile.damageMultiplier > 0f) {
                    totalDamage = Math.max(1, (int) (totalDamage * currentMotionProfile.damageMultiplier));
                }

                // Glancing Blow: 35% base damage
                if (isGlancing) {
                    totalDamage = glancingBlowDamage(totalDamage);
                }

                if (com.bpm.minotaur.managers.DimensionalManager.getInstance().isInVoid()) {
                    if (dmgType == DamageType.PHYSICAL) {
                        totalDamage = Math.max(1, (int) (totalDamage * com.bpm.minotaur.managers.DimensionalManager.getInstance().getPhysicalDamageMultiplier()));
                        eventManager.addEvent(new GameEvent("VOID DAMPENING! Physical -60%", 1f));
                    } else {
                        totalDamage = Math.max(1, (int) (totalDamage * com.bpm.minotaur.managers.DimensionalManager.getInstance().getSpiritualDamageMultiplier()));
                        eventManager.addEvent(new GameEvent("VOID RESONANCE! Spiritual +250%", 1f));
                    }
                }

                if (isCrit) {
                    totalDamage = (int) (totalDamage * player.getCritMultiplier());
                    eventManager.addEvent(new GameEvent("CRITICAL HIT!", 1f));
                }

                int actualDamage = monster.takeDamage(totalDamage, dmgType, isCrit);
                Monster.Affinity affinity = monster.getAffinity(dmgType);
                String dmgPrefix = "";
                com.badlogic.gdx.graphics.Color textColor = com.badlogic.gdx.graphics.Color.WHITE;

                String comboTag = "";
                if (currentMotionProfile != null && currentMotionProfile.comboStep > 0) {
                    if (currentMotionProfile.isFinisher) {
                        comboTag = "FINISHER! ";
                    } else {
                        comboTag = "COMBO x" + (currentMotionProfile.comboStep + 1) + "! ";
                    }
                }

                if (isCrit) {
                    dmgPrefix = comboTag + "CRIT! ";
                    textColor = com.badlogic.gdx.graphics.Color.RED;
                } else if (isGlancing) {
                    dmgPrefix = "GLANCE! ";
                    textColor = com.badlogic.gdx.graphics.Color.CYAN;
                    eventManager.addEvent(new GameEvent("Glancing blow on " + monster.getType() + " for " + actualDamage + " dmg!", 1.2f));
                } else if (currentMotionProfile != null && currentMotionProfile.isFinisher) {
                    dmgPrefix = comboTag + "[" + currentMotionProfile.comboName + "] ";
                    textColor = com.badlogic.gdx.graphics.Color.GOLD;
                } else if (currentMotionProfile != null && currentMotionProfile.comboStep > 0) {
                    dmgPrefix = comboTag;
                    textColor = com.badlogic.gdx.graphics.Color.YELLOW;
                } else if (affinity == Monster.Affinity.RESISTANT) {
                    dmgPrefix = "RESISTED! ";
                    textColor = com.badlogic.gdx.graphics.Color.CYAN;
                    String attackCategory = (dmgType == DamageType.PHYSICAL) ? "War" : "Spiritual";
                    eventManager.addEvent(new GameEvent(monster.getType() + " resists " + attackCategory + " attacks!", 1.5f));
                } else if (affinity == Monster.Affinity.WEAK) {
                    dmgPrefix = "WEAKNESS! ";
                    textColor = com.badlogic.gdx.graphics.Color.GOLD;
                    String attackCategory = (dmgType == DamageType.PHYSICAL) ? "War" : "Spiritual";
                    eventManager.addEvent(new GameEvent(monster.getType() + " is weak to " + attackCategory + " attacks!", 1.5f));
                }

                showDamageText(actualDamage, new GridPoint2((int) monster.getPosition().x, (int) monster.getPosition().y), dmgPrefix, textColor, isCrit, dmgType);
                lastDamageDealt = actualDamage;

                com.bpm.minotaur.telemetry.TelemetryManager.getInstance().recordAttack(
                        isGlancing ? com.bpm.minotaur.telemetry.TelemetryManager.HitType.GLANCING
                                : com.bpm.minotaur.telemetry.TelemetryManager.HitType.HIT,
                        actualDamage);

                // --- Open5e Ring of the Ram Trigger ---
                if (player.getEquipment() != null && player.getEquipment().getRingCharges(com.bpm.minotaur.gamedata.item.RingEffectType.RAM) > 0) {
                    player.getEquipment().expendRingCharge(com.bpm.minotaur.gamedata.item.RingEffectType.RAM);
                    int forceDmg = com.bpm.minotaur.utils.DiceRoller.roll("2d10");
                    int ramActual = monster.takeDamage(forceDmg, DamageType.PHYSICAL, false);
                    eventManager.addEvent(new GameEvent("RAM FORCE! A spectral ram head batters the " + monster.getType() + " for +" + ramActual + " force dmg!", 2.0f));
                    showDamageText(ramActual, new GridPoint2((int) monster.getPosition().x, (int) monster.getPosition().y), "RAM! ", com.badlogic.gdx.graphics.Color.CYAN);

                    if (maze != null && monster.getCurrentHP() > 0) {
                        int tileX = (int) monster.getPosition().x;
                        int tileY = (int) monster.getPosition().y;
                        Direction pushDir = player.getFacing();
                        com.badlogic.gdx.math.Vector2 pushVec = pushDir.getVector();

                        maze.getMonsters().remove(new GridPoint2(tileX, tileY));
                        for (int p = 0; p < 2; p++) {
                            int nextX = tileX + (int) pushVec.x;
                            int nextY = tileY + (int) pushVec.y;
                            if (maze.isWallBlocking(tileX, tileY, pushDir) || !maze.isPassable(nextX, nextY) || maze.getMonsters().containsKey(new GridPoint2(nextX, nextY))) {
                                break;
                            }
                            tileX = nextX;
                            tileY = nextY;
                        }
                        monster.getPosition().set(tileX + 0.5f, tileY + 0.5f);
                        maze.getMonsters().put(new GridPoint2(tileX, tileY), monster);
                    }
                }

                // --- Open5e Ring of Shooting Stars Trigger ---
                if (player.getEquipment() != null && player.getEquipment().getRingCharges(com.bpm.minotaur.gamedata.item.RingEffectType.SHOOTING_STARS) > 0) {
                    player.getEquipment().expendRingCharge(com.bpm.minotaur.gamedata.item.RingEffectType.SHOOTING_STARS);
                    int starDmg = com.bpm.minotaur.utils.DiceRoller.roll("2d6");
                    int starActual = monster.takeDamage(starDmg, DamageType.LIGHT, false);
                    eventManager.addEvent(new GameEvent("SHOOTING STARS! Dazzling motes of light strike " + monster.getType() + " for +" + starActual + " light dmg!", 2.0f));
                    showDamageText(starActual, new GridPoint2((int) monster.getPosition().x, (int) monster.getPosition().y), "STARS! ", com.badlogic.gdx.graphics.Color.YELLOW);
                }

                // --- VISCERAL: Feedback ---
                float damageRatio = (float) totalDamage / (float) monster.getMaxHP();
                boolean isHeavy = damageRatio > 0.2f || affinity == Monster.Affinity.WEAK || isCrit;

                // 1. Audio
                if (isGlancing || (affinity == Monster.Affinity.RESISTANT && !isCrit)) {
                    soundManager.playWeaponImpact(false); // Dull deflection
                } else {
                    soundManager.playWeaponImpact(isHeavy); // Meat/Metal hit
                }
                soundManager.playMonsterReaction(monster, damageRatio); // Grunts/Roars

                // 2. Screen Shake & Hit Pause
                if (game != null && game.getScreen() instanceof com.bpm.minotaur.screens.GameScreen) {
                    com.bpm.minotaur.screens.GameScreen gs = (com.bpm.minotaur.screens.GameScreen) game.getScreen();

                    // Coat blade with blood
                    gs.getWeaponOverlay().addBloodToWeapon();

                    // Shake
                    float extraTrauma = (currentMotionProfile != null) ? currentMotionProfile.screenTrauma : 0f;
                    float trauma = (isCrit ? 0.5f : (isHeavy ? 0.3f : 0.1f)) + extraTrauma;
                    gs.addTrauma(Math.min(1.0f, trauma));

                    // Pause (Freeze frame)
                    float pauseDur = isCrit ? 0.15f : (currentMotionProfile != null && currentMotionProfile.isFinisher ? 0.12f : 0.05f);
                    gs.triggerHitPause(pauseDur);
                }

                // 3. Blood (Scaling)
                GridPoint2 cid = (worldManager != null) ? worldManager.getCurrentPlayerChunkId() : new GridPoint2(0, 0);
                float wx = cid.x * 36.0f + monster.getPosition().x;
                float wz = cid.y * 36.0f + monster.getPosition().y;
                Vector3 hitPos = new Vector3(wx, 0.5f, wz);
                Vector3 exitDir = new Vector3(monster.getPosition().x - player.getPosition().x, 0.2f, monster.getPosition().y - player.getPosition().y).nor();
                GoreProfile profile = GoreProfile.fromMonster(monster);

                int bloodIntensity;
                if (damageRatio < 0.15f) {
                    // Chip damage
                    bloodIntensity = 2;
                    maze.getGoreManager().spawnBloodSpray(hitPos, exitDir, bloodIntensity, profile);
                } else if (damageRatio < 0.35f) {
                    // Solid Hit
                    bloodIntensity = 5;
                    maze.getGoreManager().spawnBloodSpray(hitPos, exitDir, bloodIntensity, profile);
                    maze.addBlood((int) monster.getPosition().x, (int) monster.getPosition().y, 0.1f);
                } else {
                    // Massive Hit
                    bloodIntensity = 8;
                    maze.getGoreManager().spawnBloodSpray(hitPos, exitDir, bloodIntensity, profile);
                    if (profile.hasGibs) {
                        maze.getGoreManager().spawnGibExplosion(hitPos, exitDir, 1, profile);
                    }
                    maze.addBlood((int) monster.getPosition().x, (int) monster.getPosition().y, 0.3f);
                }

                applyWeaponBlood(bloodIntensity, profile);
                splatterPlayer(bloodIntensity, profile, false);
            }

        } else {
            // Miss
            eventManager.addEvent(new GameEvent("Miss!", 1f));
            com.bpm.minotaur.telemetry.TelemetryManager.getInstance().recordAttack(
                    com.bpm.minotaur.telemetry.TelemetryManager.HitType.MISS, 0);
            if (game != null && game.getScreen() instanceof com.bpm.minotaur.screens.GameScreen) {
                com.bpm.minotaur.screens.GameScreen gs = (com.bpm.minotaur.screens.GameScreen) game.getScreen();
                gs.getWeaponOverlay().triggerWhiff();
            }
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
        } else {
            // Synchronous Retaliation with Poise Stagger:
            // In real-time bump combat, surviving monsters trade blows on the same tick
            // unless staggered by a Critical Hit, Shield Bash, or Combo Finisher.
            boolean isStaggered = isCrit || (currentMotionProfile != null && (currentMotionProfile.isFinisher || currentMotionProfile.isShieldBash));
            if (!isStaggered) {
                monsterMeleeStrike(monster);
            } else {
                eventManager.addEvent(new GameEvent(monster.getType() + " is staggered by the blow!", 1.0f));
            }
        }
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
                    }
                }
            }
        }
    }

    public void showDamageText(int damage, GridPoint2 position) {
        showDamageText(damage, position, "", com.badlogic.gdx.graphics.Color.WHITE, false, DamageType.PHYSICAL);
    }

    public void showDamageText(int damage, GridPoint2 position, String prefix, com.badlogic.gdx.graphics.Color color) {
        showDamageText(damage, position, prefix, color, false, DamageType.PHYSICAL);
    }

    public void showDamageText(int damage, GridPoint2 position, String prefix, com.badlogic.gdx.graphics.Color color, boolean isCrit, DamageType damageType) {
        String text = (prefix != null ? prefix : "") + damage;
        animationManager.addAnimation(
                new Animation(Animation.AnimationType.DAMAGE_TEXT, position, text, color, 1.2f, isCrit, false, damageType));
    }

    public void showPlayerDamageText(int damage) {
        showPlayerDamageText(damage, false, DamageType.PHYSICAL);
    }

    public void showPlayerDamageText(int damage, boolean isCrit, DamageType damageType) {
        if (damage <= 0) return;
        String text = "-" + damage + " HP";
        com.badlogic.gdx.graphics.Color col = (damageType == DamageType.SPIRITUAL || damageType == DamageType.MAGICAL)
                ? com.badlogic.gdx.graphics.Color.valueOf("B388FF")
                : com.badlogic.gdx.graphics.Color.valueOf("FF4B36");
        animationManager.addAnimation(
                new Animation(Animation.AnimationType.DAMAGE_TEXT, null, text, col, 1.2f, isCrit, true, damageType));
    }

    public void update(float delta) {
        if (attackIndicatorMonster != null && attackIndicatorElapsed < attackIndicatorDuration) {
            attackIndicatorElapsed += delta;
        }

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
            if (shouldTriggerDeathInversion(monster)) {
                triggerDeathInversion(monster);
            } else {
                eventManager.addEvent(new GameEvent(GameEvent.EventType.PLAYER_DIED, null));
            }
            endCombat();
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
        spawnCorpseEffects(monster, 0);
    }

    private void spawnCorpseEffects(Monster monster, int overkillTier) {
        if (maze == null || itemDataManager == null)
            return;

        GridPoint2 pos = new GridPoint2((int) monster.getPosition().x, (int) monster.getPosition().y);

        // 1. Determine Gib Count based on Damage and Overkill Tier (Stochastic Pacing)
        int dropChance = 20; // 20% base chance for normal kill
        if (overkillTier >= 2) {
            dropChance = 45; // 45% on Tier 2 obliteration
        } else if (overkillTier == 1 || lastDamageDealt > 10) {
            dropChance = 30;
        }

        int gibCount = 0;
        if (random.nextInt(100) < dropChance) {
            gibCount = (overkillTier >= 2) ? (1 + random.nextInt(3)) : 1;
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
            Item rHand = monster.getInventory().getRightHand();
            Item lHand = monster.getInventory().getLeftHand();

            // Equipped slots drop at 25% chance per slot
            if (rHand != null && random.nextInt(100) < 25) {
                dropSingleItem(rHand, pos, monster);
            }
            if (lHand != null && random.nextInt(100) < 25) {
                dropSingleItem(lHand, pos, monster);
            }

            // Other non-equipped inventory contents
            for (Item item : monster.getInventory().getMainInventory()) {
                if (item != null) {
                    dropSingleItem(item, pos, monster);
                }
            }
            for (Item item : monster.getInventory().getQuickSlots()) {
                if (item != null) {
                    dropSingleItem(item, pos, monster);
                }
            }
        }

        // 4. Spellcaster Magical Spoils Drop
        if (monster.isSpellcaster() && monster.getSpellbook() != null) {
            // 40% chance for Tome or Arcane Book / Scroll
            if (random.nextInt(100) < 40) {
                Item.ItemType dropType = Item.ItemType.BOOK;
                int lvl = monster.getLevel();
                if (lvl >= 12) {
                    dropType = (random.nextBoolean()) ? Item.ItemType.TOME_OF_THE_ARCANE : Item.ItemType.SPIRITUAL_BOOK;
                } else if (lvl >= 6) {
                    dropType = (random.nextBoolean()) ? Item.ItemType.TOME_OF_ELEMENTS : Item.ItemType.BOOK;
                } else {
                    dropType = (random.nextBoolean()) ? Item.ItemType.TOME_OF_THE_INITIATE : Item.ItemType.SCROLL;
                }
                Item magicTome = itemDataManager.createItem(dropType, pos.x, pos.y, ItemColor.WHITE,
                        (game != null) ? game.getAssetManager() : null);
                if (magicTome != null) {
                    dropSingleItem(magicTome, pos, monster);
                    if (eventManager != null) {
                        eventManager.addEvent(new GameEvent(monster.getMonsterType() + " dropped arcane knowledge!", 2.0f));
                    }
                }
            }

            // 35% chance for Mana Potion or Clarity Elixir
            if (random.nextInt(100) < 35) {
                Item.ItemType potType = (random.nextBoolean()) ? Item.ItemType.POTION_BLUE : Item.ItemType.POTION_CLARITY;
                Item manaPot = itemDataManager.createItem(potType, pos.x, pos.y, ItemColor.BLUE,
                        (game != null) ? game.getAssetManager() : null);
                if (manaPot != null) {
                    dropSingleItem(manaPot, pos, monster);
                }
            }

            // High-tier caster bonus (Level 12+ e.g. Lich, Beholder): 20% chance for enchanted ring or wand
            if (monster.getLevel() >= 12 && random.nextInt(100) < 20) {
                Item.ItemType relic = (random.nextBoolean()) ? Item.ItemType.RING_SPELL_STORING : Item.ItemType.WAND;
                Item magicRelic = itemDataManager.createItem(relic, pos.x, pos.y, ItemColor.GOLD,
                        (game != null) ? game.getAssetManager() : null);
                if (magicRelic != null) {
                    dropSingleItem(magicRelic, pos, monster);
                }
            }
        }

        // Caves of Qud Night Hunter trigger
        if (player != null && player.getStatusManager() != null && player.getStatusManager().hasEffect(StatusEffectType.NIGHT_HUNTER)) {
            player.getStatusManager().addEffect(StatusEffectType.TELEPATHY, 15, 1, false);
            eventManager.addEvent(new GameEvent("METABOLIC TRIGGER: Night Hunter grants void ESP!", 2.0f));
        }

        // Minotaur Defeat Check: Unlocks Classic Mode globally and Pact of Torment
        if (monster != null && monster.getType() == Monster.MonsterType.MINOTAUR) {
            SaveManager.getInstance().unlockClassicMode();
            if (eventManager != null) {
                eventManager.addEvent(new GameEvent("THE MINOTAUR HAS FALLEN! Classic Mode and Pact of Torment unlocked!", 5.0f));
            }
        }
    }

    private void dropSingleItem(Item item, GridPoint2 pos, Monster monster) {
        if (item == null || maze == null) return;
        item.setPosition(monster.getPosition().x, monster.getPosition().y);

        if (!maze.getItems().containsKey(pos)) {
            maze.getItems().put(pos, item);
        } else {
            // Scatter
            boolean placed = false;
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    if (dx == 0 && dy == 0)
                        continue;
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

    public int getDamageTakenInCombat() {
        return damageTakenInCombat;
    }

    public int getCurrentCombatTurns() {
        return currentCombatTurns;
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
        if (monster instanceof GhostPlayerMonster) {
            ((GhostPlayerMonster) monster).performGhostSpell(player, eventManager, soundManager);
            currentState = CombatState.PLAYER_MENU;
            return;
        }
        eventManager.addEvent(new GameEvent(monster.getType() + " casts a dark spell!", 2f));
        int spellDmg = 5 + monster.getIntelligence();
        player.takeSpiritualDamage(spellDmg, DamageType.SORCERY);
        BalanceLogger.getInstance().logCombatRound("MONSTER", "Spell", -1, spellDmg, player.getWarStrength());

        currentState = CombatState.PLAYER_MENU;
    }

    private void performMonsterMeleeAttack() {
        triggerAttackIndicator(monster);
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
            dmg = applyGuardMitigation(dmg);

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

        if (monster instanceof GhostPlayerMonster) {
            GhostPlayerMonster ghost = (GhostPlayerMonster) monster;
            BonesData bData = ghost.getBonesData();
            if (bData != null) {
                bData.defeated = true;
                BonesManager.getInstance().consumeBones(bData);
                if (maze != null && maze.getScenery() != null) {
                    for (Scenery s : maze.getScenery().values()) {
                        if (s.isDecomposingCorpse() && s.getBonesData() != null
                                && s.getBonesData().id != null && s.getBonesData().id.equals(bData.id)) {
                            s.getBonesData().defeated = true;
                        }
                    }
                }
            }
            eventManager.addEvent(new GameEvent("The ghost of " + ghost.getGhostPlayerName() + " has been laid to rest!", 3f));
            eventManager.addEvent(new GameEvent("The decomposing remains can now be safely looted!", 3f));
        }

        UnlockManager.getInstance().recordKill(monster.getMonsterType());
        com.bpm.minotaur.telemetry.TelemetryManager.getInstance().recordKill(monster.getMonsterType());
        int baseExp = monster.getBaseExperience();
        float colorMultiplier = monster.getMonsterColor().getXpMultiplier();
        float levelMultiplier = 1.0f + (maze.getLevel() * 0.1f);
        int totalExp = (int) (baseExp * colorMultiplier * levelMultiplier);
        player.addExperience(totalExp, eventManager);

        com.bpm.minotaur.gamedata.monster.MonsterTemplate killTemplate = monster.getTemplate();
        if (killTemplate != null) {
            int divAmount = DivinityManager.getInstance().awardKillDivinities(killTemplate.baseLevel, maze.getLevel());
            com.bpm.minotaur.telemetry.TelemetryManager.getInstance().recordDivinitiesEarned(divAmount);
            eventManager.addEvent(new GameEvent("+" + divAmount + " " + DivinityManager.DIVINITY_NAME, 2f));
        }

        maze.addBlood((int) monster.getPosition().x, (int) monster.getPosition().y, 0.10f);

        // --- GIB & OVERKILL ANIMATION ---
        GridPoint2 cid = (worldManager != null) ? worldManager.getCurrentPlayerChunkId() : new GridPoint2(0, 0);
        float worldX = cid.x * 36.0f + monster.getPosition().x;
        float worldZ = cid.y * 36.0f + monster.getPosition().y;
        Vector3 gibOrigin = new Vector3(worldX, 0.5f, worldZ);
        Vector3 exitVector = new Vector3(
                monster.getPosition().x - player.getPosition().x,
                0.25f,
                monster.getPosition().y - player.getPosition().y
        ).nor();

        GoreProfile profile = GoreProfile.fromMonster(monster);

        int maxHp = Math.max(1, monster.getMaxHP());
        int overkill = Math.max(0, -monster.getCurrentHP());
        float overkillRatio = (float) overkill / (float) maxHp;
        boolean isHeavyKill = (lastDamageDealt >= maxHp * 0.40f);

        int overkillTier = 0;
        if (overkillRatio >= 0.50f || (isHeavyKill && overkillRatio >= 0.25f)) {
            overkillTier = 2; // Complete Obliteration
        } else if (overkillRatio >= 0.25f || isHeavyKill) {
            overkillTier = 1; // Significant Dismemberment
        }

        if (overkillTier > 0) {
            // Trigger Visor Blood Droplet splash & camera trauma
            if (game != null && game.getScreen() instanceof com.bpm.minotaur.screens.GameScreen) {
                com.bpm.minotaur.screens.GameScreen gs = (com.bpm.minotaur.screens.GameScreen) game.getScreen();
                gs.triggerVisorSplatter();
                gs.addTrauma(overkillTier == 2 ? 0.6f : 0.35f);
            }
        }

        int killBloodIntensity;
        if (DebugManager.getInstance().getRenderMode() == DebugManager.RenderMode.RETRO) {
            String[] spriteData = monster.getSpriteData();
            if (spriteData != null) {
                maze.getGoreManager().spawnRetroGibs(gibOrigin, spriteData, monster.getColor());
                killBloodIntensity = 0;
            } else {
                maze.getGoreManager().spawnGibExplosion(gibOrigin, exitVector, Math.max(1, overkillTier), profile);
                killBloodIntensity = 0;
            }
        } else {
            if (overkillTier > 0) {
                maze.getGoreManager().spawnGibExplosion(gibOrigin, exitVector, overkillTier, profile);
                killBloodIntensity = (overkillTier == 2) ? 8 : 5;
                maze.getGoreManager().spawnBloodSpray(gibOrigin, exitVector, killBloodIntensity, profile);
            } else {
                killBloodIntensity = 2;
                maze.getGoreManager().spawnBloodSpray(gibOrigin, exitVector, killBloodIntensity, profile);
            }
        }

        // Weapon shares in the killing blow's blood, same as every other
        // player-caused hit above.
        applyWeaponBlood(killBloodIntensity, profile);
        splatterPlayer(killBloodIntensity, profile, true);

        spawnCorpseEffects(monster, overkillTier);
        DivinityOrbManager.getInstance().spawnOrb();
    }

    /**
     * Bloodies the equipped weapon in sync with the same intensity dispersed
     * into the world by a player-caused hit -- same decal class, color, and
     * texture family as the floor/wall splats it's landing alongside.
     */
    private final java.util.Random bloodRandom = new java.util.Random();

    /**
     * Spray from something he hit lands on him too, and stays: the paperdoll shows it
     * the next time the inventory opens. Bloodless creatures leave nothing -- bone dust
     * and soul mist are not blood.
     */
    private void splatterPlayer(int intensity, GoreProfile profile, boolean kill) {
        if (intensity <= 0 || profile == null || !profile.hasBlood || player == null) {
            return;
        }
        com.badlogic.gdx.graphics.Color c = profile.primaryColor != null
                ? profile.primaryColor
                : com.bpm.minotaur.gamedata.gore.GoreManager.UNIFIED_BLOOD_COLOR;
        int rgb = com.bpm.minotaur.gamedata.gore.BloodSpatterGenerator.rgb(c.r, c.g, c.b);
        player.getBlood().splatter(kill
                ? com.bpm.minotaur.gamedata.gore.BloodSpatterGenerator.forKill(intensity, rgb, bloodRandom)
                : com.bpm.minotaur.gamedata.gore.BloodSpatterGenerator.forHitDealt(intensity, rgb, bloodRandom));
    }

    /** His own blood, from a wound, sized by how much of him the blow took. */
    private void bleedPlayer(int damage) {
        if (damage <= 0 || player == null) {
            return;
        }
        com.badlogic.gdx.graphics.Color c = com.bpm.minotaur.gamedata.gore.GoreManager.UNIFIED_BLOOD_COLOR;
        player.getBlood().splatter(com.bpm.minotaur.gamedata.gore.BloodSpatterGenerator.forWound(
                damage, player.getStats().getMaxHP(),
                com.bpm.minotaur.gamedata.gore.BloodSpatterGenerator.rgb(c.r, c.g, c.b), bloodRandom));
    }

    private void applyWeaponBlood(int intensity, GoreProfile profile) {
        if (intensity <= 0 || game == null || !(game.getScreen() instanceof com.bpm.minotaur.screens.GameScreen)) {
            return;
        }
        com.bpm.minotaur.screens.GameScreen gs = (com.bpm.minotaur.screens.GameScreen) game.getScreen();
        com.badlogic.gdx.graphics.Color bloodColor = (profile.primaryColor != null)
                ? profile.primaryColor
                : com.bpm.minotaur.gamedata.gore.GoreManager.UNIFIED_BLOOD_COLOR;
        gs.getWeaponOverlay().addBloodDecals(intensity, bloodColor, maze.getGoreManager().getRandomBloodTexture());
    }
}
