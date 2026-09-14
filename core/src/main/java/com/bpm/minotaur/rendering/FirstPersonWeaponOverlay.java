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
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.TextureData;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.math.Vector2;
import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.gamedata.item.ItemDataManager;
import com.bpm.minotaur.gamedata.item.ItemTemplate;
import com.bpm.minotaur.rendering.animation.AnimationArchetype;
import com.bpm.minotaur.rendering.animation.CombatMotionProfile;
import com.bpm.minotaur.rendering.animation.WeaponTrailRenderer;

import java.io.File;
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

    // Discrete blood decals on the weapon, reusing the exact same SurfaceDecal
    // class (expand/oxidize/fade) as the world gore system, so the blade
    // accumulates visible splats in step with how much blood a hit dispersed.
    public static final int MAX_WEAPON_BLOOD_DECALS = 24;
    private final List<com.bpm.minotaur.gamedata.gore.SurfaceDecal> weaponBloodDecals = new ArrayList<>();
    private TextureRegion blankDecalTexture;

    // Solid non-alpha pixel coordinates precomputed from the equipped weapon sprite.
    // Each Vector2 is a normalized offset (x, y) relative to the sprite center, in [-0.5, 0.5].
    private final List<Vector2> solidPixelPoints = new ArrayList<>();

    // Offscreen FrameBuffer for strict destination-alpha silhouette masking
    private static final int FBO_SIZE = 512;
    private FrameBuffer weaponFbo;
    private TextureRegion weaponFboRegion;
    private SpriteBatch fboBatch;

    // Buff Aura Glow: pulsating edge tint matching the active buff's element
    // (strength/speed/holy), set externally each frame from player status effects.
    private Color buffGlowColor = null;
    private float buffGlowPulseTimer = 0f;
    private static final float BUFF_GLOW_PULSE_SPEED = 4f;
    private static final float BUFF_GLOW_MAX_STRENGTH = 0.4f;

    // Tome Weapon Attack: page-flutter particles spawned when swinging a book.
    private static class PageParticle {
        float offsetX, offsetY;
        float vx, vy;
        float rotation, rotSpeed;
        float life, maxLife;
    }

    private final List<PageParticle> pageParticles = new ArrayList<>();
    private Texture pageParticleTexture;

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
            this.weaponBloodDecals.clear();
            this.bloodLevel = 0f;
            extractSolidWeaponPixels();
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

        // Tome Weapon Attack: fluttering pages instead of a normal weapon swing feel
        if (com.bpm.minotaur.managers.CombatManager.isBookWeapon(weapon)) {
            spawnPageFlutter();
        }
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

    /**
     * Resets any active attack animations, walking states, and trails (e.g. when opening modal dialogs).
     */
    public void reset() {
        active = false;
        attackTimer = 0f;
        comboIndex = 0;
        comboWindowTimer = 0f;
        isWalking = false;
        isGuarding = false;
        guardFlinchTimer = 0f;
        turnSway = 0f;
        if (trailRenderer != null) {
            trailRenderer.clear();
        }
    }

    /**
     * Clears all accumulated blood splatters and resets blood coating level.
     */
    public void clearBloodDecals() {
        this.weaponBloodDecals.clear();
        this.bloodLevel = 0f;
    }

    /**
     * Forces re-initialization of weapon textures, archetypes, combo chains,
     * and solid pixels even if the equipped item reference is unchanged (e.g. after shelter respawn).
     */
    public void forceRefreshEquipment(Item rightHand, Item leftHand) {
        this.mainHandItem = null;
        this.offHandItem = null;
        setEquipment(rightHand, leftHand);
    }

    public AnimationArchetype getMainHandArchetype() {
        return mainHandArchetype;
    }

    public void addBloodToWeapon() {
        this.bloodLevel = Math.min(1.0f, this.bloodLevel + 0.35f);
    }

    /**
     * Inspects the equipped weapon's texture or sprite data to find all non-alpha pixels
     * (alpha > 32). Decals will be sampled exclusively from these coordinates so blood
     * droplets land directly on the blade, guard, or handle rather than floating in empty air.
     */
    private void extractSolidWeaponPixels() {
        solidPixelPoints.clear();

        Pixmap pixmap = null;
        boolean needsDispose = false;

        // 1. Try loading Pixmap from item template texturePath via Gdx.files or direct assets file
        // 1. Try reading weapon PNG file via ImageIO or FileHandle
        if (mainHandItem != null && mainHandItem.getTemplate() != null
                && mainHandItem.getTemplate().texturePath != null) {
            String path = mainHandItem.getTemplate().texturePath;
            File file = null;
            if (Gdx.files != null) {
                try {
                    FileHandle h = Gdx.files.internal(path);
                    if (h != null && h.exists()) {
                        file = h.file();
                    }
                } catch (Throwable ignored) {
                }
            }
            if (file == null || !file.exists()) {
                File f = new File("assets/" + path);
                if (!f.exists()) {
                    f = new File("../assets/" + path);
                }
                if (f.exists()) {
                    file = f;
                }
            }
            if (file != null && file.exists()) {
                try {
                    java.awt.image.BufferedImage img = javax.imageio.ImageIO.read(file);
                    if (img != null) {
                        int w = img.getWidth();
                        int h = img.getHeight();
                        int step = Math.max(2, Math.min(w, h) / 128);
                        for (int py = 0; py < h; py += step) {
                            for (int px = 0; px < w; px += step) {
                                int argb = img.getRGB(px, py);
                                int alpha = (argb >>> 24) & 0xFF;
                                if (alpha > 32) {
                                    float normX = ((float) px + 0.5f) / (float) w - 0.5f;
                                    float normY = ((float) (h - 1 - py) + 0.5f) / (float) h - 0.5f;
                                    solidPixelPoints.add(new Vector2(normX, normY));
                                }
                            }
                        }
                    }
                } catch (Throwable ignored) {
                }
            }
        }

        // 2. Try inspecting TextureData if Pixmap was not loaded from file
        if (solidPixelPoints.isEmpty() && mainHandTexture != null && mainHandTexture.getTexture() != null) {
            try {
                TextureData data = mainHandTexture.getTexture().getTextureData();
                if (!data.isPrepared()) {
                    data.prepare();
                }
                pixmap = data.consumePixmap();
                needsDispose = data.disposePixmap();
            } catch (Throwable ignored) {
            }
        }

        // If a Pixmap is available, scan for solid non-alpha pixels (alpha > 32)
        if (pixmap != null) {
            try {
                int regX = (mainHandTexture != null) ? mainHandTexture.getRegionX() : 0;
                int regY = (mainHandTexture != null) ? mainHandTexture.getRegionY() : 0;
                int regW = (mainHandTexture != null) ? mainHandTexture.getRegionWidth() : pixmap.getWidth();
                int regH = (mainHandTexture != null) ? mainHandTexture.getRegionHeight() : pixmap.getHeight();

                // Adaptive step size for smooth distribution across any image resolution
                int step = Math.max(2, Math.min(regW, regH) / 128);
                for (int py = 0; py < regH; py += step) {
                    for (int px = 0; px < regW; px += step) {
                        int pixel = pixmap.getPixel(regX + px, regY + py);
                        int alpha = pixel & 0xFF; // RGBA8888 alpha
                        if (alpha > 32) {
                            // Normalized coordinates centered at (0, 0) in [-0.5, 0.5].
                            // In Pixmap, py=0 is top; in SpriteBatch/GL, y=0 is bottom.
                            float normX = ((float) px + 0.5f) / (float) regW - 0.5f;
                            float normY = ((float) (regH - 1 - py) + 0.5f) / (float) regH - 0.5f;
                            solidPixelPoints.add(new Vector2(normX, normY));
                        }
                    }
                }
            } catch (Exception ignored) {
            } finally {
                if (needsDispose) {
                    try {
                        pixmap.dispose();
                    } catch (Exception ignored) {
                    }
                }
            }
        }

        // 3. Fallback: RETRO ASCII sprite data (# characters)
        String[] spriteData = (mainHandSpriteData != null) ? mainHandSpriteData
                : (mainHandItem != null && mainHandItem.getTemplate() != null ? mainHandItem.getTemplate().spriteData : null);
        if (solidPixelPoints.isEmpty() && spriteData != null && spriteData.length > 0) {
            int rows = spriteData.length;
            int cols = spriteData[0].length();
            for (int r = 0; r < rows; r++) {
                String row = spriteData[r];
                for (int c = 0; c < cols && c < row.length(); c++) {
                    if (row.charAt(c) == '#') {
                        float normX = ((float) c + 0.5f) / (float) cols - 0.5f;
                        float normY = ((float) (rows - 1 - r) + 0.5f) / (float) rows - 0.5f;
                        solidPixelPoints.add(new Vector2(normX, normY));
                    }
                }
            }
        }

        // 4. Fallback for testing environments without graphics assets
        if (solidPixelPoints.isEmpty()) {
            for (int i = 0; i < 30; i++) {
                float t = i / 29.0f;
                float normX = MathUtils.random(-0.04f, 0.04f);
                float normY = MathUtils.lerp(-0.25f, 0.40f, t);
                solidPixelPoints.add(new Vector2(normX, normY));
            }
        }
    }

    public int getSolidPixelCount() {
        return solidPixelPoints.size();
    }

    /**
     * Spawns {@code count} blood decals onto the equipped weapon -- one real
     * {@link com.bpm.minotaur.gamedata.gore.SurfaceDecal} per decal, so each
     * splat expands, oxidizes, and fades exactly like a world floor decal.
     * Decals are sampled strictly from detected non-alpha weapon pixels,
     * scaled down by 80% (20% of previous size) to form fine blood droplets,
     * and blended with realistic translucent opacity.
     */
    public void addBloodDecals(int count, Color color, TextureRegion texture) {
        Color baseColor = (color != null) ? color : com.bpm.minotaur.gamedata.gore.GoreManager.UNIFIED_BLOOD_COLOR;
        for (int i = 0; i < count; i++) {
            if (weaponBloodDecals.size() >= MAX_WEAPON_BLOOD_DECALS) {
                weaponBloodDecals.remove(0);
            }
            com.bpm.minotaur.gamedata.gore.SurfaceDecal decal = new com.bpm.minotaur.gamedata.gore.SurfaceDecal();

            // 1. Decal position: sample strictly from detected non-alpha weapon pixels
            float localX, localY;
            if (!solidPixelPoints.isEmpty()) {
                Vector2 pt = solidPixelPoints.get(MathUtils.random(0, solidPixelPoints.size() - 1));
                localX = pt.x;
                localY = pt.y;
            } else {
                localX = MathUtils.random(-0.04f, 0.04f);
                localY = MathUtils.random(-0.25f, 0.40f);
            }

            // 2. Opacity: translucent realistic blood (0.70f to 0.85f alpha)
            float alpha = (baseColor.a > 0.01f ? baseColor.a : 1.0f) * MathUtils.random(0.70f, 0.85f);
            Color splatColor = new Color(baseColor.r, baseColor.g, baseColor.b, alpha);

            // 3. Size: reduced by 80% (20% of previous 0.08f-0.16f -> 0.016f-0.032f)
            float decalRadius = MathUtils.random(0.016f, 0.032f);

            decal.init(new com.badlogic.gdx.math.Vector3(localX, 0f, localY), splatColor, decalRadius, texture);
            weaponBloodDecals.add(decal);
        }
    }

    private TextureRegion getBlankDecalTexture() {
        if (blankDecalTexture == null && Gdx.gl != null) {
            int sz = 16;
            Pixmap pixmap = new Pixmap(sz, sz, Pixmap.Format.RGBA8888);
            pixmap.setColor(0f, 0f, 0f, 0f);
            pixmap.fill();
            float center = (sz - 1) / 2.0f;
            float radius = sz / 2.0f;
            for (int y = 0; y < sz; y++) {
                for (int x = 0; x < sz; x++) {
                    float dist = (float) Math.hypot(x - center, y - center);
                    if (dist <= radius) {
                        float a = MathUtils.clamp(1.0f - (dist / radius) * 0.4f, 0f, 1f);
                        pixmap.setColor(1f, 1f, 1f, a);
                        pixmap.drawPixel(x, y);
                    }
                }
            }
            blankDecalTexture = new TextureRegion(new Texture(pixmap));
            pixmap.dispose();
        }
        return blankDecalTexture;
    }

    public int getBloodDecalCount() {
        return weaponBloodDecals.size();
    }

    public List<com.bpm.minotaur.gamedata.gore.SurfaceDecal> getWeaponBloodDecals() {
        return java.util.Collections.unmodifiableList(weaponBloodDecals);
    }

    /** Sets the active Buff Aura Glow tint, or null to clear it (no active buff). */
    public void setBuffGlow(Color color) {
        this.buffGlowColor = color;
    }

    /** Spawns a small burst of fluttering page particles near the weapon hand. */
    private void spawnPageFlutter() {
        for (int i = 0; i < 6; i++) {
            PageParticle p = new PageParticle();
            p.offsetX = MathUtils.random(-10f, 10f);
            p.offsetY = MathUtils.random(-6f, 6f);
            p.vx = MathUtils.random(-45f, 45f);
            p.vy = MathUtils.random(35f, 95f);
            p.rotation = MathUtils.random(360f);
            p.rotSpeed = MathUtils.random(-220f, 220f);
            p.maxLife = MathUtils.random(0.45f, 0.85f);
            p.life = p.maxLife;
            pageParticles.add(p);
        }
    }

    private void updatePageFlutter(float delta) {
        for (int i = pageParticles.size() - 1; i >= 0; i--) {
            PageParticle p = pageParticles.get(i);
            p.offsetX += p.vx * delta;
            p.offsetY += p.vy * delta;
            p.vy -= 30f * delta; // gentle arc back down as the flutter settles
            p.rotation += p.rotSpeed * delta;
            p.life -= delta;
            if (p.life <= 0f) {
                pageParticles.remove(i);
            }
        }
    }

    private Texture getPageParticleTexture() {
        if (pageParticleTexture == null) {
            com.badlogic.gdx.graphics.Pixmap pixmap = new com.badlogic.gdx.graphics.Pixmap(1, 1,
                    com.badlogic.gdx.graphics.Pixmap.Format.RGBA8888);
            pixmap.setColor(Color.WHITE);
            pixmap.fill();
            pageParticleTexture = new Texture(pixmap);
            pixmap.dispose();
        }
        return pageParticleTexture;
    }

    private void renderPageFlutter(SpriteBatch batch, float worldW, float worldH) {
        if (pageParticles.isEmpty()) {
            return;
        }
        Texture tex = getPageParticleTexture();
        Color originalColor = batch.getColor();
        float anchorX = worldW * 0.72f;
        float anchorY = worldH * (CombatMotionProfile.IDLE_Y_REL + 0.12f);
        float size = worldH * 0.02f;
        for (PageParticle p : pageParticles) {
            float alpha = Math.max(0f, p.life / p.maxLife);
            batch.setColor(0.96f, 0.93f, 0.78f, alpha); // parchment tint
            batch.draw(tex,
                    anchorX + p.offsetX - size * 0.5f, anchorY + p.offsetY - size * 0.5f,
                    size * 0.5f, size * 0.5f,
                    size, size,
                    1f, 1f,
                    p.rotation,
                    0, 0, tex.getWidth(), tex.getHeight(),
                    false, false);
        }
        batch.setColor(originalColor);
    }

    /** Disposes textures and FrameBuffer owned by this overlay. */
    public void dispose() {
        if (weaponFbo != null) {
            weaponFbo.dispose();
            weaponFbo = null;
        }
        if (fboBatch != null) {
            fboBatch.dispose();
            fboBatch = null;
        }
        if (pageParticleTexture != null) {
            pageParticleTexture.dispose();
            pageParticleTexture = null;
        }
        if (blankDecalTexture != null && blankDecalTexture.getTexture() != null) {
            blankDecalTexture.getTexture().dispose();
            blankDecalTexture = null;
        }
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
        if (buffGlowColor != null) {
            buffGlowPulseTimer += delta * BUFF_GLOW_PULSE_SPEED;
        }

        if (!pageParticles.isEmpty()) {
            updatePageFlutter(delta);
        }

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

        // Age blood decals using SurfaceDecal's own update() -- identical
        // expand/oxidize/fade timeline as the world gore system.
        for (int i = weaponBloodDecals.size() - 1; i >= 0; i--) {
            com.bpm.minotaur.gamedata.gore.SurfaceDecal decal = weaponBloodDecals.get(i);
            decal.update(delta);
            if (decal.lifeTimer <= 0) {
                weaponBloodDecals.remove(i);
            }
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

        // 3. Render Tome Weapon Attack page-flutter particles, if any are active
        renderPageFlutter(batch, worldW, worldH);
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
            // Idle ready posture: comfortably anchored in lower corners
            drawX = (worldW * 0.72f) + bobX;
            drawY = (worldH * CombatMotionProfile.IDLE_Y_REL) + bobY;
            rotation = -5f; // Vertically aligned starting posture per user test notes

            if (mainHandArchetype == AnimationArchetype.SLASHING_2H) {
                // Two-handed grip stance (slightly centered, lower-right)
                drawX = (worldW * 0.65f) + (bobX * 0.6f);
                rotation = -4f;
            } else if (mainHandArchetype == AnimationArchetype.POLEARM_SWEEP) {
                // Long shaft stance: wider, angled forward slightly
                drawX = (worldW * 0.67f) + (bobX * 0.7f);
                rotation = -10f;
            } else if (mainHandArchetype == AnimationArchetype.AXE_CHOPPING) {
                // Heavy haft stance: slightly higher ready grip
                drawX = (worldW * 0.70f) + bobX;
                rotation = -2f;
            } else if (mainHandArchetype == AnimationArchetype.FLAIL_WHIP) {
                // Flexible hanging stance: tilted outwards ready to swing
                drawX = (worldW * 0.72f) + bobX;
                rotation = -12f;
            } else if (mainHandArchetype == AnimationArchetype.THRUSTING_PIERCE) {
                drawX = (worldW * 0.71f) + bobX;
                rotation = -8f;
            } else if (mainHandArchetype == AnimationArchetype.RANGED_BOW || mainHandArchetype == AnimationArchetype.RANGED_FIREARM) {
                drawX = (worldW * 0.70f) + bobX;
                rotation = -6f;
            }
        }

        float targetHeight = worldH * 0.44f;
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
        Color weaponColor = Color.WHITE;
        if (bloodLevel > 0.05f) {
            // Blood-stained red tinting on blade
            weaponColor = new Color(1.0f, 1.0f - (bloodLevel * 0.45f), 1.0f - (bloodLevel * 0.55f), 1.0f);
        } else if (buffGlowColor != null) {
            // Buff Aura Glow: pulsating edge tint matching the active buff's element
            float pulse = (0.5f + 0.5f * MathUtils.sin(buffGlowPulseTimer)) * BUFF_GLOW_MAX_STRENGTH;
            weaponColor = new Color(
                    1f + (buffGlowColor.r - 1f) * pulse,
                    1f + (buffGlowColor.g - 1f) * pulse,
                    1f + (buffGlowColor.b - 1f) * pulse,
                    1.0f);
        }

        if (!weaponBloodDecals.isEmpty() && Gdx.gl != null) {
            renderMaskedWeaponWithDecals(batch, viewport, drawX, drawY, originX, originY,
                    targetWidth, targetHeight, rotation, weaponColor);
        } else {
            batch.setColor(weaponColor);
            batch.draw(mainHandTexture,
                    drawX, drawY,
                    originX, originY,
                    targetWidth, targetHeight,
                    1f, 1f,
                    rotation);
            if (!weaponBloodDecals.isEmpty()) {
                renderWeaponBloodDecals(batch, drawX, drawY, originX, originY, targetWidth, targetHeight, rotation);
            }
        }

        batch.setColor(originalColor);
    }

    /**
     * Dual-pass off-screen composite rendering: draws the weapon sprite into an off-screen FBO
     * to establish the exact alpha silhouette mask, then stamps the blood decals using
     * GL_DST_ALPHA destination alpha blending. Decals outside the non-alpha pixels of the weapon
     * are multiplied by 0, ensuring zero blood ever displays in empty air outside the blade.
     */
    private void renderMaskedWeaponWithDecals(SpriteBatch batch, Viewport viewport,
            float drawX, float drawY, float originX, float originY,
            float targetWidth, float targetHeight, float rotation, Color weaponColor) {
        if (weaponFbo == null) {
            weaponFbo = new FrameBuffer(Pixmap.Format.RGBA8888, FBO_SIZE, FBO_SIZE, false);
            weaponFbo.getColorBufferTexture().setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
            weaponFboRegion = new TextureRegion(weaponFbo.getColorBufferTexture());
            weaponFboRegion.flip(false, true); // Flip Y because OpenGL FBOs have inverted Y
            fboBatch = new SpriteBatch();
        }

        // 1. Temporarily flush and pause the main screen batch
        boolean wasDrawing = batch.isDrawing();
        if (wasDrawing) {
            batch.end();
        }

        // Query active OpenGL framebuffer binding and viewport before switching to weaponFbo
        int previousFbo = 0;
        int prevVpX = 0, prevVpY = 0, prevVpW = 0, prevVpH = 0;
        if (Gdx.gl != null) {
            java.nio.IntBuffer intBuf = com.badlogic.gdx.utils.BufferUtils.newIntBuffer(16);
            Gdx.gl.glGetIntegerv(GL20.GL_FRAMEBUFFER_BINDING, intBuf);
            previousFbo = intBuf.get(0);

            intBuf.clear();
            Gdx.gl.glGetIntegerv(GL20.GL_VIEWPORT, intBuf);
            prevVpX = intBuf.get(0);
            prevVpY = intBuf.get(1);
            prevVpW = intBuf.get(2);
            prevVpH = intBuf.get(3);
        }

        // 2. Render weapon sprite into FBO to establish alpha mask
        weaponFbo.begin();
        Gdx.gl.glClearColor(0f, 0f, 0f, 0f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        fboBatch.getProjectionMatrix().setToOrtho2D(0, 0, FBO_SIZE, FBO_SIZE);
        fboBatch.begin();

        fboBatch.setColor(weaponColor);
        fboBatch.draw(mainHandTexture, 0, 0, FBO_SIZE, FBO_SIZE);
        fboBatch.flush();

        // 3. Mask blood decals to the weapon's non-alpha pixels!
        // GL_DST_ALPHA multiplies incoming decal color by destination (weapon) alpha.
        // Where weapon alpha is 0 (outside blade), decal is multiplied by 0 (zero blood drawn outside blade).
        // Where weapon alpha is 1 (on blade), decal is drawn with its opacity.
        // GL_ZERO, GL_ONE preserves the weapon's alpha silhouette.
        fboBatch.setBlendFunctionSeparate(
                GL20.GL_DST_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA,
                GL20.GL_ZERO, GL20.GL_ONE
        );

        float aspectFactor = (targetHeight > 0f) ? (targetWidth / targetHeight) : 1f;

        for (com.bpm.minotaur.gamedata.gore.SurfaceDecal decal : weaponBloodDecals) {
            TextureRegion tex = (decal.textureRegion != null) ? decal.textureRegion : getBlankDecalTexture();
            if (tex == null) continue;

            float fboX = (0.5f + decal.position.x) * FBO_SIZE;
            float fboY = (0.5f + decal.position.z) * FBO_SIZE;
            float decalW = decal.size * FBO_SIZE;
            float decalH = decalW * aspectFactor;

            fboBatch.setColor(decal.color);
            fboBatch.draw(tex, fboX - decalW * 0.5f, fboY - decalH * 0.5f, decalW, decalH);
        }

        fboBatch.flush();
        fboBatch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        fboBatch.end();
        weaponFbo.end();

        // If an outer FBO was active (e.g. GameScreen CRT / post-processing FBO),
        // weaponFbo.end() reverted to the default backbuffer (0). Restore the outer FBO!
        if (previousFbo != 0 && Gdx.gl != null) {
            Gdx.gl.glBindFramebuffer(GL20.GL_FRAMEBUFFER, previousFbo);
        }

        // 4. Restore viewport and resume main batch
        if (Gdx.gl != null && prevVpW > 0 && prevVpH > 0) {
            Gdx.gl.glViewport(prevVpX, prevVpY, prevVpW, prevVpH);
        } else if (viewport != null) {
            viewport.apply();
        }
        if (wasDrawing) {
            batch.begin();
        }

        // 5. Draw composite weapon with its world position, scale, origin, and rotation!
        batch.setColor(Color.WHITE);
        batch.draw(weaponFboRegion,
                drawX, drawY,
                originX, originY,
                targetWidth, targetHeight,
                1f, 1f,
                rotation);
    }

    /**
     * Stamps each accumulated blood decal on top of the weapon sprite, rotated
     * with it exactly like the trail sampling above. Each decal draws with its
     * own SurfaceDecal.color, which already carries the expand/oxidize/fade
     * state computed in update() -- no separate weapon-specific fade logic.
     */
    private void renderWeaponBloodDecals(SpriteBatch batch, float drawX, float drawY,
            float originX, float originY, float targetWidth, float targetHeight, float rotation) {
        if (weaponBloodDecals.isEmpty()) return;

        float rad = rotation * MathUtils.degreesToRadians;
        float cos = MathUtils.cos(rad);
        float sin = MathUtils.sin(rad);

        for (com.bpm.minotaur.gamedata.gore.SurfaceDecal decal : weaponBloodDecals) {
            // World decals fall back to a plain colored quad when no atlas
            // texture was available (see World3DRenderer's blankTexture)
            // rather than disappearing; match that instead of skipping.
            TextureRegion tex = (decal.textureRegion != null) ? decal.textureRegion : getBlankDecalTexture();

            float localX = targetWidth * (0.5f + decal.position.x) - originX;
            float localY = targetHeight * (0.5f + decal.position.z) - originY;
            float dx = drawX + originX + (localX * cos - localY * sin);
            float dy = drawY + originY + (localX * sin + localY * cos);

            float decalSize = decal.size * targetWidth;
            batch.setColor(decal.color);
            batch.draw(tex, dx - decalSize * 0.5f, dy - decalSize * 0.5f, decalSize, decalSize);
        }
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
            // Idle offhand: comfortably anchored in lower-left corner
            drawX = (worldW * 0.12f) - (bobX * 0.5f);
            drawY = (worldH * CombatMotionProfile.IDLE_Y_REL) + (bobY * 0.8f);
            rotation = 16f;
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
        if (item == null || item.getTemplate() == null || assetManager == null) {
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
