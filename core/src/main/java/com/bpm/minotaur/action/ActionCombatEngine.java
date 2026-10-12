package com.bpm.minotaur.action;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;

import java.util.ArrayList;
import java.util.List;

/**
 * Real-time visceral combat engine implementing Elderborn/Project Brutality mechanics:
 * - Directional melee trace hitboxes with hitstop micro-pauses
 * - Active parry/block windows that deflect incoming attacks and poise-break monsters
 * - Heavy off-hand kick that launches monsters into hazards
 * - Instant hotbar weapon swapping (1-5) and quick-cast spells (Q/E)
 * - Resource shards (health/mana) bursting from slain enemies with vacuum physics
 */
public class ActionCombatEngine {

    public enum WeaponType {
        BROADSWORD("Broadsword", 42f, 2.3f, 65f, 0.35f, 0.038f, 0.25f),
        BATTLEAXE("Battleaxe", 85f, 2.7f, 85f, 0.55f, 0.055f, 0.45f),
        CROSSBOW("Crossbow", 55f, 18.0f, 15f, 0.45f, 0.025f, 0.20f),
        PISTOL("Flintlock Pistol", 95f, 14.0f, 20f, 0.65f, 0.045f, 0.50f),
        SPELLBOOK("Spellbook", 60f, 15.0f, 35f, 0.30f, 0.020f, 0.15f);

        public final String name;
        public final float damage;
        public final float reach;
        public final float arcDeg;
        public final float cooldown;
        public final float hitstop;
        public final float trauma;

        WeaponType(String name, float damage, float reach, float arcDeg, float cooldown, float hitstop, float trauma) {
            this.name = name;
            this.damage = damage;
            this.reach = reach;
            this.arcDeg = arcDeg;
            this.cooldown = cooldown;
            this.hitstop = hitstop;
            this.trauma = trauma;
        }
    }

    public static class ShardPickup {
        public enum ShardType { HEALTH, MANA }
        public final Vector3 position = new Vector3();
        public final Vector3 velocity = new Vector3();
        public final ShardType type;
        public final float amount;
        public float lifetime = 15f;

        public ShardPickup(Vector3 pos, ShardType type, float amount) {
            this.position.set(pos);
            this.type = type;
            this.amount = amount;
            // Erupt slightly outward on spawn
            this.velocity.set(MathUtils.random(-2f, 2f), MathUtils.random(2f, 4f), MathUtils.random(-2f, 2f));
        }
    }

    public static class Projectile {
        public final Vector3 position = new Vector3();
        public final Vector3 velocity = new Vector3();
        public final float damage;
        public final boolean isFlame;
        public float lifetime = 4f;

        public Projectile(Vector3 pos, Vector3 dir, float speed, float damage, boolean isFlame) {
            this.position.set(pos);
            this.velocity.set(dir).nor().scl(speed);
            this.damage = damage;
            this.isFlame = isFlame;
        }
    }

    public interface CombatEventListener {
        void onMonsterHit(Vector3 pos, Vector3 hitDirection, boolean isLethal);
        void onPlayerHit();
    }

    public CombatEventListener eventListener;
    public float playerInvulnTimer = 0f;

    // Player stats
    public float playerHealth = 100f;
    public float maxPlayerHealth = 100f;
    public float playerMana = 100f;
    public float maxPlayerMana = 100f;

    // Equipped state
    public WeaponType equippedWeapon = WeaponType.BROADSWORD;
    public float weaponCooldownTimer = 0f;

    // Parry state
    public boolean isParrying = false;
    public float parryTimer = 0f;
    public static final float PARRY_WINDOW = 0.28f;
    public float parryFlashTimer = 0f;

    // Kick state
    public boolean isKicking = false;
    public float kickTimer = 0f;
    public float kickCooldownTimer = 0f;
    public static final float KICK_DURATION = 0.30f;
    public static final float KICK_COOLDOWN = 0.85f;

    // Dynamic pickups & flying projectiles
    public final List<ShardPickup> shards = new ArrayList<>();
    public final List<Projectile> projectiles = new ArrayList<>();

    // Combat events / sound triggers
    public boolean triggeredSwingSound = false;
    public boolean triggeredHitSound = false;
    public boolean triggeredKickSound = false;
    public boolean triggeredParrySound = false;
    public boolean triggeredMuzzleFlash = false;

    public void update(float delta, ActionPlayerController player, List<ActionMonsterEntity> monsters) {
        // Cooldown decays
        if (weaponCooldownTimer > 0f) weaponCooldownTimer = Math.max(0f, weaponCooldownTimer - delta);
        if (kickCooldownTimer > 0f) kickCooldownTimer = Math.max(0f, kickCooldownTimer - delta);
        if (parryFlashTimer > 0f) parryFlashTimer = Math.max(0f, parryFlashTimer - delta);

        // Reset frame flags
        triggeredSwingSound = false;
        triggeredHitSound = false;
        triggeredKickSound = false;
        triggeredParrySound = false;
        triggeredMuzzleFlash = false;

        // 1. Process Parry
        if (isParrying) {
            parryTimer -= delta;
            if (parryTimer <= 0f) isParrying = false;
        }

        // 2. Process Kick & I-Frames
        if (isKicking) {
            kickTimer -= delta;
            if (kickTimer <= 0f) isKicking = false;
        }
        if (playerInvulnTimer > 0f) playerInvulnTimer = Math.max(0f, playerInvulnTimer - delta);

        // 3. Process Input
        handleCombatInput(delta, player, monsters);

        // 4. Update Projectiles
        updateProjectiles(delta, monsters, player);

        // 5. Update Shard Pickups (Vacuum physics toward player)
        updateShardPickups(delta, player);
    }

    private void handleCombatInput(float delta, ActionPlayerController player, List<ActionMonsterEntity> monsters) {
        if (Gdx.input == null) return;

        // Hotbar Weapon Switching (1-5)
        if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_1)) equippedWeapon = WeaponType.BROADSWORD;
        if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_2)) equippedWeapon = WeaponType.BATTLEAXE;
        if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_3)) equippedWeapon = WeaponType.CROSSBOW;
        if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_4)) equippedWeapon = WeaponType.PISTOL;
        if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_5)) equippedWeapon = WeaponType.SPELLBOOK;

        // Right Click: Block & Parry Window
        if (Gdx.input.isButtonJustPressed(Input.Buttons.RIGHT) && !isParrying && !isKicking) {
            isParrying = true;
            parryTimer = PARRY_WINDOW;
        }

        // F Key: Off-Hand Kick
        if (Gdx.input.isKeyJustPressed(Input.Keys.F) && kickCooldownTimer <= 0f && !isKicking) {
            executeKick(player, monsters);
        }

        // Left Click: Primary Weapon Attack
        if (Gdx.input.isButtonPressed(Input.Buttons.LEFT) && weaponCooldownTimer <= 0f && !isParrying && !isKicking) {
            executeWeaponAttack(player, monsters);
        }

        // Q Key: Quick-Cast Flame Bolt (costs 15 mana)
        if (Gdx.input.isKeyJustPressed(Input.Keys.Q)) {
            castQuickSpellFlame(player);
        }

        // E Key: Quick-Cast Frost Ray (costs 20 mana)
        if (Gdx.input.isKeyJustPressed(Input.Keys.E)) {
            castQuickSpellFrost(player);
        }
    }

    public void triggerAttack(ActionPlayerController player, List<ActionMonsterEntity> monsters) {
        if (weaponCooldownTimer <= 0f && !isParrying && !isKicking) {
            executeWeaponAttack(player, monsters);
        }
    }

    public void triggerParry() {
        if (!isParrying && !isKicking) {
            isParrying = true;
            parryTimer = PARRY_WINDOW;
        }
    }

    public void triggerKick(ActionPlayerController player, List<ActionMonsterEntity> monsters) {
        if (kickCooldownTimer <= 0f && !isKicking) {
            executeKick(player, monsters);
        }
    }

    public void castQuickSpellFlame(ActionPlayerController player) {
        if (playerMana >= 15f) {
            playerMana -= 15f;
            Vector3 spawn = new Vector3(player.position).add(0f, 0.45f, 0f).add(new Vector3(player.direction).scl(0.5f));
            projectiles.add(new Projectile(spawn, player.direction, 28f, 50f, true));
            player.addTrauma(0.12f);
        }
    }

    public void castQuickSpellFrost(ActionPlayerController player) {
        if (playerMana >= 20f) {
            playerMana -= 20f;
            Vector3 spawn = new Vector3(player.position).add(0f, 0.45f, 0f).add(new Vector3(player.direction).scl(0.5f));
            projectiles.add(new Projectile(spawn, player.direction, 35f, 65f, false));
            player.addTrauma(0.15f);
        }
    }

    private void executeWeaponAttack(ActionPlayerController player, List<ActionMonsterEntity> monsters) {
        weaponCooldownTimer = equippedWeapon.cooldown;
        triggeredSwingSound = true;

        if (equippedWeapon == WeaponType.PISTOL) {
            triggeredMuzzleFlash = true;
            player.addTrauma(equippedWeapon.trauma);
            executeHitscanFirearm(player, monsters);
            return;
        }

        if (equippedWeapon == WeaponType.CROSSBOW) {
            Vector3 spawn = new Vector3(player.position).add(0f, 0.45f, 0f).add(new Vector3(player.direction).scl(0.4f));
            projectiles.add(new Projectile(spawn, player.direction, 42f, equippedWeapon.damage, false));
            player.addTrauma(equippedWeapon.trauma);
            return;
        }

        if (equippedWeapon == WeaponType.SPELLBOOK) {
            Vector3 spawn = new Vector3(player.position).add(0f, 0.45f, 0f).add(new Vector3(player.direction).scl(0.4f));
            projectiles.add(new Projectile(spawn, player.direction, 32f, equippedWeapon.damage, true));
            player.addTrauma(equippedWeapon.trauma);
            return;
        }

        // Melee trace fan (Broadsword, Battleaxe)
        boolean connected = false;
        for (ActionMonsterEntity monster : monsters) {
            if (monster.isDead()) continue;

            float dist = (float) Math.hypot(monster.position.x - player.position.x, monster.position.z - player.position.z);
            if (dist > equippedWeapon.reach + monster.radius) continue;

            // Angle between player forward direction and monster vector
            Vector3 toMonster = new Vector3(monster.position.x - player.position.x, 0f, monster.position.z - player.position.z).nor();
            float dot = player.forwardHorizontal.dot(toMonster);
            float angleDeg = (float) Math.toDegrees(Math.acos(MathUtils.clamp(dot, -1f, 1f)));

            if (angleDeg <= equippedWeapon.arcDeg * 0.5f) {
                connected = true;
                Vector3 impulse = new Vector3(player.forwardHorizontal).scl(equippedWeapon.damage * 0.15f);
                boolean isLethal = (monster.health <= equippedWeapon.damage);

                monster.takeDamage(equippedWeapon.damage, impulse, isLethal);

                if (isLethal) {
                    spawnDeathShards(monster.position);
                }
                if (eventListener != null) {
                    eventListener.onMonsterHit(monster.position, impulse, isLethal);
                }
            }
        }

        if (connected) {
            triggeredHitSound = true;
            player.triggerHitstop(equippedWeapon.hitstop);
            player.addTrauma(equippedWeapon.trauma);
        }
    }

    private void executeHitscanFirearm(ActionPlayerController player, List<ActionMonsterEntity> monsters) {
        ActionMonsterEntity closest = null;
        float closestDist = Float.MAX_VALUE;

        for (ActionMonsterEntity monster : monsters) {
            if (monster.isDead()) continue;

            float dist = player.position.dst(monster.position);
            if (dist > equippedWeapon.reach) continue;

            Vector3 toMonster = new Vector3(monster.position).sub(player.position).nor();
            float dot = player.direction.dot(toMonster);
            if (dot > 0.92f && dist < closestDist) { // Tight hitscan crosshair cone
                closest = monster;
                closestDist = dist;
            }
        }

        if (closest != null) {
            triggeredHitSound = true;
            Vector3 impulse = new Vector3(player.direction).scl(12.0f);
            boolean isLethal = (closest.health <= equippedWeapon.damage);
            closest.takeDamage(equippedWeapon.damage, impulse, isLethal);

            if (isLethal) {
                spawnDeathShards(closest.position);
            }
            if (eventListener != null) {
                eventListener.onMonsterHit(closest.position, impulse, isLethal);
            }
            player.triggerHitstop(equippedWeapon.hitstop);
        }
    }

    private void executeKick(ActionPlayerController player, List<ActionMonsterEntity> monsters) {
        isKicking = true;
        kickTimer = KICK_DURATION;
        kickCooldownTimer = KICK_COOLDOWN;
        triggeredKickSound = true;

        for (ActionMonsterEntity monster : monsters) {
            if (monster.isDead()) continue;

            float dist = (float) Math.hypot(monster.position.x - player.position.x, monster.position.z - player.position.z);
            if (dist > 2.2f + monster.radius) continue;

            Vector3 toMonster = new Vector3(monster.position.x - player.position.x, 0f, monster.position.z - player.position.z).nor();
            float dot = player.forwardHorizontal.dot(toMonster);
            if (dot > 0.65f) { // Within 50 degree forward kick cone
                // Launch monster flying backward violently!
                Vector3 kickImpulse = new Vector3(player.forwardHorizontal).scl(17.5f).add(0f, 3.5f, 0f);
                monster.launchKick(kickImpulse);
                monster.takeDamage(25f, null, false);
                if (eventListener != null) {
                    eventListener.onMonsterHit(monster.position, kickImpulse, false);
                }

                player.triggerHitstop(0.045f);
                player.addTrauma(0.35f);
                break;
            }
        }
    }

    public void onMonsterAttackHitsPlayer(ActionMonsterEntity monster, ActionPlayerController player) {
        if (player.isInvulnerable() || playerInvulnTimer > 0f) return; // Dashing or I-Frames

        if (isParrying) {
            // Successful Parry! Stagger monster with poise break and flash
            monster.staggerFromParry(1.2f);
            isParrying = false;
            parryFlashTimer = 0.25f;
            triggeredParrySound = true;
            player.triggerHitstop(0.065f);
            player.addTrauma(0.10f);
            return;
        }

        // Damage connects with 0.55s invulnerability buffer
        playerInvulnTimer = 0.55f;
        playerHealth = Math.max(0f, playerHealth - monster.attackDamage);
        player.addTrauma(0.45f);
        if (eventListener != null) {
            eventListener.onPlayerHit();
        }
    }

    private void spawnDeathShards(Vector3 pos) {
        // Drop 2 health shards and 1 mana shard on dismemberment
        shards.add(new ShardPickup(pos, ShardPickup.ShardType.HEALTH, 20f));
        shards.add(new ShardPickup(pos, ShardPickup.ShardType.HEALTH, 20f));
        shards.add(new ShardPickup(pos, ShardPickup.ShardType.MANA, 25f));
    }

    private void updateProjectiles(float delta, List<ActionMonsterEntity> monsters, ActionPlayerController player) {
        for (int i = projectiles.size() - 1; i >= 0; i--) {
            Projectile p = projectiles.get(i);
            p.lifetime -= delta;
            p.position.add(p.velocity.x * delta, p.velocity.y * delta, p.velocity.z * delta);

            boolean hit = false;
            for (ActionMonsterEntity monster : monsters) {
                if (monster.isDead()) continue;
                if (p.position.dst(monster.position) < monster.radius + 0.35f) {
                    hit = true;
                    boolean isLethal = (monster.health <= p.damage);
                    monster.takeDamage(p.damage, new Vector3(p.velocity).nor().scl(4f), isLethal);
                    if (isLethal) spawnDeathShards(monster.position);
                    if (eventListener != null) {
                        eventListener.onMonsterHit(monster.position, p.velocity, isLethal);
                    }
                    break;
                }
            }

            if (hit || p.lifetime <= 0f) {
                projectiles.remove(i);
            }
        }
    }

    private void updateShardPickups(float delta, ActionPlayerController player) {
        for (int i = shards.size() - 1; i >= 0; i--) {
            ShardPickup s = shards.get(i);
            s.lifetime -= delta;

            float dist = s.position.dst(player.position);
            // Vacuum towards player when within 4.5 meters
            if (dist < 4.5f) {
                Vector3 pull = new Vector3(player.position).sub(s.position).nor().scl(14.0f);
                s.velocity.lerp(pull, delta * 12f);
            } else {
                s.velocity.scl((float) Math.pow(0.85f, delta * 60f));
            }

            s.position.add(s.velocity.x * delta, s.velocity.y * delta, s.velocity.z * delta);

            // Collect pickup on contact
            if (dist < 0.85f) {
                if (s.type == ShardPickup.ShardType.HEALTH) {
                    playerHealth = Math.min(maxPlayerHealth, playerHealth + s.amount);
                } else {
                    playerMana = Math.min(maxPlayerMana, playerMana + s.amount);
                }
                shards.remove(i);
            } else if (s.lifetime <= 0f) {
                shards.remove(i);
            }
        }
    }
}
