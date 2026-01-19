package com.bpm.minotaur.rendering;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.gamedata.item.ItemDataManager;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Texture;

public class FirstPersonWeaponOverlay {

    private TextureRegion weaponTexture;
    private boolean active = false;
    private float timer = 0f;
    private float duration = 0.25f; // Short, fast swipe

    // Animation parameters

    private float startRotation = -60f;
    private float endRotation = 60f;

    private final AssetManager assetManager;

    public FirstPersonWeaponOverlay(ItemDataManager itemDataManager, AssetManager assetManager) {
        this.assetManager = assetManager;
    }

    public void triggerAttack(Item weapon) {
        this.active = true;
        this.timer = 0f;

        // Default or Null check
        if (weapon == null) {
            // Fists? Or just don't show overlay?
            // Let's assume no overlay for fists for now, or use a default hand texture if
            // we had one.
            this.active = false;
            return;
        }

        // Resolve texture
        if (weapon.getTemplate() != null) {
            String[] spriteData = weapon.getTemplate().spriteData;

            // Debug Log
            com.badlogic.gdx.Gdx.app.log("WeaponOverlay", "Triggering attack with: " + weapon.getType());
            if (spriteData != null) {
                com.badlogic.gdx.Gdx.app.log("WeaponOverlay", "SpriteData: " + java.util.Arrays.toString(spriteData));
            } else {
                com.badlogic.gdx.Gdx.app.log("WeaponOverlay", "SpriteData is NULL");
            }

            // Fallback to DART if missing (known good)
            if (spriteData == null || spriteData.length < 2) {
                com.badlogic.gdx.Gdx.app.log("WeaponOverlay", "Using Fallback (DART)");
                spriteData = new String[] { "items", "dart" }; // Assuming this is valid based on other code
                // Or better, fetch from ItemDataManager if possible, but hardcoding for debug
                // safety
            }

            if (spriteData != null && spriteData.length >= 2) {
                // Determine Atlas (usually "items" or "weapons" depending on implementation,
                // but spriteData[0] is often the atlas name or category.
                // In Tarmin2, items are usually in "packed/items.atlas".

                String atlasPath = "packed/items.atlas";
                // Note: Some might be in different atlases.
                // Checking EntityRenderer logic (which I can't see right now but I recall it
                // uses main atlases).
                // Let's try loading from items.atlas.

                if (assetManager.isLoaded(atlasPath)) {
                    TextureAtlas atlas = assetManager.get(atlasPath, TextureAtlas.class);
                    // spriteData[1] is the region name
                    TextureRegion region = atlas.findRegion(spriteData[1]);
                    if (region != null) {
                        this.weaponTexture = region;
                        com.badlogic.gdx.Gdx.app.log("WeaponOverlay", "Texture Loaded: " + spriteData[1]);
                    } else {
                        com.badlogic.gdx.Gdx.app.log("WeaponOverlay", "Texture Region NOT FOUND: " + spriteData[1]);

                        // Check for explicit texturePath from template
                        if (weapon.getTemplate().texturePath != null) {
                            this.weaponTexture = loadTexture(weapon.getTemplate().texturePath);
                        }

                        if (this.weaponTexture == null) {
                            this.weaponTexture = resolveGenericFallback(weapon);
                        }
                    }
                } else {
                    com.badlogic.gdx.Gdx.app.log("WeaponOverlay", "Atlas NOT LOADED: " + atlasPath);
                    // Check for explicit texturePath from template
                    if (weapon.getTemplate().texturePath != null) {
                        this.weaponTexture = loadTexture(weapon.getTemplate().texturePath);
                    }

                    if (this.weaponTexture == null) {
                        this.weaponTexture = resolveGenericFallback(weapon);
                    }
                }
            } else {
                if (weapon.getTemplate().texturePath != null) {
                    this.weaponTexture = loadTexture(weapon.getTemplate().texturePath);
                }
                if (this.weaponTexture == null) {
                    this.weaponTexture = resolveGenericFallback(weapon);
                }
            }
        } else {
            com.badlogic.gdx.Gdx.app.log("WeaponOverlay", "Weapon Template is NULL");
        }
    }

    private TextureRegion loadTexture(String path) {
        if (path == null)
            return null;
        if (!assetManager.isLoaded(path)) {
            try {
                assetManager.load(path, Texture.class);
                assetManager.finishLoadingAsset(path);
                com.badlogic.gdx.Gdx.app.log("WeaponOverlay", "Loaded Explicit Texture: " + path);
            } catch (Exception e) {
                com.badlogic.gdx.Gdx.app.log("WeaponOverlay", "Failed to load explicit texture: " + path);
                return null;
            }
        }
        if (assetManager.isLoaded(path)) {
            return new TextureRegion(assetManager.get(path, Texture.class));
        }
        return null;
    }

    private TextureRegion resolveGenericFallback(Item weapon) {
        if (weapon == null)
            return null;
        String name = weapon.getType().toString().toUpperCase();
        String fallbackPath = null;

        if (name.contains("SWORD") || name.contains("BLADE"))
            fallbackPath = "images/weapons/sword.png";
        else if (name.contains("AXE"))
            fallbackPath = "images/weapons/axe.png";
        else if (name.contains("SPEAR") || name.contains("PIKE"))
            fallbackPath = "images/weapons/spear.png";
        else if (name.contains("DANCE") || name.contains("DAGGER") || name.contains("KNIFE"))
            fallbackPath = "images/weapons/knife.png";
        else if (name.contains("MACE") || name.contains("CLUB") || name.contains("HAMMER"))
            fallbackPath = "images/weapons/axe.png";
        else if (name.contains("BOW"))
            fallbackPath = "images/weapons/bow.png";
        else if (name.contains("CROSSBOW"))
            fallbackPath = "images/weapons/crossbow.png";

        // Final fallback
        if (fallbackPath == null) {
            if (assetManager.isLoaded("packed/items.atlas")) {
                TextureAtlas atlas = assetManager.get("packed/items.atlas", TextureAtlas.class);
                return atlas.findRegion("stick");
            }
            return null;
        }

        // Load specific texture
        if (!assetManager.isLoaded(fallbackPath)) {
            try {
                assetManager.load(fallbackPath, Texture.class);
                assetManager.finishLoadingAsset(fallbackPath);
                com.badlogic.gdx.Gdx.app.log("WeaponOverlay", "Loaded Generic Fallback: " + fallbackPath);
            } catch (Exception e) {
                com.badlogic.gdx.Gdx.app.log("WeaponOverlay", "Failed to load fallback: " + fallbackPath);
                return null;
            }
        }

        if (assetManager.isLoaded(fallbackPath)) {
            Texture tex = assetManager.get(fallbackPath, Texture.class);
            return new TextureRegion(tex);
        }

        return null;
    }

    // For direct injection of the region (e.g. from GameScreen which has access to
    // Atlases)
    public void triggerAttack(TextureRegion region) {
        this.weaponTexture = region;
        this.active = true;
        this.timer = 0f;
    }

    public void update(float delta) {
        if (!active)
            return;

        timer += delta;
        if (timer >= duration) {
            active = false;
        }
    }

    // Manually advance animation to the impact point (approx 40% through)
    // Used for instant attacks so the weapon is visible during the hit pause.
    public void jumpToImpact() {
        if (!active)
            return;
        this.timer = duration * 0.4f;
    }

    public void render(SpriteBatch batch, Viewport viewport) {
        if (!active || weaponTexture == null)
            return;

        float progress = timer / duration;
        // Ease out cubic
        progress = 1f - (float) Math.pow(1f - progress, 3);

        float sw = viewport.getWorldWidth();
        float sh = viewport.getWorldHeight();

        // Start: Hand at Bottom-Right
        float startX = sw * 0.85f;
        float startY = -sh * 0.1f; // Hand slightly below screen

        // End: Hand at Bottom-Left
        float endX = sw * 0.35f;
        float endY = -sh * 0.1f;

        float x = MathUtils.lerp(startX, endX, progress);
        float y = MathUtils.lerp(startY, endY, progress);

        float rotation = MathUtils.lerp(startRotation, endRotation, progress);

        // Scale to occupy approx 60% of screen height
        float targetHeight = sh * 0.6f;
        float scale = targetHeight / weaponTexture.getRegionHeight();

        batch.setColor(Color.WHITE);
        // Draw centered on position
        float width = weaponTexture.getRegionWidth() * scale;
        float height = weaponTexture.getRegionHeight() * scale;

        // Origin at Top Center (Handle of upside-down sprite)
        float originX = width / 2;
        float originY = height;

        batch.draw(weaponTexture,
                x - originX, y - originY, // Align origin to (x,y)
                originX, originY,
                width, height,
                -1f, -1f, // Flip 180 degrees around the handle
                rotation);

        // Trail?
        if (progress < 0.8f) {
            batch.setColor(1f, 1f, 1f, 0.3f);
            // Draw a 'ghost' slightly behind
            float lag = 0.05f;
            float pLag = Math.max(0, progress - lag);
            float xL = MathUtils.lerp(startX, endX, pLag);
            float yL = MathUtils.lerp(startY, endY, pLag);

            batch.draw(weaponTexture, xL - originX, yL - originY, originX, originY, width, height, -1f, -1f,
                    MathUtils.lerp(startRotation, endRotation, pLag));
            batch.setColor(Color.WHITE);
        }
    }

    public boolean isActive() {
        return active;
    }
}
