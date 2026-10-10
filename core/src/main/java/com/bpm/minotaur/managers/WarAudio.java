package com.bpm.minotaur.managers;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.math.MathUtils;
import com.bpm.minotaur.gamedata.history.war.WarSoundscape;

import java.util.EnumMap;
import java.util.Map;

/**
 * Plays the war the {@link WarSoundscape} describes (Living War W9, W11): its looping beds streamed
 * and faded toward each turn's mix, panned to the side the fighting is on, and its one-shots rolled
 * once a turn. Sinks under Tarmin's Knell while he speaks.
 */
public final class WarAudio {

    /** Seconds for a bed to fade fully in or out. */
    static final float FADE = 1.5f;
    /** How far the war sinks while the Knell rings. */
    static final float KNELL_DUCK = 0.25f;

    private final Map<WarSoundscape.Bed, Music> beds = new EnumMap<>(WarSoundscape.Bed.class);
    private final Map<WarSoundscape.Bed, Float> level = new EnumMap<>(WarSoundscape.Bed.class);
    private WarSoundscape.Mix target = new WarSoundscape.Mix();

    /** This turn's mix: the beds fade toward it, and its one-shots may sound now. */
    public void onTurn(WarSoundscape.Mix mix, SoundManager sounds) {
        target = mix == null ? new WarSoundscape.Mix() : mix;
        if (sounds == null) return;
        for (WarSoundscape.Shot s : target.shots) {
            if (MathUtils.random() < s.chance) sounds.playWarSound(s.key, s.volume, s.pan);
        }
    }

    /** Every frame: beds ease toward the mix. {@code ducked}: Tarmin is speaking. */
    public void update(float delta, boolean ducked) {
        if (Gdx.audio == null || Gdx.files == null) return;
        float sfx = SoundManager.getInstance() != null ? SoundManager.getInstance().getEffectiveSfxVolume() : 0.8f;
        float duck = ducked ? KNELL_DUCK : 1f;
        for (WarSoundscape.Bed bed : WarSoundscape.Bed.values()) {
            float want = target.volume(bed);
            float now = level.getOrDefault(bed, 0f);
            float step = delta / FADE;
            now = want > now ? Math.min(want, now + step) : Math.max(want, now - step);
            level.put(bed, now);
            Music music = beds.get(bed);
            if (now <= 0.001f) {
                if (music != null && music.isPlaying()) music.pause();
                continue;
            }
            if (music == null) {
                music = open(bed);
                if (music == null) continue;
            }
            music.setPan(MathUtils.clamp(target.pan(bed), -1f, 1f), now * sfx * duck);
            if (!music.isPlaying()) music.play();
        }
    }

    private Music open(WarSoundscape.Bed bed) {
        FileHandle file = Gdx.files.internal("sounds/war/" + bed.file + ".ogg");
        if (!file.exists()) return null;
        try {
            Music m = Gdx.audio.newMusic(file);
            m.setLooping(true);
            beds.put(bed, m);
            return m;
        } catch (Exception e) {
            Gdx.app.error("WarAudio", "Cannot open " + file.path(), e);
            return null;
        }
    }

    /** Silence at once: leaving the game for a menu or the death screen. */
    public void stop() {
        target = new WarSoundscape.Mix();
        level.clear();
        for (Music m : beds.values()) m.pause();
    }

    public void dispose() {
        for (Music m : beds.values()) {
            try {
                m.stop();
                m.dispose();
            } catch (Exception ignored) {
            }
        }
        beds.clear();
    }
}
