package com.bpm.minotaur.rendering;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.TextureData;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.bpm.minotaur.gamedata.gore.SilhouetteMask;
import com.bpm.minotaur.gamedata.monster.Monster;

import java.util.HashMap;
import java.util.Map;

/**
 * Reads a monster sprite's silhouette once and remembers it, so wounds can be placed on the body.
 *
 * <p>The sprite is decoded from its texture data, which is not free, so each distinct sprite is
 * read once. Anything that cannot be read falls back to the whole sprite, which is no worse than
 * the old placement.
 */
public final class MonsterSilhouettes {

    private static final int MAX_CACHED = 256;
    private static final Map<String, SilhouetteMask> CACHE = new HashMap<>();

    private MonsterSilhouettes() {
    }

    public static SilhouetteMask forMonster(Monster monster) {
        if (monster == null || Gdx.gl == null) {
            return SilhouetteMask.full();
        }
        TextureRegion region = monster.getTextureRegion();
        if (region == null && monster.getTexture() != null) {
            region = new TextureRegion(monster.getTexture());
        }
        if (region == null || region.getTexture() == null) {
            return SilhouetteMask.full();
        }

        String key = System.identityHashCode(region.getTexture()) + ":" + region.getRegionX() + ","
                + region.getRegionY() + "," + region.getRegionWidth() + "," + region.getRegionHeight();
        SilhouetteMask cached = CACHE.get(key);
        if (cached != null) {
            return cached;
        }

        SilhouetteMask mask = read(region);
        if (CACHE.size() >= MAX_CACHED) {
            CACHE.clear();
        }
        CACHE.put(key, mask);
        return mask;
    }

    private static SilhouetteMask read(TextureRegion region) {
        try {
            TextureData data = region.getTexture().getTextureData();
            if (!data.isPrepared()) {
                data.prepare();
            }
            Pixmap pixmap = data.consumePixmap();
            try {
                return SilhouetteMask.fromPixmap(pixmap, region.getRegionX(), region.getRegionY(),
                        region.getRegionWidth(), region.getRegionHeight());
            } finally {
                if (data.disposePixmap()) {
                    pixmap.dispose();
                }
            }
        } catch (Throwable unreadable) {
            return SilhouetteMask.full();
        }
    }
}
