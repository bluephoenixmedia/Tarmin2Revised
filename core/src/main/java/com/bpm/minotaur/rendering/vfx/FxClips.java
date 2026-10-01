package com.bpm.minotaur.rendering.vfx;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;

import java.util.HashMap;
import java.util.Map;

/**
 * The frame-sequence animations (blood spurts, the alert, and the spell effects to come).
 *
 * <p>Every clip lives in one packed atlas described by {@code assets/data/fx.json}, built by
 * {@code tools/build_fx_atlas.py}. Parsing is plain data and needs no graphics; only
 * {@link #getFrame} touches the GPU, and it does so lazily.
 */
public final class FxClips {

    /** One animation: where each frame sits in the atlas, how long it lasts, how it blends. */
    public static final class Clip {
        public final String id;
        public final float duration;
        public final boolean additive;
        public final int frameWidth;
        public final int frameHeight;
        private final int[][] frames;

        Clip(String id, int[][] frames, float duration, boolean additive, int frameWidth, int frameHeight) {
            this.id = id;
            this.frames = frames;
            this.duration = duration;
            this.additive = additive;
            this.frameWidth = frameWidth;
            this.frameHeight = frameHeight;
        }

        public int frameCount() {
            return frames.length;
        }

        /** The frame's rectangle in the atlas: {x, y, width, height}. */
        public int[] frame(int index) {
            return frames[index];
        }

        /** The frame to show at a point (0 to 1) through the clip; the last frame is held once finished. */
        public int frameIndexAt(float progress) {
            int n = frames.length;
            int index = (int) (progress * n);
            return Math.max(0, Math.min(n - 1, index));
        }
    }

    private final String atlasPath;
    private final Map<String, Clip> clips = new HashMap<>();
    private final Map<String, TextureRegion[]> regions = new HashMap<>();
    private Texture atlas;
    private boolean atlasFailed;

    private FxClips(String atlasPath) {
        this.atlasPath = atlasPath;
    }

    public static FxClips parse(JsonValue root) {
        FxClips fx = new FxClips(root.getString("atlas"));
        JsonValue all = root.get("clips");
        for (JsonValue c = all == null ? null : all.child; c != null; c = c.next) {
            JsonValue rects = c.get("frames");
            int[][] frames = new int[rects.size][];
            int i = 0;
            for (JsonValue r = rects.child; r != null; r = r.next) {
                frames[i++] = new int[] { r.getInt(0), r.getInt(1), r.getInt(2), r.getInt(3) };
            }
            fx.clips.put(c.name, new Clip(c.name, frames, c.getFloat("duration", 0.5f),
                    c.getBoolean("additive", false), c.getInt("frameWidth", frames[0][2]),
                    c.getInt("frameHeight", frames[0][3])));
        }
        return fx;
    }

    public String atlasPath() {
        return atlasPath;
    }

    public boolean has(String id) {
        return clips.containsKey(id);
    }

    public Clip get(String id) {
        return clips.get(id);
    }

    // --- runtime (needs a graphics context) -------------------------------------------------

    private static FxClips instance;

    public static synchronized FxClips getInstance() {
        if (instance == null) {
            FxClips loaded = loadOrEmpty();
            if (loaded.clips.isEmpty()) {
                return loaded; // not cached, so a later attempt with a working file system can still succeed
            }
            instance = loaded;
        }
        return instance;
    }

    /** The shipped clips, or an empty library if the data cannot be read (no file system, bad file). */
    static FxClips loadOrEmpty() {
        try {
            if (Gdx.files != null) {
                return parse(new JsonReader().parse(Gdx.files.internal("data/fx.json")));
            }
        } catch (Exception e) {
            if (Gdx.app != null) {
                Gdx.app.error("FxClips", "Cannot read data/fx.json; effects are disabled", e);
            }
        }
        return new FxClips("");
    }

    /** The frame to draw, or null if the clip or the atlas is unavailable (an effect must never crash a hit). */
    public TextureRegion getFrame(String id, float progress) {
        Clip clip = clips.get(id);
        if (clip == null) {
            return null;
        }
        TextureRegion[] frames = regions.get(id);
        if (frames == null && !atlasFailed) {
            frames = slice(clip);
            if (frames == null) {
                return null;
            }
            regions.put(id, frames);
        }
        return frames[clip.frameIndexAt(progress)];
    }

    private TextureRegion[] slice(Clip clip) {
        try {
            if (atlas == null) {
                atlas = new Texture(Gdx.files.internal(atlasPath));
                // Pixel art: keep the edges crisp when a 32px burst is drawn a metre wide.
                atlas.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
            }
            TextureRegion[] out = new TextureRegion[clip.frameCount()];
            for (int i = 0; i < out.length; i++) {
                int[] r = clip.frame(i);
                out[i] = new TextureRegion(atlas, r[0], r[1], r[2], r[3]);
            }
            return out;
        } catch (Exception e) {
            atlasFailed = true; // say so once, not on every frame of every effect
            if (Gdx.app != null) {
                Gdx.app.error("FxClips", "Cannot load the effects atlas " + atlasPath, e);
            }
            return null;
        }
    }

    /** Releases the atlas texture; the library loads it again if an effect is played afterwards. */
    public static synchronized void disposeShared() {
        if (instance != null) {
            if (instance.atlas != null) {
                instance.atlas.dispose();
            }
            instance = null;
        }
    }
}
