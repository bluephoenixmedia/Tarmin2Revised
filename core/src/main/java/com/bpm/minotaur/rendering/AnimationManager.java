package com.bpm.minotaur.rendering;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.bpm.minotaur.gamedata.Maze;
import com.bpm.minotaur.gamedata.player.Player;
import com.bpm.minotaur.gamedata.Projectile;

import com.badlogic.gdx.math.GridPoint2;

import java.util.ArrayList;
import java.util.List;

public class AnimationManager {

    private final List<Animation> animations = new ArrayList<>();
    private final EntityRenderer entityRenderer;
    private final BitmapFont damageFont;

    public AnimationManager(EntityRenderer entityRenderer) {
        BitmapFont df = null;
        try {
            if (com.badlogic.gdx.Gdx.files != null && com.badlogic.gdx.Gdx.files.internal("fonts/intellivision.ttf").exists()) {
                com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator gen =
                        new com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator(com.badlogic.gdx.Gdx.files.internal("fonts/intellivision.ttf"));
                com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator.FreeTypeFontParameter param =
                        new com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator.FreeTypeFontParameter();
                param.size = 26;
                param.color = Color.WHITE;
                param.shadowColor = new Color(0.04f, 0.03f, 0.02f, 0.90f);
                param.shadowOffsetX = 2;
                param.shadowOffsetY = 2;
                param.borderWidth = 1.5f;
                param.borderColor = new Color(0.04f, 0.03f, 0.02f, 0.95f);
                param.minFilter = com.badlogic.gdx.graphics.Texture.TextureFilter.Linear;
                param.magFilter = com.badlogic.gdx.graphics.Texture.TextureFilter.Linear;
                df = gen.generateFont(param);
                gen.dispose();
            }
        } catch (Throwable t) {
            df = null;
        }
        if (df == null) {
            try {
                df = new BitmapFont();
                df.getData().setScale(1.8f);
            } catch (Throwable t) {
                df = null;
            }
        }
        this.damageFont = df;
        this.entityRenderer = entityRenderer;
    }

    // Protected constructor for Headless/Mocking
    protected AnimationManager() {
        this.damageFont = null;
        this.entityRenderer = null;
    }

    public void addAnimation(Animation animation) {
        if (animation != null) {
            animations.add(animation);
        }
    }

    public List<Animation> getAnimations() {
        return animations;
    }

    public void spawnExplosion(com.bpm.minotaur.rendering.vfx.SpellExplosionRegistry.ExplosionType type, com.badlogic.gdx.math.Vector3 position3D) {
        addAnimation(new Animation(type, position3D));
    }

    public void spawnExplosion(com.bpm.minotaur.rendering.vfx.SpellExplosionRegistry.ExplosionType type, com.badlogic.gdx.math.Vector3 position3D, float scale, float duration) {
        addAnimation(new Animation(type, position3D, scale, duration));
    }

    public void update(float delta) {
        animations.removeIf(animation -> {
            animation.update(delta);
            if (animation.getType() == Animation.AnimationType.PROJECTILE_SPELL) {
                float progress = animation.getProgress();
                float x = animation.getStartPosition().x
                        + (animation.getEndPosition().x - animation.getStartPosition().x) * progress;
                float y = animation.getStartPosition().y
                        + (animation.getEndPosition().y - animation.getStartPosition().y) * progress;
                spawnSpellParticle(new com.badlogic.gdx.math.Vector3(x, 0.5f, y), animation.getColor());
            }
            return animation.isFinished();
        });

        for (int i = activeParticles.size - 1; i >= 0; i--) {
            SpellParticle p = activeParticles.get(i);
            p.update(delta);
            if (p.lifeTimer <= 0) {
                activeParticles.removeIndex(i);
                particlePool.free(p);
            }
        }
    }

    public void render(ShapeRenderer shapeRenderer, Player player, Viewport viewport, float[] depthBuffer,
            FirstPersonRenderer firstPersonRenderer, Maze maze) {
        for (Animation animation : animations) {
            if (animation.getType() == Animation.AnimationType.PROJECTILE_PLAYER
                    || animation.getType() == Animation.AnimationType.PROJECTILE_MONSTER
                    || animation.getType() == Animation.AnimationType.PROJECTILE_SPELL) {
                float progress = animation.getProgress();
                float x = animation.getStartPosition().x
                        + (animation.getEndPosition().x - animation.getStartPosition().x) * progress;
                float y = animation.getStartPosition().y
                        + (animation.getEndPosition().y - animation.getStartPosition().y) * progress;

                // Pass the sprite data from the animation to the projectile
                Projectile p = new Projectile(
                        new com.badlogic.gdx.math.Vector2(x, y),
                        new com.badlogic.gdx.math.Vector2(0, 0),
                        animation.getColor(),
                        1f,
                        animation.getSpriteData());

                entityRenderer.renderSingleProjectile(shapeRenderer, player, p, viewport, depthBuffer,
                        firstPersonRenderer, maze);
            }
        }
        renderSpellParticles(shapeRenderer, player, viewport, depthBuffer);
    }

    private final com.badlogic.gdx.graphics.g2d.GlyphLayout glyphLayout = new com.badlogic.gdx.graphics.g2d.GlyphLayout();

    /**
     * Renders damage text overlays with standard viewport fallback.
     */
    public void renderDamageText(SpriteBatch batch, Viewport viewport) {
        renderDamageText(batch, viewport, null, null);
    }

    /**
     * Renders damage text overlays projected from 3D entity world coordinates,
     * including player incoming damage floaters and critical strike scaling.
     */
    public void renderDamageText(SpriteBatch batch, Viewport viewport, Player player, float[] depthBuffer) {
        if (damageFont == null || viewport == null) return;

        for (Animation animation : animations) {
            if (animation.getType() == Animation.AnimationType.DAMAGE_TEXT) {
                float screenX = viewport.getWorldWidth() / 2f;
                float screenY = viewport.getWorldHeight() / 2f + 160f;
                boolean projected = false;

                if (animation.isPlayerDamage()) {
                    // Incoming hit on player: centered lower FOV above vitals
                    screenX = viewport.getWorldWidth() / 2f;
                    screenY = 280f;
                    projected = true;
                } else if (player != null && depthBuffer != null && animation.getTextPosition() != null) {
                    GridPoint2 targetPos = animation.getTextPosition();
                    float worldX = targetPos.x + 0.5f;
                    float worldY = targetPos.y + 0.5f;

                    float dx = worldX - player.getPosition().x;
                    float dy = worldY - player.getPosition().y;
                    float planeX = player.getCameraPlane().x;
                    float planeY = player.getCameraPlane().y;
                    float dirX = player.getDirectionVector().x;
                    float dirY = player.getDirectionVector().y;
                    float invDet = 1.0f / (planeX * dirY - dirX * planeY);
                    float transformX = invDet * (dirY * dx - dirX * dy);
                    float transformY = invDet * (-planeY * dx + planeX * dy);

                    if (transformY > 0.2f) {
                        com.badlogic.gdx.graphics.Camera camera = viewport.getCamera();
                        int projX = (int) ((camera.viewportWidth / 2f) * (1f + transformX / transformY));
                        if (projX >= 0 && projX < depthBuffer.length && transformY < depthBuffer[projX] + 0.6f) {
                            float spriteScale = Math.abs(camera.viewportHeight / transformY);
                            screenX = projX;
                            screenY = (camera.viewportHeight / 2f) + (spriteScale * 0.42f);
                            projected = true;
                        }
                    }
                }

                // If outgoing damage was completely offscreen, suppress 3D floating text
                if (!projected && !animation.isPlayerDamage() && player != null) {
                    continue;
                }

                // Apply randomized horizontal drift so multi-hits don't stack directly
                screenX += animation.getDriftOffset();

                // Float upward over time
                float floatOffset = animation.getElapsedTime() * 45f;
                screenY += floatOffset;

                // Fade out over time
                float progress = animation.getProgress();
                float alpha = Math.max(0f, 1.0f - progress);

                // Scale punch-in bounce
                float scale = 1.0f;
                if (animation.isCritical()) {
                    scale = (progress < 0.25f) ? 1.45f - (progress / 0.25f) * 0.45f : 1.0f;
                } else {
                    scale = (progress < 0.20f) ? 1.18f - (progress / 0.20f) * 0.18f : 1.0f;
                }
                damageFont.getData().setScale(scale);

                // Color calculation: critical hits flash gold in first 20% of duration
                Color animColor = animation.getColor() != null ? animation.getColor() : Color.WHITE;
                if (animation.isCritical() && progress < 0.20f) {
                    damageFont.setColor(1.0f, 0.85f, 0.35f, alpha);
                } else {
                    damageFont.setColor(animColor.r, animColor.g, animColor.b, alpha);
                }

                glyphLayout.setText(damageFont, animation.getDamageText());
                float drawX = screenX - glyphLayout.width / 2f;
                damageFont.draw(batch, animation.getDamageText(), drawX, screenY);
            }
        }
        damageFont.getData().setScale(1.0f);
        damageFont.setColor(Color.WHITE); // Reset color
    }

    public void dispose() {
        if (damageFont != null) damageFont.dispose();
        if (entityRenderer != null) entityRenderer.dispose();
    }

    // --- NEW: Particle System ---
    private final com.badlogic.gdx.utils.Array<SpellParticle> activeParticles = new com.badlogic.gdx.utils.Array<>(
            false, 500);
    private final com.badlogic.gdx.utils.Pool<SpellParticle> particlePool = new com.badlogic.gdx.utils.Pool<SpellParticle>() {
        @Override
        protected SpellParticle newObject() {
            return new SpellParticle();
        }
    };

    public void spawnSpellParticle(com.badlogic.gdx.math.Vector3 position, Color color) {
        SpellParticle p = particlePool.obtain();
        // Random velocity for nice trail effect
        float vx = com.badlogic.gdx.math.MathUtils.random(-0.5f, 0.5f);
        float vy = com.badlogic.gdx.math.MathUtils.random(-0.5f, 0.5f);
        float vz = com.badlogic.gdx.math.MathUtils.random(-0.5f, 0.5f);
        p.init(position, new com.badlogic.gdx.math.Vector3(vx, vy, vz), color.cpy(), 0.5f, 0.02f); // Size reduced to 0.02
        activeParticles.add(p);
    }

    public void spawnSpellCascade(com.badlogic.gdx.math.Vector3 position, Color color, int count, float speed, float size) {
        for (int i = 0; i < count; i++) {
            SpellParticle p = particlePool.obtain();
            float vx = com.badlogic.gdx.math.MathUtils.random(-speed, speed);
            float vy = com.badlogic.gdx.math.MathUtils.random(-speed, speed);
            float vz = com.badlogic.gdx.math.MathUtils.random(-speed, speed);
            float life = com.badlogic.gdx.math.MathUtils.random(0.3f, 0.7f);
            p.init(position.cpy(), new com.badlogic.gdx.math.Vector3(vx, vy, vz), color.cpy(), life, size);
            activeParticles.add(p);
        }
    }

    public void spawnArchetypeCascade(com.badlogic.gdx.math.Vector3 position, com.bpm.minotaur.gamedata.spells.VisualArchetype archetype, int count) {
        if (archetype == null) return;
        Color prim = archetype.getPrimaryColor();
        Color sec = archetype.getSecondaryColor();

        float speed = 0.8f;
        float baseSize = 0.035f;

        if (archetype == com.bpm.minotaur.gamedata.spells.VisualArchetype.EXPLOSIVE_BURST) {
            speed = 1.4f;
            baseSize = 0.05f;
        } else if (archetype == com.bpm.minotaur.gamedata.spells.VisualArchetype.FROST_RAY) {
            speed = 0.6f;
            baseSize = 0.03f;
        } else if (archetype == com.bpm.minotaur.gamedata.spells.VisualArchetype.THUNDER_CONCUSSION) {
            speed = 1.6f;
            baseSize = 0.045f;
        }

        for (int i = 0; i < count; i++) {
            SpellParticle p = particlePool.obtain();
            Color pColor = (i % 2 == 0) ? prim.cpy() : sec.cpy();
            float vx = com.badlogic.gdx.math.MathUtils.random(-speed, speed);
            float vy = com.badlogic.gdx.math.MathUtils.random(-speed * 0.4f, speed * 1.1f);
            float vz = com.badlogic.gdx.math.MathUtils.random(-speed, speed);
            float life = com.badlogic.gdx.math.MathUtils.random(0.4f, 0.9f);
            p.init(position.cpy(), new com.badlogic.gdx.math.Vector3(vx, vy, vz), pColor, life, baseSize);
            activeParticles.add(p);
        }
    }

    private void renderSpellParticles(ShapeRenderer shapeRenderer, Player player, Viewport viewport,
            float[] depthBuffer) {
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        float halfHeight = viewport.getWorldHeight() / 2.0f;
        com.badlogic.gdx.graphics.Camera camera = viewport.getCamera();

        for (SpellParticle p : activeParticles) {
            float dx = p.position.x - player.getPosition().x;
            float dy = p.position.z - player.getPosition().y;
            float planeX = player.getCameraPlane().x;
            float planeY = player.getCameraPlane().y;
            float dirX = player.getDirectionVector().x;
            float dirY = player.getDirectionVector().y;
            float invDet = 1.0f / (planeX * dirY - dirX * planeY);
            float transformX = invDet * (dirY * dx - dirX * dy);
            float transformY = invDet * (-planeY * dx + planeX * dy);

            if (transformY > 0.2f) { // Clip closer to avoid singularity
                int screenX = (int) ((camera.viewportWidth / 2) * (1 + transformX / transformY));
                if (screenX >= 0 && screenX < depthBuffer.length && transformY < depthBuffer[screenX]) {
                    float spriteScale = Math.abs(camera.viewportHeight / transformY);
                    float screenY = halfHeight + (p.position.y - 0.5f) * spriteScale;
                    float particleSize = spriteScale * p.size;

                    // Clamp max size
                    if (particleSize > 100)
                        particleSize = 100;

                    // Simple Glow Effect (Draw larger transparent first)
                    shapeRenderer.setColor(p.color.r, p.color.g, p.color.b, p.color.a * 0.3f);
                    shapeRenderer.rect(screenX - particleSize, screenY - particleSize, particleSize * 2,
                            particleSize * 2);

                    // Core
                    shapeRenderer.setColor(p.color);
                    shapeRenderer.rect(screenX - particleSize / 2, screenY - particleSize / 2, particleSize,
                            particleSize);
                }
            }
        }
        shapeRenderer.end();
    }
}
