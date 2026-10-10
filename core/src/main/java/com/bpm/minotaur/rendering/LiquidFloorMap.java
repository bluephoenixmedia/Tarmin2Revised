package com.bpm.minotaur.rendering;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.utils.Disposable;
import com.bpm.minotaur.gamedata.Maze;
import com.bpm.minotaur.gamedata.liquid.LiquidType;

/**
 * The maze's liquids as a texture of one texel a tile, for the first-person floor shaders: the colour
 * of whatever lies on each tile, its alpha how strongly. Molten fire is written at full alpha, which
 * the shaders read as "glows by its own light". Without it the retro and textured floors drew water,
 * blood and the Bridge of Souls' chasm as bare floor -- the chasm an invisible wall.
 */
final class LiquidFloorMap implements Disposable {

    /** Liquids that light themselves; every other is lit like the floor, and never quite opaque. */
    static final float EMISSIVE_ALPHA = 1f;
    static final float MAX_ALPHA = 0.8f;

    private Pixmap pixmap;
    private Texture texture;
    private int width;
    private int height;

    /** Brings the texture up to date with {@code maze}'s liquids; null if it has none to show. */
    Texture update(Maze maze) {
        if (maze == null || maze.getLiquidManager() == null) return null;
        int w = maze.getWidth();
        int h = maze.getHeight();
        if (w <= 0 || h <= 0) return null;
        if (texture == null || w != width || h != height) {
            dispose();
            width = w;
            height = h;
            pixmap = new Pixmap(w, h, Pixmap.Format.RGBA8888);
            pixmap.setBlending(Pixmap.Blending.None);
            texture = new Texture(pixmap);
            texture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        }
        boolean any = false;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                LiquidType t = maze.getLiquidManager().getLiquidAt(x, y);
                if (t == null || t == LiquidType.NONE) {
                    pixmap.drawPixel(x, y, 0);
                    continue;
                }
                any = true;
                Color c = t.getColor();
                float a = t.isImpassable() ? EMISSIVE_ALPHA : Math.min(MAX_ALPHA, c.a * 0.85f);
                pixmap.drawPixel(x, y, Color.rgba8888(c.r, c.g, c.b, a));
            }
        }
        if (!any) return null;
        texture.draw(pixmap, 0, 0);
        return texture;
    }

    @Override
    public void dispose() {
        if (texture != null) texture.dispose();
        if (pixmap != null) pixmap.dispose();
        texture = null;
        pixmap = null;
    }
}
