package com.bpm.minotaur.rendering;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.ScreenUtils;
import com.bpm.minotaur.gamedata.gore.ProceduralDecalGenerator;
import com.bpm.minotaur.gamedata.gore.WoundDecal;
import com.bpm.minotaur.gamedata.monster.Monster;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Manages offscreen FBO dynamic texture composition for wounded monsters.
 * Clamps wound decals directly to the monster's opaque sprite silhouette using
 * GL_DST_ALPHA masking, with zero memory leakage and lightweight texture baking.
 */
public class MonsterDecalCompositor implements Disposable {

    private static MonsterDecalCompositor instance;
    private static final int FBO_SIZE = 256;
    private static final int MAX_ACTIVE_FBOS = 3;

    private static class FboSlot {
        FrameBuffer fbo;
        TextureRegion fboRegion;
        boolean inUse = false;
        Monster assignedMonster = null;
        boolean dirty = true;
    }

    private final List<FboSlot> fboPool = new ArrayList<>(MAX_ACTIVE_FBOS);
    private final Map<Monster, FboSlot> activeAssignments = new HashMap<>();
    private SpriteBatch batch;
    private boolean initialized = false;

    public static synchronized MonsterDecalCompositor getInstance() {
        if (instance == null) {
            instance = new MonsterDecalCompositor();
        }
        return instance;
    }

    public MonsterDecalCompositor() {
        initGlResources();
    }

    private void initGlResources() {
        if (Gdx.gl == null) return;
        try {
            batch = new SpriteBatch();
            for (int i = 0; i < MAX_ACTIVE_FBOS; i++) {
                FboSlot slot = new FboSlot();
                slot.fbo = new FrameBuffer(Pixmap.Format.RGBA8888, FBO_SIZE, FBO_SIZE, false);
                slot.fboRegion = new TextureRegion(slot.fbo.getColorBufferTexture());
                slot.fboRegion.flip(false, true); // OpenGL FBO texture Y-inversion
                fboPool.add(slot);
            }
            initialized = true;
        } catch (Exception e) {
            Gdx.app.error("MonsterDecalCompositor", "Failed to initialize FBO pool: " + e.getMessage());
            initialized = false;
        }
    }

    public void addWound(Monster monster, WoundDecal decal) {
        if (monster == null || decal == null) return;
        monster.addWoundDecal(decal);
        FboSlot slot = activeAssignments.get(monster);
        if (slot != null) {
            slot.dirty = true;
        }
    }

    public TextureRegion getCompositeRegion(Monster monster) {
        if (monster == null) return null;

        if (monster.getWoundDecals().isEmpty()) {
            if (monster.getBakedWoundTexture() != null) {
                return new TextureRegion(monster.getBakedWoundTexture());
            }
            return monster.getTextureRegion() != null
                    ? monster.getTextureRegion()
                    : (monster.getTexture() != null ? new TextureRegion(monster.getTexture()) : null);
        }

        if (!initialized || Gdx.gl == null) {
            return monster.getTextureRegion() != null
                    ? monster.getTextureRegion()
                    : (monster.getTexture() != null ? new TextureRegion(monster.getTexture()) : null);
        }

        FboSlot slot = activeAssignments.get(monster);
        if (slot == null) {
            slot = acquireSlot(monster);
        }

        if (slot != null && slot.dirty) {
            renderWoundsToFbo(monster, slot);
            slot.dirty = false;
        }

        return (slot != null) ? slot.fboRegion : monster.getTextureRegion();
    }

    private FboSlot acquireSlot(Monster monster) {
        for (FboSlot s : fboPool) {
            if (!s.inUse) {
                s.inUse = true;
                s.assignedMonster = monster;
                s.dirty = true;
                activeAssignments.put(monster, s);
                return s;
            }
        }
        // If pool is full, evict oldest
        if (!fboPool.isEmpty()) {
            FboSlot oldest = fboPool.get(0);
            if (oldest.assignedMonster != null) {
                releaseMonster(oldest.assignedMonster, true);
            }
            oldest.inUse = true;
            oldest.assignedMonster = monster;
            oldest.dirty = true;
            activeAssignments.put(monster, oldest);
            return oldest;
        }
        return null;
    }

    private void renderWoundsToFbo(Monster monster, FboSlot slot) {
        TextureRegion baseRegion = monster.getTextureRegion() != null
                ? monster.getTextureRegion()
                : (monster.getTexture() != null ? new TextureRegion(monster.getTexture()) : null);

        if (baseRegion == null) return;

        // Remember previous viewport
        int prevVpW = Gdx.graphics.getWidth();
        int prevVpH = Gdx.graphics.getHeight();

        slot.fbo.begin();
        Gdx.gl.glClearColor(0f, 0f, 0f, 0f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        batch.getProjectionMatrix().setToOrtho2D(0, 0, FBO_SIZE, FBO_SIZE);
        batch.begin();

        // 1. Draw pristine base monster sprite (establishes silhouette alpha mask)
        batch.setColor(Color.WHITE);
        batch.draw(baseRegion, 0, 0, FBO_SIZE, FBO_SIZE);
        batch.flush();

        // 2. Clamp wounds strictly to destination alpha
        batch.setBlendFunctionSeparate(
                GL20.GL_DST_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA,
                GL20.GL_ZERO, GL20.GL_ONE
        );

        ProceduralDecalGenerator gen = ProceduralDecalGenerator.getInstance();

        for (WoundDecal w : monster.getWoundDecals()) {
            TextureRegion brush = (w.customRegion != null) ? w.customRegion : gen.getRegionForType(w.type);
            if (brush == null) continue;

            float cx = w.u * FBO_SIZE;
            float cy = w.v * FBO_SIZE;
            float dw = w.length * FBO_SIZE;
            float dh = w.width * FBO_SIZE;

            batch.setColor(w.color);
            batch.draw(
                    brush,
                    cx - dw * 0.5f, cy - dh * 0.5f,
                    dw * 0.5f, dh * 0.5f,
                    dw, dh,
                    1f, 1f,
                    w.angle * MathUtils.radiansToDegrees
            );
        }

        batch.flush();
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        batch.end();

        slot.fbo.end();

        // Restore viewport
        Gdx.gl.glViewport(0, 0, prevVpW, prevVpH);
    }

    public void releaseMonster(Monster monster, boolean bakeIfAlive) {
        if (monster == null) return;
        FboSlot slot = activeAssignments.remove(monster);
        if (slot != null) {
            if (bakeIfAlive && monster.getCurrentHP() > 0 && Gdx.gl != null) {
                try {
                    slot.fbo.begin();
                    Pixmap pm = ScreenUtils.getFrameBufferPixmap(0, 0, FBO_SIZE, FBO_SIZE);
                    slot.fbo.end();
                    if (pm != null) {
                        Texture baked = new Texture(pm);
                        baked.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
                        monster.setBakedWoundTexture(baked);
                        pm.dispose();
                    }
                } catch (Exception ignored) {
                }
            }
            slot.inUse = false;
            slot.assignedMonster = null;
            slot.dirty = false;
        }
    }

    public void clearAll() {
        for (FboSlot s : fboPool) {
            s.inUse = false;
            s.assignedMonster = null;
            s.dirty = false;
        }
        activeAssignments.clear();
    }

    @Override
    public void dispose() {
        clearAll();
        for (FboSlot s : fboPool) {
            if (s.fbo != null) {
                s.fbo.dispose();
            }
        }
        fboPool.clear();
        if (batch != null) {
            batch.dispose();
        }
        initialized = false;
    }
}
