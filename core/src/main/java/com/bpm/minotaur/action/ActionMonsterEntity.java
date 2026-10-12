package com.bpm.minotaur.action;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.bpm.minotaur.gamedata.monster.Monster;

import java.util.List;

/**
 * Real-time mobbing monster entity for the action prototype:
 * - Continuous 3D steering and flocking separation
 * - Telegraphed attack windups (visual and audio cues for dodging/parrying)
 * - Poise break / stagger frames on parry
 * - Dynamic kick physics (sent flying into wall hazards)
 * - Real-time dismemberment on death and resource shard drops
 */
public class ActionMonsterEntity {

    public enum State {
        CHASE,
        WINDUP,
        ATTACKING,
        RECOVERY,
        STAGGERED,
        LAUNCHED,
        DEAD
    }

    public final Monster.MonsterType type;
    public final Vector3 position = new Vector3();
    public final Vector3 velocity = new Vector3();
    public final Vector3 facing = new Vector3();

    public float health;
    public float maxHealth;
    public float radius;
    public float baseSpeed;
    public float attackReach;
    public float attackDamage;

    public State state = State.CHASE;
    public float stateTimer = 0f;
    public float windupDuration = 0.45f;
    public float attackTellGlow = 0f; // 0..1 indicator for telegraphed visual tell

    // Stagger & launch parameters
    public float staggerTimer = 0f;
    public boolean wasSeveredLethally = false;
    public Vector3 deathImpulse = new Vector3();

    public ActionMonsterEntity(Monster.MonsterType type, Vector3 spawnPos) {
        this.type = type;
        this.position.set(spawnPos);
        this.facing.set(0f, 0f, 1f);

        // Stats by archetype
        switch (type) {
            case MINOTAUR:
                this.maxHealth = 380f;
                this.baseSpeed = 7.5f;
                this.radius = 0.65f;
                this.attackReach = 2.4f;
                this.attackDamage = 45f;
                this.windupDuration = 0.55f;
                break;
            case GHOUL:
                this.maxHealth = 130f;
                this.baseSpeed = 7.0f;
                this.radius = 0.40f;
                this.attackReach = 1.6f;
                this.attackDamage = 22f;
                this.windupDuration = 0.35f;
                break;
            case SKELETAL_WIZARD:
                this.maxHealth = 110f;
                this.baseSpeed = 4.2f;
                this.radius = 0.42f;
                this.attackReach = 12.0f; // Ranged spell caster
                this.attackDamage = 30f;
                this.windupDuration = 0.70f;
                break;
            case HOBGOBLIN:
                this.maxHealth = 175f;
                this.baseSpeed = 5.8f;
                this.radius = 0.45f;
                this.attackReach = 1.8f;
                this.attackDamage = 28f;
                this.windupDuration = 0.42f;
                break;
            case SKELETON:
            default:
                this.maxHealth = 95f;
                this.baseSpeed = 5.2f;
                this.radius = 0.40f;
                this.attackReach = 1.6f;
                this.attackDamage = 18f;
                this.windupDuration = 0.40f;
                break;
        }
        this.health = this.maxHealth;
    }

    /**
     * Updates AI, steering, attack states, and collision against the arena.
     * @return true if an attack hit connected with the player this frame
     */
    public boolean update(float delta, Vector3 playerPos, List<ActionMonsterEntity> others,
                          boolean[][] solidGrid, List<ActionArenaBuilder.HazardSpike> hazards) {
        if (state == State.DEAD) return false;

        boolean didDealDamage = false;

        // 1. State machine
        switch (state) {
            case LAUNCHED:
                // Decelerate and check hazard impacts
                velocity.scl((float) Math.pow(0.85f, delta * 60f));
                if (velocity.len2() < 2f) {
                    state = State.CHASE;
                }
                // Check spike hazard collisions while airborne/sliding
                if (hazards != null) {
                    for (ActionArenaBuilder.HazardSpike spike : hazards) {
                        if (position.dst(spike.position) < 1.4f) {
                            takeDamage(spike.damage, new Vector3(0, 5, 0), true);
                            break;
                        }
                    }
                }
                break;

            case STAGGERED:
                staggerTimer -= delta;
                velocity.scl((float) Math.pow(0.6f, delta * 60f));
                if (staggerTimer <= 0f) {
                    state = State.CHASE;
                }
                break;

            case CHASE:
                attackTellGlow = 0f;
                float distToPlayer = (float) Math.hypot(playerPos.x - position.x, playerPos.z - position.z);

                // Steer toward player
                Vector3 toPlayer = new Vector3(playerPos.x - position.x, 0f, playerPos.z - position.z).nor();
                facing.set(toPlayer);

                // Flocking separation from other monsters
                Vector3 separation = new Vector3();
                if (others != null) {
                    for (ActionMonsterEntity other : others) {
                        if (other == this || other.state == State.DEAD) continue;
                        float d = position.dst(other.position);
                        float minDist = radius + other.radius;
                        if (d < minDist && d > 0.001f) {
                            Vector3 away = new Vector3(position).sub(other.position).nor();
                            separation.add(away.scl((minDist - d) * 12f));
                        }
                    }
                }

                // If in attack reach, initiate telegraphed windup
                if (distToPlayer <= attackReach) {
                    state = State.WINDUP;
                    stateTimer = windupDuration;
                    velocity.setZero();
                } else {
                    velocity.set(toPlayer).scl(baseSpeed).add(separation);
                }
                break;

            case WINDUP:
                stateTimer -= delta;
                // Pulsate glowing attack tell cue
                attackTellGlow = 1f - Math.max(0f, stateTimer / windupDuration);
                // Keep facing player during windup
                facing.set(playerPos.x - position.x, 0f, playerPos.z - position.z).nor();
                velocity.scl((float) Math.pow(0.7f, delta * 60f));

                if (stateTimer <= 0f) {
                    state = State.ATTACKING;
                    stateTimer = 0.18f; // Active attack hit window
                }
                break;

            case ATTACKING:
                stateTimer -= delta;
                // Check if attack strikes player
                float hitDist = (float) Math.hypot(playerPos.x - position.x, playerPos.z - position.z);
                if (hitDist <= attackReach + 0.35f) {
                    didDealDamage = true;
                }
                if (stateTimer <= 0f) {
                    state = State.RECOVERY;
                    stateTimer = 0.45f;
                }
                break;

            case RECOVERY:
                attackTellGlow = 0f;
                stateTimer -= delta;
                velocity.scl((float) Math.pow(0.8f, delta * 60f));
                if (stateTimer <= 0f) {
                    state = State.CHASE;
                }
                break;
        }

        // 2. Integrate position and resolve collisions with arena walls
        resolveWallCollisions(delta, solidGrid);

        return didDealDamage;
    }

    private void resolveWallCollisions(float delta, boolean[][] solidGrid) {
        float newX = position.x + velocity.x * delta;
        float newZ = position.z + velocity.z * delta;

        if (solidGrid != null) {
            int arenaH = solidGrid.length;
            int arenaW = solidGrid[0].length;
            int tileX = MathUtils.floor(newX);
            int tileY = MathUtils.floor(-newZ);

            for (int dy = -1; dy <= 1; dy++) {
                for (int dx = -1; dx <= 1; dx++) {
                    int tx = tileX + dx;
                    int ty = tileY + dy;
                    if (tx < 0 || tx >= arenaW || ty < 0 || ty >= arenaH) continue;
                    if (!solidGrid[ty][tx]) continue;

                    float minX = tx;
                    float maxX = tx + 1f;
                    float minZ = -(ty + 1f);
                    float maxZ = -ty;

                    float closeX = MathUtils.clamp(newX, minX, maxX);
                    float closeZ = MathUtils.clamp(newZ, minZ, maxZ);

                    float diffX = newX - closeX;
                    float diffZ = newZ - closeZ;
                    float distSq = diffX * diffX + diffZ * diffZ;

                    if (distSq < radius * radius) {
                        float dist = (float) Math.sqrt(distSq);
                        float normalX = (dist > 0.001f) ? diffX / dist : 1f;
                        float normalZ = (dist > 0.001f) ? diffZ / dist : 0f;

                        // Push out
                        float penetration = radius - dist;
                        newX += normalX * penetration;
                        newZ += normalZ * penetration;

                        // Cancel into-wall velocity
                        float dot = velocity.x * normalX + velocity.z * normalZ;
                        if (dot < 0f) {
                            // If launched violently into a wall, take heavy impact slam damage!
                            if (state == State.LAUNCHED && Math.abs(dot) > 8f) {
                                takeDamage(Math.abs(dot) * 6f, new Vector3(normalX, 1f, normalZ), false);
                            }
                            velocity.x -= dot * normalX;
                            velocity.z -= dot * normalZ;
                        }
                    }
                }
            }
        }

        position.x = newX;
        position.z = newZ;
    }

    public void takeDamage(float amount, Vector3 impulse, boolean isLethalSevering) {
        if (state == State.DEAD) return;

        this.health -= amount;
        if (impulse != null) {
            this.velocity.add(impulse);
        }

        if (this.health <= 0f) {
            this.health = 0f;
            this.state = State.DEAD;
            this.wasSeveredLethally = isLethalSevering;
            if (impulse != null) this.deathImpulse.set(impulse);
        } else {
            // Heavy hit staggers the monster
            if (amount > 30f && state != State.LAUNCHED) {
                this.state = State.STAGGERED;
                this.staggerTimer = 0.50f;
            }
        }
    }

    public void staggerFromParry(float duration) {
        this.state = State.STAGGERED;
        this.staggerTimer = duration;
        this.attackTellGlow = 0f;
        this.velocity.setZero();
    }

    public void launchKick(Vector3 kickImpulse) {
        this.state = State.LAUNCHED;
        this.velocity.set(kickImpulse);
        this.attackTellGlow = 0f;
    }

    public boolean isDead() {
        return state == State.DEAD;
    }
}
