package com.bpm.minotaur.screens.inventory;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.NinePatch;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator.FreeTypeFontParameter;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.scenes.scene2d.utils.NinePatchDrawable;
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
    public static final Color COL_PAGE_LIGHT = Color.valueOf("D8BF8AFF");
    public static final Color COL_PAGE_DARK = Color.valueOf("B8924AFF");
    public static final Color COL_BORDER_DARK = Color.valueOf("2A1A0AFF");
    public static final Color COL_BORDER_MID = Color.valueOf("5C3A10FF");
    public static final Color COL_SLOT_FILL = Color.valueOf("1A1008BB");
    public static final Color COL_EQUIP_FILL = Color.valueOf("0E0A05CC");
    public static final Color COL_GEM_BLUE = Color.valueOf("3A70FFFF");
    public static final Color COL_GEM_PURPLE = Color.valueOf("8A3AFFFF");
    public static final Color COL_TEXT_HEADER = Color.valueOf("E8D5A3FF");
    public static final Color COL_TEXT_BODY = Color.valueOf("C8B070FF");
    public static final Color COL_TEXT_MUTED = Color.valueOf("907040FF");
    public static final Color COL_TEXT_VALUE = Color.valueOf("EEE0B8FF");
    public static final Color COL_SPINE = Color.valueOf("1E1006FF");

    private final List<Texture> owned = new ArrayList<>();

    private Texture whitePixel;

    private Drawable slotNormal;
    private Drawable slotEquip;
    private Drawable slotHighlightValid;
    private Drawable slotHighlightInvalid;
    private Drawable leftPageDrawable;
    private Drawable rightPageDrawable;
    private Drawable spineDrawable;

    // Decorative drawables
    private Drawable portraitFrameDrawable;
    private Drawable runeCircleDrawable;
    private Drawable panelBoxDrawable;

    // Stat icon drawables
    private Drawable iconRed;
    private Drawable iconBlue;
    private Drawable iconGold;
    private Drawable iconGreen;
    private Drawable iconOrange;
    private Drawable iconPurple;
    private Drawable iconGray;

    private BitmapFont fontHeader;
    private BitmapFont fontBody;
    private BitmapFont fontSmall;
    private BitmapFont fontAttributes;

    public InventorySkin() {
        buildWhitePixel();
        buildSlotDrawables();
        buildPageDrawables();
        buildDecorativeDrawables();
        buildIconDrawables();
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
        slotNormal = slotDrawable(COL_SLOT_FILL, COL_BORDER_DARK, COL_BORDER_MID, 56);
        slotEquip = slotDrawable(COL_EQUIP_FILL, COL_BORDER_MID, COL_GEM_BLUE, 56);
        slotHighlightValid = highlightDrawable(new Color(0f, 0.75f, 0.2f, 0.35f),
                new Color(0f, 1f, 0.3f, 0.9f));
        slotHighlightInvalid = highlightDrawable(new Color(0.8f, 0f, 0f, 0.35f),
                new Color(1f, 0.2f, 0.2f, 0.9f));
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
        leftPageDrawable = richParchment(Color.valueOf("D4B86AFF"), Color.valueOf("BCA050FF"), false);
        rightPageDrawable = richParchment(Color.valueOf("CEB062FF"), Color.valueOf("B09448FF"), true);

        Pixmap sp = new Pixmap(6, 2, Pixmap.Format.RGBA8888);
        sp.setColor(COL_SPINE);
        sp.fill();
        Texture st = register(new Texture(sp));
        sp.dispose();
        spineDrawable = new TextureRegionDrawable(st);
    }

    private Drawable richParchment(Color light, Color dark, boolean spineOnRight) {
        int W = 64, H = 128;
        Pixmap p = new Pixmap(W, H, Pixmap.Format.RGBA8888);
        p.setBlending(Pixmap.Blending.None);
        for (int y = 0; y < H; y++) {
            float ty = (float) y / (H - 1);
            for (int x = 0; x < W; x++) {
                float tx = (float) x / (W - 1);
                float blend = ty * 0.55f;
                float spineX = spineOnRight ? tx : 1f - tx;
                float edge = 1f - 0.14f * spineX * spineX;
                float noise = 1f + 0.022f * (float) (Math.sin(x * 4.1 + y * 2.7) * Math.cos(x * 1.3 + y * 3.9));
                float r = Math.min(1f, (light.r + (dark.r - light.r) * blend) * edge * noise);
                float g = Math.min(1f, (light.g + (dark.g - light.g) * blend) * edge * noise);
                float b = Math.min(1f, (light.b + (dark.b - light.b) * blend) * edge * noise);
                p.setColor(r, g, b, 1f);
                p.drawPixel(x, y);
            }
        }
        Texture t = register(new Texture(p));
        p.dispose();
        return new TextureRegionDrawable(t);
    }

    private void buildDecorativeDrawables() {
        runeCircleDrawable = buildRuneCircle();
        portraitFrameDrawable = buildPortraitFrame();
        panelBoxDrawable = buildPanelBox();
    }

    private Drawable buildRuneCircle() {
        int sz = 256;
        Pixmap p = new Pixmap(sz, sz, Pixmap.Format.RGBA8888);
        p.setBlending(Pixmap.Blending.None);
        p.setColor(0, 0, 0, 0);
        p.fill();
        int cx = sz / 2, cy = sz / 2;
        p.setColor(new Color(0.65f, 0.44f, 0.12f, 0.22f));
        p.drawCircle(cx, cy, 120);
        p.drawCircle(cx, cy, 100);
        p.drawCircle(cx, cy, 78);
        p.drawCircle(cx, cy, 52);
        p.drawCircle(cx, cy, 24);
        for (int i = 0; i < 8; i++) {
            double a = Math.PI * i / 4.0;
            p.drawLine(cx + (int) (24 * Math.cos(a)), cy + (int) (24 * Math.sin(a)),
                    cx + (int) (120 * Math.cos(a)), cy + (int) (120 * Math.sin(a)));
        }
        for (int i = 0; i < 8; i++) {
            double a = Math.PI * i / 4.0;
            p.drawCircle(cx + (int) (100 * Math.cos(a)), cy + (int) (100 * Math.sin(a)), 7);
        }
        p.setColor(new Color(0.65f, 0.44f, 0.12f, 0.10f));
        p.drawCircle(cx, cy, 110);
        p.drawCircle(cx, cy, 88);
        Texture t = register(new Texture(p));
        p.dispose();
        return new TextureRegionDrawable(t);
    }

    private Drawable buildPortraitFrame() {
        int W = 128, H = 200;
        int border = 14;
        Pixmap p = new Pixmap(W, H, Pixmap.Format.RGBA8888);
        p.setBlending(Pixmap.Blending.None);
        p.setColor(0, 0, 0, 0);
        p.fill();
        // Gold frame bands
        p.setColor(Color.valueOf("C8A432FF"));
        p.fillRectangle(0, 0, W, border);
        p.fillRectangle(0, H - border, W, border);
        p.fillRectangle(0, border, border, H - border * 2);
        p.fillRectangle(W - border, border, border, H - border * 2);
        // Outer dark outline
        p.setColor(COL_BORDER_DARK);
        p.drawRectangle(0, 0, W, H);
        p.drawRectangle(1, 1, W - 2, H - 2);
        // Inner dark outline
        p.drawRectangle(border - 2, border - 2, W - (border - 2) * 2, H - (border - 2) * 2);
        p.drawRectangle(border - 1, border - 1, W - (border - 1) * 2, H - (border - 1) * 2);
        // Highlight edges
        p.setColor(Color.valueOf("E8C850FF"));
        for (int x = 2; x < W - 2; x++) {
            p.drawPixel(x, 2);
            p.drawPixel(x, H - 3);
        }
        for (int y = 2; y < H - 2; y++) {
            p.drawPixel(2, y);
            p.drawPixel(W - 3, y);
        }
        // Corner gems
        int g = 14;
        gemRect(p, 1, 1, g, g, COL_GEM_BLUE);
        gemRect(p, W - g - 1, 1, g, g, COL_GEM_PURPLE);
        gemRect(p, 1, H - g - 1, g, g, COL_GEM_PURPLE);
        gemRect(p, W - g - 1, H - g - 1, g, g, COL_GEM_BLUE);
        Texture t = register(new Texture(p));
        p.dispose();
        return new TextureRegionDrawable(t);
    }

    private void gemRect(Pixmap p, int x, int y, int w, int h, Color c) {
        p.setColor(new Color(c.r * 0.4f, c.g * 0.4f, c.b * 0.4f, 1f));
        p.fillRectangle(x, y, w, h);
        p.setColor(c);
        p.fillRectangle(x + 2, y + 2, w - 4, h - 4);
        p.setColor(new Color(Math.min(1f, c.r + 0.45f), Math.min(1f, c.g + 0.45f), Math.min(1f, c.b + 0.45f), 1f));
        p.fillRectangle(x + 3, y + 3, Math.max(1, w / 3), Math.max(1, h / 3));
    }

    private Drawable buildPanelBox() {
        int corner = 18;
        int W = corner * 2 + 8;
        int H = corner * 2 + 8;
        Pixmap p = new Pixmap(W, H, Pixmap.Format.RGBA8888);
        p.setBlending(Pixmap.Blending.None);
        p.setColor(Color.valueOf("9A703066"));
        p.fill();
        p.setColor(COL_BORDER_DARK);
        p.drawRectangle(0, 0, W, H);
        p.drawRectangle(1, 1, W - 2, H - 2);
        p.setColor(COL_BORDER_MID);
        p.drawRectangle(2, 2, W - 4, H - 4);
        gemRect(p, 0, 0, corner, corner, COL_GEM_BLUE);
        gemRect(p, W - corner, 0, corner, corner, COL_GEM_PURPLE);
        gemRect(p, 0, H - corner, corner, corner, COL_GEM_PURPLE);
        gemRect(p, W - corner, H - corner, corner, corner, COL_GEM_BLUE);
        Texture t = register(new Texture(p));
        p.dispose();
        NinePatch np = new NinePatch(t, corner, corner, corner, corner);
        return new NinePatchDrawable(np);
    }

    private void buildIconDrawables() {
        iconRed = buildIcon(Color.valueOf("DD3333FF"));
        iconBlue = buildIcon(Color.valueOf("3366DDFF"));
        iconGold = buildIcon(Color.valueOf("DDAA22FF"));
        iconGreen = buildIcon(Color.valueOf("33AA44FF"));
        iconOrange = buildIcon(Color.valueOf("DD7722FF"));
        iconPurple = buildIcon(Color.valueOf("8833AAFF"));
        iconGray = buildIcon(Color.valueOf("888888FF"));
    }

    private Drawable buildIcon(Color fill) {
        int sz = 12;
        Pixmap p = new Pixmap(sz, sz, Pixmap.Format.RGBA8888);
        p.setBlending(Pixmap.Blending.None);
        p.setColor(fill);
        p.fill();
        p.setColor(COL_BORDER_DARK);
        p.drawRectangle(0, 0, sz, sz);
        p.setColor(
                new Color(Math.min(1f, fill.r + 0.5f), Math.min(1f, fill.g + 0.5f), Math.min(1f, fill.b + 0.5f), 1f));
        p.drawPixel(2, 2);
        p.drawPixel(3, 2);
        Texture t = register(new Texture(p));
        p.dispose();
        return new TextureRegionDrawable(t);
    }

    // ── Font loading ─────────────────────────────────────────────────

    private void loadFonts() {
        try {
            FreeTypeFontGenerator gen = new FreeTypeFontGenerator(Gdx.files.internal("fonts/intellivision.ttf"));
            fontHeader = gen.generateFont(param(20, COL_TEXT_HEADER, 0.9f));
            fontBody = gen.generateFont(param(14, COL_TEXT_BODY, 0.6f));
            fontSmall = gen.generateFont(param(11, COL_TEXT_BODY, 0.5f));
            fontAttributes = gen.generateFont(param(13, COL_TEXT_BODY, 0.5f));
            gen.dispose();
        } catch (Exception e) {
            Gdx.app.error("InventorySkin", "intellivision.ttf not found — using default BitmapFont", e);
            fontHeader = new BitmapFont();
            fontHeader.getData().setScale(2.2f);
            fontBody = new BitmapFont();
            fontBody.getData().setScale(1.6f);
            fontSmall = new BitmapFont();
            fontSmall.getData().setScale(1.1f);
            fontAttributes = new BitmapFont();
            fontAttributes.getData().setScale(1.6f);
        }
    }

    private FreeTypeFontParameter param(int size, Color color, float borderW) {
        FreeTypeFontParameter p = new FreeTypeFontParameter();
        p.size = size;
        p.color = new Color(color);
        p.borderWidth = borderW;
        p.borderColor = COL_BORDER_DARK;
        p.minFilter = Texture.TextureFilter.Nearest;
        p.magFilter = Texture.TextureFilter.Nearest;
        return p;
    }

    // ── Helpers ──────────────────────────────────────────────────────

    private Texture register(Texture t) {
        owned.add(t);
        return t;
    }

    public void dispose() {
        for (Texture t : owned)
            if (t != null)
                t.dispose();
        if (fontHeader != null)
            fontHeader.dispose();
        if (fontBody != null)
            fontBody.dispose();
        if (fontSmall != null)
            fontSmall.dispose();
    }

    // ── Getters ──────────────────────────────────────────────────────

    public Texture getWhitePixel() {
        return whitePixel;
    }

    public Drawable getNormalSlotDrawable() {
        return slotNormal;
    }

    public Drawable getEquipSlotDrawable() {
        return slotEquip;
    }

    public Drawable getHighlightValidDrawable() {
        return slotHighlightValid;
    }

    public Drawable getHighlightInvalidDrawable() {
        return slotHighlightInvalid;
    }

    public Drawable getLeftPageDrawable() {
        return leftPageDrawable;
    }

    public Drawable getRightPageDrawable() {
        return rightPageDrawable;
    }

    public Drawable getSpineDrawable() {
        return spineDrawable;
    }

    public Drawable getPortraitFrameDrawable() {
        return portraitFrameDrawable;
    }

    public Drawable getRuneCircleDrawable() {
        return runeCircleDrawable;
    }

    public Drawable getPanelBoxDrawable() {
        return panelBoxDrawable;
    }

    public Drawable getIconRed() {
        return iconRed;
    }

    public Drawable getIconBlue() {
        return iconBlue;
    }

    public Drawable getIconGold() {
        return iconGold;
    }

    public Drawable getIconGreen() {
        return iconGreen;
    }

    public Drawable getIconOrange() {
        return iconOrange;
    }

    public Drawable getIconPurple() {
        return iconPurple;
    }

    public Drawable getIconGray() {
        return iconGray;
    }

    public BitmapFont getFontHeader() {
        return fontHeader;
    }

    public BitmapFont getFontBody() {
        return fontBody;
    }

    public BitmapFont getFontSmall() {
        return fontSmall;
    }

    public BitmapFont getFontAttributes() {
        return fontAttributes;
    }
}
