package com.bpm.minotaur.rendering;

import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.bpm.minotaur.gamedata.Player;
import com.bpm.minotaur.gamedata.Projectile;
import com.bpm.minotaur.gamedata.Maze;


import java.util.ArrayList;
import java.util.List;

public class AnimationManager {

    private final List<Animation> animations = new ArrayList<>();
    private final EntityRenderer entityRenderer = new EntityRenderer();


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
            if (animation.getType() == Animation.AnimationType.PROJECTILE) {
                float progress = animation.getProgress();
                float x = animation.getStartPosition().x + (animation.getEndPosition().x - animation.getStartPosition().x) * progress;
                float y = animation.getStartPosition().y + (animation.getEndPosition().y - animation.getStartPosition().y) * progress;

                Projectile p = new Projectile(new com.badlogic.gdx.math.Vector2(x, y), new com.badlogic.gdx.math.Vector2(0, 0), animation.getColor(), 1);
                entityRenderer.renderSingleProjectile(shapeRenderer, player, p, viewport, depthBuffer, firstPersonRenderer, maze);
            }
        }
    }
}
