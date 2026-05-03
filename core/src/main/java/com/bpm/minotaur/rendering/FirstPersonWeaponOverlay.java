package com.bpm.minotaur.rendering;

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

public class FirstPersonWeaponOverlay {

    private TextureRegion weaponTexture;
    // RETRO mode sprite data, populated alongside the texture in triggerAttack
    private String[] weaponSpriteData;
    private Color weaponSpriteColor = Color.WHITE;

    private boolean active = false;
    private float timer = 0f;
    private float duration = 0.6f; // Slower swipe for better visibility

    // Animation parameters
    private float startRotation = -60f;
    private float endRotation = 60f;
    private float scaleX = -1f;
    private float scaleY = -1f;

    // New Position parameters
    private float startXRel = 0.75f;
    private float endXRel = 0.25f;
    private float startYRel = -0.05f;
    private float endYRel = -0.05f;

    private final AssetManager assetManager;

    public FirstPersonWeaponOverlay(ItemDataManager itemDataManager, AssetManager assetManager) {
        this.assetManager = assetManager;
    }

    public void triggerAttack(Item weapon) {
        this.active = true;
        this.timer = 0f;

        // Default or Null check
        if (weapon == null) {
            this.active = false;
            return;
        }

        // Cache RETRO sprite data from the weapon — used by renderRetro()
        this.weaponSpriteData = weapon.getSpriteData();
        this.weaponSpriteColor = (weapon.getColor() != null) ? weapon.getColor().cpy() : Color.WHITE;

        // Resolve texture
        if (weapon.getTemplate() != null) {

            String texturePath = weapon.getTemplate().texturePath;

            // Re-initialize texture to ensure we don't hold stale state
            this.weaponTexture = null;

            // PRIORITY 1: Explicit Texture Path from Data
            if (texturePath != null) {
                // Try to load/get the texture directly
                try {
                    if (assetManager.isLoaded(texturePath)) {
                        Texture t = assetManager.get(texturePath, Texture.class);
                        this.weaponTexture = new TextureRegion(t);
                        com.badlogic.gdx.Gdx.app.log("WeaponOverlay",
                                "Resolved texture from AssetManager (Explicit): " + texturePath);
                    } else {
                        // Force load if missing. This is a blocking call to ensure visual correctness.
                        assetManager.load(texturePath, Texture.class);
                        assetManager.finishLoadingAsset(texturePath);
                        Texture t = assetManager.get(texturePath, Texture.class);
                        this.weaponTexture = new TextureRegion(t);
                        com.badlogic.gdx.Gdx.app.log("WeaponOverlay",
                                "Forced load & Resolved texture (Explicit): " + texturePath);
                    }
                } catch (Exception e) {
                    com.badlogic.gdx.Gdx.app.error("WeaponOverlay", "Failed to load explicit texture: " + texturePath,
                            e);
                }
            }

            // PRIORITY 2: Atlas Lookups (Fallback if explicit load failed)
            if (this.weaponTexture == null) {
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

                // Try weapons.atlas
                if (regionName != null && assetManager.isLoaded("packed/weapons.atlas")) {
                    TextureAtlas weaponAtlas = assetManager.get("packed/weapons.atlas", TextureAtlas.class);
                    TextureRegion region = weaponAtlas.findRegion(regionName);
                    if (region != null) {
                        this.weaponTexture = region;
                        com.badlogic.gdx.Gdx.app.log("WeaponOverlay",
                                "Resolved texture from Weapons Atlas: " + regionName);
                    }
                }

                // Try items.atlas
                if (this.weaponTexture == null && regionName != null && assetManager.isLoaded("packed/items.atlas")) {
                    TextureAtlas itemsAtlas = assetManager.get("packed/items.atlas", TextureAtlas.class);
                    TextureRegion region = itemsAtlas.findRegion(regionName);
                    if (region != null) {
                        this.weaponTexture = region;
                        com.badlogic.gdx.Gdx.app.log("WeaponOverlay",
                                "Resolved texture from Items Atlas: " + regionName);
                    }
                }
            }

            // PRIORITY 3: Strict File Load Fallback
            // If explicit priority 1 failed (e.g. invalid path? or some other issue), try
            // loadTexture helper
            if (this.weaponTexture == null && texturePath != null) {
                this.weaponTexture = loadTexture(texturePath);
                if (this.weaponTexture != null) {
                    com.badlogic.gdx.Gdx.app.log("WeaponOverlay",
                            "Resolved texture via loadTexture fallback: " + texturePath);
                }
            }

            // FINAL FALLBACK
            if (this.weaponTexture == null) {
                com.badlogic.gdx.Gdx.app.log("WeaponOverlay",
                        "Texture NOT found for " + weapon.getType() + ". Using GENERIC fallback.");
                this.weaponTexture = resolveGenericFallback(weapon);
            }

            // Apply Rotation & Scale & Position Settings
            ItemTemplate t = weapon.getTemplate();
            this.startRotation = t.attackStartRotation;
            this.endRotation = t.attackEndRotation;
            this.scaleX = t.attackScaleX;
            this.scaleY = t.attackScaleY;

            this.startXRel = t.attackStartX;
            this.endXRel = t.attackEndX;
            this.startYRel = t.attackStartY;
            this.endYRel = t.attackEndY;

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
        return new TextureRegion(assetManager.get(path, Texture.class));
    }

    // New fallback helper
    private TextureRegion resolveGenericFallback(Item weapon) {
        // Just use DART as ultimate fallback if nothing matches
        // Ideally we have a 'generic_weapon.png'
        // For now, rely on logic checking items.atlas for "dart" if exists
        if (assetManager.isLoaded("packed/items.atlas")) {
            TextureAtlas itemsAtlas = assetManager.get("packed/items.atlas", TextureAtlas.class);
            return itemsAtlas.findRegion("dart");
        }
        return null;
    }

    public void update(float delta) {
        if (active) {
            timer += delta;
            if (timer >= duration) {
                active = false;
                timer = 0f;
            }
        }
    }

    public boolean isActive() {
        return active;
    }

    // Force animation to complete/skip to end
    public void jumpToImpact() {
        if (active) {
            timer = duration;
            // active = false; // Maybe let next update frame handle the disable?
            // Actually, usually jumpToImpact implies skipping the windup.
            // If damage happens at end of animation, this helps sync.
        }
    }

    public void render(SpriteBatch batch, Viewport viewport) {
        if (!active || weaponTexture == null) {
            return;
        }

        float progress = timer / duration;

        // Sine wave for smooth swing
        // 0 -> 1 -> 0 ? No, usually a slash is Start -> End
        // Let's do a simple Linear or SmoothStep interp
        // float t = MathUtils.sin(progress * MathUtils.PI); // Arc motion 0 -> 1 -> 0
        // (if we want back and forth)

        // For a slash: Start -> End
        float t = progress; // Linear
        // t = t * t * (3 - 2 * t); // SmoothStep

        // Interpolate Rotation
        float currentRotation = MathUtils.lerp(startRotation, endRotation, t);

        // Interpolate Position (Screen space)
        // Default: Bottom Right -> Bottom Left
        float startX = viewport.getWorldWidth() * startXRel;
        float endX = viewport.getWorldWidth() * endXRel;
        float startY = viewport.getWorldHeight() * startYRel; // Below screen
        float endY = viewport.getWorldHeight() * endYRel; // Below screen

        // We want the weapon to arc up? Or just slide?
        // Let's add an arc height offset
        float arcHeight = viewport.getWorldHeight() * 0.1f;
        float yOffset = MathUtils.sin(progress * MathUtils.PI) * arcHeight;

        float currentX = MathUtils.lerp(startX, endX, t);
        float currentY = MathUtils.lerp(startY, endY, t) + yOffset + 200f;

        // Draw
        batch.setColor(Color.WHITE);

        // Scale based on texture size vs screen size?
        // Let's just draw it large enough to look like a first-person item.
        // E.g. height = 1/2 screen height
        float targetHeight = viewport.getWorldHeight() * 0.6f;
        float ratio = (float) weaponTexture.getRegionWidth() / (float) weaponTexture.getRegionHeight();
        float targetWidth = targetHeight * ratio;

        // Apply flip if needed
        float finalWidth = targetWidth * (scaleX > 0 ? 1 : -1) * Math.abs(scaleX); // Logic: scaleX sign determines flip
        float finalHeight = targetHeight * scaleY;

        // Origin for rotation should be bottom-right (handle) usually?
        // Or center?
        // Let's try Center-Bottom roughly
        float originX = finalWidth / 2f;
        float originY = 0f; // Handle at bottom

        batch.draw(weaponTexture,
                currentX, currentY,
                originX, originY,
                finalWidth, finalHeight,
                1f, 1f, // Scale is already applied to W/H
                currentRotation);
    }

    /**
     * RETRO render path: draws the weapon's ASCII sprite data using ShapeRenderer
     * instead of a texture. The sweep animation position is identical to the MODERN
     * path but rotation is omitted (pixel blocks don't rotate gracefully).
     */
    public void renderRetro(ShapeRenderer shapeRenderer, Viewport viewport) {
        if (!active) return;

        float progress = timer / duration;
        float t = progress;

        float startX = viewport.getWorldWidth() * startXRel;
        float endX   = viewport.getWorldWidth() * endXRel;
        float startY = viewport.getWorldHeight() * startYRel;
        float endY   = viewport.getWorldHeight() * endYRel;
        float arcHeight = viewport.getWorldHeight() * 0.1f;
        float yOffset   = MathUtils.sin(progress * MathUtils.PI) * arcHeight;

        float currentX = MathUtils.lerp(startX, endX, t);
        float currentY = MathUtils.lerp(startY, endY, t) + yOffset + 200f;

        float targetHeight = viewport.getWorldHeight() * 0.55f;

        if (weaponSpriteData != null && weaponSpriteData.length > 0) {
            int rows = weaponSpriteData.length;
            int cols = weaponSpriteData[0].length();
            float pixelW = (targetHeight / 2f) / cols;
            float pixelH = targetHeight / rows;

            shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
            shapeRenderer.setColor(weaponSpriteColor);
            for (int py = 0; py < rows; py++) {
                String row = weaponSpriteData[py];
                for (int px = 0; px < cols && px < row.length(); px++) {
                    if (row.charAt(px) == '#') {
                        float drawX = currentX + px * pixelW;
                        float drawY = currentY + (rows - 1 - py) * pixelH;
                        shapeRenderer.rect(drawX, drawY, pixelW, pixelH);
                    }
                }
            }
            shapeRenderer.end();
        } else {
            // No sprite data — draw a plain coloured block as fallback
            float blockW = 60f;
            float blockH = 160f;
            shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
            shapeRenderer.setColor(weaponSpriteColor);
            shapeRenderer.rect(currentX, currentY, blockW, blockH);
            shapeRenderer.end();
        }
    }
}
