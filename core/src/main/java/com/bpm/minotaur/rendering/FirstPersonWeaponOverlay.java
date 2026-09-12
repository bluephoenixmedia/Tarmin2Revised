package com.bpm.minotaur.rendering;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.badlogic.gdx.assets.AssetManager;
import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.gamedata.item.ItemDataManager;
import com.bpm.minotaur.gamedata.item.ItemTemplate;
import com.bpm.minotaur.rendering.animation.AnimationArchetype;
import com.bpm.minotaur.rendering.animation.CombatMotionProfile;
import com.bpm.minotaur.rendering.animation.WeaponTrailRenderer;

import java.util.ArrayList;
import java.util.List;

/**
 * Overhauled First-Person Weapon & Combat Animation System.
 * Supports persistent ready stance with breathing and walking bob, camera inertia sway,
 * dedicated animation archetypes, multi-phase snappy kinematics, true hit-frame callbacks,
 * rhythmic windowed combos with devastating finishers, dual-hand viewport (shields/offhand),
 * and procedural ribbon slash trails.
 */
public class FirstPersonWeaponOverlay {

    public interface HitFrameCallback {
        void onHitFrame(CombatMotionProfile profile);
    }

    // Main hand item and texture
    private Item mainHandItem;
    private TextureRegion mainHandTexture;
    private String[] mainHandSpriteData;
    private Color mainHandSpriteColor = Color.WHITE;
    private AnimationArchetype mainHandArchetype = AnimationArchetype.SLASHING_1H;

    // Off-hand item and texture
    private Item offHandItem;
    private TextureRegion offHandTexture;
    private String[] offHandSpriteData;
    private Color offHandSpriteColor = Color.WHITE;

    // Motion and combo state
    private boolean active = false;
    private float attackTimer = 0f;
    private List<CombatMotionProfile> comboChain = new ArrayList<>();
    private int comboIndex = 0;
    private CombatMotionProfile currentProfile;
    private boolean hasFiredHitFrame = false;
    private HitFrameCallback hitFrameCallback;

    // Combo timing window (seconds remaining to chain next strike)
    private float comboWindowTimer = 0f;
    private static final float COMBO_WINDOW_MAX = 0.55f;

    // Persistent stance dynamics
    private float idleBobTimer = 0f;
    private float walkBobTimer = 0f;
    private boolean isWalking = false;
    private float turnSway = 0f;
    private float menuLoweringProgress = 0f; // 0 = fully ready, 1 = lowered offscreen

    // Off-hand guard & flinch state
    private boolean isGuarding = false;
    private float guardFlinchTimer = 0f;
    private static final float GUARD_FLINCH_DURATION = 0.20f;

    // Blood coating on weapon
    private float bloodLevel = 0f; // 0.0 to 1.0

    // Procedural weapon trail
    private final WeaponTrailRenderer trailRenderer = new WeaponTrailRenderer();

    private final AssetManager assetManager;

    public FirstPersonWeaponOverlay(ItemDataManager itemDataManager, AssetManager assetManager) {
        this.assetManager = assetManager;
        this.currentProfile = new CombatMotionProfile();
    }

    public void setHitFrameCallback(HitFrameCallback callback) {
        this.hitFrameCallback = callback;
    }

    public void setEquipment(Item rightHand, Item leftHand) {
        if (this.mainHandItem != rightHand) {
            this.mainHandItem = rightHand;
            this.mainHandTexture = resolveTexture(rightHand);
            if (rightHand != null) {
                this.mainHandSpriteData = rightHand.getSpriteData();
                this.mainHandSpriteColor = (rightHand.getColor() != null) ? rightHand.getColor().cpy() : Color.WHITE;
            } else {
                this.mainHandSpriteData = null;
            }
            this.mainHandArchetype = AnimationArchetype.fromItem(rightHand);
            rebuildComboChain();
        }

        if (this.offHandItem != leftHand) {
            this.offHandItem = leftHand;
            this.offHandTexture = resolveTexture(leftHand);
            if (leftHand != null) {
                this.offHandSpriteData = leftHand.getSpriteData();
                this.offHandSpriteColor = (leftHand.getColor() != null) ? leftHand.getColor().cpy() : Color.WHITE;
            } else {
                this.offHandSpriteData = null;
            }
            rebuildComboChain();
        }
    }

    private void rebuildComboChain() {
        boolean isDualWielding = (mainHandItem != null && offHandItem != null
                && mainHandItem.isWeapon() && offHandItem.isWeapon());
        this.comboChain = CombatMotionProfile.buildComboChain(mainHandArchetype, mainHandItem, isDualWielding);
        if (this.comboIndex >= this.comboChain.size()) {
            this.comboIndex = 0;
        }
        if (!comboChain.isEmpty()) {
            this.currentProfile = comboChain.get(this.comboIndex);
        }
    }

    /**
     * Triggers the next attack in the combo chain.
     */
    public void triggerAttack(Item weapon) {
        setEquipment(weapon, this.offHandItem);

        if (comboChain.isEmpty()) {
            rebuildComboChain();
        }

        // Advance combo if within the window, else reset to opener
        if (comboWindowTimer > 0f && !comboChain.isEmpty()) {
            comboIndex = (comboIndex + 1) % comboChain.size();
        } else {
            comboIndex = 0;
        }

        this.currentProfile = comboChain.get(comboIndex);
        this.active = true;
        this.attackTimer = 0f;
        this.hasFiredHitFrame = false;
        this.comboWindowTimer = 0f;

        // Configure trail color based on weapon elemental/category
        configureTrailColor(weapon);
        trailRenderer.clear();
        trailRenderer.setEmitting(true);
    }

    /**
     * Triggers an offensive shield bash.
     */
    public void triggerShieldBash(Item shield) {
        this.currentProfile = new CombatMotionProfile();
        this.currentProfile.comboName = "SHIELD BASH";
        this.currentProfile.isShieldBash = true;
        this.currentProfile.duration = 0.30f;
        this.currentProfile.anticipationRatio = 0.20f;
        this.currentProfile.impactRatio = 0.38f;
        this.currentProfile.screenTrauma = 0.30f;
        this.currentProfile.damageMultiplier = 1.0f;

        this.active = true;
        this.attackTimer = 0f;
        this.hasFiredHitFrame = false;
        this.comboWindowTimer = 0f;
        trailRenderer.clear();
    }

    /**
     * Triggers defensive guard deflection flinch when an incoming hit is blocked.
     */
    public void triggerGuardFlinch() {
        this.guardFlinchTimer = GUARD_FLINCH_DURATION;
    }

    /**
     * Halts an in-flight swing when hitting a solid obstacle/wall, resetting combo.
     */
    public void triggerWallClank() {
        if (active) {
            active = false;
            attackTimer = 0f;
            comboIndex = 0;
            comboWindowTimer = 0f;
            trailRenderer.clear();
        }
    }

    /**
     * Whiff on empty air: resets combo string back to opener.
     */
    public void triggerWhiff() {
        comboIndex = 0;
        comboWindowTimer = 0f;
    }

    public void addBloodToWeapon() {
        this.bloodLevel = Math.min(1.0f, this.bloodLevel + 0.35f);
    }

    public void setWalking(boolean walking) {
        this.isWalking = walking;
    }

    public void addTurnSway(float yawDelta) {
        // Clamp and add rotational inertia lag
        this.turnSway = MathUtils.clamp(this.turnSway + (yawDelta * 35f), -120f, 120f);
    }

    public void setGuarding(boolean guarding) {
        this.isGuarding = guarding;
    }

    public void setMenuLowered(boolean lowered) {
        // Smooth transition target handled in update
    }

    public void update(float delta) {
        // Update persistent breathing and walking bob
        idleBobTimer += delta * 2.2f;
        if (isWalking) {
            walkBobTimer += delta * 8.5f;
        }

        // Decay camera turn sway back to zero
        turnSway = MathUtils.lerp(turnSway, 0f, delta * 9f);

        // Decay blood coating on blade
        if (bloodLevel > 0f) {
            bloodLevel = Math.max(0f, bloodLevel - (delta * 0.08f));
        }

        // Decay guard flinch
        if (guardFlinchTimer > 0f) {
            guardFlinchTimer -= delta;
        }

        // Update trail renderer
        trailRenderer.update(delta);

        // Update attack animation
        if (active && currentProfile != null) {
            attackTimer += delta;
            float progress = attackTimer / currentProfile.duration;

            // Stop emitting trail once past impact follow-through
            if (progress > currentProfile.impactRatio + 0.15f) {
                trailRenderer.setEmitting(false);
            }

            // Fire True Hit-Frame callback exactly when blade connects
            if (progress >= currentProfile.impactRatio && !hasFiredHitFrame) {
                hasFiredHitFrame = true;
                if (hitFrameCallback != null) {
                    hitFrameCallback.onHitFrame(currentProfile);
                }
                // Open combo chaining window
                comboWindowTimer = COMBO_WINDOW_MAX;
            }

            if (attackTimer >= currentProfile.duration) {
                active = false;
                attackTimer = 0f;
                trailRenderer.setEmitting(false);
            }
        } else {
            // Count down active combo rhythm window
            if (comboWindowTimer > 0f) {
                comboWindowTimer -= delta;
                if (comboWindowTimer <= 0f) {
                    comboWindowTimer = 0f;
                    comboIndex = 0; // Rhythm expired, reset combo to opener
                }
            }
        }
    }

    public boolean isActive() {
        return active;
    }

    /**
     * Micro-lock: during windup anticipation, player movement is locked to guarantee aim.
     * After impact frame, recovery is immediately cancelable by movement.
     */
    public boolean isMovementLocked() {
        if (!active || currentProfile == null) return false;
        float progress = attackTimer / currentProfile.duration;
        return progress < currentProfile.impactRatio;
    }

    public int getComboIndex() {
        return comboIndex;
    }

    public CombatMotionProfile getCurrentProfile() {
        return currentProfile;
    }

    public void render(SpriteBatch batch, Viewport viewport) {
        float worldW = viewport.getWorldWidth();
        float worldH = viewport.getWorldHeight();

        // Calculate walking and breathing bob offsets
        float breathY = MathUtils.sin(idleBobTimer) * (worldH * 0.012f);
        float walkBobX = isWalking ? MathUtils.cos(walkBobTimer * 0.5f) * (worldW * 0.008f) : 0f;
        float walkBobY = isWalking ? Math.abs(MathUtils.sin(walkBobTimer)) * (worldH * 0.022f) : 0f;
        float totalBobX = walkBobX + (turnSway * (worldW / 1920f));
        float totalBobY = breathY - walkBobY;

        // 1. Render Off-Hand (Left Hand: Shield, Offhand Weapon, or Lantern)
        renderOffHand(batch, viewport, worldW, worldH, totalBobX, totalBobY);

        // 2. Render Main Hand (Right Hand)
        renderMainHand(batch, viewport, worldW, worldH, totalBobX, totalBobY);
    }

    private void renderMainHand(SpriteBatch batch, Viewport viewport, float worldW, float worldH, float bobX, float bobY) {
        if (mainHandTexture == null) {
            return;
        }

        float drawX, drawY, rotation;

        if (active && currentProfile != null) {
            float progress = attackTimer / currentProfile.duration;
            CombatMotionProfile.MotionState state = currentProfile.evaluate(progress);

            drawX = (worldW * state.xRel) + (bobX * 0.3f);
            drawY = (worldH * state.yRel) + (bobY * 0.3f);
            rotation = state.rotation;
        } else {
            // Idle ready posture: moved down just off-screen
            drawX = (worldW * 0.76f) + bobX;
            drawY = (worldH * CombatMotionProfile.IDLE_Y_REL) + bobY;
            rotation = -22f;

            if (mainHandArchetype == AnimationArchetype.SLASHING_2H) {
                // Centered two-handed grip stance
                drawX = (worldW * 0.60f) + (bobX * 0.6f);
                rotation = -12f;
            } else if (mainHandArchetype == AnimationArchetype.THRUSTING_PIERCE) {
                drawX = (worldW * 0.70f) + bobX;
                rotation = -32f;
            }
        }

        float targetHeight = worldH * 0.46f;
        float ratio = (float) mainHandTexture.getRegionWidth() / (float) mainHandTexture.getRegionHeight();
        float targetWidth = targetHeight * ratio;

        float originX = targetWidth * 0.5f;
        float originY = targetHeight * 0.10f;

        // Sample blade tip and hilt positions for procedural trail
        if (active && currentProfile != null) {
            float rad = rotation * MathUtils.degreesToRadians;
            float cos = MathUtils.cos(rad);
            float sin = MathUtils.sin(rad);

            float tipLocalX = targetWidth * 0.5f - originX;
            float tipLocalY = targetHeight * 0.95f - originY;
            float hiltLocalX = targetWidth * 0.5f - originX;
            float hiltLocalY = targetHeight * 0.25f - originY;

            float tipWorldX = drawX + originX + (tipLocalX * cos - tipLocalY * sin);
            float tipWorldY = drawY + originY + (tipLocalX * sin + tipLocalY * cos);
            float hiltWorldX = drawX + originX + (hiltLocalX * cos - hiltLocalY * sin);
            float hiltWorldY = drawY + originY + (hiltLocalX * sin + hiltLocalY * cos);

            trailRenderer.addSample(tipWorldX, tipWorldY, hiltWorldX, hiltWorldY);
        }

        // Draw weapon sprite
        Color originalColor = batch.getColor();
        if (bloodLevel > 0.05f) {
            // Blood-stained red tinting on blade
            batch.setColor(1.0f, 1.0f - (bloodLevel * 0.45f), 1.0f - (bloodLevel * 0.55f), 1.0f);
        } else {
            batch.setColor(Color.WHITE);
        }

        batch.draw(mainHandTexture,
                drawX, drawY,
                originX, originY,
                targetWidth, targetHeight,
                1f, 1f,
                rotation);

        batch.setColor(originalColor);
    }

    private void renderOffHand(SpriteBatch batch, Viewport viewport, float worldW, float worldH, float bobX, float bobY) {
        if (offHandTexture == null) {
            return;
        }

        float drawX, drawY, rotation;

        if (active && currentProfile != null && currentProfile.isShieldBash) {
            float progress = attackTimer / currentProfile.duration;
            CombatMotionProfile.MotionState state = currentProfile.evaluate(progress);
            drawX = worldW * state.xRel;
            drawY = worldH * state.yRel;
            rotation = state.rotation;
        } else if (guardFlinchTimer > 0f) {
            // Defensive flinch
            float flinchT = guardFlinchTimer / GUARD_FLINCH_DURATION;
            drawX = (worldW * 0.18f) - (bobX * 0.5f) + (MathUtils.sin(flinchT * MathUtils.PI) * (worldW * 0.04f));
            drawY = (worldH * 0.02f) + bobY;
            rotation = 28f - (flinchT * 12f);
        } else if (isGuarding) {
            // High Guard stance - raised into view to block
            drawX = (worldW * 0.22f) - (bobX * 0.5f);
            drawY = (worldH * 0.02f) + bobY;
            rotation = 8f;
        } else {
            // Idle offhand: moved down just off-screen
            drawX = (worldW * 0.12f) - (bobX * 0.5f);
            drawY = (worldH * CombatMotionProfile.IDLE_Y_REL) + (bobY * 0.8f);
            rotation = 18f;
        }

        float targetHeight = worldH * 0.36f;
        float ratio = (float) offHandTexture.getRegionWidth() / (float) offHandTexture.getRegionHeight();
        float targetWidth = targetHeight * ratio;

        float originX = targetWidth * 0.5f;
        float originY = targetHeight * 0.12f;

        batch.setColor(Color.WHITE);
        batch.draw(offHandTexture,
                drawX, drawY,
                originX, originY,
                targetWidth, targetHeight,
                1f, 1f,
                rotation);
    }

    public void renderTrails(ShapeRenderer shapeRenderer) {
        trailRenderer.render(shapeRenderer);
    }

    /**
     * RETRO mode render path: renders ASCII sprite pixel data with kinematic parity,
     * stance bobbing, combo arcs, and off-hand shield support.
     */
    public void renderRetro(ShapeRenderer shapeRenderer, Viewport viewport) {
        float worldW = viewport.getWorldWidth();
        float worldH = viewport.getWorldHeight();

        float breathY = MathUtils.sin(idleBobTimer) * (worldH * 0.012f);
        float walkBobY = isWalking ? Math.abs(MathUtils.sin(walkBobTimer)) * (worldH * 0.022f) : 0f;
        float totalBobY = breathY - walkBobY;

        float drawX, drawY;

        if (active && currentProfile != null) {
            float progress = attackTimer / currentProfile.duration;
            CombatMotionProfile.MotionState state = currentProfile.evaluate(progress);
            drawX = worldW * state.xRel;
            drawY = worldH * state.yRel;
        } else {
            drawX = worldW * 0.76f;
            drawY = (worldH * CombatMotionProfile.IDLE_Y_REL) + totalBobY;
        }

        // Render main hand ASCII block
        renderRetroSprite(shapeRenderer, mainHandSpriteData, mainHandSpriteColor, drawX, drawY, worldH * 0.44f);

        // Render off-hand ASCII block if equipped
        if (offHandSpriteData != null) {
            float offX = isGuarding ? (worldW * 0.22f) : (worldW * 0.12f);
            float offY = isGuarding ? (worldH * 0.02f) : (worldH * CombatMotionProfile.IDLE_Y_REL) + totalBobY;
            renderRetroSprite(shapeRenderer, offHandSpriteData, offHandSpriteColor, offX, offY, worldH * 0.36f);
        }
    }

    private void renderRetroSprite(ShapeRenderer shapeRenderer, String[] spriteData, Color color, float x, float y, float targetHeight) {
        if (spriteData != null && spriteData.length > 0) {
            int rows = spriteData.length;
            int cols = spriteData[0].length();
            float pixelW = (targetHeight / 2f) / cols;
            float pixelH = targetHeight / rows;

            shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
            shapeRenderer.setColor(color);
            for (int py = 0; py < rows; py++) {
                String row = spriteData[py];
                for (int px = 0; px < cols && px < row.length(); px++) {
                    if (row.charAt(px) == '#') {
                        float bx = x + px * pixelW;
                        float by = y + (rows - 1 - py) * pixelH;
                        shapeRenderer.rect(bx, by, pixelW, pixelH);
                    }
                }
            }
            shapeRenderer.end();
        } else {
            shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
            shapeRenderer.setColor(color);
            shapeRenderer.rect(x, y, 60f, 160f);
            shapeRenderer.end();
        }
    }

    private void configureTrailColor(Item weapon) {
        if (weapon == null) {
            trailRenderer.setTintColor(new Color(1f, 1f, 1f, 0.65f));
            return;
        }
        if (weapon.getCategory() == com.bpm.minotaur.gamedata.item.ItemCategory.SPIRITUAL_WEAPON) {
            // Holy gold
            trailRenderer.setTintColor(new Color(1.0f, 0.88f, 0.35f, 0.75f));
        } else if (weapon.getTemplate() != null && weapon.getTemplate().fireDamage > 0) {
            // Fiery ember
            trailRenderer.setTintColor(new Color(1.0f, 0.45f, 0.15f, 0.80f));
        } else {
            // Steel silver
            trailRenderer.setTintColor(new Color(0.88f, 0.94f, 1.0f, 0.65f));
        }
    }

    private TextureRegion resolveTexture(Item item) {
        if (item == null || item.getTemplate() == null) {
            return null;
        }

        String texturePath = item.getTemplate().texturePath;

        // PRIORITY 1: Explicit Texture Path
        if (texturePath != null) {
            try {
                if (assetManager.isLoaded(texturePath)) {
                    return new TextureRegion(assetManager.get(texturePath, Texture.class));
                } else {
                    assetManager.load(texturePath, Texture.class);
                    assetManager.finishLoadingAsset(texturePath);
                    return new TextureRegion(assetManager.get(texturePath, Texture.class));
                }
            } catch (Exception ignored) {
            }
        }

        // PRIORITY 2: Atlas Lookup
        String regionName = null;
        if (texturePath != null) {
            int lastSlash = texturePath.lastIndexOf('/');
            int lastDot = texturePath.lastIndexOf('.');
            if (lastDot > lastSlash) {
                regionName = texturePath.substring(lastSlash + 1, lastDot);
            } else {
                regionName = texturePath;
            }
        }

        if (regionName != null) {
            if (assetManager.isLoaded("packed/weapons.atlas")) {
                TextureAtlas atlas = assetManager.get("packed/weapons.atlas", TextureAtlas.class);
                TextureRegion r = atlas.findRegion(regionName);
                if (r != null) return r;
            }
            if (assetManager.isLoaded("packed/items.atlas")) {
                TextureAtlas atlas = assetManager.get("packed/items.atlas", TextureAtlas.class);
                TextureRegion r = atlas.findRegion(regionName);
                if (r != null) return r;
            }
            if (assetManager.isLoaded("packed/armor.atlas")) {
                TextureAtlas atlas = assetManager.get("packed/armor.atlas", TextureAtlas.class);
                TextureRegion r = atlas.findRegion(regionName);
                if (r != null) return r;
            }
        }

        return null;
    }
}
