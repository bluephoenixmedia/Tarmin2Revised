package com.bpm.minotaur.rendering;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.NinePatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.scenes.scene2d.utils.NinePatchDrawable;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Disposable;

import java.util.ArrayList;
import java.util.List;

/**
 * Programmatically generates textures, drawables, and fonts for the
 * Carved Stone & Wrought Iron HUD dashboard. Zero external asset files needed.
 */
public class HudSkin implements Disposable {

    // Palette: Slate Stone & Iron
    public static final Color COL_DASHBOARD_BG     = Color.valueOf("14171AEE");
    public static final Color COL_PANEL_BG         = Color.valueOf("101316F0");
    public static final Color COL_STONE_HIGHLIGHT  = Color.valueOf("48535D");
    public static final Color COL_STONE_MID        = Color.valueOf("2D343B");
    public static final Color COL_STONE_DARK       = Color.valueOf("181C20");
    public static final Color COL_SHADOW_DEEP      = Color.valueOf("090B0D");

    // Palette: Metals & Accents
    public static final Color COL_GOLD_BRIGHT      = Color.valueOf("FFD700");
    public static final Color COL_GOLD_ANTIQUE     = Color.valueOf("D4AF37");
    public static final Color COL_GOLD_MUTED       = Color.valueOf("997D32");
    public static final Color COL_IRON_RIVET       = Color.valueOf("58626C");

    // Palette: Vitals & Meters
    public static final Color COL_HP_RED           = Color.valueOf("D92525");
    public static final Color COL_HP_CRITICAL      = Color.valueOf("FF1A1A");
    public static final Color COL_MP_BLUE          = Color.valueOf("2566D9");
    public static final Color COL_EXP_AMBER        = Color.valueOf("D9A225");
    public static final Color COL_FOOD_GREEN       = Color.valueOf("2CB84B");
    public static final Color COL_WATER_CYAN       = Color.valueOf("259BD9");
    public static final Color COL_TEMP_ORANGE      = Color.valueOf("D97325");

    private final List<Texture> ownedTextures = new ArrayList<>();

    // Drawables
    private Drawable dashboardBg;
    private Drawable panelBg;
    private Drawable dividerIron;
    private Drawable slotRecessed;
    private Drawable slotActive;
    private Drawable slotHand;
    private Drawable portraitCameo;
    private Drawable gaugeTrack;
    private Drawable tooltipBg;

    // Textures for direct rendering or bars
    private Texture whitePixel;
    private Texture compassDial;

    // Fonts
    private BitmapFont fontMain;
    private BitmapFont fontSmall;
    private BitmapFont fontLog;
    private BitmapFont fontHeader;
    private BitmapFont fontCompass;

    public HudSkin() {
        buildWhitePixel();
        buildDashboardBg();
        buildPanelDrawables();
        buildDivider();
        buildSlotDrawables();
        buildPortraitCameo();
        buildGaugeTrack();
        buildTooltipBg();
        buildCompassDial();
        loadFonts();
    }

    private Texture register(Texture t) {
        ownedTextures.add(t);
        return t;
    }

    private void buildWhitePixel() {
        Pixmap p = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        p.setColor(Color.WHITE);
        p.fill();
        whitePixel = register(new Texture(p));
        p.dispose();
    }

    /** 1920x200 Stone Dashboard background with carved 10px top and bottom bevels. */
    private void buildDashboardBg() {
        int w = 64;
        int h = 200;
        Pixmap p = new Pixmap(w, h, Pixmap.Format.RGBA8888);

        // Body slate
        p.setColor(COL_DASHBOARD_BG);
        p.fill();

        // Top Stone Bevel (10px)
        p.setColor(COL_STONE_HIGHLIGHT);
        p.drawLine(0, 0, w - 1, 0);
        p.drawLine(0, 1, w - 1, 1);
        p.setColor(COL_STONE_MID);
        p.drawLine(0, 2, w - 1, 2);
        p.drawLine(0, 3, w - 1, 3);
        p.setColor(COL_STONE_DARK);
        p.drawLine(0, 4, w - 1, 4);
        p.drawLine(0, 5, w - 1, 5);
        p.setColor(COL_SHADOW_DEEP);
        p.drawLine(0, 6, w - 1, 6);
        p.drawLine(0, 7, w - 1, 7);
        p.setColor(new Color(0.05f, 0.06f, 0.07f, 0.5f));
        p.drawLine(0, 8, w - 1, 8);
        p.drawLine(0, 9, w - 1, 9);

        // Bottom Stone Bevel (10px)
        int bStart = h - 10;
        p.setColor(COL_SHADOW_DEEP);
        p.drawLine(0, bStart, w - 1, bStart);
        p.drawLine(0, bStart + 1, w - 1, bStart + 1);
        p.setColor(COL_STONE_DARK);
        p.drawLine(0, bStart + 2, w - 1, bStart + 2);
        p.drawLine(0, bStart + 3, w - 1, bStart + 3);
        p.setColor(COL_STONE_MID);
        p.drawLine(0, bStart + 4, w - 1, bStart + 4);
        p.drawLine(0, bStart + 5, w - 1, bStart + 5);
        p.setColor(COL_STONE_HIGHLIGHT);
        p.drawLine(0, bStart + 6, w - 1, bStart + 6);
        p.drawLine(0, h - 1, w - 1, h - 1);

        Texture tex = register(new Texture(p));
        p.dispose();

        // 9-slice: preserves top 10px and bottom 10px bevels across any width
        NinePatch np = new NinePatch(tex, 4, 4, 10, 10);
        dashboardBg = new NinePatchDrawable(np);
    }

    /** Recessed sub-panel container backing with subtle border and inner shadow. */
    private void buildPanelDrawables() {
        int sz = 32;
        Pixmap p = new Pixmap(sz, sz, Pixmap.Format.RGBA8888);
        p.setColor(COL_PANEL_BG);
        p.fill();

        // Outer border
        p.setColor(COL_STONE_MID);
        p.drawRectangle(0, 0, sz, sz);

        // Inset shadow (Top & Left)
        p.setColor(COL_SHADOW_DEEP);
        p.drawLine(1, 1, sz - 2, 1);
        p.drawLine(1, 1, 1, sz - 2);

        // Bottom & Right rim highlight
        p.setColor(new Color(0.3f, 0.35f, 0.4f, 0.4f));
        p.drawLine(1, sz - 2, sz - 2, sz - 2);
        p.drawLine(sz - 2, 1, sz - 2, sz - 2);

        Texture tex = register(new Texture(p));
        p.dispose();

        NinePatch np = new NinePatch(tex, 4, 4, 4, 4);
        panelBg = new NinePatchDrawable(np);
    }

    /** Vertical Wrought Iron Divider bar (6px wide) with brass rivets. */
    private void buildDivider() {
        int w = 6;
        int h = 180;
        Pixmap p = new Pixmap(w, h, Pixmap.Format.RGBA8888);

        p.setColor(COL_SHADOW_DEEP);
        p.fill();

        // Vertical highlight stripe in center
        p.setColor(COL_STONE_HIGHLIGHT);
        p.drawLine(2, 0, 2, h - 1);
        p.setColor(COL_STONE_MID);
        p.drawLine(3, 0, 3, h - 1);

        // Draw 3 brass rivets
        int[] rivetYs = { 20, h / 2, h - 20 };
        for (int ry : rivetYs) {
            p.setColor(COL_GOLD_ANTIQUE);
            p.fillRectangle(1, ry - 2, 4, 4);
            p.setColor(COL_GOLD_BRIGHT);
            p.drawPixel(2, ry - 1);
            p.setColor(COL_SHADOW_DEEP);
            p.drawPixel(3, ry + 1);
        }

        Texture tex = register(new Texture(p));
        p.dispose();
        dividerIron = new TextureRegionDrawable(new TextureRegion(tex));
    }

    /** 56x56 Recessed Stone Slot with 3D drop-shadow (for backpack and hands). */
    private void buildSlotDrawables() {
        int sz = 56;
        Pixmap p = new Pixmap(sz, sz, Pixmap.Format.RGBA8888);

        // Fill deep recessed dark
        p.setColor(new Color(0.06f, 0.07f, 0.08f, 0.95f));
        p.fill();

        // Outer beveled frame
        p.setColor(COL_STONE_MID);
        p.drawRectangle(0, 0, sz, sz);

        // Top & Left heavy drop shadow (inset effect)
        p.setColor(COL_SHADOW_DEEP);
        p.drawLine(1, 1, sz - 2, 1);
        p.drawLine(1, 2, sz - 3, 2);
        p.drawLine(1, 1, 1, sz - 2);
        p.drawLine(2, 1, 2, sz - 3);

        // Bottom & Right rim highlight
        p.setColor(COL_STONE_HIGHLIGHT);
        p.drawLine(1, sz - 2, sz - 2, sz - 2);
        p.drawLine(sz - 2, 1, sz - 2, sz - 2);

        Texture tex = register(new Texture(p));
        p.dispose();

        NinePatch np = new NinePatch(tex, 4, 4, 4, 4);
        slotRecessed = new NinePatchDrawable(np);

        // Active slot (pulsing gold border)
        Pixmap pa = new Pixmap(sz, sz, Pixmap.Format.RGBA8888);
        pa.setColor(new Color(0.12f, 0.14f, 0.16f, 0.95f));
        pa.fill();
        pa.setColor(COL_GOLD_ANTIQUE);
        pa.drawRectangle(0, 0, sz, sz);
        pa.drawRectangle(1, 1, sz - 2, sz - 2);
        pa.setColor(COL_GOLD_BRIGHT);
        pa.drawPixel(0, 0); pa.drawPixel(sz - 1, 0);
        pa.drawPixel(0, sz - 1); pa.drawPixel(sz - 1, sz - 1);

        Texture texA = register(new Texture(pa));
        pa.dispose();
        NinePatch npA = new NinePatch(texA, 4, 4, 4, 4);
        slotActive = new NinePatchDrawable(npA);

        // Hand slot (metallic rimmed)
        Pixmap ph = new Pixmap(sz, sz, Pixmap.Format.RGBA8888);
        ph.setColor(new Color(0.08f, 0.09f, 0.11f, 0.95f));
        ph.fill();
        ph.setColor(COL_STONE_HIGHLIGHT);
        ph.drawRectangle(0, 0, sz, sz);
        ph.setColor(COL_SHADOW_DEEP);
        ph.drawLine(1, 1, sz - 2, 1);
        ph.drawLine(1, 1, 1, sz - 2);
        ph.setColor(COL_GOLD_MUTED);
        ph.drawRectangle(2, 2, sz - 4, sz - 4);

        Texture texH = register(new Texture(ph));
        ph.dispose();
        NinePatch npH = new NinePatch(texH, 4, 4, 4, 4);
        slotHand = new NinePatchDrawable(npH);
    }

    /** Cameo Stone Frame for Portrait (104x104 px). */
    private void buildPortraitCameo() {
        int sz = 104;
        Pixmap p = new Pixmap(sz, sz, Pixmap.Format.RGBA8888);

        p.setColor(new Color(0.07f, 0.08f, 0.10f, 0.95f));
        p.fill();

        // Multi-tier stone and brass frame
        p.setColor(COL_STONE_HIGHLIGHT);
        p.drawRectangle(0, 0, sz, sz);
        p.setColor(COL_STONE_MID);
        p.drawRectangle(1, 1, sz - 2, sz - 2);
        p.setColor(COL_GOLD_ANTIQUE);
        p.drawRectangle(2, 2, sz - 4, sz - 4);
        p.setColor(COL_SHADOW_DEEP);
        p.drawRectangle(3, 3, sz - 6, sz - 6);

        // Gold corner accents
        int cs = 6;
        p.setColor(COL_GOLD_BRIGHT);
        p.fillRectangle(0, 0, cs, 2); p.fillRectangle(0, 0, 2, cs);
        p.fillRectangle(sz - cs, 0, cs, 2); p.fillRectangle(sz - 2, 0, 2, cs);
        p.fillRectangle(0, sz - 2, cs, 2); p.fillRectangle(0, sz - cs, 2, cs);
        p.fillRectangle(sz - cs, sz - 2, cs, 2); p.fillRectangle(sz - 2, sz - cs, 2, cs);

        Texture tex = register(new Texture(p));
        p.dispose();
        NinePatch np = new NinePatch(tex, 8, 8, 8, 8);
        portraitCameo = new NinePatchDrawable(np);
    }

    /** Dark recessed track for progress/stat bars. */
    private void buildGaugeTrack() {
        int w = 32;
        int h = 24;
        Pixmap p = new Pixmap(w, h, Pixmap.Format.RGBA8888);

        p.setColor(new Color(0.06f, 0.07f, 0.08f, 0.95f));
        p.fill();

        p.setColor(COL_STONE_MID);
        p.drawRectangle(0, 0, w, h);

        p.setColor(COL_SHADOW_DEEP);
        p.drawLine(1, 1, w - 2, 1);
        p.drawLine(1, 1, 1, h - 2);

        Texture tex = register(new Texture(p));
        p.dispose();
        NinePatch np = new NinePatch(tex, 3, 3, 3, 3);
        gaugeTrack = new NinePatchDrawable(np);
    }

    /** Floating dark slate tooltip card. */
    private void buildTooltipBg() {
        int sz = 32;
        Pixmap p = new Pixmap(sz, sz, Pixmap.Format.RGBA8888);
        p.setColor(new Color(0.08f, 0.10f, 0.12f, 0.96f));
        p.fill();
        p.setColor(COL_GOLD_ANTIQUE);
        p.drawRectangle(0, 0, sz, sz);
        p.setColor(COL_SHADOW_DEEP);
        p.drawRectangle(1, 1, sz - 2, sz - 2);

        Texture tex = register(new Texture(p));
        p.dispose();
        NinePatch np = new NinePatch(tex, 4, 4, 4, 4);
        tooltipBg = new NinePatchDrawable(np);
    }

    /** Circular Compass Medallion Dial (40x40 px). */
    private void buildCompassDial() {
        int sz = 40;
        Pixmap p = new Pixmap(sz, sz, Pixmap.Format.RGBA8888);
        p.setColor(new Color(0, 0, 0, 0));
        p.fill();

        int cx = sz / 2;
        int cy = sz / 2;
        int r = sz / 2 - 2;

        // Dial base
        p.setColor(COL_STONE_DARK);
        p.fillCircle(cx, cy, r);

        // Brass ring
        p.setColor(COL_GOLD_ANTIQUE);
        p.drawCircle(cx, cy, r);
        p.drawCircle(cx, cy, r - 1);

        // Cardinal tick marks
        p.setColor(COL_GOLD_BRIGHT);
        p.drawLine(cx, 2, cx, 5);          // N
        p.drawLine(cx, sz - 3, cx, sz - 6); // S
        p.drawLine(2, cy, 5, cy);          // W
        p.drawLine(sz - 3, cy, sz - 6, cy); // E

        compassDial = register(new Texture(p));
        p.dispose();
    }

    private void loadFonts() {
        FreeTypeFontGenerator gen = new FreeTypeFontGenerator(Gdx.files.internal("fonts/intellivision.ttf"));
        FreeTypeFontGenerator.FreeTypeFontParameter param = new FreeTypeFontGenerator.FreeTypeFontParameter();
        param.minFilter = Texture.TextureFilter.Nearest;
        param.magFilter = Texture.TextureFilter.Nearest;

        // Main stat font (size 18)
        param.size = 18;
        param.color = Color.WHITE;
        fontMain = gen.generateFont(param);

        // Small badge font (size 14) for hotkeys [1]-[6] and tooltips
        param.size = 14;
        param.color = COL_GOLD_BRIGHT;
        fontSmall = gen.generateFont(param);

        // Log font (size 17) for action chronicle
        param.size = 17;
        param.color = Color.WHITE;
        fontLog = gen.generateFont(param);

        // Header font (size 19, Gold)
        param.size = 19;
        param.color = COL_GOLD_BRIGHT;
        fontHeader = gen.generateFont(param);

        // Compass heading font (size 22, bold gold)
        param.size = 22;
        param.color = COL_GOLD_BRIGHT;
        fontCompass = gen.generateFont(param);

        gen.dispose();
    }

    // Getters
    public Drawable getDashboardBg() { return dashboardBg; }
    public Drawable getPanelBg() { return panelBg; }
    public Drawable getDividerIron() { return dividerIron; }
    public Drawable getSlotRecessed() { return slotRecessed; }
    public Drawable getSlotActive() { return slotActive; }
    public Drawable getSlotHand() { return slotHand; }
    public Drawable getPortraitCameo() { return portraitCameo; }
    public Drawable getGaugeTrack() { return gaugeTrack; }
    public Drawable getTooltipBg() { return tooltipBg; }
    public Texture getWhitePixel() { return whitePixel; }
    public Texture getCompassDial() { return compassDial; }

    public BitmapFont getFontMain() { return fontMain; }
    public BitmapFont getFontSmall() { return fontSmall; }
    public BitmapFont getFontLog() { return fontLog; }
    public BitmapFont getFontHeader() { return fontHeader; }
    public BitmapFont getFontCompass() { return fontCompass; }

    @Override
    public void dispose() {
        for (Texture t : ownedTextures) {
            t.dispose();
        }
        ownedTextures.clear();

        if (fontMain != null) fontMain.dispose();
        if (fontSmall != null) fontSmall.dispose();
        if (fontLog != null) fontLog.dispose();
        if (fontHeader != null) fontHeader.dispose();
        if (fontCompass != null) fontCompass.dispose();
    }
}
