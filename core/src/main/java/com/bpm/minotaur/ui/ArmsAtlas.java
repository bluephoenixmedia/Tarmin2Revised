package com.bpm.minotaur.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Disposable;
import com.bpm.minotaur.gamedata.history.Doctrine;
import com.bpm.minotaur.gamedata.history.DoctrineCatalog;
import com.bpm.minotaur.gamedata.history.HistoryWorld;
import com.bpm.minotaur.gamedata.history.arms.ArmsRaster;
import com.bpm.minotaur.gamedata.history.arms.ChargeArt;
import com.bpm.minotaur.gamedata.history.arms.HouseArms;

import java.util.HashMap;
import java.util.Map;

/**
 * Each house's Arms as textures, drawn the first time they are asked for at a size and kept
 * (Living War A43, A45). Create one per screen or renderer and dispose it with its owner.
 */
public final class ArmsAtlas implements Disposable {

    private final HistoryWorld world;
    private final DoctrineCatalog catalog;
    private final Map<Long, TextureRegion> drawn = new HashMap<>();
    private final Map<String, ArmsRaster.Mask> masks = new HashMap<>();

    public ArmsAtlas(HistoryWorld world, DoctrineCatalog catalog) {
        this.world = world;
        this.catalog = catalog;
    }

    /** The house's shield, {@code size} canvas units wide; null for no such house. */
    public TextureRegion shield(int houseId, int size) {
        return region(houseId, size, false);
    }

    /** The house's banner, for its captains; null for no such house. */
    public TextureRegion banner(int houseId, int size) {
        return region(houseId, size, true);
    }

    private TextureRegion region(int houseId, int size, boolean banner) {
        long key = (long) houseId << 32 | (long) size << 1 | (banner ? 1 : 0);
        TextureRegion r = drawn.get(key);
        if (r != null) return r;
        HouseArms arms = HouseArms.of(world, houseId);
        if (arms == null) return null;
        Doctrine d = catalog.get(arms.doctrineId);
        int border = rgb(d == null ? null : d.primaryColor);
        int secondary = rgb(d == null ? null : d.secondaryColor);
        ArmsRaster.Mask mask = mask(arms.charge);
        r = upload(banner ? ArmsRaster.banner(arms, border, secondary, mask, size) : ArmsRaster.shield(arms, border, mask, size));
        drawn.put(key, r);
        return r;
    }

    private static int rgb(String hex) {
        if (hex == null) return 0x3A3A3A;
        return Color.rgba8888(Color.valueOf(hex)) >>> 8;
    }

    /** A charge with no art leaves its field bare, rather than stopping the game. */
    private ArmsRaster.Mask mask(String charge) {
        if (masks.containsKey(charge)) return masks.get(charge);
        ArmsRaster.Mask m = null;
        String path = ChargeArt.pathOf(charge);
        FileHandle file = path == null ? null : Gdx.files.internal(path);
        if (file != null && file.exists()) {
            Pixmap p = new Pixmap(file);
            byte[] coverage = new byte[p.getWidth() * p.getWidth()];
            for (int y = 0; y < p.getWidth(); y++) {
                for (int x = 0; x < p.getWidth(); x++) coverage[y * p.getWidth() + x] = (byte) (p.getPixel(x, y) & 0xFF);
            }
            m = new ArmsRaster.Mask(p.getWidth(), coverage);
            p.dispose();
        }
        masks.put(charge, m);
        return m;
    }

    private static TextureRegion upload(ArmsRaster.Image image) {
        Pixmap p = new Pixmap(image.width, image.height, Pixmap.Format.RGBA8888);
        for (int y = 0; y < image.height; y++) {
            for (int x = 0; x < image.width; x++) p.drawPixel(x, y, image.rgba[y * image.width + x]);
        }
        Texture t = new Texture(p);
        t.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        p.dispose();
        return new TextureRegion(t);
    }

    @Override
    public void dispose() {
        for (TextureRegion r : drawn.values()) r.getTexture().dispose();
        drawn.clear();
    }
}
