package com.bpm.minotaur.screens.map;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Widget;

import java.util.HashMap;
import java.util.Map;

/**
 * One of the map's 7x7 glyphs, drawn pixel-exact at a whole-number cell size: on its 9x9
 * parchment plate (the prototype's {@code cellsFor}: ink rim, clipped corners) or bare.
 * The legend, the pin row and the header's seals and rotate buttons all use it, so the chrome
 * shows the same pixels as the plates on the board.
 */
final class MapGlyph extends Widget {

    /** Glyphs the chrome needs beyond the board's own set. */
    private static final Map<String, String[]> CHROME = new HashMap<>();
    static {
        CHROME.put("rotl", new String[]{"..####.", ".#....#", "###...#", ".#....#", "......#", ".#...#.", "..###.."});
        CHROME.put("rotr", new String[]{".####..", "#....#.", "#...###", "#....#.", "#......", ".#...#.", "..###.."});
        CHROME.put("diamond", new String[]{"...#...", "..#.#..", ".#...#.", "#.....#", ".#...#.", "..#.#..", "...#..."});
        CHROME.put("diamond_full", new String[]{"...#...", "..###..", ".#####.", "#######", ".#####.", "..###..", "...#..."});
    }

    private final TextureRegion white;
    private final int cell;
    private final boolean plate;
    private String name;
    private Color bg;
    private Color ink;

    MapGlyph(TextureRegion white, String name, Color bg, Color ink, int cell, boolean plate) {
        this.white = white;
        this.name = name;
        this.bg = bg;
        this.ink = ink;
        this.cell = cell;
        this.plate = plate;
        setTouchable(Touchable.disabled);
        setSize(getPrefWidth(), getPrefHeight());
    }

    static String[] rows(String name) {
        String[] g = IsometricDioramaRenderer.GLYPHS.get(name);
        if (g == null) g = CHROME.get(name);
        return g;
    }

    void set(String name, Color bg, Color ink) {
        this.name = name;
        this.bg = bg;
        this.ink = ink;
    }

    @Override
    public float getPrefWidth() {
        return (plate ? 9 : 7) * cell;
    }

    @Override
    public float getPrefHeight() {
        return (plate ? 9 : 7) * cell;
    }

    @Override
    public void draw(Batch batch, float parentAlpha) {
        String[] g = rows(name);
        if (g == null) return;
        Color old = batch.getColor().cpy();
        int n = plate ? 9 : 7;
        float x0 = Math.round(getX());
        float top = Math.round(getY()) + n * cell;
        for (int j = 0; j < n; j++) {
            for (int i = 0; i < n; i++) {
                Color c;
                if (plate) {
                    boolean edge = i == 0 || j == 0 || i == 8 || j == 8;
                    boolean corner = (i == 0 || i == 8) && (j == 0 || j == 8);
                    if (corner) continue;
                    c = edge || g[j - 1].charAt(i - 1) == '#' ? ink : bg;
                } else {
                    if (g[j].charAt(i) != '#') continue;
                    c = ink;
                }
                if (c == null || c.a <= 0f) continue;
                batch.setColor(c.r, c.g, c.b, c.a * parentAlpha);
                batch.draw(white, x0 + i * cell, top - (j + 1) * cell, cell, cell);
            }
        }
        batch.setColor(old);
    }
}
