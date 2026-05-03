package com.bpm.minotaur.screens.inventory;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator.FreeTypeFontParameter;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;

import java.util.ArrayList;
import java.util.List;

/**
 * Holds all programmatically-generated textures, drawables, and fonts for the
 * modern inventory UI. All assets are created via Pixmap so no external sprite
 * sheet is required.
 */
public class InventorySkin {

    // Parchment colour palette (matches the open-book mockup)
    public static final Color COL_PAGE_LIGHT    = Color.valueOf("D8BF8AFF");
    public static final Color COL_PAGE_DARK     = Color.valueOf("B8924AFF");
    public static final Color COL_BORDER_DARK   = Color.valueOf("2A1A0AFF");
    public static final Color COL_BORDER_MID    = Color.valueOf("5C3A10FF");
    public static final Color COL_SLOT_FILL     = Color.valueOf("1A1008BB");
    public static final Color COL_EQUIP_FILL    = Color.valueOf("0E0A05CC");
    public static final Color COL_GEM_BLUE      = Color.valueOf("3A70FFFF");
    public static final Color COL_GEM_PURPLE    = Color.valueOf("8A3AFFFF");
    public static final Color COL_TEXT_HEADER   = Color.valueOf("E8D5A3FF");
    public static final Color COL_TEXT_BODY     = Color.valueOf("C8B070FF");
    public static final Color COL_TEXT_MUTED    = Color.valueOf("907040FF");
    public static final Color COL_TEXT_VALUE    = Color.valueOf("EEE0B8FF");
    public static final Color COL_SPINE         = Color.valueOf("1E1006FF");

    private final List<Texture> owned = new ArrayList<>();

    private Texture whitePixel;

    private Drawable slotNormal;
    private Drawable slotEquip;
    private Drawable slotHighlightValid;
    private Drawable slotHighlightInvalid;
    private Drawable leftPageDrawable;
    private Drawable rightPageDrawable;
    private Drawable spineDrawable;

    private BitmapFont fontHeader;
    private BitmapFont fontBody;
    private BitmapFont fontSmall;

    public InventorySkin() {
        buildWhitePixel();
        buildSlotDrawables();
        buildPageDrawables();
        loadFonts();
    }

    // ── Texture builders ─────────────────────────────────────────────

    private void buildWhitePixel() {
        Pixmap p = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        p.setColor(Color.WHITE);
        p.fill();
        whitePixel = register(new Texture(p));
        p.dispose();
    }

    private void buildSlotDrawables() {
        slotNormal           = slotDrawable(COL_SLOT_FILL,  COL_BORDER_DARK, COL_BORDER_MID,  56);
        slotEquip            = slotDrawable(COL_EQUIP_FILL, COL_BORDER_MID,  COL_GEM_BLUE,    56);
        slotHighlightValid   = highlightDrawable(new Color(0f, 0.75f, 0.2f, 0.35f),
                                                 new Color(0f, 1f,    0.3f, 0.9f));
        slotHighlightInvalid = highlightDrawable(new Color(0.8f, 0f, 0f, 0.35f),
                                                 new Color(1f,   0.2f, 0.2f, 0.9f));
    }

    /** Bordered slot: fill → outerBorder (1px) → innerAccent (1px). */
    private Drawable slotDrawable(Color fill, Color outer, Color inner, int sz) {
        Pixmap p = new Pixmap(sz, sz, Pixmap.Format.RGBA8888);
        p.setColor(fill);
        p.fill();
        p.setColor(outer);
        p.drawRectangle(0, 0, sz, sz);
        p.drawRectangle(1, 1, sz - 2, sz - 2);
        p.setColor(inner);
        p.drawRectangle(2, 2, sz - 4, sz - 4);
        Texture t = register(new Texture(p));
        p.dispose();
        return new TextureRegionDrawable(t);
    }

    private Drawable highlightDrawable(Color fill, Color border) {
        Pixmap p = new Pixmap(56, 56, Pixmap.Format.RGBA8888);
        p.setColor(fill);
        p.fill();
        p.setColor(border);
        p.drawRectangle(0, 0, 56, 56);
        p.drawRectangle(1, 1, 54, 54);
        Texture t = register(new Texture(p));
        p.dispose();
        return new TextureRegionDrawable(t);
    }

    private void buildPageDrawables() {
        leftPageDrawable  = pageGradient(Color.valueOf("C8A86AFF"), Color.valueOf("B89050FF"));
        rightPageDrawable = pageGradient(Color.valueOf("C4A464FF"), Color.valueOf("AA844AFF"));

        // Book spine — narrow dark strip
        Pixmap sp = new Pixmap(6, 2, Pixmap.Format.RGBA8888);
        sp.setColor(COL_SPINE);
        sp.fill();
        Texture st = register(new Texture(sp));
        sp.dispose();
        spineDrawable = new TextureRegionDrawable(st);
    }

    /** Creates a simple vertical gradient page background (4 × 4 pixels, stretched by Scene2D). */
    private Drawable pageGradient(Color top, Color bottom) {
        int H = 8;
        Pixmap p = new Pixmap(4, H, Pixmap.Format.RGBA8888);
        for (int y = 0; y < H; y++) {
            float t = (float) y / (H - 1);
            p.setColor(
                top.r + (bottom.r - top.r) * t,
                top.g + (bottom.g - top.g) * t,
                top.b + (bottom.b - top.b) * t,
                1f
            );
            p.drawLine(0, y, 3, y);
        }
        Texture tex = register(new Texture(p));
        p.dispose();
        return new TextureRegionDrawable(tex);
    }

    // ── Font loading ─────────────────────────────────────────────────

    private void loadFonts() {
        try {
            FreeTypeFontGenerator gen =
                    new FreeTypeFontGenerator(Gdx.files.internal("fonts/intellivision.ttf"));
            fontHeader = gen.generateFont(param(20, COL_TEXT_HEADER, 0.9f));
            fontBody   = gen.generateFont(param(14, COL_TEXT_BODY,   0.6f));
            fontSmall  = gen.generateFont(param(11, COL_TEXT_BODY,   0.5f));
            gen.dispose();
        } catch (Exception e) {
            Gdx.app.error("InventorySkin", "intellivision.ttf not found — using default BitmapFont", e);
            fontHeader = new BitmapFont();  fontHeader.getData().setScale(2.2f);
            fontBody   = new BitmapFont();  fontBody.getData().setScale(1.6f);
            fontSmall  = new BitmapFont();  fontSmall.getData().setScale(1.1f);
        }
    }

    private FreeTypeFontParameter param(int size, Color color, float borderW) {
        FreeTypeFontParameter p = new FreeTypeFontParameter();
        p.size        = size;
        p.color       = new Color(color);
        p.borderWidth = borderW;
        p.borderColor = COL_BORDER_DARK;
        p.minFilter   = Texture.TextureFilter.Nearest;
        p.magFilter   = Texture.TextureFilter.Nearest;
        return p;
    }

    // ── Helpers ──────────────────────────────────────────────────────

    private Texture register(Texture t) {
        owned.add(t);
        return t;
    }

    public void dispose() {
        for (Texture t : owned) if (t != null) t.dispose();
        if (fontHeader != null) fontHeader.dispose();
        if (fontBody   != null) fontBody.dispose();
        if (fontSmall  != null) fontSmall.dispose();
    }

    // ── Getters ──────────────────────────────────────────────────────

    public Texture  getWhitePixel()              { return whitePixel; }
    public Drawable getNormalSlotDrawable()      { return slotNormal; }
    public Drawable getEquipSlotDrawable()       { return slotEquip; }
    public Drawable getHighlightValidDrawable()  { return slotHighlightValid; }
    public Drawable getHighlightInvalidDrawable(){ return slotHighlightInvalid; }
    public Drawable getLeftPageDrawable()        { return leftPageDrawable; }
    public Drawable getRightPageDrawable()       { return rightPageDrawable; }
    public Drawable getSpineDrawable()           { return spineDrawable; }
    public BitmapFont getFontHeader()            { return fontHeader; }
    public BitmapFont getFontBody()              { return fontBody; }
    public BitmapFont getFontSmall()             { return fontSmall; }
}
