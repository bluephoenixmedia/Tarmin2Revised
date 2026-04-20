package com.bpm.minotaur.rendering;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.bpm.minotaur.gamedata.Maze;
import com.bpm.minotaur.gamedata.player.Player;
import com.bpm.minotaur.gamedata.Projectile;

import java.util.ArrayList;
import java.util.List;

public class AnimationManager {

    private final List<Animation> animations = new ArrayList<>();
    private final EntityRenderer entityRenderer;
    private final BitmapFont damageFont;

    public AnimationManager(EntityRenderer entityRenderer) {
        damageFont = new BitmapFont();
        damageFont.setColor(Color.WHITE);
        damageFont.getData().setScale(4.0f); // Make it slightly larger and easier to see
        this.entityRenderer = entityRenderer;
    }

    // Protected constructor for Headless/Mocking
    protected AnimationManager() {
        this.damageFont = null;
        this.entityRenderer = null;
    }

    public void addAnimation(Animation animation) {
        animations.add(animation);
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

    /**
     * Renders damage text overlays. Call this separately with a SpriteBatch.
     * This should be called AFTER your main 3D rendering, during the HUD/UI phase.
     */
    public void renderDamageText(SpriteBatch batch, Viewport viewport) {
        for (Animation animation : animations) {
            if (animation.getType() == Animation.AnimationType.DAMAGE_TEXT) {
                // Position the text in screen center (above the monster in the player's view)
                float screenX = viewport.getWorldWidth() / 2 + 120; // Slightly left of center
                float screenY = viewport.getWorldHeight() / 2 + 180; // Above center

                // Make it float upward over time
                float floatOffset = animation.getElapsedTime() * 50f;

                // Fade out over time
                float alpha = 1.0f - animation.getProgress();
                damageFont.setColor(1f, 1f, 1f, alpha);

                damageFont.draw(batch, animation.getDamageText(), screenX, screenY + floatOffset);
            }
        }
        damageFont.setColor(Color.WHITE); // Reset color
    }

    public void dispose() {
        damageFont.dispose();
        entityRenderer.dispose();
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
        p.init(position, new com.badlogic.gdx.math.Vector3(vx, vy, vz), color.cpy(), 0.5f, 0.02f); // Size reduced to
                                                                                                   // 0.02
        activeParticles.add(p);
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
