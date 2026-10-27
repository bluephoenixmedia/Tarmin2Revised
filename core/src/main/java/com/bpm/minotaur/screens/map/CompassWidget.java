package com.bpm.minotaur.screens.map;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.utils.Disposable;
import com.bpm.minotaur.rendering.HudSkin;

import java.util.function.Supplier;

/**
 * Carved Frame rotating compass medallion for the isometric diorama expedition map.
 * Displays world North rotation matching camera rotation.
 */
public class CompassWidget extends Actor implements Disposable {

    private static final Color COLOR_BG = new Color(0x0b / 255f, 0x09 / 255f, 0x07 / 255f, 0.86f);
    private static final Color COLOR_BORDER = new Color(0x6e / 255f, 0x4d / 255f, 0x25 / 255f, 1.0f);
    private static final Color COLOR_INNER_RING = new Color(0x3a / 255f, 0x2a / 255f, 0x18 / 255f, 1.0f);
    private static final Color COLOR_NORTH_LIT = new Color(0xff / 255f, 0xd7 / 255f, 0x7a / 255f, 1.0f); // #FFD77A
    private static final Color COLOR_NORTH_SHADE = new Color(0xb8 / 255f, 0x86 / 255f, 0x2f / 255f, 1.0f); // #B8862F
    private static final Color COLOR_SOUTH = new Color(0x3a / 255f, 0x2a / 255f, 0x18 / 255f, 1.0f); // #3A2A18
    private static final Color COLOR_NORTH_TEXT = new Color(0xff / 255f, 0xd7 / 255f, 0x7a / 255f, 1.0f);

    private final Supplier<Float> rotationSupplier;
    private final HudSkin skin;
    private ShapeRenderer shapes;

    public CompassWidget(HudSkin skin, Supplier<Float> rotationSupplier) {
        this.skin = skin;
        this.rotationSupplier = rotationSupplier;
        setSize(104f, 104f);
    }

    @Override
    public void draw(Batch batch, float parentAlpha) {
        if (parentAlpha <= 0.001f) return;

        float rot = rotationSupplier != null ? rotationSupplier.get() : 0f;
        float cx = getX() + getWidth() / 2f;
        float cy = getY() + getHeight() / 2f;
        float r = 42f;

        batch.end();

        if (shapes == null) {
            shapes = new ShapeRenderer();
        }
        shapes.setProjectionMatrix(batch.getProjectionMatrix());
        shapes.setTransformMatrix(batch.getTransformMatrix());

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        // Circular background
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(new Color(COLOR_BG.r, COLOR_BG.g, COLOR_BG.b, COLOR_BG.a * parentAlpha));
        shapes.circle(cx, cy, r, 32);

        // Rotating compass needle matching isometric projection of North (+Y in world)
        float angle = calculateNeedleAngle(rot);
        float cos = (float) Math.cos(angle);
        float sin = (float) Math.sin(angle);
        float nx = -sin;
        float ny = cos;

        // North arrow: tip at +32, base at -4, width 6
        float tipX = cx + cos * 32f;
        float tipY = cy + sin * 32f;
        float baseCenterX = cx - cos * 4f;
        float baseCenterY = cy - sin * 4f;
        float wLeftX = cx + nx * 6f;
        float wLeftY = cy + ny * 6f;
        float wRightX = cx - nx * 6f;
        float wRightY = cy - ny * 6f;

        // Lit side (left half)
        shapes.setColor(new Color(COLOR_NORTH_LIT.r, COLOR_NORTH_LIT.g, COLOR_NORTH_LIT.b, parentAlpha));
        shapes.triangle(tipX, tipY, wLeftX, wLeftY, baseCenterX, baseCenterY);

        // Shaded side (right half)
        shapes.setColor(new Color(COLOR_NORTH_SHADE.r, COLOR_NORTH_SHADE.g, COLOR_NORTH_SHADE.b, parentAlpha));
        shapes.triangle(tipX, tipY, baseCenterX, baseCenterY, wRightX, wRightY);

        // South arrow: tip at -26
        float sTipX = cx - cos * 26f;
        float sTipY = cy - sin * 26f;
        shapes.setColor(new Color(COLOR_SOUTH.r, COLOR_SOUTH.g, COLOR_SOUTH.b, parentAlpha));
        shapes.triangle(sTipX, sTipY, wRightX, wRightY, baseCenterX, baseCenterY);
        shapes.triangle(sTipX, sTipY, baseCenterX, baseCenterY, wLeftX, wLeftY);

        shapes.end();

        // Border rings
        shapes.begin(ShapeRenderer.ShapeType.Line);
        shapes.setColor(new Color(COLOR_BORDER.r, COLOR_BORDER.g, COLOR_BORDER.b, parentAlpha));
        shapes.circle(cx, cy, r, 32);
        shapes.circle(cx, cy, r - 1f, 32);

        shapes.setColor(new Color(COLOR_INNER_RING.r, COLOR_INNER_RING.g, COLOR_INNER_RING.b, parentAlpha));
        shapes.circle(cx, cy, r - 8f, 32);
        shapes.end();

        batch.begin();

        // North "N" text marker
        if (skin != null && skin.getFontSmall() != null) {
            BitmapFont font = skin.getFontSmall();
            font.setColor(COLOR_NORTH_TEXT.r, COLOR_NORTH_TEXT.g, COLOR_NORTH_TEXT.b, parentAlpha);
            float nDist = r - 6f;
            float nxPos = cx + cos * nDist - 4f;
            float nyPos = cy + sin * nDist + 6f;
            font.draw(batch, "N", nxPos, nyPos);
            font.setColor(Color.WHITE);
        }
    }

    /** Where world north points on screen at a camera rotation; the renderer's own projection decides. */
    public static float calculateNeedleAngle(float rotRad) {
        return IsometricDioramaRenderer.northAngle(rotRad);
    }

    @Override
    public void dispose() {
        if (shapes != null) {
            shapes.dispose();
            shapes = null;
        }
    }
}
