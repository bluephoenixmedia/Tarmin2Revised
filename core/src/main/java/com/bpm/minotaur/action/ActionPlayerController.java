package com.bpm.minotaur.action;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;

/**
 * High-speed 3D First-Person Player Controller inspired by DOOM 2016 and Project Brutality:
 * - Continuous 3D WASD acceleration with smooth wall sliding
 * - Pitch/Yaw mouse look with clamped angles
 * - Lateral Dash burst with I-Frames (Shift / double-tap)
 * - Jump & Vault (Space)
 * - Crouch-Slide (Ctrl)
 * - Trauma camera shake and micro-hitstop integration
 */
public class ActionPlayerController {

    // Locomotion constants
    public static final float BASE_RUN_SPEED = 9.2f;
    public static final float ACCELERATION = 65.0f;
    public static final float FRICTION = 11.0f;
    public static final float JUMP_VELOCITY = 7.2f;
    public static final float GRAVITY = -19.5f;
    public static final float EYE_HEIGHT_STAND = 0.65f;
    public static final float EYE_HEIGHT_CROUCH = 0.32f;
    public static final float COLLISION_RADIUS = 0.38f;

    // Dash constants
    public static final float DASH_SPEED = 23.0f;
    public static final float DASH_DURATION = 0.22f;
    public static final float DASH_COOLDOWN = 0.85f;

    // Slide constants
    public static final float SLIDE_DURATION = 0.55f;
    public static final float SLIDE_SPEED = 14.5f;

    // Transform state
    public final Vector3 position = new Vector3();
    public final Vector3 velocity = new Vector3();
    public final Vector3 direction = new Vector3();
    public final Vector3 forwardHorizontal = new Vector3();
    public final Vector3 rightHorizontal = new Vector3();
    public final Vector3 cameraOffset = new Vector3();

    // Look angles in degrees
    public float yaw = 90.0f;    // Facing North initially (along -Z)
    public float pitch = 0.0f;
    public float mouseSensitivity = 0.16f;

    // Mobility state
    public boolean isGrounded = true;
    public boolean isDashing = false;
    public boolean isSliding = false;
    public float dashTimer = 0f;
    public float dashCooldownTimer = 0f;
    public float slideTimer = 0f;
    public float currentEyeHeight = EYE_HEIGHT_STAND;

    // Dash vector
    private final Vector3 dashDirection = new Vector3();

    // Kinetic feedback & head bob
    public float trauma = 0f;
    public float hitstopTimer = 0f;
    public float bobTimer = 0f;
    public float bobAmount = 0f;

    public ActionPlayerController(Vector3 spawnPosition) {
        this.position.set(spawnPosition);
        updateDirectionVectors();
    }

    /**
     * Updates player camera look based on mouse delta.
     */
    public void processMouseLook(float deltaX, float deltaY) {
        yaw -= deltaX * mouseSensitivity;
        pitch -= deltaY * mouseSensitivity;

        // Keep yaw in [0, 360)
        yaw = (yaw % 360f + 360f) % 360f;
        // Clamp pitch to avoid neck inversion
        pitch = MathUtils.clamp(pitch, -86.0f, 86.0f);

        updateDirectionVectors();
    }

    private void updateDirectionVectors() {
        float cosPitch = MathUtils.cosDeg(pitch);
        float sinPitch = MathUtils.sinDeg(pitch);
        float cosYaw = MathUtils.cosDeg(yaw);
        float sinYaw = MathUtils.sinDeg(yaw);

        // Standard OpenGL forward vector: yaw 90 -> cos=0, sin=1 -> (0, 0, -1)
        direction.set(cosYaw * cosPitch, sinPitch, -sinYaw * cosPitch).nor();

        // Horizontal forward & right (for WASD plane)
        forwardHorizontal.set(cosYaw, 0f, -sinYaw).nor();
        rightHorizontal.set(sinYaw, 0f, cosYaw).nor();
    }

    /**
     * Primary physics & input update tick.
     */
    public void update(float delta, boolean[][] solidGrid) {
        // Handle micro-hitstop freeze frame
        if (hitstopTimer > 0f) {
            hitstopTimer -= delta;
            return;
        }

        // Decay trauma & dash cooldown
        if (trauma > 0f) trauma = Math.max(0f, trauma - delta * 1.3f);
        if (dashCooldownTimer > 0f) dashCooldownTimer = Math.max(0f, dashCooldownTimer - delta);

        // 1. Process Dash
        if (isDashing) {
            dashTimer -= delta;
            velocity.set(dashDirection).scl(DASH_SPEED);
            if (dashTimer <= 0f) {
                isDashing = false;
            }
        }
        // 2. Process Slide
        else if (isSliding) {
            slideTimer -= delta;
            velocity.scl((float) Math.pow(0.88f, delta * 60f));
            currentEyeHeight = MathUtils.lerp(currentEyeHeight, EYE_HEIGHT_CROUCH, delta * 15f);
            if (slideTimer <= 0f || velocity.len2() < 4f) {
                isSliding = false;
            }
        }
        // 3. Normal WASD Ground/Air Movement
        else {
            currentEyeHeight = MathUtils.lerp(currentEyeHeight, EYE_HEIGHT_STAND, delta * 12f);
            handleInputLocomotion(delta);
        }

        // 4. Gravity & Vertical motion
        velocity.y += GRAVITY * delta;
        position.y += velocity.y * delta;

        // Ground floor collision (y = currentEyeHeight is standing floor)
        if (position.y <= currentEyeHeight) {
            position.y = currentEyeHeight;
            velocity.y = 0f;
            isGrounded = true;
        } else {
            isGrounded = false;
        }

        // 5. Horizontal Integration with Wall-Sliding Collision
        resolveHorizontalCollision(delta, solidGrid);

        // 6. Head bobbing & camera shake calculations
        updateCameraShakeAndBob(delta);
    }

    private void handleInputLocomotion(float delta) {
        Vector3 moveWish = new Vector3();

        if (Gdx.input != null) {
            float moveX = 0f;
            float moveZ = 0f;

            if (Gdx.input.isKeyPressed(Input.Keys.W)) moveZ += 1f;
            if (Gdx.input.isKeyPressed(Input.Keys.S)) moveZ -= 1f;
            if (Gdx.input.isKeyPressed(Input.Keys.A)) moveX -= 1f;
            if (Gdx.input.isKeyPressed(Input.Keys.D)) moveX += 1f;

            if (moveZ != 0f || moveX != 0f) {
                moveWish.add(forwardHorizontal.x * moveZ + rightHorizontal.x * moveX,
                             0f,
                             forwardHorizontal.z * moveZ + rightHorizontal.z * moveX).nor();
            }

            // Trigger Dash on Shift if off cooldown
            if (Gdx.input.isKeyJustPressed(Input.Keys.SHIFT_LEFT) && dashCooldownTimer <= 0f) {
                isDashing = true;
                dashTimer = DASH_DURATION;
                dashCooldownTimer = DASH_COOLDOWN;
                dashDirection.set(moveWish.isZero() ? forwardHorizontal : moveWish).nor();
                addTrauma(0.15f);
                return;
            }

            // Trigger Crouch-Slide on Ctrl while running
            if (Gdx.input.isKeyJustPressed(Input.Keys.CONTROL_LEFT) && isGrounded && velocity.len() > 6.0f) {
                isSliding = true;
                slideTimer = SLIDE_DURATION;
                velocity.nor().scl(SLIDE_SPEED);
                return;
            }

            // Trigger Jump on Space
            if (Gdx.input.isKeyJustPressed(Input.Keys.SPACE) && isGrounded) {
                velocity.y = JUMP_VELOCITY;
                isGrounded = false;
            }
        }

        // Ground friction & acceleration
        if (isGrounded) {
            float speed = (float) Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);
            if (speed > 0f) {
                float drop = speed * FRICTION * delta;
                float newSpeed = Math.max(0f, speed - drop);
                velocity.x *= newSpeed / speed;
                velocity.z *= newSpeed / speed;
            }

            if (!moveWish.isZero()) {
                velocity.x += moveWish.x * ACCELERATION * delta;
                velocity.z += moveWish.z * ACCELERATION * delta;

                // Clamp to max run speed
                float hSpeed = (float) Math.hypot(velocity.x, velocity.z);
                if (hSpeed > BASE_RUN_SPEED) {
                    velocity.x = (velocity.x / hSpeed) * BASE_RUN_SPEED;
                    velocity.z = (velocity.z / hSpeed) * BASE_RUN_SPEED;
                }
            }
        } else {
            // Air control (reduced acceleration, no ground friction)
            if (!moveWish.isZero()) {
                velocity.x += moveWish.x * ACCELERATION * 0.45f * delta;
                velocity.z += moveWish.z * ACCELERATION * 0.45f * delta;
            }
        }
    }

    /**
     * Continuous swept capsule collision against arena solid tiles with sliding planes and corner chamfers.
     */
    private void resolveHorizontalCollision(float delta, boolean[][] solidGrid) {
        float newX = position.x + velocity.x * delta;
        float newZ = position.z + velocity.z * delta;

        if (solidGrid == null) {
            position.x = newX;
            position.z = newZ;
            return;
        }

        int arenaH = solidGrid.length;
        int arenaW = solidGrid[0].length;

        // Tile coords for 3x3 surrounding check
        int playerTileX = MathUtils.floor(newX);
        int playerTileY = MathUtils.floor(-newZ); // Tarmin 3D -Z is Tile Y

        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                int tx = playerTileX + dx;
                int ty = playerTileY + dy;

                if (tx < 0 || tx >= arenaW || ty < 0 || ty >= arenaH) continue;
                if (!solidGrid[ty][tx]) continue; // Open floor

                // Solid tile bounding box: x in [tx, tx+1], z in [-(ty+1), -ty]
                float minX = tx;
                float maxX = tx + 1f;
                float minZ = -(ty + 1f);
                float maxZ = -ty;

                // Find closest point on AABB to player
                float closeX = MathUtils.clamp(newX, minX, maxX);
                float closeZ = MathUtils.clamp(newZ, minZ, maxZ);

                float diffX = newX - closeX;
                float diffZ = newZ - closeZ;
                float distSq = diffX * diffX + diffZ * diffZ;

                if (distSq < COLLISION_RADIUS * COLLISION_RADIUS) {
                    float dist = (float) Math.sqrt(distSq);
                    float normalX = 0f, normalZ = 0f;

                    if (dist > 0.0001f) {
                        normalX = diffX / dist;
                        normalZ = diffZ / dist;
                    } else {
                        // Embedded directly in wall center; push along velocity opposite
                        normalX = -velocity.x;
                        normalZ = -velocity.z;
                        float nLen = (float) Math.hypot(normalX, normalZ);
                        if (nLen > 0.001f) { normalX /= nLen; normalZ /= nLen; }
                        else { normalX = 1f; normalZ = 0f; }
                    }

                    // Push player out to collision radius
                    float penetration = COLLISION_RADIUS - dist;
                    newX += normalX * penetration;
                    newZ += normalZ * penetration;

                    // Project velocity along tangent (sliding plane)
                    float dot = velocity.x * normalX + velocity.z * normalZ;
                    if (dot < 0f) {
                        velocity.x -= dot * normalX;
                        velocity.z -= dot * normalZ;
                    }
                }
            }
        }

        position.x = newX;
        position.z = newZ;
    }

    private void updateCameraShakeAndBob(float delta) {
        float hSpeed = (float) Math.hypot(velocity.x, velocity.z);
        if (isGrounded && hSpeed > 1.0f) {
            bobTimer += delta * (hSpeed * 1.35f);
            bobAmount = MathUtils.sin(bobTimer) * 0.038f;
        } else {
            bobAmount = MathUtils.lerp(bobAmount, 0f, delta * 10f);
        }

        // Trauma camera kick noise
        cameraOffset.setZero();
        if (trauma > 0f) {
            float shake = trauma * trauma; // Quadratic trauma falloff
            cameraOffset.x = (MathUtils.random() * 2f - 1f) * 0.045f * shake;
            cameraOffset.y = (MathUtils.random() * 2f - 1f) * 0.045f * shake;
            cameraOffset.z = (MathUtils.random() * 2f - 1f) * 0.045f * shake;
        }
    }

    public void addTrauma(float amount) {
        trauma = MathUtils.clamp(trauma + amount, 0f, 1.0f);
    }

    public void triggerHitstop(float seconds) {
        hitstopTimer = Math.max(hitstopTimer, seconds);
    }

    public boolean isInvulnerable() {
        return isDashing;
    }

    public float getEyeY() {
        return position.y + bobAmount + cameraOffset.y;
    }
}
