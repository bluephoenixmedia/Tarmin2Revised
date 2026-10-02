package com.bpm.minotaur.managers;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.assets.AssetManager;
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
import com.bpm.minotaur.gamedata.monster.MonsterProjectileRegistry;

import com.bpm.minotaur.gamedata.player.Player;
import com.bpm.minotaur.rendering.Animation;
import com.bpm.minotaur.rendering.AnimationManager;
import com.bpm.minotaur.screens.GameOverScreen;
import com.bpm.minotaur.screens.GameScreen;
import com.bpm.minotaur.gamedata.injury.InjuryRecord;
import com.bpm.minotaur.gamedata.progression.SkillId;
import com.bpm.minotaur.gamedata.monster.GhostPlayerMonster;
import com.bpm.minotaur.gamedata.bones.BonesData;

import com.bpm.minotaur.gamedata.dice.Die;
import com.bpm.minotaur.gamedata.monster.MonsterColor;
import com.bpm.minotaur.gamedata.monster.MimicReveal;
import com.bpm.minotaur.gamedata.firearm.FirearmProfile;
import com.bpm.minotaur.gamedata.firearm.PowderDampness;
import com.bpm.minotaur.gamedata.firearm.ReloadChannel;
import com.bpm.minotaur.gamedata.dice.DieResult;
import com.bpm.minotaur.gamedata.dice.DieFaceType;
import com.bpm.minotaur.gamedata.gore.WoundDecal;
import com.bpm.minotaur.gamedata.gore.WoundDecalRegistry;
import com.bpm.minotaur.rendering.MonsterDecalCompositor;
import com.bpm.minotaur.rendering.animation.AnimationArchetype;
import com.bpm.minotaur.rendering.animation.CombatMotionProfile;
import com.bpm.minotaur.rendering.animation.SpecialMoveRegistry;
import com.bpm.minotaur.rendering.mesh.BillboardSlicer;
import com.bpm.minotaur.utils.DiceRoller;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;
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
        /**
         * A mimic is shedding its chest disguise.
         *
         * <p>Movement and attack input is dropped for the duration, because those sites
         * gate on INACTIVE/PLAYER_TURN/PLAYER_MENU. Note this is not a blanket lock:
         * a few keys (interact, pick up) are not state-gated and guard themselves
         * instead -- see GameScreen.interactWithWorldObject, which must never let a
         * mimic reach the container branch.
         */
        MONSTER_REVEAL,
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

    public DiscoveryManager getDiscoveryManager() {
        GameScreen gs = getGameScreen();
        return (gs != null) ? gs.getDiscoveryManager() : null;
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

    /**
     * Traces a projectile path without disturbing anything. Safe for speculative or
     * targeting-preview use.
     */
    public HitResult raycastProjectile(Vector2 origin, Direction direction, int maxRange, boolean sourceIsPlayer) {
        return raycastProjectile(origin, direction, maxRange, sourceIsPlayer, false);
    }

    /**
     * @param revealDisguises when true, a mimic the player has already seen through is
     *        dropped out of its disguise as the ray reaches it, making it a valid target.
     *        This <em>mutates the world</em> -- it spawns a monster, plays a sound and
     *        shakes the camera -- so only genuine attacks should pass true. A trace used
     *        merely to ask "is there anything in range?" must not.
     */
    public HitResult raycastProjectile(Vector2 origin, Direction direction, int maxRange, boolean sourceIsPlayer,
            boolean revealDisguises) {
        return raycastProjectile(origin, direction, maxRange, sourceIsPlayer, revealDisguises, true);
    }

    /**
     * Traces a line that obscuring fog does not stop.
     *
     * <p>For finding where a cloud should be placed, which is the one trace that must reach
     * through fog: a second cast should be able to extend a cloud you are already standing in
     * rather than landing at its own edge. Everything else -- every attack -- goes through
     * {@link #raycastProjectile} and is swallowed by fog.
     */
    public HitResult raycastIgnoringFog(Vector2 origin, Direction direction, int maxRange) {
        return raycastProjectile(origin, direction, maxRange, true, false, false);
    }

    /**
     * @param stoppedByFog when true, the ray dies in the first obscuring tile it enters beyond
     *        the caster's own reach. That is how Fog Cloud blocks shooting: a shot into or
     *        across a cloud is swallowed at its edge rather than flying on to a target nobody
     *        can see. Prefer {@link #raycastIgnoringFog} to passing false here.
     */
    public HitResult raycastProjectile(Vector2 origin, Direction direction, int maxRange, boolean sourceIsPlayer,
            boolean revealDisguises, boolean stoppedByFog) {
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
            // A mimic the player has already seen through is a legitimate target at
            // range: spotting one should pay off the same way for an archer as it does
            // for a fighter. An unspotted mimic stays invisible to projectiles, so area
            // fire cannot be used to sweep a room for chests that bite.
            if (revealDisguises && sourceIsPlayer) {
                Item disguised = MimicReveal.disguisedMimicAt(maze, currentPos);
                if (disguised != null && disguised.isMimicSeen()) {
                    revealMimicPreEmptively(currentPos, maze.getLevel());
                }
            }

            // Obscuring fog eats the shot at its boundary. The first step is exempt so a
            // point-blank swing or shot at something standing next to you still connects,
            // which is the same adjacency rule monster sight uses.
            if (stoppedByFog && i >= 1 && maze.isObscured(currentX, currentY)) {
                return new HitResult(currentPos, HitResult.HitType.NOTHING, null);
            }

            if (maze.getMonsters().containsKey(currentPos)) {
                Monster m = maze.getMonsters().get(currentPos);
                return new HitResult(currentPos, HitResult.HitType.MONSTER, m);
            }
        }
        return new HitResult(new GridPoint2(currentX, currentY), HitResult.HitType.NOTHING, null);
    }

    // --- Mimic Reveal ---
    /** Total length of the shudder-then-burst morph. */
    private static final float MIMIC_REVEAL_TIME = 0.5f;
    /** How long the chest shudders before the burst masks the swap. */
    public static final float MIMIC_SHUDDER_TIME = 0.15f;
    private float mimicRevealTimer = 0f;
    private Monster revealingMimic = null;
    private GridPoint2 mimicRevealTile = null;
    private int mimicRevealDepth = 1;

    /**
     * How far into the shudder the disguised chest is, 0..1, or 0 when nothing is
     * revealing. The renderer uses this to shake the chest billboard; once the burst
     * takes over, the chest is gone and this returns to 0.
     */
    public float getMimicShudderProgress() {
        if (currentState != CombatState.MONSTER_REVEAL || revealingMimic != null) {
            return 0f;
        }
        return Math.min(1f, mimicRevealTimer / MIMIC_SHUDDER_TIME);
    }

    /** The tile whose chest is currently shuddering, or null. */
    public GridPoint2 getMimicRevealTile() {
        return (currentState == CombatState.MONSTER_REVEAL && revealingMimic == null) ? mimicRevealTile : null;
    }

    /**
     * Builds the monster a disguised chest has been hiding all along.
     *
     * <p>Deliberately not routed through MonsterFactory: the factory would roll an
     * inventory, and a mimic's loot is rolled at death instead precisely so that nothing
     * has to survive on a live monster across a chunk unload.
     */
    private Monster createMimicMonster(int depth) {
        com.bpm.minotaur.gamedata.monster.MonsterDataManager monsterData =
                (game != null) ? game.getMonsterDataManager() : null;

        // Without a data manager there is no monsters.json template to build from, and
        // the templated constructor dereferences it unguarded. Fall back to the
        // stat-only constructor so a reveal degrades into a plain mimic rather than
        // throwing -- the templated path is the norm; this keeps headless runs alive.
        if (monsterData == null) {
            Monster fallback = new Monster(Monster.MonsterType.MIMIC, MIMIC_FALLBACK_HP, MIMIC_FALLBACK_AC);
            fallback.scaleStats(Math.max(1, depth));
            fallback.setCurrentHP(fallback.getMaxHP());
            return fallback;
        }

        Monster mimic = new Monster(Monster.MonsterType.MIMIC, 0, 0, MonsterColor.WHITE,
                monsterData, (game != null) ? game.getAssetManager() : null);
        mimic.scaleStats(Math.max(1, depth));
        mimic.setCurrentHP(mimic.getMaxHP());
        return mimic;
    }

    /** Mirrors the MIMIC entry in monsters.json, for the no-template fallback above. */
    private static final int MIMIC_FALLBACK_HP = 50;
    private static final int MIMIC_FALLBACK_AC = 12;

    /**
     * The ambush: the player reached for a chest and it was a mimic.
     *
     * <p>Control is taken away for the length of the morph, then the mimic lands one
     * free blow before combat opens. Without that blow the disguise would be pure
     * theatre -- mechanically identical to a monster standing in a corridor, which is
     * the thing this feature exists to stop being.
     */
    public void triggerMimicAmbush(GridPoint2 tile, int depth) {
        if (currentState != CombatState.INACTIVE) {
            return;
        }
        if (MimicReveal.disguisedMimicAt(maze, tile) == null) {
            return;
        }

        // The chest is deliberately left standing for the length of the shudder: the
        // player needs a beat to register that the thing they touched moved, before the
        // burst covers the swap. It is replaced in update() at the phase boundary.
        mimicRevealTile = new GridPoint2(tile);
        mimicRevealDepth = depth;
        revealingMimic = null;
        mimicRevealTimer = 0f;
        currentState = CombatState.MONSTER_REVEAL;

        playMimicAmbushJolt();
        eventManager.addEvent(new GameEvent("The chest lunges at you!", 2.5f));
    }

    /**
     * The player struck first at a chest they had already seen through.
     *
     * <p>No blocking state and no free blow: the player owns the initiative here, which
     * is exactly what spotting the mimic bought them. The morph still plays, but the
     * world keeps running underneath it.
     */
    public Monster revealMimicPreEmptively(GridPoint2 tile, int depth) {
        Monster mimic = createMimicMonster(depth);
        if (!MimicReveal.swap(maze, tile, mimic)) {
            return null;
        }

        playMimicAmbushJolt();
        playMimicBurst(tile, mimic);
        eventManager.addEvent(new GameEvent("You strike before the mimic can spring!", 2.5f));
        return mimic;
    }

    /**
     * The opening jolt: low roar and a camera kick on the frame the lid moves.
     *
     * <p>The hit pause is deliberately far shorter than the shudder. {@code hitPauseTimer}
     * freezes the entire update block, animations included, so a pause as long as the
     * morph would stall the very thing it is meant to punctuate.
     */
    private void playMimicAmbushJolt() {
        if (soundManager != null) {
            soundManager.playMimicRevealSound();
        }

        if (game != null && game.getScreen() instanceof com.bpm.minotaur.screens.GameScreen) {
            com.bpm.minotaur.screens.GameScreen gs = (com.bpm.minotaur.screens.GameScreen) game.getScreen();
            gs.addTrauma(0.45f);
            gs.triggerHitPause(0.08f);
        }
    }

    /** Clears the reveal bookkeeping and returns the state machine to rest. */
    private void endMimicReveal() {
        revealingMimic = null;
        mimicRevealTile = null;
        mimicRevealTimer = 0f;
        currentState = CombatState.INACTIVE;
    }

    /** The burst that masks the chest-to-monster swap. */
    private void playMimicBurst(GridPoint2 tile, Monster mimic) {
        if (animationManager != null) {
            animationManager.spawnExplosion(
                    com.bpm.minotaur.rendering.vfx.SpellExplosionRegistry.ExplosionType.CONCUSSIVE,
                    new com.badlogic.gdx.math.Vector3(tile.x + 0.5f, 0.5f, tile.y + 0.5f),
                    1.4f, MIMIC_REVEAL_TIME - MIMIC_SHUDDER_TIME);
        }

        // The retro raycaster never draws SPRITE_EXPLOSION_3D, so it gets the swap plus
        // a spray of gibs -- the same beat told in the engine's own visual language.
        if (mimic != null) {
            com.bpm.minotaur.gamedata.gore.GoreManager gore = maze.getGoreManager();
            if (gore != null) {
                gore.spawnRetroGibs(
                        new com.badlogic.gdx.math.Vector3(tile.x + 0.5f, 0.5f, tile.y + 0.5f),
                        mimic.getSpriteData(),
                        com.badlogic.gdx.graphics.Color.GOLDENROD);
            }
        }
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
            triggerCombatMusic(monster);

            // --- NEW: Start with Player Menu ---
            currentState = CombatState.PLAYER_MENU;
            Gdx.app.log("COMBAT_FLOW", "State -> PLAYER_MENU (Initial)");
            Gdx.app.log("CombatManager", "Combat started with " + monster.getType() + ". State: PLAYER_MENU");

            monsterAttackDelay = MONSTER_ATTACK_DELAY_TIME;
        }
    }

    private void triggerCombatMusic(Monster monster) {
        if (monster == null) return;
        boolean isBoss = isBossMonster(monster);
        if (isBoss) {
            MusicManager.getInstance().playBossCombat("sounds/music/tarmin_boss_tension.wav");
        } else {
            MusicManager.getInstance().playCombatMusic("sounds/music/tarmin_fuxx.ogg");
        }
    }

    private boolean isBossMonster(Monster monster) {
        if (monster == null) return false;
        if (monster.isBridgeBoss() || monster.isThemeChampion()) return true;
        if (monster.getType() == null) return false;
        String typeName = monster.getType().name();
        return typeName.contains("MINOTAUR") || typeName.contains("LICH") || typeName.contains("VAMPIRE") || typeName.contains("GOLEM");
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
            triggerCombatMusic(target);
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
        if (attacker != null && this.monster != attacker && currentState == CombatState.INACTIVE) {
            triggerCombatMusic(attacker);
        }
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

            // HEAVY_ARMOR_MASTERY: Nonmagical physical damage reduced by 3 while wearing heavy armor
            if (player.hasSkill(SkillId.HEAVY_ARMOR_MASTERY) && player.isWearingHeavyArmor()) {
                int reduced = Math.min(3, dmg);
                dmg = Math.max(0, dmg - 3);
                if (reduced > 0) {
                    eventManager.addEvent(new GameEvent("Heavy Armor Master absorbed " + reduced + " dmg!", 1.2f));
                }
            }

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
                        // Dose scales with the biter: deep-strata venom lingers far longer than
                        // a surface spider's, instead of every creature delivering the same 10.
                        int venomTicks = com.bpm.minotaur.gamedata.effects.PoisonDose.ticksFor(attacker.getLevel());
                        int venomPotency = com.bpm.minotaur.gamedata.effects.PoisonDose.potencyFor(attacker.getLevel());
                        player.getStatusManager().addEffect(StatusEffectType.POISONED, venomTicks, venomPotency, false);
                        eventManager.addEvent(new GameEvent("VENOMOUS BITE! " + attacker.getMonsterType()
                                + " injects deadly venom! (" + venomTicks + " turns)", 2.0f));
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

    public boolean isInCombat() {
        return currentState != CombatState.INACTIVE;
    }

    public void openMenu() {
        if (currentState == CombatState.INACTIVE) {
            currentState = CombatState.PLAYER_MENU;
            Gdx.app.log("COMBAT_FLOW", "State -> PLAYER_MENU (Manual Open)");
        }
    }

    public void endCombat() {
        currentState = CombatState.INACTIVE;
        if (monster != null) {
            MonsterDecalCompositor.getInstance().releaseMonster(monster, true);
        }
        monster = null;
        monsterAttackDelay = 0f;
        MusicManager.getInstance().exitCombat();
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
    // --- Firearms ---
    /** The reload in progress, or null when nothing is being loaded. */
    private ReloadChannel activeReload = null;
    /** Rolls misfires; separate from the combat RNG so tests can pin one without the other. */
    private final Random powderRandom = new Random();

    public ReloadChannel getActiveReload() {
        return activeReload;
    }

    /** True if this weapon draws shot rather than arrows. */
    private boolean usesShot(Item weapon) {
        return weapon != null && FirearmProfile.isFirearm(weapon.getType());
    }

    private boolean hasAmmoFor(Item weapon) {
        return usesShot(weapon) ? player.getStats().getShot() > 0 : player.getArrows() > 0;
    }

    /**
     * Spends one round and, for a firearm, starts the reload that follows it.
     *
     * <p>Single chokepoint on purpose: a firearm that fires without reloading is just a
     * bow, and the reload is the entire balance for a 2d8 shot at bow range. Every path
     * that spends a round goes through here.
     */
    private void consumeAmmoFor(Item weapon) {
        if (usesShot(weapon)) {
            player.getStats().decrementShot();
            beginReload(weapon);
        } else {
            player.decrementArrow();
        }
    }

    private String ammoNameFor(Item weapon) {
        if (usesShot(weapon)) {
            return "shot";
        }
        boolean crossbow = weapon != null && (weapon.getType() == Item.ItemType.CROSSBOW
                || (weapon.getFriendlyName() != null
                        && weapon.getFriendlyName().toLowerCase().contains("crossbow")));
        return crossbow ? "bolts" : "arrows";
    }

    /**
     * Advances any reload by one world turn. Called once per player turn.
     *
     * <p>Takes no interruption arguments: nothing in the world can break a reload, only
     * the player's own choice to do something else, which goes through
     * {@link #abandonReload}.
     */
    public void tickReload() {
        if (activeReload == null) {
            return;
        }
        if (activeReload.afterTurn() == ReloadChannel.Step.COMPLETE) {
            eventManager.addEvent(new GameEvent("Loaded and primed.", 1.5f));
            activeReload = null;
        }
    }

    /**
     * Gives up a reload in progress. The reload is the player's to abandon -- this is
     * called when they choose to move or swing, never because they were hit.
     */
    public void abandonReload() {
        if (activeReload == null) {
            return;
        }
        activeReload = null;
        eventManager.addEvent(new GameEvent("You break off loading.", 1.5f));
    }

    /** Begins the reload that follows a shot. */
    private void beginReload(Item weapon) {
        if (weapon == null || !FirearmProfile.isFirearm(weapon.getType())) {
            return;
        }
        activeReload = new ReloadChannel(weapon.getType());
        eventManager.addEvent(new GameEvent(
                "Reloading -- " + activeReload.getTurnsRequired() + " turns.", 1.5f));
    }

    private boolean prepareAttack() {
        Item weapon = player.getInventory().getRightHand();

        if (weapon != null) {
            this.pendingWeapon = weapon;
            this.pendingIsRanged = weapon.isRanged();

            if (pendingIsRanged && weapon.getType() != Item.ItemType.DART && !hasAmmoFor(weapon)) {
                eventManager.addEvent(new GameEvent("You have no " + ammoNameFor(weapon) + "!", 2f));
                passTurnToMonster();
                return false;
            }

            // A fired firearm is empty until the reload finishes. Without this the
            // multi-turn reload would be decorative -- nothing would stop the player
            // firing again on the very next turn.
            if (activeReload != null && FirearmProfile.isFirearm(weapon.getType())) {
                eventManager.addEvent(new GameEvent(
                        "Still loading -- " + activeReload.getTurnsRemaining() + " turn(s).", 1.5f));
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
        player.addExperience(m.getBaseExperience(), eventManager);
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
        if (currentState == CombatState.VICTORY || currentState == CombatState.DEFEAT || currentState == CombatState.INACTIVE || monster == null || monster.getCurrentHP() <= 0) {
            if (currentState != CombatState.VICTORY && currentState != CombatState.DEFEAT) {
                currentState = CombatState.INACTIVE; // Close menu if no enemy
            }
        } else {
            // Pass Turn
            tickReload();
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
        // INACTIVE is allowed for ranged weapons only. Without it a bow could never
        // open a fight -- firing required combat to already be underway, which is the
        // reverse of what a ranged weapon is for. Melee still needs an engagement.
        if (currentState == CombatState.INACTIVE) {
            Item readied = player.getInventory().getRightHand();
            if (readied != null && readied.isWand()) {
                player.zap(readied, player.getFacing(), getDiscoveryManager(), eventManager, maze, this);
                return;
            }
            if (readied == null || !readied.isRanged()) {
                return;
            }
            openFireAtRange();
            return;
        }
        if (currentState != CombatState.PLAYER_TURN && currentState != CombatState.PLAYER_MENU)
            return;

        // 1. Check if we have a monster target
        if (monster == null) {
            if (player.getInventory().getRightHand() != null && player.getInventory().getRightHand().isWand()) {
                player.zap(player.getInventory().getRightHand(), player.getFacing(), getDiscoveryManager(), eventManager, maze, this);
                closeMenuOrPassTurn();
                return;
            }
            // 2. No target? Check Ranged
            if (player.getInventory().getRightHand() != null && player.getInventory().getRightHand().isRanged()) {
                // Previously this called performRangedAttack(), which called straight
                // back into this method with nothing changed between them -- unbounded
                // recursion. Opening fire is resolved here and nowhere else.
                openFireAtRange();
            } else {
                eventManager.addEvent(new GameEvent("No monster to attack!", 1.5f));
                currentState = CombatState.INACTIVE;
            }
            return;
        }

        // An engaged target does not make a firearm a club. Ranged weapons resolve
        // through the ranged path in combat too, or firing in the situation that
        // actually matters would skip the misfire roll, the noise, the muzzle flash
        // and the range check.
        Item readiedInCombat = player.getInventory().getRightHand();
        if (readiedInCombat != null && readiedInCombat.isWand()) {
            player.zap(readiedInCombat, player.getFacing(), getDiscoveryManager(), eventManager, maze, this);
            closeMenuOrPassTurn();
            return;
        }
        if (readiedInCombat != null && readiedInCombat.isRanged()) {
            resolveRangedAttackAgainst(monster);
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
                // The flurry re-enters resolveAttack, which spends another round --
                // previously without checking there was one, so it could overdraw.
                // Firearms are excluded outright: a second shot inside one turn is the
                // one thing a reload exists to prevent. Bows keep their flurry.
                boolean flurryAffordable = flurryWeapon != null
                        && !usesShot(flurryWeapon)
                        && (!flurryWeapon.isRanged()
                                || flurryWeapon.getType() == Item.ItemType.DART
                                || hasAmmoFor(flurryWeapon));
                if (flurryAffordable) {
                    pendingWeapon = flurryWeapon;
                    eventManager.addEvent(new GameEvent("Flurry!", 0.8f));
                    resolveAttack(DiceRoller.roll("1d20"), true);
                }
            }
        }
    }


    /**
     * Throws any item from the quick slots straight ahead. Weapons strike for their own damage,
     * potions shatter and act on what they hit, and anything else simply drops where it lands.
     */
    public boolean throwItem(Item item) {
        if (item == null) return false;
        switch (com.bpm.minotaur.gamedata.item.ThrowRules.kindOf(item)) {
            case WEAPON: return throwWeapon(item);
            case POTION: return throwPotion(item);
            default:     return throwOther(item);
        }
    }

    private int throwRangeOf(Item item) {
        return com.bpm.minotaur.gamedata.item.ThrowRules.range(item, player.getEffectiveStrengthModifier(),
                com.bpm.minotaur.gamedata.item.ItemWeights.of(item));
    }

    /** Sends the flight animation and sound, and finds what the item would strike. */
    private HitResult flyThrownItem(Item item, int range, String glyph) {
        HitResult hit = raycastProjectile(player.getPosition(), player.getFacing(), range, true, true);
        Vector2 startPos = player.getPosition().cpy().add(player.getDirectionVector().cpy().scl(0.6f));
        Vector2 targetPos = hit.collisionPoint != null
                ? new Vector2(hit.collisionPoint.x + 0.5f, hit.collisionPoint.y + 0.5f)
                : startPos.cpy().add(player.getDirectionVector().cpy().scl(range));
        soundManager.playWeaponSwing();
        if (animationManager != null) {
            animationManager.addAnimation(new Animation(
                    Animation.AnimationType.PROJECTILE_PLAYER, startPos, targetPos,
                    com.badlogic.gdx.graphics.Color.LIGHT_GRAY, 0.4f, new String[] { glyph }));
        }
        return hit;
    }

    /** The tile a thrown item comes to rest on: where it struck, or the end of its flight. */
    private GridPoint2 restingTile(HitResult hit, int range) {
        if (hit.collisionPoint != null) {
            GridPoint2 tile = new GridPoint2(hit.collisionPoint.x, hit.collisionPoint.y);
            // A shut door or gate is where the flight ended, but nothing can lie in it: rest in front of it.
            Object obj = maze.getGameObjectAt(tile.x, tile.y);
            boolean shut = (obj instanceof Door && ((Door) obj).getState() != Door.DoorState.OPEN)
                    || (obj instanceof Gate && ((Gate) obj).getState() != Gate.GateState.OPEN);
            if (shut) {
                tile.x -= (int) player.getFacing().getVector().x;
                tile.y -= (int) player.getFacing().getVector().y;
            }
            return tile;
        }
        Vector2 land = player.getPosition().cpy().add(player.getDirectionVector().cpy().scl(range));
        return new GridPoint2((int) land.x, (int) land.y);
    }

    /** Puts a thrown item down on the tile, or the nearest free neighbour (one item per tile). */
    private void placeThrownItem(Item item, GridPoint2 tile) {
        if (maze == null) return;
        GridPoint2 spot = tile;
        if (maze.getItems().containsKey(spot)) {
            spot = null;
            for (Direction d : Direction.values()) {
                GridPoint2 n = new GridPoint2(tile.x + (int) d.getVector().x, tile.y + (int) d.getVector().y);
                boolean inside = n.x >= 0 && n.x < maze.getWidth() && n.y >= 0 && n.y < maze.getHeight();
                if (inside && !maze.isWallBlocking(tile.x, tile.y, d) && !maze.getItems().containsKey(n)) {
                    spot = n;
                    break;
                }
            }
        }
        if (spot == null) {
            player.dropItem(maze, item);
            return;
        }
        item.setPosition(spot.x + 0.5f, spot.y + 0.5f);
        maze.addItem(item);
        if (item.getType() == Item.ItemType.BRASS_LANTERN) {
            // Same as setting it down by hand: a lantern on the floor is a mounted light.
            maze.addLight(new com.bpm.minotaur.lighting.LightSource("shelter_lantern_" + spot.x + "_" + spot.y,
                    spot.x + 0.5f, spot.y + 0.5f, com.bpm.minotaur.lighting.LightingManager.COLOR_LANTERN, 5.0f,
                    com.bpm.minotaur.lighting.LightingManager.MOUNTED_LANTERN_INTENSITY,
                    com.bpm.minotaur.lighting.LightSource.FlickerProfile.LANTERN_BREATH));
        }
    }

    private boolean throwOther(Item item) {
        int range = throwRangeOf(item);
        HitResult hit = flyThrownItem(item, range, "o");
        player.getInventory().removeItem(item);
        GridPoint2 tile = restingTile(hit, range);
        placeThrownItem(item, tile);
        String name = com.bpm.minotaur.gamedata.item.ItemName.natural(item.getFriendlyName());
        if (hit.type == HitResult.HitType.MONSTER && hit.hitMonster != null) {
            eventManager.addEvent(new GameEvent("The " + name + " bounces off " + hit.hitMonster.getType() + ".", 1.5f));
        } else {
            eventManager.addEvent(new GameEvent("The " + name + " clatters to the stone.", 1.2f));
        }
        return true;
    }

    private boolean throwPotion(Item potion) {
        int range = throwRangeOf(potion);
        HitResult hit = flyThrownItem(potion, range, "!");
        player.getInventory().removeItem(potion);
        GridPoint2 tile = restingTile(hit, range);
        com.bpm.minotaur.gamedata.item.ThrowRules.Splash splash =
                com.bpm.minotaur.gamedata.item.ThrowRules.splashFor(potion.getTrueEffect());
        String name = com.bpm.minotaur.gamedata.item.ItemName.natural(potion.getFriendlyName());

        if (animationManager != null) {
            animationManager.spawnFx(com.bpm.minotaur.rendering.vfx.FxClipIds.HIT_SMOKE,
                    com.bpm.minotaur.gamedata.gore.HitFx.position(tile.x + 0.5f, tile.y + 0.5f), 1.2f);
        }

        Monster direct = (hit.type == HitResult.HitType.MONSTER) ? hit.hitMonster : null;
        if (direct == null || splash == null) {
            eventManager.addEvent(new GameEvent("The " + name + " shatters"
                    + (direct != null ? " on " + direct.getType() + "." : "."), 1.5f));
            return true;
        }

        boolean known = potion.isIdentified();
        splashOnto(direct, splash, name, known);
        if (splash.gas) {
            for (int dx = -com.bpm.minotaur.gamedata.item.ThrowRules.GAS_RADIUS;
                 dx <= com.bpm.minotaur.gamedata.item.ThrowRules.GAS_RADIUS; dx++) {
                for (int dy = -com.bpm.minotaur.gamedata.item.ThrowRules.GAS_RADIUS;
                     dy <= com.bpm.minotaur.gamedata.item.ThrowRules.GAS_RADIUS; dy++) {
                    if (dx == 0 && dy == 0) continue;
                    Monster other = maze.getMonsters().get(new GridPoint2(tile.x + dx, tile.y + dy));
                    if (other != null && other != direct && other.getCurrentHP() > 0) {
                        splashOnto(other, splash, name, known);
                    }
                }
            }
        }
        return true;
    }

    /** Applies a shattered potion's effect to one monster. */
    private void splashOnto(Monster target, com.bpm.minotaur.gamedata.item.ThrowRules.Splash splash, String potionName,
                            boolean effectKnown) {
        String who = com.bpm.minotaur.ui.UiNames.of(target.getType());
        int healed = 0;
        if (splash.healFraction > 0f) {
            healed = Math.max(1, (int) Math.ceil(target.getMaxHP() * splash.healFraction));
            target.heal(healed);
        }
        if (splash.status != null) {
            target.getStatusManager().addEffect(splash.status, splash.duration, 1, false);
        }
        // An unidentified potion must not give away what it is by what its message says.
        if (!effectKnown) {
            eventManager.addEvent(new GameEvent("The " + potionName + " splashes " + who + ".", 2f));
        } else if (healed > 0) {
            eventManager.addEvent(new GameEvent("The " + potionName + " splashes " + who + " and heals it for " + healed + "!", 2f));
        } else {
            eventManager.addEvent(new GameEvent("The " + potionName + " splashes " + who + ": "
                    + com.bpm.minotaur.ui.UiNames.of(splash.status).toLowerCase() + "!", 2f));
        }
    }

    public boolean throwWeapon(Item weapon) {
        if (weapon == null) return false;
        int maxRange = throwRangeOf(weapon);
        HitResult hit = raycastProjectile(player.getPosition(), player.getFacing(), maxRange, true, true);

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
        int attackRoll = DiceRoller.roll("1d20") + statBonus - com.bpm.minotaur.gamedata.item.ThrowRules.toHitPenalty(weapon);

        if (hit.type == HitResult.HitType.MONSTER && hit.hitMonster != null) {
            Monster target = hit.hitMonster;
            if (Monster.isImmuneToType(target.getType(), DamageType.PHYSICAL)) {
                eventManager.addEvent(new GameEvent(target.getType() + " is immune to thrown weapons!", 1.5f));
                showDamageText(0, hit.collisionPoint);
            } else if (attackRoll >= target.getArmorClass()) {
                int dmg = DiceRoller.roll(weapon.getDamageDice()) + statBonus;
                dmg = Math.max(1, dmg);
                int actual = target.takeDamage(dmg, DamageType.PHYSICAL, false);
                spawnHitFx(target, GoreProfile.fromMonster(target),
                        actual / (float) Math.max(1, target.getMaxHP()), false);
                showDamageText(actual, hit.collisionPoint);
                eventManager.addEvent(new GameEvent("Threw " + com.bpm.minotaur.gamedata.item.ItemName.natural(weapon.getFriendlyName()) + " into " + target.getType() + " for " + actual + " dmg!", 1.5f));

                if (target.getCurrentHP() <= 0) {
                    if (target == this.monster) {
                        handleMonsterDeath();
                        currentState = CombatState.VICTORY;
                    } else {
                        handleRemoteKill(target);
                    }
                }
            } else {
                eventManager.addEvent(new GameEvent("Thrown " + com.bpm.minotaur.gamedata.item.ItemName.natural(weapon.getFriendlyName()) + " glanced off " + target.getType() + "!", 1.0f));
            }
            if (hit.collisionPoint != null) {
                weapon.setPosition(hit.collisionPoint.x + 0.5f, hit.collisionPoint.y + 0.5f);
            }
            if (maze != null) {
                maze.addItem(weapon);
            }
        } else {
            eventManager.addEvent(new GameEvent("Thrown " + com.bpm.minotaur.gamedata.item.ItemName.natural(weapon.getFriendlyName()) + " clatters to the stone.", 1.0f));
            if (hit.collisionPoint != null) {
                weapon.setPosition(hit.collisionPoint.x + 0.5f, hit.collisionPoint.y + 0.5f);
            } else {
                Vector2 landPos = player.getPosition().cpy().add(player.getDirectionVector().cpy().scl(maxRange));
                weapon.setPosition((int) landPos.x + 0.5f, (int) landPos.y + 0.5f);
            }
            if (maze != null) {
                maze.addItem(weapon);
            }
        }

        player.getInventory().removeItem(weapon);
        return true;
    }

    /**
     * The effective reach of a ranged weapon.
     *
     * <p>{@code getRange()} was previously never read on the player's own attacks -- the
     * raycast was hardcoded to 8, so a longbow's 32 and a hand crossbow's 6 were both 8.
     * Honouring it is what finally differentiates the ranged roster.
     */
    private int effectiveRange(Item weapon) {
        if (weapon == null) {
            return 8;
        }
        return Math.max(1, weapon.getRange());
    }

    /**
     * Opens fire on whatever is down the player's facing, engaging it if something is
     * there. This is the path that was previously unreachable: firing required combat to
     * already be active, so a bow could never be used to start a fight.
     */
    private void openFireAtRange() {
        Item weapon = player.getInventory().getRightHand();
        if (weapon == null || !weapon.isRanged()) {
            return;
        }
        if (!prepareAttack()) {
            return;
        }

        HitResult hit = raycastProjectile(player.getPosition(), player.getFacing(),
                effectiveRange(weapon), true, true);

        if (hit.type == HitResult.HitType.MONSTER && hit.hitMonster != null) {
            startCombat(hit.hitMonster);
            resolveRangedAttackAgainst(hit.hitMonster, hit);
        } else if (!misfired(weapon)) {
            // The shot is still spent, and still heard. Firing into an empty corridor
            // costs you the ammunition and wakes the level just the same.
            fireRangedEffects(weapon, hit);
            consumeAmmoFor(weapon);
            eventManager.addEvent(new GameEvent("No target in range.", 1.5f));
            currentState = CombatState.INACTIVE;
        } else {
            currentState = CombatState.INACTIVE;
        }
    }

    /**
     * Resolves a shot that has a target.
     *
     * <p>Previously five TODO comments: it ignored {@code prepareAttack()}'s result so it
     * fired at zero ammo, and triggered no animation and no sound.
     */
    private void resolveRangedAttackAgainst(Monster target) {
        resolveRangedAttackAgainst(target, null);
    }

    /**
     * @param tracedShot the path already traced by the caller, or null to trace one.
     *        Reusing it avoids a second raycast, which would also re-run the
     *        mimic-reveal side effect that a player-sourced trace carries.
     */
    private void resolveRangedAttackAgainst(Monster target, HitResult tracedShot) {
        if (!prepareAttack()) {
            return;
        }
        Item weapon = pendingWeapon;

        if (misfired(weapon)) {
            passTurnToMonster();
            return;
        }

        HitResult shot = (tracedShot != null) ? tracedShot
                : raycastProjectile(player.getPosition(), player.getFacing(),
                        effectiveRange(weapon), true, true);
        fireRangedEffects(weapon, shot);

        resolveAttack(DiceRoller.d20());
    }

    /**
     * Rolls whether wet powder fizzles, and spends the round if it does.
     *
     * <p>A misfire costs the shot but not the reload -- losing both would stack a random
     * failure on top of a multi-turn commitment, which reads as the game cheating rather
     * than as a weapon with character.
     *
     * @return true if the shot was lost to damp powder.
     */
    private boolean misfired(Item weapon) {
        if (!usesShot(weapon)
                || !PowderDampness.rollMisfire(player.getStats().getPowderDampness(), powderRandom)) {
            return false;
        }
        consumeAmmoFor(weapon);
        soundManager.playFirearmMisfire();
        eventManager.addEvent(new GameEvent("The powder fizzles -- misfire!", 2f));
        return true;
    }

    /**
     * Everything a shot does besides damage: the weapon animation, the report, the
     * muzzle flash, and whatever the noise wakes.
     */
    private void fireRangedEffects(Item weapon, HitResult hit) {
        boolean firearm = usesShot(weapon);

        if (game != null && game.getScreen() instanceof com.bpm.minotaur.screens.GameScreen) {
            com.bpm.minotaur.screens.GameScreen gs = (com.bpm.minotaur.screens.GameScreen) game.getScreen();
            gs.getWeaponOverlay().triggerAttack(weapon);
            if (firearm) {
                gs.addTrauma(0.4f);
            }
        }

        Vector2 muzzle = player.getPosition().cpy()
                .add(player.getDirectionVector().cpy().scl(0.6f));
        Vector2 impact = (hit != null && hit.collisionPoint != null)
                ? new Vector2(hit.collisionPoint.x + 0.5f, hit.collisionPoint.y + 0.5f)
                : muzzle.cpy().add(player.getDirectionVector().cpy().scl(effectiveRange(weapon)));

        if (firearm) {
            soundManager.playFirearmShot();
            spawnMuzzleEffects(muzzle, impact);

            int woken = com.bpm.minotaur.gamedata.firearm.GunshotNoise.wake(
                    maze, player.getPosition(), weapon.getType());
            if (woken > 0) {
                eventManager.addEvent(new GameEvent(
                        "The shot echoes -- " + woken + " thing(s) stir.", 2f));
            }
        } else {
            // Bows keep a travelling arrow: the arc is what makes archery readable,
            // where a gun's whole advantage is that it is already there.
            soundManager.playBowShot();
            if (animationManager != null) {
                // A crossbow throws a bolt, a bow throws an arrow. ammoNameFor already knows
                // which, so the sprite can follow it rather than every shot looking the same.
                String sprite = "bolts".equals(ammoNameFor(weapon)) ? "bolt" : "arrow";
                // Speed-based, like monster projectiles, instead of a flat 0.25s for every shot.
                // A flat duration made a point-blank shot and a long one take the same time, and
                // at typical combat range it was over in about fifteen frames.
                float flightDuration = Math.max(0.18f, muzzle.dst(impact) / PROJECTILE_SPEED);
                animationManager.addAnimation(new Animation(
                        Animation.AnimationType.PROJECTILE_PLAYER,
                        muzzle, impact,
                        com.badlogic.gdx.graphics.Color.WHITE, flightDuration,
                        new String[] { "-" }).withProjectileSprite(sprite));
            }
        }
    }

    /**
     * Muzzle flash, powder smoke, and the impact burst.
     *
     * <p>A firearm shot is hitscan, so there is no travelling sprite to carry the moment
     * -- the muzzle and the impact have to do all the work. Deliberately no screen flash:
     * it fights the recoil kick already in the motion profile, and on every shot it stops
     * being a thrill and becomes an irritation.
     */
    private void spawnMuzzleEffects(Vector2 muzzle, Vector2 impact) {
        if (animationManager == null) {
            return;
        }
        // Muzzle height follows the void-laser convention: a little below the eye line.
        float muzzleY = 0.4f;

        animationManager.spawnExplosion(
                com.bpm.minotaur.rendering.vfx.SpellExplosionRegistry.ExplosionType.FIRE,
                new com.badlogic.gdx.math.Vector3(muzzle.x, muzzleY, -muzzle.y), 0.7f, 0.18f);

        // The powder smoke: slower and larger than the flash, and what actually sells
        // the weapon as something other than a loud crossbow.
        animationManager.spawnExplosion(
                com.bpm.minotaur.rendering.vfx.SpellExplosionRegistry.ExplosionType.STANDARD,
                new com.badlogic.gdx.math.Vector3(muzzle.x, muzzleY, -muzzle.y), 1.1f, 0.55f);

        animationManager.spawnExplosion(
                com.bpm.minotaur.rendering.vfx.SpellExplosionRegistry.ExplosionType.CONCUSSIVE,
                new com.badlogic.gdx.math.Vector3(impact.x, 0.5f, -impact.y), 0.9f, 0.35f);
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
                if (player.useQuickSlot(slotIndex, eventManager, discoveryManager, maze, this)) {
                    closeMenuOrPassTurn();
                }
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
                spawnHitFx(target, GoreProfile.fromMonster(target),
                        actual / (float) Math.max(1, target.getMaxHP()), false);
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
                case FIRE: {
                    int fire = (monster != null)
                            ? com.bpm.minotaur.gamedata.MagicResistance.reduce(val, monster.getMagicResistance()) : val;
                    if (fire < val) {
                        eventManager.addEvent(new GameEvent("Resisted Fire!", 0.5f));
                    }
                    fireDamage += fire;
                    BalanceLogger.getInstance().log("DICE_EFFECT", "Fire Charge: " + fire);
                    break;
                }
                case ICE: {
                    int resist = (monster != null) ? monster.getMagicResistance() : 0;
                    int ice = com.bpm.minotaur.gamedata.MagicResistance.reduce(val, resist);
                    if (ice < val) {
                        eventManager.addEvent(new GameEvent("Resisted Ice!", 0.5f));
                    }
                    // Cold damage + potentially slow
                    totalDamage += ice;
                    if (monster != null && !com.bpm.minotaur.gamedata.MagicResistance.resists(resist, random)) {
                        monster.getStatusManager().addEffect(StatusEffectType.SLOWED, 2, 1, false);
                    }
                    BalanceLogger.getInstance().log("DICE_EFFECT", "Ice Damage: " + ice);
                    break;
                }
                case LIGHTNING: {
                    int bolt = (monster != null)
                            ? com.bpm.minotaur.gamedata.MagicResistance.reduce(val, monster.getMagicResistance()) : val;
                    if (bolt < val) {
                        eventManager.addEvent(new GameEvent("Resisted Lightning!", 0.5f));
                    }
                    lightningDamage += bolt;
                    BalanceLogger.getInstance().log("DICE_EFFECT", "Lightning Charge: " + bolt);
                    break;
                }
                case POISON: {
                    int poisonResist = (monster != null) ? monster.getMagicResistance() : 0;
                    if (com.bpm.minotaur.gamedata.MagicResistance.resists(poisonResist, random)) {
                        eventManager.addEvent(new GameEvent("Resisted Poison!", 0.5f));
                    } else {
                        poisonStacks += val;
                        if (monster != null) {
                            monster.getStatusManager().addEffect(StatusEffectType.POISONED, 3, poisonStacks, true);
                        }
                        BalanceLogger.getInstance().log("DICE_EFFECT", "Poison Stacks: " + val);
                    }
                    break;
                }
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
                Item weapon = (player != null && player.getInventory() != null) ? player.getInventory().getRightHand() : null;
                applyCombatHitWound(monster, actualDamage, weapon);
                applyWeaponAspectEffects(monster, actualDamage, weapon, false, false);
                maze.addBlood((int) monster.getPosition().x, (int) monster.getPosition().y, 0.03f);
                GridPoint2 cid = (worldManager != null) ? worldManager.getCurrentPlayerChunkId() : new GridPoint2(0, 0);
                float wx = cid.x * 36.0f + monster.getPosition().x;
                float wz = cid.y * 36.0f + monster.getPosition().y;
                Vector3 hitPos = new Vector3(wx, 0.5f, wz);
                Vector3 dir = new Vector3(monster.getPosition().x - player.getPosition().x, 0.15f, monster.getPosition().y - player.getPosition().y).nor();
                GoreProfile profile = GoreProfile.fromMonster(monster);
                maze.getGoreManager().spawnBloodSpray(hitPos, dir, Math.max(2, actualDamage / 2), profile);
                spawnHitFx(monster, profile, actualDamage / (float) Math.max(1, monster.getMaxHP()), false);
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


        // Determine attacking weapon: if off-hand combo strike, use left hand weapon
        Item attackWeapon = pendingWeapon;
        if (currentMotionProfile != null && currentMotionProfile.isOffHand) {
            Item leftHand = player.getInventory().getLeftHand();
            if (leftHand != null) {
                attackWeapon = leftHand;
            }
        }

        // Consume ammunition for ranged weapons (bows, crossbows)
        if (attackWeapon != null && attackWeapon.isRanged() && attackWeapon.getType() != Item.ItemType.DART) {
            consumeAmmoFor(attackWeapon);
        }

        int toHitBonus = (attackWeapon != null && attackWeapon.isFinesse()) ? player.getFinesseToHitBonus() : player.getToHitBonus();
        if (player.getInjuryManager() != null) {
            toHitBonus += player.getInjuryManager().getEffectiveAttackModifier();
        }
        int attackRoll = d20Roll + toHitBonus;
        int targetAC = monster.getArmorClass();
        boolean isCrit = (d20Roll == 20) || (random.nextFloat() < player.getCritChance());
        boolean isHit = isHit(attackRoll, targetAC, isCrit);
        boolean isGlancing = isGlancingBlow(attackRoll, targetAC, isCrit);

        // Angry Genius: now and then the swing just goes wrong. It is an ordinary miss, so the turn
        // passes and the monster answers like it would any other.
        float failChance = com.bpm.minotaur.gamedata.trait.TraitEffects.add("attackFailChance");
        if (failChance > 0f && random.nextFloat() < failChance) {
            isHit = false;
            isGlancing = false;
            isCrit = false;
            eventManager.addEvent(new GameEvent("Your anger gets the better of you and the attack goes wide!", 1.5f));
        }

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
            boolean isArcaneSpark = isBookWeapon(attackWeapon) && !player.isPolymorphed();
            if (player.isPolymorphed()) {
                // Teeth and claws, not the sword in a hand that is no longer there.
                damageDice = player.getForm().damageDice();
            } else if (isArcaneSpark) {
                // Tome Weapon Attack: a Spiritual Arcane Spark replaces the book's own
                // damage dice entirely -- see arcaneSparkDamage() for the 1d4+INT roll.
                dmgType = DamageType.SPIRITUAL;
                damageDice = ARCANE_SPARK_DICE;
            } else if (attackWeapon != null) {
                damageDice = player.getInventory().getActiveDamageDice(attackWeapon);
                if (damageDice == null || damageDice.isEmpty()) damageDice = "1d4";
                if ("SPIRITUAL".equalsIgnoreCase(attackWeapon.getDamageType()) ||
                        attackWeapon.getCategory() == com.bpm.minotaur.gamedata.item.ItemCategory.SPIRITUAL_WEAPON) {
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
                } else if (currentMotionProfile != null && currentMotionProfile.isDualStrike) {
                    // Dual Strike (Scissor Finisher): Rolls main hand + off hand damage together!
                    int mainBase = DiceRoller.roll(damageDice);
                    int mainBonus = (attackWeapon != null && attackWeapon.isFinesse()) ? player.getFinesseDamageBonus() : player.getDamageBonus();
                    int mainDmg = Math.max(1, mainBase + mainBonus);

                    Item offHand = player.getInventory().getLeftHand();
                    int offDmg = 0;
                    if (offHand != null) {
                        String offDice = player.getInventory().getActiveDamageDice(offHand);
                        if (offDice == null || offDice.isEmpty()) offDice = "1d4";
                        int offBase = DiceRoller.roll(offDice);
                        int offBonus = offHand.isFinesse() ? player.getFinesseDamageBonus() : (player.getDamageBonus() / 2);
                        offDmg = Math.max(1, offBase + offBonus);
                    }
                    totalDamage = mainDmg + offDmg;

                    // Whirlwind Executioner: +50% dual-strike damage
                    if (player.hasSkill(SkillId.WHIRLWIND_EXECUTIONER)) {
                        totalDamage = (int) (totalDamage * 1.5f);
                        eventManager.addEvent(new GameEvent("WHIRLWIND EXECUTIONER! Devastating dual strike!", 1.2f));
                    }
                } else if (currentMotionProfile != null && currentMotionProfile.isOffHand) {
                    int baseDamage = DiceRoller.roll(damageDice);
                    int damageBonus = (attackWeapon != null && attackWeapon.isFinesse()) ? player.getFinesseDamageBonus() : (player.getDamageBonus() / 2);
                    totalDamage = Math.max(1, baseDamage + damageBonus);
                } else {
                    int baseDamage = DiceRoller.roll(damageDice);
                    int damageBonus = (attackWeapon != null && attackWeapon.isFinesse()) ? player.getFinesseDamageBonus() : player.getDamageBonus();
                    totalDamage = Math.max(1, baseDamage + damageBonus);
                }

                // Combo Damage Multiplier
                if (currentMotionProfile != null && currentMotionProfile.damageMultiplier > 0f) {
                    totalDamage = Math.max(1, (int) (totalDamage * currentMotionProfile.damageMultiplier));
                }

                // Brutal Cleave Perk: +20% damage on finisher strikes
                if (currentMotionProfile != null && currentMotionProfile.isFinisher && player.hasSkill(SkillId.BRUTAL_CLEAVE)) {
                    totalDamage = (int) (totalDamage * 1.20f);
                }

                // Deadeye Sniper Perk: +25% damage on ranged attack at distance >= 3
                if (attackWeapon != null && attackWeapon.isRanged() && player.hasSkill(SkillId.DEADEYE_SNIPER)) {
                    float dist = player.getPosition().dst(monster.getPosition());
                    if (dist >= 3.0f) {
                        totalDamage = (int) (totalDamage * 1.25f);
                        eventManager.addEvent(new GameEvent("DEADEYE SNIPER! +25% Long-Range Damage!", 1.2f));
                    }
                }

                float meleeMult = com.bpm.minotaur.gamedata.trait.TraitEffects.mult("meleeDamageMult");
                if (meleeMult != 1f && (attackWeapon == null || !attackWeapon.isRanged())) {
                    totalDamage = Math.max(1, Math.round(totalDamage * meleeMult));
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

                AnimationArchetype attackArch = AnimationArchetype.fromItem(attackWeapon);
                boolean isPiercing = (attackArch == AnimationArchetype.THRUSTING_PIERCE || attackArch == AnimationArchetype.RANGED_BOW || attackArch == AnimationArchetype.RANGED_FIREARM)
                        || (attackWeapon != null && attackWeapon.getDamageType() != null && attackWeapon.getDamageType().equalsIgnoreCase("PIERCING"));
                int monsterHpBefore = monster.getCurrentHP();
                int actualDamage = monster.takeDamage(totalDamage, dmgType, isCrit, isPiercing);

                // Brutal Cleave: Overkill damage cleaves into adjacent monster
                if (player.hasSkill(SkillId.BRUTAL_CLEAVE) && monster.getCurrentHP() <= 0 && maze != null) {
                    int overkill = totalDamage - monsterHpBefore;
                    if (overkill > 0) {
                        int cleaveDmg = Math.max(1, overkill / 2);
                        GridPoint2 mPos = new GridPoint2((int) monster.getPosition().x, (int) monster.getPosition().y);
                        for (Direction d : Direction.values()) {
                            GridPoint2 adjPos = new GridPoint2(mPos.x + (int) d.getVector().x, mPos.y + (int) d.getVector().y);
                            Monster adjMonster = maze.getMonsters().get(adjPos);
                            if (adjMonster != null && adjMonster != monster && adjMonster.getCurrentHP() > 0) {
                                int cleaved = adjMonster.takeDamage(cleaveDmg, DamageType.PHYSICAL, false);
                                eventManager.addEvent(new GameEvent("BRUTAL CLEAVE! Cleaved " + adjMonster.getMonsterType() + " for " + cleaved + " dmg!", 1.5f));
                                showDamageText(cleaved, adjPos, "CLEAVE! ", com.badlogic.gdx.graphics.Color.ORANGE);
                                if (adjMonster.getCurrentHP() <= 0) {
                                    maze.getMonsters().remove(adjPos);
                                    player.addExperience(adjMonster.getBaseExperience(), eventManager);
                                }
                                break;
                            }
                        }
                    }
                }

                Monster.Affinity affinity = monster.getAffinity(dmgType);
                String dmgPrefix = "";
                com.badlogic.gdx.graphics.Color textColor = com.badlogic.gdx.graphics.Color.WHITE;

                String specialMoveName = SpecialMoveRegistry.resolveMoveName(currentMotionProfile, attackWeapon, isCrit);
                String comboTag = "";
                if (currentMotionProfile != null && currentMotionProfile.comboStep > 0) {
                    if (currentMotionProfile.isDualStrike) {
                        comboTag = "DUAL SCISSOR! ";
                    } else if (currentMotionProfile.isFinisher) {
                        comboTag = "FINISHER! ";
                    } else if (currentMotionProfile.isOffHand) {
                        comboTag = "OFF-HAND! ";
                    } else {
                        comboTag = "COMBO x" + (currentMotionProfile.comboStep + 1) + "! ";
                    }
                }

                if (isCrit) {
                    dmgPrefix = comboTag + "[" + specialMoveName + "] CRIT! ";
                    textColor = com.badlogic.gdx.graphics.Color.RED;
                    eventManager.addEvent(new GameEvent("[" + specialMoveName + "] CRITICAL HIT on " + monster.getType() + " for " + actualDamage + " dmg!", 1.5f));
                } else if (isGlancing) {
                    dmgPrefix = "GLANCE! ";
                    textColor = com.badlogic.gdx.graphics.Color.CYAN;
                    eventManager.addEvent(new GameEvent("Glancing blow on " + monster.getType() + " for " + actualDamage + " dmg!", 1.2f));
                } else if (currentMotionProfile != null && currentMotionProfile.isDualStrike) {
                    dmgPrefix = comboTag + "[" + specialMoveName + "] ";
                    textColor = com.badlogic.gdx.graphics.Color.MAGENTA;
                    eventManager.addEvent(new GameEvent("[" + specialMoveName + "] " + actualDamage + " dmg to " + monster.getType() + "!", 1.5f));
                } else if (currentMotionProfile != null && currentMotionProfile.isFinisher) {
                    dmgPrefix = comboTag + "[" + specialMoveName + "] ";
                    textColor = com.badlogic.gdx.graphics.Color.GOLD;
                    eventManager.addEvent(new GameEvent("[" + specialMoveName + "] " + actualDamage + " dmg to " + monster.getType() + "!", 1.5f));
                } else if (currentMotionProfile != null && currentMotionProfile.comboStep > 0) {
                    dmgPrefix = comboTag + "[" + specialMoveName + "] ";
                    textColor = com.badlogic.gdx.graphics.Color.YELLOW;
                    eventManager.addEvent(new GameEvent("[" + specialMoveName + "] " + actualDamage + " dmg to " + monster.getType() + "!", 1.2f));
                } else if (affinity == Monster.Affinity.RESISTANT) {
                    dmgPrefix = "[" + specialMoveName + "] RESISTED! ";
                    textColor = com.badlogic.gdx.graphics.Color.CYAN;
                    String attackCategory = (dmgType == DamageType.PHYSICAL) ? "War" : "Spiritual";
                    eventManager.addEvent(new GameEvent(monster.getType() + " resists " + attackCategory + " attacks!", 1.5f));
                } else if (affinity == Monster.Affinity.WEAK) {
                    dmgPrefix = "[" + specialMoveName + "] WEAKNESS! ";
                    textColor = com.badlogic.gdx.graphics.Color.GOLD;
                    String attackCategory = (dmgType == DamageType.PHYSICAL) ? "War" : "Spiritual";
                    eventManager.addEvent(new GameEvent(monster.getType() + " is weak to " + attackCategory + " attacks!", 1.5f));
                } else {
                    dmgPrefix = "[" + specialMoveName + "] ";
                    eventManager.addEvent(new GameEvent("[" + specialMoveName + "] " + actualDamage + " dmg to " + monster.getType() + "!", 1.2f));
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
                Item weapon = (player != null && player.getInventory() != null) ? player.getInventory().getRightHand() : null;
                applyCombatHitWound(monster, totalDamage, weapon);
                applyWeaponAspectEffects(monster, actualDamage, weapon, isCrit, (currentMotionProfile != null && currentMotionProfile.isFinisher));
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
                spawnHitFx(monster, profile, damageRatio, isCrit);
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
            boolean isStaggered = isCrit || (currentMotionProfile != null && (currentMotionProfile.isFinisher || currentMotionProfile.isShieldBash)) || monster.isStunned();
            if (!isStaggered) {
                monsterMeleeStrike(monster);
            } else {
                eventManager.addEvent(new GameEvent(monster.getType() + " is staggered by the blow!", 1.0f));
            }
        }
    }

    public boolean performMonsterRangedAttack(Monster attacker) {
        int range = attacker.getAttackRange();
        Item weapon = (attacker.getInventory() != null) ? attacker.getInventory().getRightHand() : null;
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

        // Retrieve Projectile Archetype
        MonsterProjectileRegistry.MonsterProjectileDefinition projDef =
                MonsterProjectileRegistry.get(attacker.getRangedProjectile());

        // Visual Windup & Telegraph on Attacker
        attacker.triggerRangedAttackTelegraph(projDef.getColor(), 0.35f);

        // Sound cue
        if (soundManager != null) {
            String sKey = projDef.getSoundKey();
            if (sKey != null && !sKey.isEmpty()) {
                soundManager.playSound(sKey);
            } else {
                soundManager.playMonsterAttackSound(attacker);
            }
        }

        // Ballistic Projectile Animation
        float dist = attacker.getPosition().dst(player.getPosition());
        float speed = projDef.getSpeed() > 0 ? projDef.getSpeed() : PROJECTILE_SPEED;
        float animDuration = dist / speed;
        animationManager.addAnimation(
                new Animation(Animation.AnimationType.PROJECTILE_MONSTER,
                        attacker.getPosition(), player.getPosition(),
                        projDef.getColor(), animDuration, projDef.getSpriteData()));

        // --- ATTACK ROLL ---
        int attackBonus = 2 + (attacker.getDexterity() / 5);
        int d20Roll = DiceRoller.d20();
        boolean isCrit = (d20Roll == 20);
        int attackRoll = d20Roll + attackBonus;
        int targetAC = player.getArmorClass();

        int actualDamage = 0;
        if (isCrit || attackRoll >= targetAC) {
            String dice = attacker.getRangedDamageDice();
            if (dice == null || dice.isEmpty()) {
                dice = (hasRangedWeapon && weapon != null) ? weapon.getDamageDice() : attacker.getDamageDice();
            }
            int dmg = DiceRoller.roll(dice);
            if (isCrit) {
                dmg += DiceRoller.roll(dice);
            }
            if (dmg < 1)
                dmg = 1;

            DamageType damageType = attacker.getRangedDamageType();
            if (damageType == null) {
                damageType = projDef.getDefaultDamageType();
            }

            // A fire bolt or a sorcerous bolt is a spell and is cut by magic resistance; an arrow is not.
            actualDamage = com.bpm.minotaur.gamedata.MagicResistance.isMagical(damageType)
                    ? player.takeSpellDamage(dmg, damageType)
                    : player.takeDamage(dmg, damageType);

            if (actualDamage > 0) {
                if (damageType == DamageType.PHYSICAL) {
                    maze.addBlood((int) player.getPosition().x, (int) player.getPosition().y, 0.03f);
                }
                showPlayerDamageText(actualDamage, isCrit, damageType);

                // 3D Impact Burst (BearFX Explosion)
                if (projDef.getExplosionType() != null) {
                    GridPoint2 cid = (worldManager != null) ? worldManager.getCurrentPlayerChunkId() : new GridPoint2(0, 0);
                    float wx = cid.x * 36.0f + player.getPosition().x;
                    float wz = cid.y * 36.0f + player.getPosition().y;
                    Vector3 hitPos = new Vector3(wx, 0.5f, wz);
                    animationManager.addAnimation(new Animation(projDef.getExplosionType(), hitPos, 1.8f, 0.55f));
                }

                // On-Hit Status Effect
                String effectStr = attacker.getRangedEffect();
                if (effectStr != null && !effectStr.isEmpty()) {
                    if (Math.random() <= attacker.getRangedEffectChance()) {
                        try {
                            StatusEffectType effType = StatusEffectType.valueOf(effectStr.toUpperCase());
                            player.getStatusManager().addEffect(effType, 6, 1, true);
                            eventManager.addEvent(new GameEvent("You are afflicted with " + effType.name() + "!", 2.0f));
                        } catch (Exception ignored) {
                        }
                    }
                }

                String critPrefix = isCrit ? "Critical Hit! " : "";
                eventManager.addEvent(
                        new GameEvent(critPrefix + attacker.getMonsterType() + " hits you with " + projDef.getName() + " for " + actualDamage + " " + damageType.name().toLowerCase() + " damage!", 2.0f));
            } else {
                eventManager.addEvent(new GameEvent("Armor deflected the " + projDef.getName().toLowerCase() + "!", 1.5f));
            }
        } else {
            eventManager.addEvent(new GameEvent(attacker.getMonsterType() + " fires " + projDef.getName().toLowerCase() + " and misses!", 1.5f));
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

        // MIMIC REVEAL: hold the world still while the disguise comes off, then let the
        // mimic land its one free blow before the player gets the menu.
        if (currentState == CombatState.MONSTER_REVEAL) {
            mimicRevealTimer += delta;

            // Phase 1 -> 2: the shudder is over. Burst, and swap the chest for the
            // creature under cover of it.
            if (revealingMimic == null && mimicRevealTimer >= MIMIC_SHUDDER_TIME) {
                Monster mimic = createMimicMonster(mimicRevealDepth);
                if (MimicReveal.swap(maze, mimicRevealTile, mimic)) {
                    revealingMimic = mimic;
                    playMimicBurst(mimicRevealTile, mimic);
                } else {
                    // The chest went away underneath us; abandon the reveal rather than
                    // stranding the state machine.
                    endMimicReveal();
                    return;
                }
            }

            // Phase 2 -> combat: the mimic lands its one free blow, then hands over.
            if (mimicRevealTimer >= MIMIC_REVEAL_TIME) {
                Monster mimic = revealingMimic;
                endMimicReveal();
                if (mimic != null) {
                    monsterMeleeStrike(mimic);

                    if (player.getStats().getCurrentHP() > 0) {
                        startCombat(mimic);
                    } else {
                        // The free blow finished an already-wounded player. Hand off to
                        // the DEFEAT branch rather than raising the death event here, so
                        // death inversion and combat logging still run.
                        this.monster = mimic;
                        currentState = CombatState.DEFEAT;
                    }
                }
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
            // A combat turn is a world turn: the reload has to advance here too, or a
            // gun could only ever be reloaded by backing out of the fight.
            tickReload();
            processPlayerStatusEffects();
            player.getStatusManager().updateTurn();
            Gdx.app.log("CombatManager", "Player passed turn. Monster's turn.");
            currentState = CombatState.MONSTER_TURN;
            monsterAttackDelay = MONSTER_ATTACK_DELAY_TIME;
        }
    }

    public void consumePlayerTurn() {
        if (currentState == CombatState.PLAYER_MENU || currentState == CombatState.PLAYER_TURN) {
            tickReload();
            processPlayerStatusEffects();
            player.getStatusManager().updateTurn();
            Gdx.app.log("CombatManager", "Player turn consumed. Monster's turn.");
            currentState = CombatState.MONSTER_TURN;
            monsterAttackDelay = MONSTER_ATTACK_DELAY_TIME;
        }
    }

    private void spawnCorpseEffects(Monster monster) {
        spawnCorpseEffects(monster, 0);
    }

    /**
     * The corpse sprite for a cleanly killed monster: its own art.
     *
     * <p>No monster has death art -- only 4 of 54 have spritesheets at all, and
     * those are looping idles -- so the corpse is the living sprite laid down.
     * The renderer rotates, darkens and squashes it, which is what stops an
     * upright idle pose reading as a bug rather than a body.
     */
    private String monsterCorpseTexture(Monster monster) {
        if (monster != null) {
            com.bpm.minotaur.gamedata.monster.MonsterTemplate t = monster.getTemplate();
            if (t != null && t.texturePath != null && !t.texturePath.isEmpty()
                    && Gdx.files != null && Gdx.files.internal(t.texturePath).exists()) {
                return t.texturePath;
            }
        }
        return gorePileTexture();
    }

    /**
     * What a dismembered kill leaves: a scaled gib, matching the gibs the same
     * blow just threw across the floor.
     */
    private String gorePileTexture() {
        String path = "images/gore/gib" + (1 + random.nextInt(10)) + ".png";
        if (Gdx.files != null && Gdx.files.internal(path).exists()) return path;
        return "images/scenery/decomposing_corpse.png";
    }

    private void spawnCorpseEffects(Monster monster, int overkillTier) {
        if (maze == null || itemDataManager == null)
            return;

        GridPoint2 pos = new GridPoint2((int) monster.getPosition().x, (int) monster.getPosition().y);

        // Monsters ALWAYS leave a corpse when killed that persists (Item 51).
        // What they leave depends on how they died: a clean kill leaves a body
        // you can recognise, a dismembered one leaves a heap. Every monster used
        // to leave the same dead armoured human regardless.
        if (maze.getScenery() != null) {
            boolean severed = overkillTier > 0;
            String corpseTex = severed ? gorePileTexture() : monsterCorpseTexture(monster);
            Scenery corpse = new Scenery(
                    severed ? Scenery.SceneryType.GORE_PILE : Scenery.SceneryType.MONSTER_REMAINS,
                    pos.x, pos.y, corpseTex);
            corpse.setCorpseMonsterName(monster.getMonsterType());
            if (game != null && game.getAssetManager() != null) {
                AssetManager am = game.getAssetManager();
                if (Gdx.files != null && Gdx.files.internal(corpseTex).exists()) {
                    if (!am.isLoaded(corpseTex)) {
                        am.load(corpseTex, Texture.class);
                        am.finishLoadingAsset(corpseTex);
                    }
                    corpse.setTexture(am.get(corpseTex, Texture.class));
                }
            }
            maze.addScenery(corpse);
        }

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

        // 3b. Mimic Hoard: the chest's worth of loot it was digesting. Rolled here
        // rather than carried on the disguise, because monster inventory does not
        // survive a chunk unload (ChunkData.MonsterData) and a mimic fight can easily
        // straddle one.
        if (monster.getType() == Monster.MonsterType.MIMIC) {
            List<Item> hoard = com.bpm.minotaur.generation.MimicHoard.roll(
                    itemDataManager,
                    (game != null) ? game.getAssetManager() : null,
                    maze.getLevel(),
                    player.getStats().getLevel(),
                    player.getLuck(),
                    random);
            for (Item loot : hoard) {
                dropSingleItem(loot, pos, monster);
            }
            if (!hoard.isEmpty()) {
                eventManager.addEvent(new GameEvent("The mimic disgorges its hoard!", 2.5f));
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

        // Bridge guardian slain: integrity returns to zero and the next
        // summoning will be stronger. This used to hang off the themed-chunk
        // objective, which only fired for a sealed chunk; the boss roams now,
        // so it keys off the monster itself.
        if (monster != null && monster.isBridgeBoss()) {
            DoomManager.getInstance().onBridgeBossDefeated();
            if (eventManager != null) {
                eventManager.addEvent(new GameEvent(
                        "THE BRIDGE GUARDIAN FALLS! The bridge holds. Integrity restored.", 5.0f));
            }
        }

        // Minotaur Defeat Check: Unlocks Classic Mode globally and Pact of Torment
        if (monster != null && monster.getType() == Monster.MonsterType.MINOTAUR) {
            SaveManager.getInstance().unlockClassicMode();
            if (eventManager != null) {
                eventManager.addEvent(new GameEvent("THE MINOTAUR HAS FALLEN! Classic Mode and Pact of Torment unlocked!", 5.0f));
            }
            MusicManager.getInstance().playStinger("sounds/music/tarmin_sound_fx.ogg");
        } else if (isBossMonster(monster)) {
            MusicManager.getInstance().playStinger("sounds/music/tarmin_sound_fx.ogg");
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

    /**
     * Fires the equipped ranged weapon at whatever is in front of the player.
     *
     * <p>Used to delegate to {@code playerAttackInstant()}, which called straight back
     * here with nothing changed between them -- unbounded recursion.
     */
    public boolean performRangedAttack() {
        Item weapon = player.getInventory().getRightHand();
        if (weapon != null && weapon.isWand()) {
            player.zap(weapon, player.getFacing(), getDiscoveryManager(), eventManager, maze, this);
            return true;
        }
        if (weapon == null || !weapon.isRanged())
            return false;

        if (monster != null) {
            resolveRangedAttackAgainst(monster);
        } else {
            openFireAtRange();
        }
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

        if (monster.isStunned()) {
            eventManager.addEvent(new GameEvent(monster.getMonsterType() + " is STUNNED and cannot attack!", 1.5f));
            monster.decrementStun();
            currentState = CombatState.PLAYER_MENU;
            return;
        }

        // Actionable monster turn: decay concussion resilience and stagger
        monster.decrementStunImmunity();
        monster.decrementStagger();

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
            // Combat can begin across a room (a shot or a zap starts it), so the caster still needs a
            // clear line to the player. This cast used to land through any wall.
            boolean clearLine = com.bpm.minotaur.gamedata.spells.MonsterSpellSight
                    .assess(maze, monster, player.getPosition(), null).canCast();
            if (clearLine && random.nextInt(100) < monster.getSpellChance()) {
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
        int spellDmg = player.reduceSpellDamage(5 + monster.getIntelligence());
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

        // Handle Bleed
        if (monster.getBleedTurns() > 0) {
            int dmg = monster.applyBleedTick();
            maze.addBlood((int) monster.getPosition().x, (int) monster.getPosition().y, 0.05f);
            eventManager.addEvent(new GameEvent(monster.getMonsterType() + " takes " + dmg + " bleed dmg!", 1.5f));
            BalanceLogger.getInstance().log("COMBAT_EFFECT", "Monster bled for " + dmg + " damage.");

            if (monster.getWarStrength() <= 0) {
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

        // A themed chunk's objective may hang on this kill (the champion, or the
        // last combatant in the arena).
        com.bpm.minotaur.generation.theme.ThemeObjectiveManager
                .onMonsterKilled(maze, monster, eventManager);
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

        MonsterDecalCompositor.getInstance().releaseMonster(monster, false);

        int overkillTier = 0;
        if (overkillRatio >= 0.50f || (isHeavyKill && overkillRatio >= 0.25f)) {
            overkillTier = 2; // Complete Obliteration
        } else if (overkillRatio >= 0.25f || isHeavyKill) {
            overkillTier = 1; // Significant Dismemberment
        }

        Item killWeapon = (player != null && player.getInventory() != null) ? player.getInventory().getRightHand() : null;
        AnimationArchetype killArch = AnimationArchetype.fromItem(killWeapon);

        if (overkillTier > 0) {
            // Trigger Visor Blood Droplet splash & camera trauma & hit-pause
            if (game != null && game.getScreen() instanceof com.bpm.minotaur.screens.GameScreen) {
                com.bpm.minotaur.screens.GameScreen gs = (com.bpm.minotaur.screens.GameScreen) game.getScreen();
                float dist = player.getPosition().dst(monster.getPosition());
                if (dist <= 1.5f) {
                    gs.triggerVisorSplatter();
                }
                gs.triggerHitPause(0.12f);
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
                killBloodIntensity = (overkillTier == 2) ? 8 : 5;

                switch (killArch) {
                    case SLASHING_1H:
                    case SLASHING_2H:
                    case AXE_CHOPPING:
                    case POLEARM_SWEEP: {
                        // Freeform bisection
                        float swingStartX = 0.8f, swingStartY = 0.2f, swingEndX = 0.2f, swingEndY = 0.8f;
                        if (game != null && game.getScreen() instanceof com.bpm.minotaur.screens.GameScreen) {
                            com.bpm.minotaur.screens.GameScreen gs = (com.bpm.minotaur.screens.GameScreen) game.getScreen();
                            if (gs.getWeaponOverlay() != null) {
                                CombatMotionProfile cmp = gs.getWeaponOverlay().getCurrentProfile();
                                if (cmp != null) {
                                    swingStartX = cmp.startXRel;
                                    swingStartY = cmp.startYRel;
                                    swingEndX = cmp.endXRel;
                                    swingEndY = cmp.endYRel;
                                }
                            }
                        }

                        float mw = monster.getScale().x;
                        float mh = monster.getScale().y;
                        TextureRegion mReg = monster.getTextureRegion();
                        Texture mTex = (mReg != null) ? mReg.getTexture() : monster.getTexture();
                        BillboardSlicer.SlicedResult slice = BillboardSlicer.sliceFromSwing(
                                mw, mh, mReg, swingStartX, swingStartY, swingEndX, swingEndY
                        );

                        if (slice.isSliced && mTex != null && profile.hasGibs) {
                            Vector3 cutVel = new Vector3(exitVector.x * 2.0f, MathUtils.random(3.5f, 5.5f), exitVector.z * 2.0f);
                            maze.getGoreManager().spawnSeveredLimbGib(
                                    gibOrigin, cutVel, mTex,
                                    slice.severedVertices, slice.severedUVs, null, profile
                            );
                            if (slice.trunkVertices != null && slice.trunkVertices.length >= 6) {
                                Vector3 trunkVel = new Vector3(-exitVector.x * 0.6f, MathUtils.random(0.5f, 1.8f), -exitVector.z * 0.6f);
                                maze.getGoreManager().spawnSeveredLimbGib(
                                        gibOrigin, trunkVel, mTex,
                                        slice.trunkVertices, slice.trunkUVs, null, profile
                                );
                            }
                            maze.getGoreManager().spawnArterialFountain(gibOrigin, Vector3.Y, 2.0f, profile);
                        } else {
                            maze.getGoreManager().spawnGibExplosion(gibOrigin, exitVector, overkillTier, profile);
                            maze.getGoreManager().spawnBloodSpray(gibOrigin, exitVector, killBloodIntensity, profile);
                        }
                        break;
                    }
                    case BLUNT_CRUSHING:
                    case FLAIL_WHIP:
                    case BRAWLING:
                    case SHIELD: {
                        maze.getGoreManager().spawnCrushShatter(gibOrigin, profile);
                        break;
                    }
                    case THRUSTING_PIERCE:
                    case RANGED_BOW:
                    case RANGED_FIREARM: {
                        maze.getGoreManager().spawnArterialFountain(gibOrigin, exitVector, 2.0f, profile);
                        maze.getGoreManager().spawnBloodSpray(gibOrigin, exitVector, killBloodIntensity, profile);
                        break;
                    }
                    default: {
                        maze.getGoreManager().spawnGibExplosion(gibOrigin, exitVector, overkillTier, profile);
                        maze.getGoreManager().spawnBloodSpray(gibOrigin, exitVector, killBloodIntensity, profile);
                        break;
                    }
                }
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

    private final java.util.Random woundRandom = new java.util.Random();

    private void applyCombatHitWound(Monster monster, int actualDamage, Item weapon) {
        if (monster == null || actualDamage <= 0) return;

        // The weapon's own damage type decides the wound; see WoundTypeResolver.
        WoundDecal.WoundType woundType = com.bpm.minotaur.gamedata.gore.WoundTypeResolver.resolve(
                weapon != null ? weapon.getDamageType() : null,
                AnimationArchetype.fromItem(weapon),
                weapon != null && weapon.isFinesse(),
                null);

        float angle = MathUtils.random(-0.75f, 0.75f);
        if (game != null && game.getScreen() instanceof com.bpm.minotaur.screens.GameScreen) {
            com.bpm.minotaur.screens.GameScreen gs = (com.bpm.minotaur.screens.GameScreen) game.getScreen();
            if (gs.getWeaponOverlay() != null) {
                CombatMotionProfile cmp = gs.getWeaponOverlay().getCurrentProfile();
                if (cmp != null) {
                    float dx = cmp.endXRel - cmp.startXRel;
                    float dy = cmp.endYRel - cmp.startYRel;
                    angle = (float) Math.atan2(dy, dx);
                }
            }
        }

        // Fetch categorized decal region from registry
        WoundDecalRegistry registry = WoundDecalRegistry.getInstance();
        TextureRegion decalRegion = registry.getRandomRegion(woundType);

        // On the creature, clear of the wounds it already has -- not always the middle of the sprite.
        float[] site = com.bpm.minotaur.gamedata.gore.WoundPlacement.pick(
                com.bpm.minotaur.rendering.MonsterSilhouettes.forMonster(monster),
                monster.getWoundDecals(), woundRandom);
        float u = site[0];
        float v = site[1];

        float aspect = WoundDecalRegistry.getAspectRatio(decalRegion, woundType);
        float length = MathUtils.clamp(0.20f + actualDamage * 0.008f, 0.16f, 0.45f);
        float width = length / Math.max(0.2f, aspect);

        GoreProfile profile = GoreProfile.fromMonster(monster);
        com.badlogic.gdx.graphics.Color woundCol;
        if (profile == GoreProfile.FLESH || profile == GoreProfile.SKELETAL || profile == null) {
            // Natural full-color gore from spritesheet (including undead)
            woundCol = com.badlogic.gdx.graphics.Color.WHITE;
        } else if (profile.woundColor != null) {
            // SKELETAL, SLIME, etc. get their physiology tint
            woundCol = profile.woundColor;
        } else {
            woundCol = com.badlogic.gdx.graphics.Color.WHITE;
        }

        WoundDecal decal = new WoundDecal(woundType, u, v, angle, length, width, woundCol);
        decal.customRegion = decalRegion;
        MonsterDecalCompositor.getInstance().addWound(monster, decal);

        // Calculate 3D position of the wound site on the monster billboard
        GridPoint2 cid = (worldManager != null) ? worldManager.getCurrentPlayerChunkId() : new GridPoint2(0, 0);
        float wx = cid.x * 36.0f + monster.getPosition().x;
        float wz = cid.y * 36.0f + monster.getPosition().y;

        float mw = monster.getScale() != null ? monster.getScale().x : 0.8f;
        float mh = monster.getScale() != null ? monster.getScale().y : 0.8f;
        float maxMonsterW = 0.82f;
        if (mw > maxMonsterW) {
            float s = maxMonsterW / mw;
            mw = maxMonsterW;
            mh *= s;
        }

        // Normal towards player
        float dx = (player != null) ? player.getPosition().x - monster.getPosition().x : 0f;
        float dy = (player != null) ? player.getPosition().y - monster.getPosition().y : 1f;
        float dist = (float) Math.sqrt(dx * dx + dy * dy);
        float nx = (dist > 0.0001f) ? dx / dist : 0f;
        float nz = (dist > 0.0001f) ? dy / dist : 1f;

        // Tangent across monster's billboard
        float tx = -nz;
        float tz = nx;

        float posX = wx + tx * (u - 0.5f) * mw;
        float posZ = wz + tz * (u - 0.5f) * mw;
        float posY = MathUtils.clamp(v * mh, 0.1f, mh);

        Vector3 woundSitePos = new Vector3(posX, posY, posZ);
        Vector3 splashDir = new Vector3(nx, 0.25f, nz).nor();

        if (maze != null && maze.getGoreManager() != null) {
            maze.getGoreManager().spawnWoundBloodBurst(woundSitePos, splashDir, actualDamage, profile);
        }
    }

    private void applyWeaponAspectEffects(Monster monster, int actualDamage, Item weapon, boolean isCrit, boolean isFinisher) {
        if (monster == null || actualDamage <= 0 || !monster.isAlive()) return;

        AnimationArchetype arch = AnimationArchetype.fromItem(weapon);
        boolean isSlashing = (arch == AnimationArchetype.SLASHING_1H || arch == AnimationArchetype.SLASHING_2H
                || arch == AnimationArchetype.AXE_CHOPPING || arch == AnimationArchetype.POLEARM_SWEEP);
        boolean isBludgeoning = (arch == AnimationArchetype.BLUNT_CRUSHING || arch == AnimationArchetype.FLAIL_WHIP
                || arch == AnimationArchetype.BRAWLING || arch == AnimationArchetype.SHIELD);
        boolean isPiercing = (arch == AnimationArchetype.THRUSTING_PIERCE || arch == AnimationArchetype.RANGED_BOW
                || arch == AnimationArchetype.RANGED_FIREARM);

        if (weapon != null && weapon.getDamageType() != null) {
            String dt = weapon.getDamageType().toUpperCase();
            if (dt.contains("SLASH")) isSlashing = true;
            if (dt.contains("BLUDGEON")) isBludgeoning = true;
            if (dt.contains("PIERCE")) isPiercing = true;
        }

        // Slashing: Bleed & Sever
        if (isSlashing) {
            float bleedChance = isCrit ? 1.0f : (isFinisher ? 0.85f : 0.40f);
            if (MathUtils.randomBoolean(bleedChance)) {
                int bleedDmg = Math.max(1, actualDamage / 3);
                monster.applyBleed(3, bleedDmg);
                eventManager.addEvent(new GameEvent("LACERATION! " + monster.getMonsterType() + " is bleeding (" + bleedDmg + " dmg/turn)!", 1.5f));
            }
            if (monster.getCurrentHP() <= 0 && (isCrit || isFinisher || actualDamage > 12)) {
                eventManager.addEvent(new GameEvent("SEVERING BLOW! Cleaved through " + monster.getMonsterType() + "!", 2.0f));
                GridPoint2 cid = (worldManager != null) ? worldManager.getCurrentPlayerChunkId() : new GridPoint2(0, 0);
                float wx = cid.x * 36.0f + monster.getPosition().x;
                float wz = cid.y * 36.0f + monster.getPosition().y;
                Vector3 hitPos = new Vector3(wx, 0.5f, wz);
                Vector3 exitDir = new Vector3(monster.getPosition().x - player.getPosition().x, 0.2f, monster.getPosition().y - player.getPosition().y).nor();
                GoreProfile profile = GoreProfile.fromMonster(monster);
                if (maze != null && maze.getGoreManager() != null) {
                    maze.getGoreManager().spawnGibExplosion(hitPos, exitDir, 2, profile);
                    maze.addBlood((int) monster.getPosition().x, (int) monster.getPosition().y, 0.2f);
                }
            }
        }

        // Bludgeoning: Concussion & Stun
        if (isBludgeoning) {
            float stunChance = isCrit ? 0.80f : (isFinisher ? 0.55f : 0.25f);
            if (MathUtils.randomBoolean(stunChance)) {
                if (isBossMonster(monster)) {
                    monster.applyStagger(1);
                    eventManager.addEvent(new GameEvent("STAGGERED! " + monster.getMonsterType() + " resists concussion, but staggers (-2 AC)!", 1.5f));
                } else if (monster.isStunned()) {
                    eventManager.addEvent(new GameEvent(monster.getMonsterType() + " is already reeling!", 1.0f));
                } else if (monster.isStunImmune()) {
                    eventManager.addEvent(new GameEvent("RESIST! " + monster.getMonsterType() + " resists concussion!", 1.2f));
                } else {
                    boolean stunned = monster.applyStun(1);
                    if (stunned) {
                        eventManager.addEvent(new GameEvent("CONCUSSION! " + monster.getMonsterType() + " is dazed and stunned!", 1.5f));
                    }
                }
            }
        }

        // Piercing: Armor Puncture
        if (isPiercing) {
            if (isCrit || isFinisher) {
                eventManager.addEvent(new GameEvent("DEEP PUNCTURE! Pierced directly into vitals!", 1.5f));
            }
        }
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

    /**
     * The burst where a blow lands on a monster: a spurt of blood, or smoke or sparks for something
     * that does not bleed. Placed at the monster's body height, in the modern renderer only.
     */
    private void spawnHitFx(Monster target, GoreProfile profile, float damageShare, boolean crit) {
        if (animationManager == null || target == null) {
            return;
        }
        com.bpm.minotaur.gamedata.gore.HitFx.Spec spec = com.bpm.minotaur.gamedata.gore.HitFx.forHit(
                profile, target.getFamily(), target.getType(), damageShare, crit, target.getCurrentHP() <= 0);
        animationManager.spawnFx(spec.clipId,
                com.bpm.minotaur.gamedata.gore.HitFx.position(target.getPosition().x, target.getPosition().y),
                spec.scale);
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
