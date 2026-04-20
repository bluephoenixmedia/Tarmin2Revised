package com.bpm.minotaur.headless;

import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.bpm.minotaur.gamedata.Maze;
import com.bpm.minotaur.gamedata.player.Player;
import com.bpm.minotaur.rendering.Animation;
import com.bpm.minotaur.rendering.AnimationManager;
import com.bpm.minotaur.rendering.FirstPersonRenderer;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;

public class HeadlessAnimationManager extends AnimationManager {

    public HeadlessAnimationManager() {
        super();
    }

    @Override
    public void addAnimation(Animation animation) {
        // No-op
    }

    @Override
    public void update(float delta) {
        // No-op
    }

    @Override
    public void render(ShapeRenderer shapeRenderer, Player player, Viewport viewport, float[] depthBuffer,
            FirstPersonRenderer firstPersonRenderer, Maze maze) {
        // No-op
    }

    @Override
    public void renderDamageText(SpriteBatch batch, Viewport viewport) {
        // No-op
    }
}
