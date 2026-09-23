package com.bpm.minotaur.rendering;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.GridPoint2;
import com.badlogic.gdx.math.Vector2;

public class Animation {

    public enum AnimationType {
        PROJECTILE_MONSTER,
        PROJECTILE_PLAYER,
        PROJECTILE_SPELL,
        DAMAGE_TEXT,
        DRAIN_SPELL,
        SPRITE_EXPLOSION_3D
    }

    private float elapsedTime; // Changed from final so it can be updated

    private final AnimationType type;
    private final Vector2 startPosition;
    private final Vector2 endPosition;
    private final Color color;
    private float progress;
    private final float duration;
    private final String[] spriteData;

    private String damageText;
    private GridPoint2 textPosition;

    /**
     * Which projectile sprite the 3D renderer should draw for this shot.
     *
     * <p>Separate from {@link #spriteData}, which is the raycaster's ASCII form and must keep
     * working. Null means the renderer picks a default from the animation type.
     */
    private String projectileSprite;

    public String getProjectileSprite() {
        return projectileSprite;
    }

    public Animation withProjectileSprite(String sprite) {
        this.projectileSprite = sprite;
        return this;
    }

    // 3D Sprite Explosion fields
    private com.badlogic.gdx.math.Vector3 position3D;
    private com.bpm.minotaur.rendering.vfx.SpellExplosionRegistry.ExplosionType explosionType;
    private float scale3D = 1.6f;
    private boolean additiveBlend = true;

    // Constructor for 3D In-World Sprite Explosions (BearFX)
    public Animation(com.bpm.minotaur.rendering.vfx.SpellExplosionRegistry.ExplosionType explosionType,
                     com.badlogic.gdx.math.Vector3 position3D, float scale, float duration) {
        this.type = AnimationType.SPRITE_EXPLOSION_3D;
        this.explosionType = explosionType;
        this.position3D = (position3D != null) ? position3D.cpy() : new com.badlogic.gdx.math.Vector3();
        this.scale3D = (scale > 0) ? scale : 1.6f;
        this.duration = (duration > 0) ? duration : (explosionType != null ? explosionType.getDefaultDuration() : 0.6f);
        this.color = Color.WHITE;
        this.startPosition = new Vector2(this.position3D.x, this.position3D.z);
        this.endPosition = new Vector2(this.position3D.x, this.position3D.z);
        this.progress = 0f;
        this.elapsedTime = 0f;
        this.spriteData = null;
        this.additiveBlend = true;
    }

    public Animation(com.bpm.minotaur.rendering.vfx.SpellExplosionRegistry.ExplosionType explosionType,
                     com.badlogic.gdx.math.Vector3 position3D) {
        this(explosionType, position3D, 1.6f, -1f);
    }

    // Existing constructor for projectiles
    public Animation(AnimationType type, Vector2 startPosition, Vector2 endPosition, Color color, float duration,
            String[] spriteData) {
        this.type = type;
        this.startPosition = startPosition.cpy();
        this.endPosition = endPosition.cpy();
        this.color = color;
        this.duration = duration;
        this.progress = 0f;
        this.elapsedTime = 0f;
        this.spriteData = spriteData;
    }

    private boolean isCritical;
    private boolean isPlayerDamage;
    private float driftOffset;
    private com.bpm.minotaur.gamedata.DamageType damageType;

    // Full constructor for damage text with custom styling, positioning, and type
    public Animation(AnimationType type, GridPoint2 position, String text, Color color, float duration,
                     boolean isCritical, boolean isPlayerDamage, com.bpm.minotaur.gamedata.DamageType damageType) {
        this.type = type;
        this.textPosition = position;
        this.damageText = text;
        this.duration = duration;
        this.progress = 0f;
        this.elapsedTime = 0f;
        this.isCritical = isCritical;
        this.isPlayerDamage = isPlayerDamage;
        this.damageType = damageType;
        this.driftOffset = (com.badlogic.gdx.math.MathUtils.random() - 0.5f) * 30f;

        // Initialize unused fields for projectiles
        this.startPosition = new Vector2();
        this.endPosition = new Vector2();
        this.color = (color != null) ? color : Color.WHITE;
        this.spriteData = null;
    }

    // Constructor for damage text with custom color
    public Animation(AnimationType type, GridPoint2 position, String text, Color color, float duration) {
        this(type, position, text, color, duration, false, false, null);
    }

    public Animation(AnimationType type, GridPoint2 position, String text, float duration) {
        this(type, position, text, Color.WHITE, duration, false, false, null);
    }

    public void update(float delta) {
        progress += delta / duration;
        elapsedTime += delta; // Update elapsed time
    }

    public boolean isFinished() {
        return progress >= 1.0f;
    }

    public AnimationType getType() {
        return type;
    }

    public Vector2 getStartPosition() {
        return startPosition;
    }

    public Vector2 getEndPosition() {
        return endPosition;
    }

    public float getProgress() {
        return progress;
    }

    public Color getColor() {
        return color;
    }

    public String[] getSpriteData() {
        return spriteData;
    }

    public String getDamageText() {
        return damageText;
    }

    public GridPoint2 getTextPosition() {
        return textPosition;
    }

    public float getElapsedTime() {
        return elapsedTime;
    }

    public float getDuration() {
        return duration;
    }

    public com.badlogic.gdx.math.Vector3 getPosition3D() {
        return position3D;
    }

    public com.bpm.minotaur.rendering.vfx.SpellExplosionRegistry.ExplosionType getExplosionType() {
        return explosionType;
    }

    public float getScale3D() {
        return scale3D;
    }

    public boolean isAdditiveBlend() {
        return additiveBlend;
    }

    public boolean isCritical() {
        return isCritical;
    }

    public boolean isPlayerDamage() {
        return isPlayerDamage;
    }

    public float getDriftOffset() {
        return driftOffset;
    }

    public com.bpm.minotaur.gamedata.DamageType getDamageType() {
        return damageType;
    }
}
