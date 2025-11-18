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

    public void addAnimation(Animation animation) {
        animations.add(animation);
    }

    public void update(float delta) {
        animations.removeIf(animation -> {
            animation.update(delta);
            return animation.isFinished();
        });
    }

    public void render(ShapeRenderer shapeRenderer, Player player, Viewport viewport, float[] depthBuffer, FirstPersonRenderer firstPersonRenderer, Maze maze) {
        for (Animation animation : animations) {
            if (animation.getType() == Animation.AnimationType.PROJECTILE_PLAYER || animation.getType() == Animation.AnimationType.PROJECTILE_MONSTER) {
                float progress = animation.getProgress();
                float x = animation.getStartPosition().x + (animation.getEndPosition().x - animation.getStartPosition().x) * progress;
                float y = animation.getStartPosition().y + (animation.getEndPosition().y - animation.getStartPosition().y) * progress;

                // Pass the sprite data from the animation to the projectile
                Projectile p = new Projectile(
                    new com.badlogic.gdx.math.Vector2(x, y),
                    new com.badlogic.gdx.math.Vector2(0, 0),
                    animation.getColor(),
                    1f,
                    animation.getSpriteData()
                );

                entityRenderer.renderSingleProjectile(shapeRenderer, player, p, viewport, depthBuffer, firstPersonRenderer, maze);
            }
        }
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
}
